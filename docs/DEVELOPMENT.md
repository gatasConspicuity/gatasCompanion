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

## Xcode Archives and TestFlight Uploads

Use the existing Xcode workflow for archiving and uploading. No separate build
or upload script is required.

1. Check out the branch or commit intended for this test build and commit all
   source changes before archiving.
2. Open `iosApp/GatasCompanion.xcodeproj`, select the `GatasCompanion` scheme
   and a suitable device destination, and choose **Product > Archive**.
3. In Xcode Organizer, select the archive and choose **Distribute App**.
   Use **TestFlight & App Store**. If using the Custom App Store Connect
   distribution flow, enable **Manage version and build number**.
4. Allow Xcode to manage the build number during distribution. The uploaded
   build number can differ from `CURRENT_PROJECT_VERSION` in the checkout.
   Record the actual number shown in App Store Connect with the source commit.
5. Add English testing instructions and the source commit to the build's
   **What to Test** information, then manage tester groups in App Store Connect.

The visible app version remains a deliberate release choice. Its central
setting is `MARKETING_VERSION` in `iosApp/Configuration/Config.xcconfig`.
Keep the iPhone, Watch, and Live Activity versions consistent.

`CURRENT_PROJECT_VERSION=1` is the shared local build-number baseline. Keep
this value in the configuration so local builds and archives have a valid
`CFBundleVersion`. It does not encode a date and does not need a manual bump
before each upload. Xcode's distribution assistant manages the uploaded number.
Existing TestFlight numbers are not reset: when the current app version already
has higher uploaded numbers, Xcode selects a valid higher number as needed.

## TestFlight Notes

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
- [Apple: Preparing Your App for Distribution](https://developer.apple.com/documentation/xcode/preparing-your-app-for-distribution)
- [Apple: Distributing Your App for Beta Testing and Releases](https://developer.apple.com/documentation/xcode/distributing-your-app-for-beta-testing-and-releases)
- [Apple: Choose a Build to Submit](https://developer.apple.com/help/app-store-connect/manage-builds/choose-a-build-to-submit/)
