#!/usr/bin/env python3
"""KismetMobile release helper (Python standard library only).

Gradle builds *unsigned* release APKs on purpose.  This tool is the only place
where an APK becomes installable, and it fails closed:

  stage          Verify Gradle's unsigned release APKs (package, version, flags,
                 alignment, really unsigned) and stage them with checksums.
                 Runs on the GitHub runner.
  sign           Verify the staged APKs again, optionally verify their GitHub
                 build attestation, sign them with the persistent release key,
                 and verify the result against the pinned certificate.
                 Runs on the maintainer's computer.
  verify-signed  Re-verify a signed release directory before publishing.

Passwords are never accepted on the command line; they come from environment
variables or an interactive prompt and are handed to apksigner through an
environment variable.  See RELEASING.md for the full procedure.
"""
from __future__ import annotations

import argparse
import dataclasses
import getpass
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path
from typing import Dict, List, Optional, Sequence

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_GRADLE_FILE = REPO_ROOT / "app" / "build.gradle.kts"
DEFAULT_PIN_FILE = REPO_ROOT / "signing" / "release-cert-sha256.txt"
DEFAULT_GITHUB_REPO = "primary-null/KismetMobile"
RELEASE_WORKFLOW = ".github/workflows/release.yml"

UNSIGNED_MANIFEST = "unsigned-manifest.json"
UNSIGNED_SUMS = "SHA256SUMS.unsigned"
SIGNED_MANIFEST = "release-manifest.json"
SIGNED_SUMS = "SHA256SUMS"

# The package names are the permanent identity of the app; changing one is a
# new app that cannot update an installed one, so it must fail this gate.
BASE_APPLICATION_ID = "com.kismet.mobile"

