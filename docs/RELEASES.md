# Mobile Releases

Android publishing runs on GitHub-hosted runners. Maintainers do not need an
Android development environment or device to produce APKs. Physical Android
testers are still needed to verify Bluetooth, background behavior and GATAS.
iOS archives and uploads continue through Xcode.

## Shared release identity

`Version.xcconfig` is the source of `MARKETING_VERSION`, `CURRENT_PROJECT_VERSION`
and the checked numbering baseline. Gradle uses the same values for Android
`versionName` and `versionCode`; all Apple targets inherit them through the
shared configuration. Local signing files must not override version values.

The initial `1.0.0 (3)` only preserves the existing Apple source configuration.
Its baseline is deliberately `-1`, so it cannot be distributed by the new
workflows or archived for distribution. It does **not** establish the next
available number. Earlier numbers, including historical date-based numbers,
must be included when checking real distribution history.

Before preparing a candidate:

1. Check the highest uploaded number across all app versions in App Store
   Connect and Google Play, if the app has ever been distributed there.
2. Check GitHub releases/drafts and coordinate all active branches' reserved
   numbers. Record the reservation with the team before archiving.
3. Substitute that highest verified number for `NUMBER` below. The helper
   selects one greater than both it and the current source counter:

   ```bash
   python3 tools/releases/release.py prepare --highest-used-or-reserved NUMBER --version 1.1.0
   ```

4. Commit the version change together with the intended source changes before
   archiving. Push/merge/tag/publication still require explicit authorization.
5. Recheck reservations before distribution. The GitHub workflows also check
   published metadata and drafts. They cannot query Apple/Google accounts or
   discover unpublished reservations automatically; their confirmation inputs
   attest that the maintainer performed those checks.

Do not reset the counter for a new product version. A common build `40` can be
followed by iOS-only `41` and then common `42`; Android simply skips `41`.
Never reuse a build for different code, even under another product version.
Registered builds can be published later for the other platform from the
exact same commit. New unregistered candidates must exceed the known counter.

## One-time administrator and team setup

The workflows are not usable through **Run workflow** until they are merged
into the repository's default branch. The workflows themselves do
not run on merge and do not push source changes to `main`.

1. A repository administrator creates environments **android-release** and
   **mobile-publication**, allowing only the `main` branch (no tag or PR refs).
   Keep pull requests for traceability without requiring approving reviews.
   Leave **Required reviewers** disabled in both environments. Maintainers
   may integrate their own changes and start releases without another person's
   approval. Required user authorization, automated checks and physical-device
   acceptance still apply as described in this document.
2. Check whether an existing project signing key or previously distributed APK
   exists. Keep that key if updates to those installations must remain possible.
   Otherwise, designate two key custodians before generating a permanent project
   key outside the repository. For example, use `keytool` from a JDK:

   ```bash
   keytool -genkeypair -keystore /secure/path/gatas-release.jks -alias gatas-companion -keyalg RSA -keysize 4096 -sigalg SHA256withRSA -validity 10000 -dname "CN=GATAS Companion,O=GATAS"
   ```

   Replace `/secure/path` with a private location. Enter a strong password when
   prompted; do not put passwords in command arguments, Git, tickets or chat.
   The certificate subject is public in APKs, so use the project identity.
   With the JDK's default PKCS12 format, use the keystore password as the
   private-key password as well when configuring GitHub secrets.
3. Both custodians keep encrypted, independently recoverable backups of the
   keystore, passwords, alias and instructions. Test restoring a copy before
   enabling publication. GitHub secrets are an operational copy, not a backup.
