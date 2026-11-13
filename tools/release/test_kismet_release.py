"""Tests for kismet_release.py.

Pure unit tests always run.  Integration tests need the Android SDK build-tools
and the unsigned release APKs from `./gradlew :app:assembleStandardRelease
:app:assembleDemoRelease`; they are skipped when those are missing.  The signing
integration tests use the Android SDK's own debug keystore (well-known password)
through a test-only policy switch; no release key exists in or near this repository.
"""
from __future__ import annotations

import contextlib
import io
import os
import re
import shutil
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

sys.path.insert(0, str(Path(__file__).resolve().parent))
import kismet_release as kr  # noqa: E402

APK_ROOT = kr.REPO_ROOT / "app" / "build" / "outputs" / "apk"
DEBUG_KEYSTORE = Path.home() / ".android" / "debug.keystore"
DEBUG_ALIAS = "androiddebugkey"
DEBUG_PASSWORD = "android"  # the SDK's public debug-keystore password, not a secret

RELEASE_BADGING = (
    "package: name='com.kismet.mobile' versionCode='10' versionName='1.4.0-rc2' "
    "platformBuildVersionName='16'\n"
    "minSdkVersion:'29'\ntargetSdkVersion:'36'\n"
    "uses-permission: name='android.permission.INTERNET'\n"
    "uses-permission: name='android.permission.BLUETOOTH' maxSdkVersion='30'\n"
)
RELEASE_XML = (
    '    A: http://schemas.android.com/apk/res/android:allowBackup(0x01010280)=false\n'
)
DEBUG_XML = (
    '    A: http://schemas.android.com/apk/res/android:debuggable(0x0101000f)=true\n'
)
GOOD_VERIFY = """Verifies
Verified using v1 scheme (JAR signing): false
Verified using v2 scheme (APK Signature Scheme v2): true
Verified using v3 scheme (APK Signature Scheme v3): true
Verified using v3.1 scheme (APK Signature Scheme v3.1): false
Verified using v4 scheme (APK Signature Scheme v4): false
Verified for SourceStamp: false
Number of signers: 1
Signer #1 certificate DN: CN=KismetMobile Release
Signer #1 certificate SHA-256 digest: {fp}
Signer #1 certificate SHA-1 digest: dd7444cc8b4b2dc1ad40a7b4535ef8408bf3c769
"""
FP = "ab" * 32


def good_signature(**overrides) -> kr.SignatureInfo:
    sig = kr.parse_apksigner_verify(GOOD_VERIFY.format(fp=FP), 0)
    for key, value in overrides.items():
        setattr(sig, key, value)
    return sig


class GradleIdentityTests(unittest.TestCase):
    def test_reads_the_real_build_file(self):
        identity = kr.read_gradle_identity(kr.DEFAULT_GRADLE_FILE)
        self.assertEqual(identity.application_id, "com.kismet.mobile")
        self.assertGreaterEqual(identity.version_code, 1)
        self.assertRegex(identity.version_name, r"^\d+\.\d+\.\d+")
        self.assertEqual(identity.min_sdk, 29)

    def _write(self, text: str) -> Path:
        directory = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, directory, ignore_errors=True)
        path = directory / "build.gradle.kts"
        path.write_text(text, encoding="utf-8")
        return path

    BASE = ('applicationId = "com.kismet.mobile"\nversionCode = 3\nversionName = "1.0.0"\n'
            'minSdk = 29\ntargetSdk = 36\n')

    def test_rejects_duplicate_and_missing_fields(self):
        with self.assertRaises(kr.ReleaseError):
            kr.read_gradle_identity(self._write(self.BASE + 'versionName = "2.0.0"\n'))
        with self.assertRaises(kr.ReleaseError):
            kr.read_gradle_identity(self._write(self.BASE.replace("minSdk = 29\n", "")))

    def test_rejects_a_changed_package_name(self):
        with self.assertRaisesRegex(kr.ReleaseError, "cannot update"):
            kr.read_gradle_identity(self._write(self.BASE.replace("com.kismet.mobile", "org.example.kismet")))

    def test_version_name_suffix_is_not_the_version(self):
        text = self.BASE + '        versionNameSuffix = "-demo"\n'
        self.assertEqual(kr.read_gradle_identity(self._write(text)).version_name, "1.0.0")