TAG_RE = re.compile(r"^v\d+\.\d+\.\d+(?:-[0-9A-Za-z]+(?:\.[0-9A-Za-z]+)*)?$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
GIT_SHA_RE = re.compile(r"^[0-9a-f]{40}$")
APK_SIG_BLOCK_MAGIC = b"APK Sig Block 42"
V1_SIGNATURE_ENTRY_RE = re.compile(
    r"^META-INF/[^/]+\.(SF|RSA|DSA|EC)$|^META-INF/MANIFEST\.MF$", re.IGNORECASE
)


class ReleaseError(Exception):
    """A release check failed; the message is shown to the maintainer."""


# --------------------------------------------------------------------------
# Release identity
# --------------------------------------------------------------------------

@dataclasses.dataclass(frozen=True)
class Flavor:
    key: str
    id_suffix: str
    version_suffix: str
    asset_stem: str

    def gradle_apk(self, apk_root: Path) -> Path:
        return apk_root / self.key / "release" / f"app-{self.key}-release-unsigned.apk"

    def application_id(self) -> str:
        return BASE_APPLICATION_ID + self.id_suffix

    def version_name(self, base_version_name: str) -> str:
        return base_version_name + self.version_suffix

    def unsigned_name(self, release_version: str) -> str:
        return f"{self.asset_stem}-{release_version}-unsigned.apk"

    def signed_name(self, release_version: str) -> str:
        return f"{self.asset_stem}-{release_version}.apk"


FLAVORS = (
    Flavor("standard", "", "", "KismetMobile"),
    Flavor("demo", ".demo", "-demo", "KismetMobile-demo"),
)


@dataclasses.dataclass(frozen=True)
class GradleIdentity:
    application_id: str
    version_code: int
    version_name: str
    min_sdk: int
    target_sdk: int


def read_gradle_identity(path: Path) -> GradleIdentity:
    """Read the single-source version and SDK levels from app/build.gradle.kts."""
    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        raise ReleaseError(f"cannot read {path}: {exc}") from exc

    def only(pattern: str, label: str) -> str:
        found = re.findall(pattern, text, flags=re.MULTILINE)
        if len(found) != 1:
            raise ReleaseError(f"expected exactly one {label} in {path.name}, found {len(found)}")
        return found[0]

    identity = GradleIdentity(
        application_id=only(r'^\s*applicationId\s*=\s*"([^"]+)"', "applicationId"),
        version_code=int(only(r"^\s*versionCode\s*=\s*(\d+)\s*$", "versionCode")),
        version_name=only(r'^\s*versionName\s*=\s*"([^"]+)"', "versionName"),
        min_sdk=int(only(r"^\s*minSdk\s*=\s*(\d+)\s*$", "minSdk")),
        target_sdk=int(only(r"^\s*targetSdk\s*=\s*(\d+)\s*$", "targetSdk")),
    )
    if identity.application_id != BASE_APPLICATION_ID:
        raise ReleaseError(
            f"applicationId is {identity.application_id!r} but the release identity is "
            f"{BASE_APPLICATION_ID!r}; a different package cannot update installed apps"
        )
    if identity.version_code < 1:
        raise ReleaseError("versionCode must be a positive integer")
    return identity


def check_tag(tag: Optional[str], version_name: str) -> str:
    """Return the release tag, which must be 'v' + versionName."""
    expected = f"v{version_name}"
    if tag is None:
        return expected
    if not TAG_RE.match(tag):
        raise ReleaseError(f"tag {tag!r} is not of the form vMAJOR.MINOR.PATCH[-prerelease]")
    if tag != expected:
        raise ReleaseError(f"tag {tag!r} does not match versionName {version_name!r} (expected {expected!r})")
    return tag


def is_prerelease(version_name: str) -> bool:
    return "-" in version_name


# --------------------------------------------------------------------------
# Android SDK tools
# --------------------------------------------------------------------------

def _version_key(name: str) -> tuple:
    parts = re.findall(r"\d+", name)
    return tuple(int(p) for p in parts)


class AndroidTools:
    """Locates aapt2, zipalign and apksigner from the Android SDK build-tools."""

    def __init__(self, build_tools: Path):
        self.dir = build_tools
        self.aapt2 = self._tool("aapt2")
        self.zipalign = self._tool("zipalign")
        self.apksigner = self._tool("apksigner")

    def _tool(self, name: str) -> str:
        found = shutil.which(name, path=str(self.dir))
        if not found:
            raise ReleaseError(f"{name} not found in {self.dir}")
        return found

    @classmethod
    def discover(cls, explicit: Optional[str] = None) -> "AndroidTools":
        candidates: List[Path] = []
        explicit = explicit or os.environ.get("ANDROID_BUILD_TOOLS")
        if explicit:
            candidates.append(Path(explicit))
        else:
            for var in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
                root = os.environ.get(var)
                if root and (Path(root) / "build-tools").is_dir():
                    versions = [p for p in (Path(root) / "build-tools").iterdir()
                                if p.is_dir() and re.fullmatch(r"\d+(\.\d+)*", p.name)]
                    versions.sort(key=lambda p: _version_key(p.name), reverse=True)
                    candidates.extend(versions)
                    break
        for candidate in candidates:
            try:
                return cls(candidate)
            except ReleaseError:
                continue
        raise ReleaseError(
            "Android SDK build-tools not found; set ANDROID_HOME or pass --build-tools"
        )


def run_tool(cmd: Sequence[str], *, env: Optional[Dict[str, str]] = None) -> subprocess.CompletedProcess:
    try:
        return subprocess.run(
            list(cmd), capture_output=True, text=True, encoding="utf-8", errors="replace", env=env
        )
    except OSError as exc:
        raise ReleaseError(f"cannot run {Path(cmd[0]).name}: {exc}") from exc


# --------------------------------------------------------------------------
# APK inspection
# --------------------------------------------------------------------------

@dataclasses.dataclass
class ApkInfo:
    package: str
    version_code: int
    version_name: str
    min_sdk: int
    target_sdk: int
    debuggable: bool
    test_only: bool
    permissions: List[str]


def _xml_bool(xmltree: str, attribute: str) -> bool:
    match = re.search(rf":{attribute}\(0x[0-9a-fA-F]+\)=(\S+)", xmltree)
    if not match:
        return False
    return match.group(1).lower() not in ("false", "0", "0x0", "0x00000000")


def parse_apk_info(badging: str, xmltree: str) -> ApkInfo:
    """Parse `aapt2 dump badging` and `aapt2 dump xmltree --file AndroidManifest.xml`."""
    package = re.search(r"^package: name='([^']*)' versionCode='(\d+)' versionName='([^']*)'", badging, re.M)
    if not package:
        raise ReleaseError("could not read the package line from aapt2 badging output")
    min_sdk = re.search(r"^minSdkVersion:'(\d+)'", badging, re.M)
    target_sdk = re.search(r"^targetSdkVersion:'(\d+)'", badging, re.M)
    if not min_sdk or not target_sdk:
        raise ReleaseError("could not read minSdkVersion/targetSdkVersion from aapt2 badging output")
    return ApkInfo(
        package=package.group(1),
        version_code=int(package.group(2)),
        version_name=package.group(3),
        min_sdk=int(min_sdk.group(1)),
        target_sdk=int(target_sdk.group(1)),
        debuggable=_xml_bool(xmltree, "debuggable") or bool(re.search(r"^application-debuggable", badging, re.M)),
        test_only=_xml_bool(xmltree, "testOnly"),
        permissions=sorted(set(re.findall(r"^uses-permission(?:-sdk-23)?: name='([^']+)'", badging, re.M))),
    )


def read_apk_info(tools: AndroidTools, apk: Path) -> ApkInfo:
    badging = run_tool([tools.aapt2, "dump", "badging", str(apk)])
    xmltree = run_tool([tools.aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)])
    if badging.returncode != 0 or xmltree.returncode != 0:
        raise ReleaseError(f"aapt2 could not read {apk.name}: {(badging.stderr or xmltree.stderr).strip()}")
    return parse_apk_info(badging.stdout, xmltree.stdout)


