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
- Coordinate Apple build numbers across all branches used for uploads. The
  iPhone app, Watch app, and Live Activity extension must share the same build
  number. The central project configuration is
  `iosApp/Configuration/Config.xcconfig`.
- Before each new TestFlight upload, check the highest build number already
  used for the app version in App Store Connect and coordinate numbers reserved
  by other active branches. Manually set `CURRENT_PROJECT_VERSION` in the shared
  configuration to a higher number, normally the highest number plus one.
- Do not reset the build number to `1` or derive the next number from local
  archives alone. Use a monotonically increasing numeric counter, not a date.
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