class TagTests(unittest.TestCase):
    def test_tag_must_be_v_plus_version_name(self):
        self.assertEqual(kr.check_tag("v1.4.0-rc2", "1.4.0-rc2"), "v1.4.0-rc2")
        self.assertEqual(kr.check_tag(None, "1.4.0"), "v1.4.0")
        for bad in ("1.4.0-rc2", "v1.4", "v1.4.0-rc2-extra", "v1.4.0 ", "refs/tags/v1.4.0-rc2"):
            with self.assertRaises(kr.ReleaseError, msg=bad):
                kr.check_tag(bad, "1.4.0-rc2")

    def test_prerelease_follows_the_version_name(self):
        self.assertTrue(kr.is_prerelease("1.4.0-rc2"))
        self.assertFalse(kr.is_prerelease("1.4.0"))


class ApkInfoTests(unittest.TestCase):
    gradle = kr.GradleIdentity("com.kismet.mobile", 10, "1.4.0-rc2", 29, 36)

    def test_parses_a_release_manifest(self):
        info = kr.parse_apk_info(RELEASE_BADGING, RELEASE_XML)
        self.assertEqual((info.package, info.version_code, info.version_name), ("com.kismet.mobile", 10, "1.4.0-rc2"))
        self.assertEqual((info.min_sdk, info.target_sdk), (29, 36))
        self.assertFalse(info.debuggable or info.test_only)
        self.assertEqual(info.permissions, ["android.permission.BLUETOOTH", "android.permission.INTERNET"])
        kr.check_apk_identity(info, kr.FLAVORS[0], self.gradle, "x.apk")

    def test_detects_debuggable_and_test_only(self):
        self.assertTrue(kr.parse_apk_info(RELEASE_BADGING, DEBUG_XML).debuggable)
        self.assertTrue(kr.parse_apk_info(RELEASE_BADGING + "application-debuggable\n", RELEASE_XML).debuggable)
        test_only = '    A: http://schemas.android.com/apk/res/android:testOnly(0x01010272)=true\n'
        self.assertTrue(kr.parse_apk_info(RELEASE_BADGING, test_only).test_only)
        explicit_false = '    A: http://schemas.android.com/apk/res/android:debuggable(0x0101000f)=false\n'
        self.assertFalse(kr.parse_apk_info(RELEASE_BADGING, explicit_false).debuggable)

    def test_unreadable_output_is_an_error(self):
        with self.assertRaises(kr.ReleaseError):
            kr.parse_apk_info("nothing useful", "")

    def test_identity_mismatches_are_all_reported(self):
        info = kr.parse_apk_info(RELEASE_BADGING.replace("versionCode='10'", "versionCode='9'"), DEBUG_XML)
        with self.assertRaises(kr.ReleaseError) as caught:
            kr.check_apk_identity(info, kr.FLAVORS[1], self.gradle, "demo.apk")
        message = str(caught.exception)
        for fragment in ("package is", "versionName is", "versionCode is 9", "debuggable"):
            self.assertIn(fragment, message)

    def test_demo_identity(self):
        badging = RELEASE_BADGING.replace("name='com.kismet.mobile'", "name='com.kismet.mobile.demo'") \
                                 .replace("1.4.0-rc2'", "1.4.0-rc2-demo'")
        kr.check_apk_identity(kr.parse_apk_info(badging, RELEASE_XML), kr.FLAVORS[1], self.gradle, "demo.apk")


