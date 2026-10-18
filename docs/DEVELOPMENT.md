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

1. Check out the branch intended for this test build. In App Store Connect,
   check the highest build number already used for the app version. Coordinate
   numbers reserved for uploads from other active branches as well; local
   archives and the current branch alone are not an authoritative counter.
2. Manually set `CURRENT_PROJECT_VERSION` in
   `iosApp/Configuration/Config.xcconfig` to a higher number, normally the highest
   used or reserved number plus one. Use a numeric counter rather than a date,
   and do not reset it to `1`. The iPhone app, Watch app and Live Activity
   extension all inherit this value; do not add target-specific overrides.
3. Commit the selected build number and all intended source changes before
   archiving. Record the full source commit and branch for the build.
4. Open `iosApp/GatasCompanion.xcodeproj`, select the `GatasCompanion` scheme
   and a suitable device destination, and choose **Product > Archive**.
5. In Xcode Organizer, select the new archive and check its version and build
   number. Choose **Distribute App** > **Custom** > **App Store Connect** >
   **Upload**. Disable **Manage version and build number** to preserve the
   committed number. Automatic signing can remain enabled; it is independent
   of build-number management.
6. Before confirming the upload, verify that the distribution summary uses the
   selected build number. After processing, confirm the same number in App Store
   Connect and record it with the version, full source commit and branch.
7. Add English testing instructions and the source commit to the build's
   **What to Test** information, then manage tester groups in App Store Connect.

Changing `CURRENT_PROJECT_VERSION` only affects subsequent builds. An existing
archive retains its original number, so create a new archive after a manual
increase. A retry of the exact same unused build does not itself require another
increase; a number App Store Connect reports as already used must be replaced.

This workflow uses manual numbering because Xcode 27 uploaded build `1` unchanged
with **Manage version and build number** enabled and App Store Connect rejected
it as **Redundant Binary Upload** (error `90189`). The standalone **Validate App**
flow failed with the same number. The cause of the missing automatic adjustment
has not been established; a different distribution entry point is not a
verified fix.

The visible app version remains a deliberate release choice. Its central
setting is `MARKETING_VERSION` in `iosApp/Configuration/Config.xcconfig`.
Keep the iPhone, Watch, and Live Activity versions consistent.

`CURRENT_PROJECT_VERSION` is the shared manually maintained build number and
supplies `CFBundleVersion`. Keep it consistent across the three Apple targets
and across branches used for uploads. The uploaded number should match the
committed source configuration and the archive.

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
