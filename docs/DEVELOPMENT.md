# Development and TestFlight Workflow

All repository content, app text, and TestFlight content are in English,
including documentation, code comments, commit messages, pull requests,
and tester notes.

## Branches

`main` is the shared integration branch. Start each feature or bug fix on its
own branch from the current `origin/main`. Keep related changes, tests, and
documentation on the same branch. Develop and review unrelated work separately.

The normal workflow is:

1. Create a branch from the current `origin/main`.
2. Develop and commit the changes, and run appropriate automated checks.
3. Run targeted hardware and TestFlight tests on that branch when needed.
4. Review the changes through a pull request and merge into `main` after approval.
5. Perform final testing of the integrated revision from `main`.
6. Mark the approved release revision with a Git tag and select a suitable
   tested build for App Store review.

A pull request organizes the review; the merge integrates the changes.
`main` does not automatically represent the latest App Store release. Identify
that release by its release tag, Git commit, and approved build number.

A permanent `develop` or `testflight` branch is not part of this workflow.
A temporary `release/<version>` branch is useful when one version is still
undergoing final testing while `main` moves on to the next. Bring release
branch fixes back into `main` as well.

`origin` refers to the team repository, `gatasConspicuity/gatasCompanion`.
`fork` is an additional local remote name for `jerod6/gatasCompanion`.
Configuring these remotes does not automatically synchronize the repositories.

These rules do not authorize automatic commits, pushes, merges, tags, branch
deletions, or publication without the corresponding user instruction.

## Shared Version and Build Numbers

`Version.xcconfig` is the only source for the visible product version and the
continuously increasing build counter. Android reads the same values as the
iPhone app, Watch app and Live Activity extension. Do not add target-specific,
Gradle or local-signing version overrides. Never reset the counter when the
product version changes, and do not use date-based numbering.

The same version/build identifies the same commit across platforms. Platforms
may skip releases: common build `40`, iOS-only `41`, then common `42` is valid.
Test and stable availability are independent for each platform; neither a merge
nor a successful build implies a publication or final approval.

Before preparing a candidate, check the highest numbers actually used across
all versions in App Store Connect and Google Play (if used), GitHub releases
and drafts, and reservations on other active branches. Local archives alone
are not authoritative. Then prepare a number above the highest checked value:

```bash
python3 tools/releases/release.py prepare --highest-used-or-reserved NUMBER --version 1.1.0
```

Commit the version and all source changes before archiving/distribution. This
helper only edits local configuration; it never commits, pushes or publishes.
The bootstrap value with baseline `-1` is not a release-ready reservation.

## Xcode Archives and TestFlight Uploads

1. Check out the intended, fully committed candidate. Coordinate numbers as
   described above; selected feature-branch testing remains possible.
2. In Xcode, select **GatasCompanion** and an iOS device destination, then
   **Product > Archive**. Archive preflight verifies a prepared number, the
   effective version/build and a clean working tree. Local signing values may
   remain in ignored `Config.local.xcconfig`; version overrides are rejected.
3. Confirm the archive's version/build in Organizer. Choose **Distribute App** >
   **App Store Connect** > **Upload**. Keep the committed build number in the
   distribution summary; automatic signing is independent of version handling.
4. After processing, confirm the same number in App Store Connect and record
   the full source commit and branch. Add the English **What to Test** notes
   described below.
5. For an integrated candidate from `main`, use **Record iOS release** to record
   actual TestFlight availability. Record stable availability only after the
   matching build becomes available in the App Store.

Changing configuration does not change an existing archive. Create a new
archive after preparing a new candidate. Retrying the same unused archive
need not allocate another number; never reuse an already used number for
another source revision.

Xcode 27 previously uploaded build `1` unchanged despite **Manage version and
build number**, producing **Redundant Binary Upload** (`90189`). The cause was
not established. This workflow deliberately prepares the shared number before
archiving and verifies that the archive and upload preserve it.

For GitHub setup, signing-key custody, Android prereleases, unchanged-APK stable
promotion and the cross-platform overview, see [Mobile Releases](RELEASES.md).

## Internal TestFlight Notes

For builds distributed by the GATAS Companion team, include the full source
commit and an English change summary in **What to Test** so testers can identify
the tested revision.

Before archiving, obtain the full commit and latest change summary from the
intended revision:

```bash
git log -1 --format='Commit: %H%nChanges: %s'
```

Copy the result into the matching TestFlight build's **What to Test** field,
with any additional English test instructions. Keep the result with the archive
if switching branches before the upload. Do not use a later `HEAD` as the source
reference for an older archive.

Example:

```text
Commit: bd1b1e3b10fe5ad6c5fd27bebde50b5c7e81eb9d
Changes: Handle Live Activity stops and Bluetooth disconnect alerts
```

The latest commit subject is a starting point. If a build contains several
changes, add a short English summary and the checks testers should perform.
Keep automated build results separate from physical device acceptance.

## Sources

- [GitHub Flow](https://docs.github.com/en/get-started/using-github/github-flow)
- [Android: Version Your App](https://developer.android.com/studio/publish/versioning)
- [Google Play: Version Codes](https://support.google.com/googleplay/android-developer/answer/17367361?hl=en)
- [Apple: Preparing Your App for Distribution](https://developer.apple.com/documentation/xcode/preparing-your-app-for-distribution)
- [Apple: Distributing Your App for Beta Testing and Releases](https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases)
- [Apple: Get Started with TestFlight](https://developer.apple.com/videos/play/tech-talks/110343/)
- [Apple: Choose a Build to Submit](https://developer.apple.com/help/app-store-connect/manage-builds/choose-a-build-to-submit/)