class SignaturePolicyTests(unittest.TestCase):
    def test_accepts_the_pinned_v3_signature_with_or_without_v2(self):
        kr.check_release_signature(good_signature(), "a.apk", FP)
        kr.check_release_signature(good_signature(schemes={"2": True, "3": True}), "a.apk", FP)

    def test_rejections(self):
        cases = {
            "unverified": good_signature(verified=False),
            "v2 only, no rotation path": good_signature(schemes={"2": True, "3": False}),
            "v1 only": good_signature(schemes={"1": True, "2": False, "3": False}),
            "two signers": good_signature(signer_count=2, cert_sha256=[FP, FP]),
            "debug certificate": good_signature(cert_dns=["C=US, O=Android, CN=Android Debug"]),
            "other certificate": good_signature(cert_sha256=["cd" * 32]),
        }
        for label, sig in cases.items():
            with self.subTest(label), self.assertRaises(kr.ReleaseError):
                kr.check_release_signature(sig, "a.apk", FP)

    def test_debug_certificate_switch_is_test_only(self):
        sig = good_signature(cert_dns=["CN=Android Debug"])
        kr.check_release_signature(sig, "a.apk", FP, allow_debug_certificate=True)
        for command in ("stage", "sign", "verify-signed"):
            buffer = io.StringIO()
            with contextlib.redirect_stdout(buffer), self.assertRaises(SystemExit):
                kr.build_parser().parse_args([command, "--help"])
            self.assertNotIn("debug", buffer.getvalue().lower(), command)

    def test_failed_verification_is_reported_as_unverified(self):
        sig = kr.parse_apksigner_verify("DOES NOT VERIFY\nERROR: Missing META-INF/MANIFEST.MF\n", 1)
        self.assertFalse(sig.verified)
        self.assertEqual(sig.signer_count, 0)

    def test_build_tools_37_signer_labels(self):
        old = GOOD_VERIFY.format(fp=FP)
        current = old.replace("Signer #1", "V3.0 Signer:")
        duplicate = old[old.index("Signer #1 certificate DN:"):].replace("Signer #1", "V3.1 Signer:")
        stamp = old[old.index("Signer #1 certificate DN:"):].replace("Signer #1", "Source Stamp Signer:")
        self.assertEqual(kr.parse_apksigner_verify(old, 0), kr.parse_apksigner_verify(current + duplicate + stamp, 0))

    def test_different_sdk_range_certificates_are_not_merged(self):
        old = GOOD_VERIFY.format(fp=FP)
        first = old.replace("Signer #1", "Signer (minSdkVersion=28, maxSdkVersion=32)")
        second = old[old.index("Signer #1 certificate DN:"):].replace(
            "Signer #1", "Signer (minSdkVersion=33, maxSdkVersion=2147483647)").replace(FP, "cd" * 32)
        self.assertEqual(2, len(kr.parse_apksigner_verify(first + second, 0).cert_sha256))


class FingerprintTests(unittest.TestCase):
    def _pin(self, text: str) -> Path:
        directory = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, directory, ignore_errors=True)
        path = directory / "pin.txt"
        path.write_text(text, encoding="utf-8")
        return path

    def test_normalises_common_spellings(self):
        colon = ":".join(FP[i:i + 2].upper() for i in range(0, 64, 2))
        for form in (FP, FP.upper(), colon, "SHA256: " + colon, "SHA256:" + colon):
            self.assertEqual(kr.normalise_fingerprint(form), FP, form)
        for bad in ("", "abc", "g" * 64, FP + "00"):
            with self.assertRaises(kr.ReleaseError, msg=bad):
                kr.normalise_fingerprint(bad)

    def test_committed_pin_file_is_a_placeholder_or_one_valid_fingerprint(self):
        # None until the release key exists; afterwards exactly one SHA-256 (the parser raises otherwise).
        pinned = kr.read_pinned_fingerprint(kr.DEFAULT_PIN_FILE)
        self.assertTrue(pinned is None or kr.SHA256_RE.match(pinned), pinned)
        self.assertNotIn("PRIVATE KEY", kr.DEFAULT_PIN_FILE.read_text(encoding="utf-8"))

    def test_pin_file_rules(self):
        self.assertEqual(kr.read_pinned_fingerprint(self._pin(f"# comment\n{FP}\n")), FP)
        with self.assertRaises(kr.ReleaseError):
            kr.read_pinned_fingerprint(self._pin(f"{FP}\n{'cd' * 32}\n"))

    def test_resolution_is_fail_closed(self):
        empty = self._pin("# nothing yet\n")
        with self.assertRaisesRegex(kr.ReleaseError, "no release certificate is pinned"):
            kr.resolve_expected_fingerprint(None, empty)
        self.assertEqual(kr.resolve_expected_fingerprint(FP.upper(), empty), FP)
        pinned = self._pin(FP + "\n")
        self.assertEqual(kr.resolve_expected_fingerprint(None, pinned), FP)
        self.assertEqual(kr.resolve_expected_fingerprint(FP, pinned), FP)
        with self.assertRaisesRegex(kr.ReleaseError, "differs"):
            kr.resolve_expected_fingerprint("cd" * 32, pinned)