def check_apk_identity(
    info: ApkInfo, flavor: Flavor, gradle: GradleIdentity, apk_name: str, release_version: Optional[str] = None
) -> None:
    """Package, version, SDK levels and the debug/test flags must match the release identity."""
    release_version = release_version or gradle.version_name
    problems = []
    if info.package != flavor.application_id():
        problems.append(f"package is {info.package!r}, expected {flavor.application_id()!r}")
    if info.version_name != flavor.version_name(release_version):
        problems.append(f"versionName is {info.version_name!r}, expected {flavor.version_name(release_version)!r}")
    if info.version_code != gradle.version_code:
        problems.append(f"versionCode is {info.version_code}, expected {gradle.version_code}")
    if info.min_sdk != gradle.min_sdk:
        problems.append(f"minSdkVersion is {info.min_sdk}, expected {gradle.min_sdk}")
    if info.target_sdk != gradle.target_sdk:
        problems.append(f"targetSdkVersion is {info.target_sdk}, expected {gradle.target_sdk}")
    if info.debuggable:
        problems.append("application is debuggable")
    if info.test_only:
        problems.append("application is marked testOnly")
    if problems:
        raise ReleaseError(f"{apk_name}: " + "; ".join(problems))


def check_aligned(tools: AndroidTools, apk: Path) -> None:
    result = run_tool([tools.zipalign, "-c", "-P", "16", "4", str(apk)])
    if result.returncode != 0:
        raise ReleaseError(f"{apk.name} is not zip-aligned (4 bytes, 16 KiB for native libraries); "
                           f"zipalign must run before signing")


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def has_signing_block(path: Path) -> bool:
    magic_len = len(APK_SIG_BLOCK_MAGIC)
    tail = b""
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            if APK_SIG_BLOCK_MAGIC in tail + chunk:
                return True
            tail = chunk[-(magic_len - 1):]
    return False


def check_really_unsigned(tools: AndroidTools, apk: Path) -> None:
    """Gradle's output must carry no signature of any scheme, not even a debug one."""
    with zipfile.ZipFile(apk) as archive:
        v1 = [n for n in archive.namelist() if V1_SIGNATURE_ENTRY_RE.match(n)]
    if v1:
        raise ReleaseError(f"{apk.name} contains JAR signature files ({v1[0]}); it must be unsigned")
    if has_signing_block(apk):
        raise ReleaseError(f"{apk.name} contains an APK Signing Block; it must be unsigned")
    if run_tool([tools.apksigner, "verify", str(apk)]).returncode == 0:
        raise ReleaseError(f"{apk.name} verifies as signed; it must be unsigned")


def payload_entries(apk: Path) -> Dict[str, tuple]:
    """Entry name -> (crc32, size), ignoring v1 signature files."""
    with zipfile.ZipFile(apk) as archive:
        return {i.filename: (i.CRC, i.file_size) for i in archive.infolist()
                if not V1_SIGNATURE_ENTRY_RE.match(i.filename)}


def check_same_payload(unsigned: Path, signed: Path) -> None:
    """Signing may only add a signature; no entry may be added, removed or changed."""
    before, after = payload_entries(unsigned), payload_entries(signed)
    if before != after:
        differing = sorted(set(before) ^ set(after) | {k for k in before if k in after and before[k] != after[k]})
        raise ReleaseError(f"signed APK payload differs from unsigned APK ({differing[:3]})")


# --------------------------------------------------------------------------
# Signature policy
# --------------------------------------------------------------------------

