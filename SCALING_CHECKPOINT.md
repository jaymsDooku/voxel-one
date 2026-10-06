# Regional population: ready for independent review

Validated recovered gameplay and test source at `39798a64aee978ac85792752c094b33af27ba4f3`. Controller finished the rebase. Gameplay and tests remain unchanged from this head. Only fresh evidence, the local progress entry, this checkpoint and a tested road playtest launcher changed during final validation.

Regional populations support 10 million residents with at most 64 nearby regional agents. Regional saves use format 11 (`0x4349543B`) and protocol 21. Existing formats 1–10 load without a regional tail; format 10 retains paved-road types. Base-generated empty and paved-road fixtures retain exact base-writer bytes. Blocks 187–189, lane choices, road tests and all 28 base road source/tool/evidence files remain intact. All 49 base progress entries remain unchanged.

91 selected tests across 16 suites passed. Final native Main playtests passed regional settlement, nearby inspection, movement/wallet conservation, save/reload, starvation and local-only citizens. The road playtest passed dirt and 2/3/4 paved lanes, direction guide, distinct widths, upgrades and cancel without world/spending changes. Both fresh F10 videos decoded fully and fresh screenshots/video frames were visually inspected. Commands, environment, expected/observed results, benchmark timings and media hashes are in `dashboard/evidence/regional-scale-tests.json`. No full-suite pass is claimed.

Fresh regional evidence replaces the prior submitted-head media. Fresh road evidence uses `regional-road-*`; original base road evidence remains unchanged. All artifacts are below 6 MB. Media publication remains pending controller staging/commit/push. The historical pre-handoff report `regional-scale-recovery-tests.json` remains preserved.

DeepSeek source review was independently checked and resolved; no delegate remains pending. No owner answer is needed. Developer decision: ready for independent review. Runner alone handles Git publication and PR updates. No developer commit, push, merge or deployment occurred.

## Post-review CI handoff

Current submitted head: `817b97e78a0b7ca91af4f3712b312b1d57c97948`. Independent reviewer reported no substantial defect. Read-only `gh run view` confirmed CI run `37519826842` completed successfully on Linux, Windows, Mac ARM and Mac Intel. Publish job was skipped. Actual command and results are saved in `dashboard/evidence/regional-scale-final-ci.json`. No gameplay or test source changed; no new playtest is claimed. Reviewer/controller handles approval and merge. No owner answer is needed.

## Complete Playtest entry

Playtest: Recorded native application checks on the recovered implementation.
Environment: Linux X11, inherited assigned DISPLAY and XAUTHORITY, Mesa software OpenGL, Java 25.0.3, isolated synthetic offline profiles under target. Browser playtesting does not apply to this Java native application.
Commands actually executed:
python3 deploy/run_regional_playtest.py --display "$DISPLAY"
python3 deploy/run_regional_road_playtest.py --display "$DISPLAY"
Both commands exited 0. These are prior executed checks; this documentation update does not claim a new execution.
Steps: Open the mayor dashboard and Districts. Settle 1,000,000 residents twice. Focus district #1 and inspect a nearby resident. Observe movement and wallet updates, check money conservation, then save/reload. Exercise a region with no food, money or work for 12 simulated seconds. Open the local-only citizen table. In the road workflow, choose dirt and paved 2/3/4 lanes, place roads using endpoints and the direction guide, upgrade dirt, cancel after selecting the first endpoint, and escape the menu. Capture using the production F10 recorder.
Expected: 2,000,000 regional residents with 64 nearby agents; individual movement and wallets; conserved money and equal save/reload state; starvation reaches hunger=0 without negative food or money; 12 local named citizens remain searchable. Roads retain distinct widths and lane choices; cancel changes neither world nor road spending; escape returns to inspect.
Observed: All assertions passed. Regional counts were 2,000,000 and 64; save/reload and money checks passed. After 12 simulated seconds the starvation case had hunger=0, food=0 and money=0. The local-only table retained 12 named citizens. Dirt and paved 2/3/4 lanes, guide, widths, upgrade, cancel and escape checks passed. Regional run rendered 57 frames. Fresh screenshots were inspected. All 11 regional video frames and 132 road video frames decoded successfully.
Evidence: regional-scale-districts.png, regional-scale-nearby.png, regional-scale-starvation-edge.png, regional-scale-local-regression.png, regional-scale-main.mp4, regional-road-surfaces.png and regional-road-main.mp4. Command details and hashes are in regional-scale-tests.json. Publication is reserved to the controller.
