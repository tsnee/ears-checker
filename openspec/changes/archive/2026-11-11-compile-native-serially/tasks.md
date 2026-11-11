# Tasks

All work is on branch `batch-release-0.2.0`, in the "release 0.2.0" theme (draft PR #17), before `add-coursier-app`. The owner pushes from their own shell; Claude reads runs with `gh`.

## 1. Evidence

- [x] 1.1 Try to reproduce: up to 20 rounds of `./mill clean` on the six native modules' compiles, then `./mill '__:ScalaNativeModule.compile'` in parallel, keeping each round's log in the scratchpad and counting failures with this trace. If it fails, run the same number of rounds with `-j 1` and expect none; if it never fails, say so in the design and the upstream issue. Record the rounds and results in `docs/worklog.md`
- [x] 1.2 Measure the six native compiles from clean, in parallel and with `-j 1` (three runs each), and record them in `docs/performance.md` with the date, commit, and machine

## 2. CI and docs

- [x] 2.1 Split `ci.yml`'s Compile step (decision 1), with a comment giving the reason and the upstream issue
- [x] 2.2 CLAUDE.md (Build and test, and the list of checks), CONTRIBUTING.md, and the roadmap item to drop the workaround once a scala-native release fixes it (decision 2). Verify with `mq`

## 3. Upstream

- [x] 3.1 Update `upstream-issue.md` with task 1.1's result as a comment on scala-native#4950, and give it to the owner to post or to approve posting; put the issue's URL in the CI comment, CLAUDE.md, CONTRIBUTING.md, and the roadmap

## 4. Checks, review, commits

- [x] 4.1 Run the full gate as documented, the new native compile step included. Report coverage, and add a dated line to `docs/worklog.md`
- [x] 4.2 Run `/code-review` in a subagent on this change, and let the owner choose which findings to fix
- [x] 4.3 Commit (plan, then implementation); the owner pushes. Report the CI run's result and minutes, and check from its log that the native compiles didn't overlap

## Workflow follow-up

- After CI passes, archive the change on the same branch (syncing `continuous-integration`) for the owner to push.