@dataclasses.dataclass
class SignatureInfo:
    verified: bool
    schemes: Dict[str, bool]
    signer_count: int
    cert_dns: List[str]
    cert_sha256: List[str]
    detail: str = ""


def parse_apksigner_verify(stdout: str, returncode: int, stderr: str = "") -> SignatureInfo:
    schemes = {m.group(1): m.group(2) == "true"
               for m in re.finditer(r"^Verified using v(\d(?:\.\d)?) scheme .*: (true|false)\s*$", stdout, re.M)}
    count = re.search(r"^Number of signers: (\d+)", stdout, re.M)
    # Build-tools 37 labels certificates "V3.0 Signer:" instead of "Signer #1".
    labels = r"(?:Signer (?:#\d+|\(minSdkVersion=[^\r\n]+\))|V\d+(?:\.\d+)? Signer(?: #\d+)?):?"
    certificates = {}
    for match in re.finditer(rf"^({labels}) certificate (DN|SHA-256 digest): ([^\r\n]+)$", stdout, re.M):
        certificates.setdefault(match.group(1), {})[match.group(2)] = match.group(3).strip()
    unique = []
    for row in certificates.values():
        pair = (row.get("DN", ""), row.get("SHA-256 digest", "").lower())
        if pair not in unique:
            unique.append(pair)
    dns = [row[0] for row in unique]
    digests = [row[1] for row in unique]
    return SignatureInfo(
        verified=(returncode == 0 and "Verifies" in stdout),
        schemes=schemes,
        signer_count=int(count.group(1)) if count else 0,
        cert_dns=dns,
        cert_sha256=[d.lower() for d in digests],
        detail=(stderr or stdout).strip().splitlines()[0] if (stderr or stdout).strip() else "",
    )


def read_signature(tools: AndroidTools, apk: Path) -> SignatureInfo:
    result = run_tool([tools.apksigner, "verify", "--verbose", "--print-certs", str(apk)])
    return parse_apksigner_verify(result.stdout, result.returncode, result.stderr)


def check_release_signature(
    sig: SignatureInfo, apk_name: str, expected_sha256: str, *, allow_debug_certificate: bool = False
) -> None:
    """The APK must verify under v3 with exactly one signer: the pinned release certificate.

    allow_debug_certificate exists for the unit tests only; no command-line option exposes it.
    """
    if not sig.verified:
        raise ReleaseError(f"{apk_name} does not verify: {sig.detail}")
    if not sig.schemes.get("3", False):
        raise ReleaseError(f"{apk_name} is not signed with APK Signature Scheme v3 (needed for later key rotation)")
    if sig.signer_count != 1 or len(sig.cert_sha256) != 1:
        raise ReleaseError(f"{apk_name} has {sig.signer_count} signers; exactly one is required")
    if not allow_debug_certificate and any("CN=Android Debug" in dn for dn in sig.cert_dns):
        raise ReleaseError(f"{apk_name} is signed with an Android debug certificate")
    if sig.cert_sha256[0] != expected_sha256:
        raise ReleaseError(
            f"{apk_name} is signed by certificate {sig.cert_sha256[0]}, "
            f"which is not the pinned release certificate {expected_sha256}"
        )


def normalise_fingerprint(value: str) -> str:
    cleaned = re.sub(r"[\s:]", "", value).lower()
    if cleaned.startswith("sha256"):
        cleaned = cleaned[len("sha256"):]
    if not SHA256_RE.match(cleaned):
        raise ReleaseError("certificate fingerprint must be 64 hexadecimal digits (SHA-256)")
    return cleaned


def read_pinned_fingerprint(pin_file: Path) -> Optional[str]:
    """Return the committed fingerprint, or None while the placeholder is still in place."""
    if not pin_file.is_file():
        return None
    lines = [ln.strip() for ln in pin_file.read_text(encoding="utf-8").splitlines()
             if ln.strip() and not ln.strip().startswith("#")]
    if not lines:
        return None
    if len(lines) != 1:
        raise ReleaseError(f"{pin_file.name} must contain exactly one fingerprint line")
    return normalise_fingerprint(lines[0])


def resolve_expected_fingerprint(cli_value: Optional[str], pin_file: Path) -> str:
    pinned = read_pinned_fingerprint(pin_file)
    given = normalise_fingerprint(cli_value) if cli_value else None
    if given and pinned and given != pinned:
        raise ReleaseError("--expected-cert-sha256 differs from the fingerprint committed in "
                           f"{pin_file.name}; refusing to guess which one is the release identity")
    chosen = given or pinned
    if not chosen:
        raise ReleaseError(
            "no release certificate is pinned. Record the SHA-256 fingerprint of the release key in "
            f"{pin_file.name} (or pass --expected-cert-sha256 for the very first release)"
        )
    return chosen


