# Releasing KismetMobile

This document is for maintainers, and for anyone who wants to sign their own build. Users should read
[Download and install](README.md#download-and-install).

## How a release is made

```
tagged commit ──► GitHub Actions: release.yml ──► unsigned APKs + checksums + build attestation
                  (no secrets, no write access)               │
                                                              ▼
                  maintainer's computer: tools/release/kismet_release.py sign
                  (verifies the attestation, signs with the persistent release key, re-verifies)
                                                              │
                                                              ▼
                  device test ──► draft GitHub Release ──► publish
```

- **GitHub builds, the maintainer signs.** The binaries come from GitHub's runners, not a workstation. The
  release key never leaves the maintainer's control, and no workflow holds a secret or can write to the
  repository or to releases.
- **Gradle never signs.** `release` has no `signingConfig` on purpose. A build that quietly falls back to a
  debug or throwaway key would produce an APK that installs but can never be updated.
- **Fail closed.** `kismet_release.py` refuses to stage anything debuggable, `testOnly`, signed, misaligned
  or with the wrong package or version, and refuses to sign or verify a release until the release
  certificate's fingerprint is recorded in `signing/release-cert-sha256.txt`.

## Release identity

| Item | Value |
|---|---|
| Version | `versionName` and `versionCode` in `app/build.gradle.kts`, the only place they are set |
| Git tag | `v<versionName>`, e.g. `v1.4.0-rc1` (the tooling rejects any other tag) |
| Standard app | `com.kismet.mobile`, asset `KismetMobile-<versionName>.apk` |
| Offline demo | `com.kismet.mobile.demo` (version name `<versionName>-demo`), asset `KismetMobile-demo-<versionName>.apk` |
| Also attached | `SHA256SUMS`, `release-manifest.json` |
| Signing | APK Signature Scheme v3, one certificate for both apps, fingerprint in `signing/release-cert-sha256.txt` |

- A `versionName` containing `-` (such as `1.4.0-rc1`) is published as a GitHub **pre-release** with
  `--latest=false`. GitHub never offers a pre-release as the latest release, so `releases/latest` and
  `releases/latest/download/...` only start working with the first stable release. Link to the Releases page
  or a tag until then.
- `versionCode` must increase for every published build, release candidates included. Android never installs
  a lower `versionCode` over a higher one, so a final `1.4.0` needs a higher code than `1.4.0-rc1`.
- Application IDs are permanent. Changing one makes a different app that cannot update the installed one,
  and the tooling fails if either ID changes.
- The standard app and the demo are separate Android apps with separate data. They can be installed side by
  side; the demo's launcher label is "KismetMobile Demo".

## One-time setup

### The release signing key

Android only accepts an update signed by the same key as the installed app, so this key is the app's
identity. If it is lost, installed copies can never be updated and users must uninstall (erasing their data);
the Android guidance says the same. Treat it like a root credential.

1. On a trusted computer, create a PKCS #12 keystore outside this repository (the tool refuses a keystore
   inside the working tree). Keep the certificate subject free of personal data, because it is public in
   every APK:

   ```bash
   keytool -genkeypair -v -keystore kismetmobile-release.p12 -storetype PKCS12 \
     -alias kismetmobile-release -keyalg RSA -keysize 4096 -validity 36500 \
     -dname "CN=KismetMobile Release"
   ```

2. Back it up before the first release: at least two encrypted copies on separate media in separate places,
   with the password stored in a password manager. Test the restore by signing a sample and comparing the
   fingerprint. Never commit the keystore or its password, and do not upload them anywhere.
3. Record the **public** fingerprint. Read it from the keystore:

   ```bash
   keytool -list -v -keystore kismetmobile-release.p12 -alias kismetmobile-release
   ```

   For the first release, pass it as `--expected-cert-sha256` (see below), then add it as the only
   non-comment line of `signing/release-cert-sha256.txt` and commit that file. From then on the committed
   value is the pin, and a conflicting `--expected-cert-sha256` is refused.
4. Key rotation, if ever needed: Android supports it with a signing lineage
   (`apksigner rotate`, APK Signature Scheme v3). It needs the old key, the lineage file must be backed up with
   the keystore, and Android's documentation does not recommend rotation for Android 12 and earlier, which
   this app still supports. Plan it carefully rather than improvising.

### Repository settings

- **Releases > Enable release immutability** (Settings). Assets and the tag of a published release can then
  no longer be changed, and GitHub adds a release attestation that `gh release verify` can check. It applies
  to future releases only, so enable it before the first one.
- **Actions > General > Workflow permissions:** read-only. The workflows declare their own minimal
  permissions as well.
- Restrict who may create `v*` tags (a tag ruleset); a tag push starts the release build.
- The workflows need no secrets. Do not add the keystore as a repository or environment secret unless you
  deliberately decide to move signing into CI and review that design first.

### Android developer verification

Google is rolling out developer verification for apps on certified Android devices: registration of the
package name together with the signing key in the Android Developer Console. The first enforcement
(September 30, 2026) covers only participating app stores in four countries; apps that users sideload from
unregistered developers can still be installed with the advanced flow or adb, and a global rollout is
planned for 2027. Decide whether to register `com.kismet.mobile` and `com.kismet.mobile.demo` once the
release certificate exists. The console accepts several signing keys for one package, and an unrecoverable
signing key means the package cannot be registered.

## Making a release

1. **Prepare.** Set `versionCode` and `versionName` in `app/build.gradle.kts`, update the README version
   badge, and run the checks:

   ```bash
   ./gradlew :app:testStandardDebugUnitTest :app:testDemoDebugUnitTest :app:lintStandardDebug :app:lintDemoDebug
   python3 -m unittest discover -s tools/release -p "test_*.py"
   ```