4. In the **android-release** environment, add these **secrets** through the
   GitHub UI or `gh secret set --env android-release NAME`:

   | Secret | Value |
   | --- | --- |
   | `ANDROID_KEYSTORE_BASE64` | Base64-encoded project keystore |
   | `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
   | `ANDROID_KEY_ALIAS` | Signing alias |
   | `ANDROID_KEY_PASSWORD` | Private-key password |

   A local CLI example that does not print the encoded keystore:

   ```bash
   base64 < /secure/path/gatas-release.jks | gh secret set ANDROID_KEYSTORE_BASE64 --repo gatasConspicuity/gatasCompanion --env android-release
   ```

   Base64 is an encoding; GitHub's secret storage provides encryption. Never
   commit even the encoded file. PR/unit-test jobs receive none of these secrets.
5. Obtain the certificate SHA-256 fingerprint locally with `keytool -list -v
   -keystore /secure/path/gatas-release.jks -alias gatas-companion`. Add it as
   repository **variable** `ANDROID_CERTIFICATE_SHA256`, so both signing and
   promotion can verify the same public certificate. Colons are accepted.
6. Only after backup recovery and setup have been verified, add environment
   **variable** `ANDROID_RELEASE_READY=true` to **android-release**.

The signing job uses a fresh runner, receives only the unsigned APK and release
metadata, checks their identity, and reads secrets only in the signing step.
It removes the temporary keystore even if signing fails. Publication assets
contain no keystore, passwords, local Apple configuration or build logs.

## Android test releases

After the prepared candidate is committed and integrated into `main`:

1. Open **Actions > Android prerelease > Run workflow**, keeping branch `main`.
2. Supply the full source commit, not a branch name. Confirm that used/reserved
   numbers were checked across both platforms.
3. GitHub runs Android Debug/Release unit tests, release lint and the unsigned
   release build. A separate protected job checks metadata, aligns and signs
   the APK, and verifies the configured project certificate.
4. GitHub creates `android/vVERSION-build.BUILD` as a prerelease with the APK,
   `SHA256SUMS`, and `release.json`. Only after all uploads succeed is its draft
   made public. It is never marked **Latest**.

The certificate and app ID must remain the same for updates. The Android
application ID is currently the public project ID `nl.rvt.gatas`. Users may
need to uninstall incompatible debug builds first, losing their local app data.

## Android stable releases

Have an Android tester verify the exact published APK with real GATAS hardware.
Then run **Android final release** on `main`, provide its complete prerelease
tag, and explicitly confirm physical-device acceptance.

The workflow downloads that APK, verifies its checksum, package identity,
version/build and project certificate, and copies it without rebuilding or
re-signing into `android/vVERSION`. The stable release is marked **Latest**.
The original prerelease stays available, with the same source and APK bytes.
A newer stable product version must be selected for the next stable release.
Promotion does not allocate a new build because it distributes the same binary.

## iOS availability

Archive the committed candidate in Xcode using **Product > Archive**, then
**Distribute App > App Store Connect > Upload**. The archive preflight requires
prepared numbering, a clean working tree and matching effective version/build
settings. Confirm the archive, upload summary and processed Apple build all
use the committed identity. Add the full commit and English change summary
to **What to Test**, as described in [Development](DEVELOPMENT.md).

Run **Record iOS release** on `main` only after the selected channel is actually
available. Provide the full source commit, choose `test` or `stable`, and
confirm that the version/build/source match the Apple build. For a test entry,
a public `https://testflight.apple.com/join/...` URL is optional when testing
is invitation-only. A stable entry requires the public `https://apps.apple.com/...`
URL and a previously recorded matching TestFlight build. Never publish private
App Store Connect links. This workflow records confirmed availability; it does
not upload, query Apple or submit an app for review.

Entries use `ios/vVERSION-build.BUILD` for tests and `ios/vVERSION` for stable.
iOS entries are never GitHub's **Latest**, leaving that link for stable APKs.
Android and iOS stable status are independent. Both test and stable Android
APKs use the technical **Release** build configuration; channel is a separate
publication status, not a compiler configuration.

## Overview, failures and recovery

Each release records platform, channel, version, build, full source commit and
branch. The latest entry in each of the four platform/channel combinations
contains a refreshed **Current availability** table with publication dates and
links. A channel without a published build says **Not published**. Old test
entries remain historical; recording an entry does not monitor later TestFlight
expiry, invitation revocation or store withdrawal.

All three publication workflows share a concurrency group to avoid concurrent
allocation/publication races. Numbers reserved outside GitHub still require team
coordination. Workflows only accept candidates reachable from the team `main`.
The existing feature-branch Xcode workflow for selected testers remains possible;
the automated public ledger is for integrated candidates from `main`.

Existing releases, assets and tags are never overwritten or deleted by these
helpers. A failed upload can leave a **draft** that reserves its number. Inspect
it before retrying; removing a draft/tag or replacing anything needs explicit
authorization. If publication succeeded but refreshing the overview failed,
rerun only the overview command using an appropriately authorized GitHub token:

```bash
python3 tools/releases/release.py overview
```

Run the offline safety tests with:

```bash
python3 -m unittest discover -s tools/releases/tests -v
python3 tools/releases/release.py check-source
```

## Future Google Play distribution

Preserve the app ID and project signing key. When enrolling in Play App Signing,
provide this existing signing key so Play-distributed APKs can update sideloaded
installations. Use a separate upload key for Play submissions. Google Play
distribution, account registration and Play upload automation are separate work.

References: [GitHub manual workflows](https://docs.github.com/en/actions/how-tos/manage-workflow-runs/manually-run-a-workflow),
[environment protection](https://docs.github.com/en/actions/reference/workflows-and-actions/deployments-and-environments),
[Android signing](https://developer.android.com/studio/publish/app-signing).