# --------------------------------------------------------------------------
# Checksums and manifests
# --------------------------------------------------------------------------

def write_sums(path: Path, files: Sequence[Path]) -> None:
    lines = [f"{sha256_file(f)} *{f.name}\n" for f in sorted(files, key=lambda f: f.name)]
    path.write_text("".join(lines), encoding="utf-8", newline="\n")


def read_sums(path: Path) -> Dict[str, str]:
    sums: Dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64}) [ *](.+)", line)
        if not match:
            raise ReleaseError(f"{path.name}: malformed line {line!r}")
        sums[match.group(2)] = match.group(1)
    return sums


def check_sums(directory: Path, sums_name: str, expected_files: Sequence[str]) -> None:
    sums = read_sums(directory / sums_name)
    if sorted(sums) != sorted(expected_files):
        raise ReleaseError(f"{sums_name} lists {sorted(sums)}, expected {sorted(expected_files)}")
    for name, digest in sums.items():
        actual = sha256_file(directory / name)
        if actual != digest:
            raise ReleaseError(f"{name}: SHA-256 {actual} does not match {sums_name}")


def write_json(path: Path, document: dict) -> None:
    path.write_text(json.dumps(document, indent=2, sort_keys=True) + "\n", encoding="utf-8", newline="\n")


def read_manifest(directory: Path, name: str, stage: str) -> dict:
    try:
        manifest = json.loads((directory / name).read_text(encoding="utf-8"))
    except (OSError, ValueError) as exc:
        raise ReleaseError(f"cannot read {directory / name}: {exc}") from exc
    if manifest.get("schema") != 1 or manifest.get("stage") != stage:
        raise ReleaseError(f"{name} is not a schema-1 {stage} manifest")
    return manifest


def flavor_by_key(key: str) -> Flavor:
    for flavor in FLAVORS:
        if flavor.key == key:
            return flavor
    raise ReleaseError(f"unknown flavor {key!r} in manifest")


# --------------------------------------------------------------------------
# stage
# --------------------------------------------------------------------------

def stage_unsigned(
    tools: AndroidTools, gradle: GradleIdentity, apk_root: Path, out_dir: Path,
    tag: Optional[str], source_sha: Optional[str],
) -> dict:
    tag = check_tag(tag, gradle.version_name)
    if source_sha is not None and not GIT_SHA_RE.match(source_sha):
        raise ReleaseError("--source-sha must be a full 40-digit lowercase commit SHA")
    if out_dir.exists() and any(out_dir.iterdir()):
        raise ReleaseError(f"{out_dir} already exists and is not empty")
    out_dir.mkdir(parents=True, exist_ok=True)

    artifacts = []
    staged: List[Path] = []
    for flavor in FLAVORS:
        source = flavor.gradle_apk(apk_root)
        if not source.is_file():
            raise ReleaseError(f"missing Gradle output {source}")
        check_really_unsigned(tools, source)
        check_aligned(tools, source)
        info = read_apk_info(tools, source)
        check_apk_identity(info, flavor, gradle, source.name)
        target = out_dir / flavor.unsigned_name(gradle.version_name)
        shutil.copyfile(source, target)
        staged.append(target)
        artifacts.append({
            "flavor": flavor.key,
            "file": target.name,
            "application_id": info.package,
            "version_name": info.version_name,
            "version_code": info.version_code,
            "sha256": sha256_file(target),
            "size": target.stat().st_size,
            "permissions": info.permissions,
        })
    manifest = {
        "schema": 1,
        "stage": "unsigned",
        "tag": tag,
        "version_name": gradle.version_name,
        "version_code": gradle.version_code,
        "prerelease": is_prerelease(gradle.version_name),
        "min_sdk": gradle.min_sdk,
        "target_sdk": gradle.target_sdk,
        "source_sha": source_sha,
        "artifacts": artifacts,
    }
    write_json(out_dir / UNSIGNED_MANIFEST, manifest)
    write_sums(out_dir / UNSIGNED_SUMS, staged)
    return manifest


# --------------------------------------------------------------------------
# sign
# --------------------------------------------------------------------------