2. **Build on GitHub.** Push the commit and create the tag (or open Actions > *Release build (unsigned)* >
   *Run workflow* and enter the tag). The workflow runs the tests, builds both flavors, runs
   `kismet_release.py stage`, uploads the `unsigned-apks-<tag>` artifact, and attests the two APKs. Its run
   summary lists their SHA-256 digests.
3. **Download** on the signing computer (artifacts need a GitHub sign-in):

   ```bash
   gh run list --workflow release.yml --limit 5
   gh run download <run-id> --name unsigned-apks-<tag> --dir dist/unsigned
   ```

4. **Sign.** Run from a checkout of the release tag: the tool compares the APKs with `app/build.gradle.kts`
   and refuses a mismatch. Needs the Android SDK build-tools (36.0.0 or newer; `ANDROID_HOME` or
   `--build-tools`), Python 3 and `gh`. The password comes from the `KISMET_KS_PASS` environment variable or an
   interactive prompt, never from an argument:

   ```bash
   python3 tools/release/kismet_release.py sign \
     --keystore <path to kismetmobile-release.p12> --key-alias kismetmobile-release
   # first release only, before the fingerprint is committed:
   #   add  --expected-cert-sha256 <fingerprint from keytool>
   ```

   The tool checks the checksums and manifest, re-checks identity, flags and alignment, verifies with
   `gh attestation verify` that GitHub's release workflow built exactly these bytes from the recorded
   commit, signs with APK Signature Scheme v3, and verifies the result: one signer, the pinned certificate,
   not a debug certificate, and a payload identical to the unsigned APK. Nothing is left in `dist/signed`
   unless every check passes. `--skip-attestation-check` exists for local dry runs only; never publish what it
   produced. The attestation check calls GitHub's API, so `gh` needs a working sign-in (`gh auth status`); an
   expired token stops signing with an HTTP 401 error rather than skipping the check.
5. **Verify again, independently:**

   ```bash
   python3 tools/release/kismet_release.py verify-signed
   apksigner verify --verbose --print-certs dist/signed/KismetMobile-<version>.apk
   ```

6. **Test on a device.** CI cannot do this, and it matters because release builds are shrunk by R8 while the
   unit tests run on debug builds. Install the signed standard and demo APKs on a real device and check that
   the app starts, connects (or shows the demo data), saves and reloads a campaign, and exports a capture.
   When a previous signed release exists, also install the new APK over it and confirm saved data survives.
7. **Create a draft release** and attach the files (the tag must already exist; otherwise drop `--verify-tag`
   and pass `--target <full commit SHA>` so publishing creates the tag):

   ```bash
   gh release create v<version> --draft --prerelease --latest=false --verify-tag \
     --title "KismetMobile <version>" --notes-file release-notes.md \
     dist/signed/KismetMobile-<version>.apk dist/signed/KismetMobile-demo-<version>.apk \
     dist/signed/SHA256SUMS dist/signed/release-manifest.json
   ```

   Leave out `--prerelease --latest=false` for a stable release. Put the signing certificate's SHA-256
   fingerprint in the notes.
8. **Publish** the draft after reviewing it. With immutability enabled the assets and tag are locked; if
   something is wrong, publish a new version with a higher `versionCode` rather than editing.
9. **Check what users get,** signed out of GitHub: download both APKs from the release page, run
   `sha256sum --check SHA256SUMS` and `apksigner verify --print-certs`, and run
   `gh release verify v<version>`.

## Signing your own build

You can sign any build with your own key; it will not be interchangeable with the official APKs (a different
key cannot update them). Build the unsigned release variant, then:

```bash
./gradlew :app:assembleStandardRelease
apksigner sign --ks my-key.p12 --out KismetMobile-mine.apk app/build/outputs/apk/standard/release/app-standard-release-unsigned.apk
apksigner verify --print-certs KismetMobile-mine.apk
```

`apksigner` ships with the Android SDK build-tools. Gradle's output is already zip-aligned, and alignment
must happen before signing, never after.

## What the automated checks do not prove

- They do not run the app. The release APKs are shrunk by R8; static review found the Gson-serialized model
  classes covered by keep rules, but only the device test in step 6 shows the shrunk app behaves.
- They show that GitHub's workflow built the unsigned bytes and that the signature is the pinned one; they
  do not show the source is free of bugs or that the key is uncompromised.
- The attestation covers the unsigned APKs. After signing, the files differ by the signing block only
  (`kismet_release.py` checks the payload is identical, and `release-manifest.json` records both digests).
  The signature and GitHub's release attestation (when immutability is on) cover the signed files.

## Sources

- GitHub: [Managing releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository),
  [REST releases](https://docs.github.com/en/rest/releases/releases) (drafts, pre-releases and "latest"),
  [release assets](https://docs.github.com/en/rest/releases/assets) (public `browser_download_url`),
  [immutable releases](https://docs.github.com/en/code-security/concepts/supply-chain-security/immutable-releases) and
  [enabling them](https://docs.github.com/en/code-security/how-tos/secure-your-supply-chain/establish-provenance-and-integrity/prevent-release-changes),
  [artifact attestations](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations),
  [workflow artifacts](https://docs.github.com/en/actions/concepts/workflows-and-actions/workflow-artifacts),
  [secure use of Actions](https://docs.github.com/en/actions/reference/security/secure-use) (least privilege, SHA pinning).
- Android: [Sign your app](https://developer.android.com/studio/publish/app-signing),
  [apksigner](https://developer.android.com/tools/apksigner),
  [APK Signature Scheme v3](https://source.android.com/docs/security/features/apksigning/v3),
  [developer verification](https://developer.android.com/developer-verification) and its
  [FAQ](https://developer.android.com/developer-verification/guides/faq).
