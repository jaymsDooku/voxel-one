# Expanded airport overlap fix ready for independent review

Exchange and private building overlap checks use airport current runway count. Regression tests attempt exchange construction on second and third runways with real road access; reject with unchanged treasury, buildings, voxel map and apply count. Outside exchange construction still works. Regional data and command compatibility remain preserved.

Current focused Maven tests: 23 passed, zero failures/errors (AviationTest10, CityBusinessTest9, RegionalSaveCompatibilityTest4). No new full-suite or package run claimed. Previous broad validation is historical in airport-tests.json.

Playtest: production Main, Linux X11/Mesa, inherited assigned DISPLAY/XAUTHORITY, isolated synthetic profile. Exchange UI attempt inside expanded runway rejected; treasury and world edits unchanged. Airport placement/expansion, single-airport edge, City hall regression, boarding/flight/arrival, compact menu and regional settle1000000/focus64 all passed. Fresh airport-exchange-rejected.png and other captures saved. F10 clip decoded fully: 40 frames, 1596688 bytes.

Commands, expected/observed results, source/media hashes in dashboard/evidence/airport-tests.json. git diff --check passed. All edits preserved; no commit, push, merge, deployment or index writes. Controller owns publication and independent review. URLs pending controller publication. Helper failed and was resolved as self; concurrency0. No owner answer pending.