def verify_attestation(apk: Path, repo: str, source_sha: Optional[str]) -> None:
    gh = shutil.which("gh")
    if not gh:
        raise ReleaseError("the GitHub CLI (gh) is required to verify build attestations; "
                           "install it or, for a local dry run only, pass --skip-attestation-check")
    cmd = [gh, "attestation", "verify", str(apk), "--repo", repo,
           "--signer-workflow", f"{repo}/{RELEASE_WORKFLOW}", "--deny-self-hosted-runners"]
    if source_sha:
        cmd += ["--source-digest", source_sha]
    result = run_tool(cmd)
    if result.returncode != 0:
        raise ReleaseError(f"build attestation check failed for {apk.name}: "
                           f"{(result.stderr or result.stdout).strip()[:300]}")


def read_password(env_name: str, prompt: str) -> str:
    value = os.environ.get(env_name)
    if value:
        return value
    if sys.stdin is not None and sys.stdin.isatty():
        value = getpass.getpass(prompt)
        if value:
            return value
    raise ReleaseError(f"set the {env_name} environment variable (or run interactively) to unlock the keystore")


def keystore_outside_repo(keystore: Path) -> Path:
    resolved = keystore.resolve()
    if not resolved.is_file():
        raise ReleaseError(f"keystore {keystore} does not exist")
    try:
        resolved.relative_to(REPO_ROOT)
    except ValueError:
        return resolved
    raise ReleaseError("the keystore must be stored outside the repository working tree")


def sign_staged(
    tools: AndroidTools,
    unsigned_dir: Path,
    out_dir: Path,
    keystore: Path,
    alias: str,
    ks_pass_env: str,
    key_pass_env: Optional[str],
    expected_sha256: str,
    *,
    gradle: Optional[GradleIdentity] = None,
    attestation_repo: Optional[str] = DEFAULT_GITHUB_REPO,
    allow_debug_certificate: bool = False,
    environment: Optional[Dict[str, str]] = None,
) -> dict:
    environment = dict(os.environ if environment is None else environment)
    manifest = read_manifest(unsigned_dir, UNSIGNED_MANIFEST, "unsigned")
    if out_dir.exists() and any(out_dir.iterdir()):
        raise ReleaseError(f"{out_dir} already exists and is not empty")
    if ks_pass_env not in environment:
        raise ReleaseError(f"{ks_pass_env} is not set")
    key_pass_env = key_pass_env or ks_pass_env
    if key_pass_env not in environment:
        raise ReleaseError(f"{key_pass_env} is not set")

    version = manifest["version_name"]
    tag = check_tag(manifest["tag"], version)
    if gradle is None:
        gradle = GradleIdentity(BASE_APPLICATION_ID, manifest["version_code"], version,
                                manifest["min_sdk"], manifest["target_sdk"])
    elif (gradle.version_name, gradle.version_code) != (version, manifest["version_code"]):
        raise ReleaseError("the staged APKs are for a different version than app/build.gradle.kts")
    if sorted(a["flavor"] for a in manifest["artifacts"]) != sorted(f.key for f in FLAVORS):
        raise ReleaseError("the unsigned manifest must list exactly the standard and demo APKs")
    if manifest["min_sdk"] < 28:
        raise ReleaseError("minSdk is below 28, which needs v1/v2 signatures as well as v3; extend sign_staged first")
    check_sums(unsigned_dir, UNSIGNED_SUMS, [a["file"] for a in manifest["artifacts"]])

    # 1. Re-verify every input before the key is touched.
    for artifact in manifest["artifacts"]:
        flavor = flavor_by_key(artifact["flavor"])
        apk = unsigned_dir / artifact["file"]
        if sha256_file(apk) != artifact["sha256"]:
            raise ReleaseError(f"{apk.name} differs from the manifest")
        if artifact["file"] != flavor.unsigned_name(version):
            raise ReleaseError(f"unexpected file name {artifact['file']!r}")
        check_really_unsigned(tools, apk)
        check_aligned(tools, apk)
        check_apk_identity(read_apk_info(tools, apk), flavor, gradle, apk.name, version)
        if attestation_repo:
            verify_attestation(apk, attestation_repo, manifest.get("source_sha"))

    # 2. Sign into a scratch directory beside the output; publish it only if every check passes.
    out_dir.parent.mkdir(parents=True, exist_ok=True)
    scratch = Path(tempfile.mkdtemp(prefix=".signing-", dir=out_dir.parent))
    try:
        signed_files: List[Path] = []
        records = []
        for artifact in manifest["artifacts"]:
            flavor = flavor_by_key(artifact["flavor"])
            source = unsigned_dir / artifact["file"]
            target = scratch / flavor.signed_name(version)
            result = run_tool(
                [tools.apksigner, "sign", "--ks", str(keystore), "--ks-key-alias", alias,
                 "--ks-pass", f"env:{ks_pass_env}", "--key-pass", f"env:{key_pass_env}",
                 # minSdk >= 28: every supported device verifies v3, and apksigner then emits v3 only
                 # (it ignores --v2-signing-enabled).  v3 is required so the signer can be rotated later.
                 "--v1-signing-enabled", "false", "--v3-signing-enabled", "true",
                 "--v4-signing-enabled", "false", "--out", str(target), str(source)],
                env=environment,
            )
            if result.returncode != 0 or not target.is_file():
                raise ReleaseError(f"apksigner could not sign {source.name}: "
                                   f"{(result.stderr or result.stdout).strip()[:300]}")
            check_release_signature(read_signature(tools, target), target.name, expected_sha256,
                                    allow_debug_certificate=allow_debug_certificate)
            check_aligned(tools, target)
            check_same_payload(source, target)
            check_apk_identity(read_apk_info(tools, target), flavor, gradle, target.name, version)
            signed_files.append(target)
            schemes = sorted(f"v{k}" for k, ok in read_signature(tools, target).schemes.items() if ok)
            records.append({
                "flavor": flavor.key,
                "file": target.name,
                "application_id": artifact["application_id"],
                "version_name": artifact["version_name"],
                "version_code": artifact["version_code"],
                "sha256": sha256_file(target),
                "size": target.stat().st_size,
                "unsigned_sha256": artifact["sha256"],
                "signature_schemes": schemes,
            })
        signed_manifest = {
            "schema": 1,
            "stage": "signed",
            "tag": tag,
            "version_name": version,
            "version_code": manifest["version_code"],
            "prerelease": manifest["prerelease"],
            "min_sdk": manifest["min_sdk"],
            "target_sdk": manifest["target_sdk"],
            "source_sha": manifest.get("source_sha"),
            "signing_certificate_sha256": expected_sha256,
            "attestation_verified": bool(attestation_repo),
            "artifacts": records,
        }
        write_json(scratch / SIGNED_MANIFEST, signed_manifest)
        write_sums(scratch / SIGNED_SUMS, signed_files)
        if out_dir.exists():
            out_dir.rmdir()
        scratch.rename(out_dir)
        return signed_manifest
    except BaseException:
        shutil.rmtree(scratch, ignore_errors=True)
        raise