class ChecksumTests(unittest.TestCase):
    def test_round_trip_and_tamper_detection(self):
        directory = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, directory, ignore_errors=True)
        (directory / "a.apk").write_bytes(b"alpha")
        (directory / "b.apk").write_bytes(b"beta")
        kr.write_sums(directory / "SHA256SUMS", [directory / "a.apk", directory / "b.apk"])
        kr.check_sums(directory, "SHA256SUMS", ["a.apk", "b.apk"])
        with self.assertRaises(kr.ReleaseError):
            kr.check_sums(directory, "SHA256SUMS", ["a.apk"])
        (directory / "b.apk").write_bytes(b"beta!")
        with self.assertRaisesRegex(kr.ReleaseError, "does not match"):
            kr.check_sums(directory, "SHA256SUMS", ["a.apk", "b.apk"])

    def test_sums_use_the_sha256sum_text_format(self):
        directory = Path(tempfile.mkdtemp())
        self.addCleanup(shutil.rmtree, directory, ignore_errors=True)
        (directory / "a.apk").write_bytes(b"alpha")
        kr.write_sums(directory / "SHA256SUMS", [directory / "a.apk"])
        text = (directory / "SHA256SUMS").read_bytes().decode()
        self.assertRegex(text, r"^[0-9a-f]{64} \*a\.apk\n$")
        self.assertNotIn("\r", text)


class SafetyTests(unittest.TestCase):
    def test_keystore_inside_the_repository_is_refused(self):
        inside = kr.REPO_ROOT / "dist" / "test-keystore.jks"
        inside.parent.mkdir(exist_ok=True)
        inside.write_bytes(b"not a keystore")
        self.addCleanup(inside.unlink)
        with self.assertRaisesRegex(kr.ReleaseError, "outside the repository"):
            kr.keystore_outside_repo(inside)

    def test_missing_keystore_is_refused(self):
        with self.assertRaisesRegex(kr.ReleaseError, "does not exist"):
            kr.keystore_outside_repo(Path(tempfile.gettempdir()) / "no-such-keystore.p12")

    def test_attestation_command_pins_workflow_repo_and_commit(self):
        captured = []

        def fake_run(cmd, **_):
            captured.append(list(cmd))
            return mock.Mock(returncode=0, stdout="ok", stderr="")

        sha = "1" * 40
        with mock.patch.object(kr, "run_tool", fake_run), mock.patch.object(kr.shutil, "which", return_value="gh"):
            kr.verify_attestation(Path("x.apk"), "o/r", sha)
        cmd = captured[0]
        self.assertEqual(cmd[1:3], ["attestation", "verify"])
        self.assertIn("--deny-self-hosted-runners", cmd)
        self.assertEqual(cmd[cmd.index("--repo") + 1], "o/r")
        self.assertEqual(cmd[cmd.index("--signer-workflow") + 1], "o/r/.github/workflows/release.yml")
        self.assertEqual(cmd[cmd.index("--source-digest") + 1], sha)

    def test_attestation_failure_and_missing_gh_are_fatal(self):
        with mock.patch.object(kr, "run_tool", return_value=mock.Mock(returncode=1, stdout="", stderr="no attestation")), \
                mock.patch.object(kr.shutil, "which", return_value="gh"):
            with self.assertRaisesRegex(kr.ReleaseError, "attestation check failed"):
                kr.verify_attestation(Path("x.apk"), "o/r", None)
        with mock.patch.object(kr.shutil, "which", return_value=None):
            with self.assertRaisesRegex(kr.ReleaseError, "gh"):
                kr.verify_attestation(Path("x.apk"), "o/r", None)

    def test_password_is_required_from_the_environment_when_not_interactive(self):
        with mock.patch.dict(os.environ, {}, clear=True), mock.patch.object(kr.sys, "stdin", None):
            with self.assertRaises(kr.ReleaseError):
                kr.read_password("KISMET_KS_PASS", "prompt")


def _tools_or_none():
    try:
        return kr.AndroidTools.discover()
    except kr.ReleaseError:
        return None


