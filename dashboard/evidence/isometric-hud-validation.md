# Isometric player HUD validation

Source change: Main passes `!isometric` to InventoryHud. The health strip, nine-slot hotbar and held-item hint render only in player view. The inventory/crafting panel retains its own visibility. The prior render overload keeps its existing behavior.

Commands:

- `mvn -q -Dmaven.repo.local=target/maven-cache -Dlwjgl.natives=natives-linux "-DargLine=-Djava.io.tmpdir=$PWD/target/hud-smoke/tmp" -Dtest=InventoryHudTest,ControlsTest test`: PASS, 7 tests, 0 failures, 0 errors.
- Initial full-suite attempts: failed because default temporary directories were outside the writable sandbox. A full-suite rerun with a worktree temporary directory was stopped before completion. No full-suite pass is claimed.
- `python3 deploy/run_isometric_hud_smoke.py --display "$DISPLAY"`: final result recorded separately in isometric-hud-results.txt. Earlier attempts failed timing checks or were interrupted with exit 143. They exposed slow software-rendered frames and unreliable title-only window targeting after an interrupted process. The final harness targets its own PID. An extra return-to-sky check timed out despite focus recovery; it is not claimed as passed. The final bounded run tests startup, inventory and return to player view; final evidence replaces them.

Playtest: Linux native GLFW application on the inherited assigned X11 display, Mesa software rendering, Java 25, synthetic offline City Builder world and isolated user.home under target/hud-smoke. No browser workflow applies to this native HUD change. Test city startup, E inventory and Escape closure as an edge case, F6 player-view restoration as a regression, then capture the restored player HUD. Expected: no bottom health/hotbar at city startup or behind inventory; inventory remains usable; player view shows both bars.

Diff audit: only HUD visibility, a native playtest harness, task progress and current evidence are changed. No unrelated source deletions. Controller must integrate latest master and rerun if source changes. Evidence publication is reserved to the controller.

Final observed result: bounded native harness exited 0. All three final screenshots were inspected: sky-view bottom player HUD absent, inventory panel visible without the bottom player HUD, and player-view health/hotbar restored. Source and media remain local pending controller publication.

Rebase recovery audit against `af66e69b16f3310e6b396693f91cd4149a1c5541`: resolved only the progress JSON conflict. Retained the current branch’s first-person planet-entry milestone and every unrelated milestone, plus the assigned HUD milestone. JSON parses and unrelated entries match index stage 2. `git diff --check` passes. Intended source deletions are the old unconditional HUD rendering lines replaced by the visibility guard; no unrelated files are deleted. Prior tests and media above predate rebase completion and are historical until final-head validation. Controller must stage and continue the rebase before that validation.

Final-head validation after controller rebase completion: `08ef5e16ff17fa7f4d32135fa69cf8a15b28da40`. Rebuilt using the focused Maven command above: 7 tests, 0 failures, 0 errors, exit 0. Native harness command above exited 0. Fresh 800 x 600 screenshots were captured on the inherited assigned display and each was inspected. Observed: bottom player bars absent at city startup and behind inventory; E opens inventory and Escape closes it; F6 restores player view and both bars. Source/media SHA-256 hashes are in isometric-hud-tested-source.json. Full-suite and extra reentry limits above remain historical and are not claimed as passed.

Final diff audit against `af66e69b16f3310e6b396693f91cd4149a1c5541`: only the two HUD source files, task harness, current task progress and evidence differ. No file deletions. The replaced unconditional rendering lines are retained inside the visibility guard. Unrelated merged features and milestones are preserved. `git diff --check` passes. Pending controller publication and independent review.