# --------------------------------------------------------------------------
# verify-signed
# --------------------------------------------------------------------------

def verify_signed_dir(
    tools: AndroidTools, directory: Path, expected_sha256: str,
    *, gradle: Optional[GradleIdentity] = None, allow_debug_certificate: bool = False,
) -> dict:
    manifest = read_manifest(directory, SIGNED_MANIFEST, "signed")
    if manifest.get("signing_certificate_sha256") != expected_sha256:
        raise ReleaseError("manifest signing certificate is not the pinned release certificate")
    version = manifest["version_name"]
    check_tag(manifest["tag"], version)
    if gradle is None:
        gradle = GradleIdentity(BASE_APPLICATION_ID, manifest["version_code"], version,
                                manifest["min_sdk"], manifest["target_sdk"])
    names = [a["file"] for a in manifest["artifacts"]]
    check_sums(directory, SIGNED_SUMS, names)
    expected_extras = set(names) | {SIGNED_MANIFEST, SIGNED_SUMS}
    unexpected = sorted(p.name for p in directory.iterdir() if p.name not in expected_extras)
    if unexpected:
        raise ReleaseError(f"unexpected files in release directory: {unexpected}")
    if sorted(a["flavor"] for a in manifest["artifacts"]) != sorted(f.key for f in FLAVORS):
        raise ReleaseError("the release must contain exactly the standard and demo APKs")
    for artifact in manifest["artifacts"]:
        flavor = flavor_by_key(artifact["flavor"])
        apk = directory / artifact["file"]
        if artifact["file"] != flavor.signed_name(version):
            raise ReleaseError(f"unexpected file name {artifact['file']!r}")
        if sha256_file(apk) != artifact["sha256"]:
            raise ReleaseError(f"{apk.name} differs from the manifest")
        check_release_signature(read_signature(tools, apk), apk.name, expected_sha256,
                                allow_debug_certificate=allow_debug_certificate)
        check_aligned(tools, apk)
        check_apk_identity(read_apk_info(tools, apk), flavor, gradle, apk.name, version)
    return manifest


