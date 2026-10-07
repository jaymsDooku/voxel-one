Intermediate rebase recovery

Only dashboard/progress.json conflicted. Retained stage 2 exactly: current recovery status and newer timestamps. JSON parses; all progress entries preserved. `git diff --check` passed.

Command: Python compared every required-base path containing city-saves, CitySaves or SavesMenu against `git show 6d1ec251:<path>`. 18 files byte-match, including source, tests, harnesses and historical media.

No new tests were run in this step. Previous recovery ran eight focused tests successfully; those are historical intermediate results.

Playtest: pending controller rebase continuation. Final-head save/copy/new/load and road workflows need fresh checks and media. Existing replayed road media is historical and is not final-head evidence.
