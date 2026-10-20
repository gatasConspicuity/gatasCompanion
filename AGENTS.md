# Project Rules: GATAS Companion

## Language and Authorization

- Write all repository content, app text, and TestFlight content in English.
  This includes documentation, code comments, commit messages, pull request
  titles and descriptions, and tester notes.
- A request to commit authorizes a local commit unless a push is also requested.
- Pushes, merges into `main`, release tags, and publication require explicit
  user authorization in the current task. Previous authorization does not
  automatically apply to later actions.
- These workflow rules do not provide blanket authorization for those actions
  or for deleting branches.

## Pre-push Privacy Review

- Before every push, check the current state of the target remote and review
  all outgoing commits, changed files and new artifacts for accidentally
  included private information. Review the full outgoing history, including
  values added and removed in earlier commits; a clean working tree or a review
  of only the final diff is not sufficient.
- Check for personal `TEAM_ID` values, personal bundle IDs, credentials, tokens,
  API keys, private keys, signing assets, local configuration, logs, screenshots
  and generated files. Examples must use placeholders for private values.
- Keep `iosApp/Configuration/Config.local.xcconfig` ignored and untracked.
  Verify that Xcode project overrides do not contain private signing values.
- If private information is found, stop the push and resolve the findings
  throughout the outgoing history before publishing. Do not rewrite already
  published history without explicit user authorization.
- Report the review result without printing private values.

## Branch Workflow

- Use `main` as the shared integration branch. Merging into `main` does not
  publish an App Store release.
- Create a separate, preferably short-lived branch from the current
  `origin/main` for each cohesive feature or bug fix. Keep related commits,
  tests, and documentation together; separate unrelated work.
- Branch from another feature branch only when there is a genuine, explicitly
  documented dependency.
- Do not introduce a permanent `develop` or `testflight` branch unless the user
  chooses a different workflow.
- Prepare changes for integration through a pull request. Merge into `main`
  after review, the agreed tests, and the required authorization.
- `origin` is the team repository at
  `https://github.com/gatasConspicuity/gatasCompanion.git`.
  Keep `fork` as an additional remote for
  `https://github.com/jerod6/gatasCompanion.git`.
- New work and its authorized pushes normally target `origin`. Use `fork` only
  when the specific task calls for it.

## Test Builds and Releases

- Targeted testing before a merge may use the feature branch directly,
  including TestFlight builds for selected testers.
- Shared integration builds and final TestFlight candidates normally come
  from `main`.
- Use a temporary `release/<version>` branch from `main` only when finalizing
  one version overlaps with development of the next. Release fixes must also
  be brought back into `main`.
- Mark an approved release revision with a Git tag. A suitable build already
  tested through App Store Connect can be selected for release; rebuilding
  solely for publication is not required.
- Create distributed builds from a fully committed working tree. Record the
  version, build number, full Git commit, and branch.
- Use a continuously increasing numeric build counter on each platform:
  Android `versionCode` and Apple `CURRENT_PROJECT_VERSION`. Do not reset
  either counter when `versionName` or `MARKETING_VERSION` changes. Track
  the Android and Apple counters independently; date-based numbering is
  not part of this workflow.
- Before each new Android distribution, check the highest `versionCode`
  already used in Google Play and numbers reserved by other branches.
  Increase `versionCode` in `composeApp/build.gradle.kts`, normally to the
  highest used or reserved number plus one.
- Coordinate Apple build numbers across all branches used for uploads. The
  iPhone app, Watch app, and Live Activity extension must share the same build
  number. The central project configuration is
  `iosApp/Configuration/Config.xcconfig`.
- Before each new TestFlight upload, check the highest build number already
  used across all app versions in App Store Connect and coordinate numbers
  reserved by other active branches. Manually set `CURRENT_PROJECT_VERSION`
  in the shared configuration to a higher number, normally the highest
  used or reserved number plus one.
- Do not derive the next build number from local archives alone. Check the
  distribution service and coordinate reservations before choosing a number.
- Commit the selected build number with the intended source changes before
  archiving. Changing source configuration does not change an archive that has
  already been created.
- Archive through Xcode Organizer and upload using **Distribute App** >
  **App Store Connect** > **Upload**. Increase the build number manually before
  archiving as described above. Confirm that the archive, distribution summary
  and uploaded build use the committed number. Automatic signing is a separate
  setting.
- For TestFlight builds distributed by the GATAS Companion team, include the
  full source commit and an English change summary in "What to Test". Obtain
  them from the intended build revision with
  `git log -1 --format='Commit: %H%nChanges: %s'`.
- Report automated checks separately from physical device testing. Successful
  simulator tests or builds do not replace acceptance testing on physical
  Android, iPhone, Watch, and GATAS devices.

Further details: [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).