# --------------------------------------------------------------------------
# Command line
# --------------------------------------------------------------------------

def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--build-tools", help="Android SDK build-tools directory (default: newest under ANDROID_HOME)")
    parser.add_argument("--gradle-file", type=Path, default=DEFAULT_GRADLE_FILE, help=argparse.SUPPRESS)
    sub = parser.add_subparsers(dest="command", required=True)

    stage = sub.add_parser("stage", help="verify and stage Gradle's unsigned release APKs")
    stage.add_argument("--apk-dir", type=Path, default=REPO_ROOT / "app" / "build" / "outputs" / "apk")
    stage.add_argument("--out", type=Path, default=REPO_ROOT / "dist" / "unsigned")
    stage.add_argument("--tag", help="release tag; must equal v + versionName")
    stage.add_argument("--source-sha", help="full commit SHA the APKs were built from")

    sign = sub.add_parser("sign", help="sign staged APKs with the persistent release key")
    sign.add_argument("--unsigned-dir", type=Path, default=REPO_ROOT / "dist" / "unsigned")
    sign.add_argument("--out", type=Path, default=REPO_ROOT / "dist" / "signed")
    sign.add_argument("--keystore", type=Path, required=True, help="release keystore, stored outside the repository")
    sign.add_argument("--key-alias", required=True)
    sign.add_argument("--ks-pass-env", default="KISMET_KS_PASS", help="environment variable holding the keystore password")
    sign.add_argument("--key-pass-env", help="environment variable holding the key password (default: keystore password)")
    sign.add_argument("--expected-cert-sha256", help="release certificate SHA-256 (first release only; otherwise "
                                                     "signing/release-cert-sha256.txt is used)")
    sign.add_argument("--pin-file", type=Path, default=DEFAULT_PIN_FILE)
    sign.add_argument("--github-repo", default=DEFAULT_GITHUB_REPO)
    sign.add_argument("--skip-attestation-check", action="store_true",
                      help="local dry runs only: do not verify the GitHub build attestation")

    verify = sub.add_parser("verify-signed", help="re-verify a signed release directory")
    verify.add_argument("--dir", type=Path, default=REPO_ROOT / "dist" / "signed")
    verify.add_argument("--expected-cert-sha256")
    verify.add_argument("--pin-file", type=Path, default=DEFAULT_PIN_FILE)
    return parser


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = build_parser().parse_args(argv)
    try:
        tools = AndroidTools.discover(args.build_tools)
        gradle = read_gradle_identity(args.gradle_file)
        if args.command == "stage":
            manifest = stage_unsigned(tools, gradle, args.apk_dir, args.out, args.tag, args.source_sha)
            print(f"staged {len(manifest['artifacts'])} unsigned APKs for {manifest['tag']} in {args.out}")
        elif args.command == "sign":
            expected = resolve_expected_fingerprint(args.expected_cert_sha256, args.pin_file)
            keystore = keystore_outside_repo(args.keystore)
            password = read_password(args.ks_pass_env, "Keystore password: ")
            environment = dict(os.environ, **{args.ks_pass_env: password})
            if args.key_pass_env and args.key_pass_env != args.ks_pass_env:
                environment[args.key_pass_env] = read_password(args.key_pass_env, "Key password: ")
            if args.skip_attestation_check:
                print("WARNING: build attestation not verified (--skip-attestation-check); "
                      "do not publish these files", file=sys.stderr)
            manifest = sign_staged(
                tools, args.unsigned_dir, args.out, keystore, args.key_alias,
                args.ks_pass_env, args.key_pass_env, expected,
                gradle=gradle,
                attestation_repo=None if args.skip_attestation_check else args.github_repo,
                environment=environment,
            )
            print(f"signed {len(manifest['artifacts'])} APKs for {manifest['tag']} in {args.out}")
            print(f"signing certificate SHA-256: {expected}")
        else:
            expected = resolve_expected_fingerprint(args.expected_cert_sha256, args.pin_file)
            manifest = verify_signed_dir(tools, args.dir, expected, gradle=gradle)
            print(f"verified {len(manifest['artifacts'])} signed APKs for {manifest['tag']}")
        for artifact in manifest["artifacts"]:
            print(f"  {artifact['sha256']}  {artifact['file']}")
        return 0
    except ReleaseError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