def _debug_keystore_fingerprint():
    """SHA-256 of the SDK debug certificate, read with keytool from the well-known debug keystore."""
    keytool = shutil.which("keytool")
    if not keytool and os.environ.get("JAVA_HOME"):
        keytool = shutil.which("keytool", path=str(Path(os.environ["JAVA_HOME"]) / "bin"))
    if not keytool or not DEBUG_KEYSTORE.is_file():
        return None
    result = kr.run_tool([keytool, "-list", "-v", "-keystore", str(DEBUG_KEYSTORE), "-alias", DEBUG_ALIAS,
                          "-storepass", DEBUG_PASSWORD])
    match = re.search(r"SHA256:\s*([0-9A-Fa-f:]{95})", result.stdout)
    return kr.normalise_fingerprint(match.group(1)) if match else None


TOOLS = _tools_or_none()
HAVE_APKS = all(f.gradle_apk(APK_ROOT).is_file() for f in kr.FLAVORS)


@unittest.skipUnless(TOOLS and HAVE_APKS, "needs Android build-tools and unsigned release APKs from Gradle")
class StageIntegrationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = Path(tempfile.mkdtemp())
        cls.gradle = kr.read_gradle_identity(kr.DEFAULT_GRADLE_FILE)
        cls.unsigned = cls.temp / "unsigned"
        cls.manifest = kr.stage_unsigned(TOOLS, cls.gradle, APK_ROOT, cls.unsigned, None, "a" * 40)

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.temp, ignore_errors=True)

    def test_stage_writes_named_verified_artifacts(self):
        version = self.gradle.version_name
        files = sorted(p.name for p in self.unsigned.iterdir())
        self.assertEqual(files, sorted([
            kr.UNSIGNED_MANIFEST, kr.UNSIGNED_SUMS,
            f"KismetMobile-{version}-unsigned.apk", f"KismetMobile-demo-{version}-unsigned.apk",
        ]))
        by_flavor = {a["flavor"]: a for a in self.manifest["artifacts"]}
        self.assertEqual(by_flavor["standard"]["application_id"], "com.kismet.mobile")
        self.assertEqual(by_flavor["demo"]["application_id"], "com.kismet.mobile.demo")
        self.assertEqual(by_flavor["demo"]["version_name"], version + "-demo")
        self.assertEqual(self.manifest["tag"], f"v{version}")
        self.assertEqual(self.manifest["source_sha"], "a" * 40)
        kr.check_sums(self.unsigned, kr.UNSIGNED_SUMS, [a["file"] for a in self.manifest["artifacts"]])

    def test_stage_refuses_wrong_tag_and_bad_source_sha(self):
        with self.assertRaisesRegex(kr.ReleaseError, "does not match versionName"):
            kr.stage_unsigned(TOOLS, self.gradle, APK_ROOT, self.temp / "t1", "v9.9.9", None)
        with self.assertRaisesRegex(kr.ReleaseError, "40-digit"):
            kr.stage_unsigned(TOOLS, self.gradle, APK_ROOT, self.temp / "t2", None, "abc")

    def test_stage_refuses_a_non_empty_output_directory(self):
        with self.assertRaisesRegex(kr.ReleaseError, "not empty"):
            kr.stage_unsigned(TOOLS, self.gradle, APK_ROOT, self.unsigned, None, None)

    def test_stage_refuses_a_signed_debuggable_apk_posing_as_release(self):
        debug_apk = APK_ROOT / "standard" / "debug" / "app-standard-debug.apk"
        if not debug_apk.is_file():
            self.skipTest("no debug APK built")
        fake = self.temp / "fake"
        target = kr.FLAVORS[0].gradle_apk(fake)
        target.parent.mkdir(parents=True)
        shutil.copyfile(debug_apk, target)
        info = kr.read_apk_info(TOOLS, debug_apk)
        self.assertTrue(info.debuggable, "the debug APK is the positive control for the debuggable check")
        with self.assertRaisesRegex(kr.ReleaseError, "signed|JAR|Signing Block"):
            kr.stage_unsigned(TOOLS, self.gradle, fake, self.temp / "t3", None, None)

    def test_release_apks_are_not_debuggable_or_test_only_and_are_aligned(self):
        for artifact in self.manifest["artifacts"]:
            apk = self.unsigned / artifact["file"]
            info = kr.read_apk_info(TOOLS, apk)
            self.assertFalse(info.debuggable)
            self.assertFalse(info.test_only)
            kr.check_aligned(TOOLS, apk)
            kr.check_really_unsigned(TOOLS, apk)


