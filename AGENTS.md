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
- Keep `CURRENT_PROJECT_VERSION=1` as the local build-number baseline. Do not
  encode a date in this value or manually increment it for TestFlight uploads.
- Archive and upload through Xcode Organizer, using its build-number management
  during distribution. Record the actual uploaded build number, which may differ
  from the value in the checked-out project.
- Include the full source commit and an English change summary in TestFlight's
  "What to Test" information. Obtain them from the intended build revision with
  `git log -1 --format='Commit: %H%nChanges: %s'`.
- Report automated checks separately from physical device testing. Successful
  simulator tests or builds do not replace acceptance testing on physical
  Android, iPhone, Watch, and GATAS devices.

Further details: [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md).
