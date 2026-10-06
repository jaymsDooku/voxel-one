# Regional population: ready for independent review

Validated recovered gameplay and test source at `39798a64aee978ac85792752c094b33af27ba4f3`. Controller finished the rebase. Gameplay and tests remain unchanged from this head. Only fresh evidence, the local progress entry, this checkpoint and a tested road playtest launcher changed during final validation.

Regional populations support 10 million residents with at most 64 nearby regional agents. Regional saves use format 11 (`0x4349543B`) and protocol 21. Existing formats 1–10 load without a regional tail; format 10 retains paved-road types. Base-generated empty and paved-road fixtures retain exact base-writer bytes. Blocks 187–189, lane choices, road tests and all 28 base road source/tool/evidence files remain intact. All 49 base progress entries remain unchanged.

91 selected tests across 16 suites passed. Final native Main playtests passed regional settlement, nearby inspection, movement/wallet conservation, save/reload, starvation and local-only citizens. The road playtest passed dirt and 2/3/4 paved lanes, direction guide, distinct widths, upgrades and cancel without world/spending changes. Both fresh F10 videos decoded fully and fresh screenshots/video frames were visually inspected. Commands, environment, expected/observed results, benchmark timings and media hashes are in `dashboard/evidence/regional-scale-tests.json`. No full-suite pass is claimed.

Fresh regional evidence replaces the prior submitted-head media. Fresh road evidence uses `regional-road-*`; original base road evidence remains unchanged. All artifacts are below 6 MB. Media publication remains pending controller staging/commit/push. The historical pre-handoff report `regional-scale-recovery-tests.json` remains preserved.

DeepSeek source review was independently checked and resolved; no delegate remains pending. No owner answer is needed. Developer decision: ready for independent review. Runner alone handles Git publication and PR updates. No developer commit, push, merge or deployment occurred.