@unittest.skipUnless(TOOLS and HAVE_APKS and DEBUG_KEYSTORE.is_file(),
                     "needs build-tools, unsigned release APKs and the SDK debug keystore")
class SigningIntegrationTests(unittest.TestCase):
    """Exercises the real apksigner flow with the SDK debug key under the test-only policy switch."""

    @classmethod
    def setUpClass(cls):
        cls.temp = Path(tempfile.mkdtemp())
        cls.gradle = kr.read_gradle_identity(kr.DEFAULT_GRADLE_FILE)
        cls.unsigned = cls.temp / "unsigned"
        kr.stage_unsigned(TOOLS, cls.gradle, APK_ROOT, cls.unsigned, None, None)
        cls.env = dict(os.environ, KISMET_TEST_PASS=DEBUG_PASSWORD)
        cls.fingerprint = _debug_keystore_fingerprint()
        if cls.fingerprint is None:
            raise unittest.SkipTest("keytool is not available to read the debug certificate fingerprint")

    @classmethod
    def tearDownClass(cls):
        shutil.rmtree(cls.temp, ignore_errors=True)

    def _sign(self, out: Path, *, fingerprint=None, allow_debug=True, unsigned=None):
        return kr.sign_staged(
            TOOLS, unsigned or self.unsigned, out, DEBUG_KEYSTORE, DEBUG_ALIAS, "KISMET_TEST_PASS", None,
            fingerprint or self.fingerprint, attestation_repo=None,
            allow_debug_certificate=allow_debug, environment=self.env,
        )

    def test_sign_then_verify_round_trip_without_password_on_the_command_line(self):
        recorded = []
        real_run = kr.run_tool

        def spy(cmd, **kwargs):
            recorded.append(list(cmd))
            return real_run(cmd, **kwargs)

        out = self.temp / "ok"
        with mock.patch.object(kr, "run_tool", spy):
            manifest = self._sign(out)
        # Exact-argument match: the word "android" legitimately appears in paths and the key alias.
        self.assertFalse(any(arg in (DEBUG_PASSWORD, f"pass:{DEBUG_PASSWORD}") or arg.startswith("pass:")
                             for cmd in recorded for arg in cmd),
                         "passwords must never appear in a command line")
        sign_cmds = [c for c in recorded if "sign" in c and Path(c[0]).stem == "apksigner"]
        self.assertEqual(len(sign_cmds), 2)
        self.assertTrue(all("env:KISMET_TEST_PASS" in c for c in sign_cmds))
        self.assertEqual(manifest["signing_certificate_sha256"], self.fingerprint)
        self.assertTrue(all("v3" in a["signature_schemes"] for a in manifest["artifacts"]))
        version = self.gradle.version_name
        self.assertEqual(sorted(p.name for p in out.iterdir()), sorted([
            kr.SIGNED_MANIFEST, kr.SIGNED_SUMS, f"KismetMobile-{version}.apk", f"KismetMobile-demo-{version}.apk"]))
        self.assertFalse([p for p in out.parent.iterdir() if p.name.startswith(".signing-")])
        verified = kr.verify_signed_dir(TOOLS, out, self.fingerprint, gradle=self.gradle, allow_debug_certificate=True)
        self.assertEqual(verified["tag"], f"v{version}")
        for artifact in manifest["artifacts"]:
            apk = out / artifact["file"]
            sig = kr.read_signature(TOOLS, apk)
            self.assertTrue(sig.schemes["3"])
            self.assertEqual(sig.signer_count, 1)
            kr.check_same_payload(self.unsigned / artifact["file"].replace(".apk", "-unsigned.apk"), apk)

    def test_default_policy_refuses_the_debug_certificate_and_leaves_nothing_behind(self):
        out = self.temp / "refused"
        with self.assertRaisesRegex(kr.ReleaseError, "debug certificate"):
            self._sign(out, allow_debug=False)
        self.assertFalse(out.exists())
        self.assertFalse([p for p in self.temp.iterdir() if p.name.startswith(".signing-")])

    def test_wrong_pinned_certificate_is_refused_and_leaves_nothing_behind(self):
        out = self.temp / "wrongpin"
        with self.assertRaisesRegex(kr.ReleaseError, "not the pinned release certificate"):
            self._sign(out, fingerprint="cd" * 32)
        self.assertFalse(out.exists())

    def test_wrong_keystore_password_is_refused(self):
        out = self.temp / "badpass"
        env = dict(os.environ, KISMET_TEST_PASS="wrong-password")
        with self.assertRaisesRegex(kr.ReleaseError, "could not sign"):
            kr.sign_staged(TOOLS, self.unsigned, out, DEBUG_KEYSTORE, DEBUG_ALIAS, "KISMET_TEST_PASS", None,
                           self.fingerprint, attestation_repo=None, allow_debug_certificate=True, environment=env)
        self.assertFalse(out.exists())

    def test_tampered_unsigned_input_is_refused_before_signing(self):
        tampered = self.temp / "tampered-unsigned"
        shutil.copytree(self.unsigned, tampered)
        apk = next(tampered.glob("KismetMobile-demo-*-unsigned.apk"))
        data = bytearray(apk.read_bytes())
        data[len(data) // 2] ^= 0xFF
        apk.write_bytes(bytes(data))
        out = self.temp / "tampered-out"
        with self.assertRaisesRegex(kr.ReleaseError, "does not match"):
            self._sign(out, unsigned=tampered)
        self.assertFalse(out.exists())

    def test_modified_signed_apk_fails_even_when_checksums_are_rewritten(self):
        out = self.temp / "modified"
        manifest = self._sign(out)
        apk = out / manifest["artifacts"][0]["file"]
        data = bytearray(apk.read_bytes())
        data[len(data) // 3] ^= 0xFF
        apk.write_bytes(bytes(data))
        with self.assertRaisesRegex(kr.ReleaseError, "does not match"):
            kr.verify_signed_dir(TOOLS, out, self.fingerprint, gradle=self.gradle, allow_debug_certificate=True)
        # A forger who also rewrites SHA256SUMS and the manifest is still stopped by apksigner.
        manifest["artifacts"][0]["sha256"] = kr.sha256_file(apk)
        kr.write_json(out / kr.SIGNED_MANIFEST, manifest)
        kr.write_sums(out / kr.SIGNED_SUMS, [out / a["file"] for a in manifest["artifacts"]])
        with self.assertRaises(kr.ReleaseError):
            kr.verify_signed_dir(TOOLS, out, self.fingerprint, gradle=self.gradle, allow_debug_certificate=True)

    def test_unexpected_extra_file_is_refused(self):
        out = self.temp / "extra"
        self._sign(out)
        (out / "notes.txt").write_text("surprise", encoding="utf-8")
        with self.assertRaisesRegex(kr.ReleaseError, "unexpected files"):
            kr.verify_signed_dir(TOOLS, out, self.fingerprint, gradle=self.gradle, allow_debug_certificate=True)

    def test_cli_refuses_to_sign_without_a_pinned_certificate(self):
        code = kr.main(["sign", "--unsigned-dir", str(self.unsigned), "--out", str(self.temp / "cli"),
                        "--keystore", str(DEBUG_KEYSTORE), "--key-alias", DEBUG_ALIAS,
                        "--pin-file", str(self.temp / "no-pin.txt")])
        self.assertEqual(code, 1)
        self.assertFalse((self.temp / "cli").exists())

    def test_cli_rejects_the_debug_key_even_when_it_is_pinned(self):
        env = {"KISMET_TEST_PASS": DEBUG_PASSWORD}
        with mock.patch.dict(os.environ, env):
            code = kr.main(["sign", "--unsigned-dir", str(self.unsigned), "--out", str(self.temp / "cli2"),
                            "--keystore", str(DEBUG_KEYSTORE), "--key-alias", DEBUG_ALIAS,
                            "--ks-pass-env", "KISMET_TEST_PASS", "--skip-attestation-check",
                            "--expected-cert-sha256", self.fingerprint,
                            "--pin-file", str(self.temp / "no-pin.txt")])
        self.assertEqual(code, 1)
        self.assertFalse((self.temp / "cli2").exists())


if __name__ == "__main__":
    unittest.main()
