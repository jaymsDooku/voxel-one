# Scoped Hub validation

The coordinator publishes only the assigned feature branch. Its push starts
`.github/workflows/hub-validation.yml`. The workflow has read-only repository
access. It does not publish releases, deploy sites or use account credentials.

After the workflow is registered, the coordinator can repeat an exact-head run:

```sh
gh workflow run hub-validation.yml --ref feature/queue-62383835386364312d346130642d346262652d383736632d393534373062343337356161 -f source_sha=EXACT_40_HEX_FEATURE_HEAD
```

Replace the placeholder with the published branch head. The workflow rejects a
different head. Collect `hub-windows-<SHA>`, `hub-mac-intel-<SHA>` and
`hub-mac-arm-<SHA>` artifacts. Check each report's source SHA and source file
fingerprints. Inspect the screenshots and decode the videos before review.
Artifacts expire after 14 days. Copy sanitized reports and selected media into
`dashboard/evidence/` for permanent feature-branch evidence.

The checker builds two app images with bundled runtimes. The two fixture shells
contain the same reviewed code with different generation markers. They test
verified shell replacement and retention, not two published production releases.
It launches native executables with an empty PATH, checks the active cached shell
process, and exercises corrupt/partial downloads, cancellation, competing
launchers and legacy installation migration. A busy competing launcher must retry
after the lock owner exits; both must reuse one download. Windows checks hold an
actual file handle while updating, then install/uninstall the MSI and check its
desktop/start-menu shortcuts. Synthetic profiles and save sentinels stay outside
uploaded evidence. Only compact reports, screenshots and bounded videos upload.

The same bundled runtime also opens the production login dialog against isolated
City and Sandbox loopback TLS servers. It checks the fixed game choice, explicit
server port, invalid-port error and successful pinned sign-in. Captures occur
before filling the password. This validates the login handoff; it does not claim
an online rendered-world session or a production account login.

Desktop checks require a real accessible runner desktop. No display, macOS screen
permission or graphics restrictions are bypassed. Failure remains a failed or
pending platform check and requires a coordinator-provided scoped desktop runner.
The checker rejects clipped 1280x800 captures. JVM scales 1/1.5/2 exercise Swing
scaling; they do not establish actual Windows monitor DPI changes. Actual Windows
100/150/200% monitor scaling, GPU gameplay and two published shell releases remain
separate acceptance checks. Do not infer these passed from an environment check.

Local partial artifact checks can use `check_packages.py --artifacts-only`.
This option does not satisfy packaged GUI/platform acceptance. Local source SHA
labels identify the base commit; working-tree fingerprints identify local edits.
Remote checks run the exact published source SHA.

The production Space Invaders route currently fails publication validation.
Anonymous requests returned 403. Supported private service access returned 404
for both `/games/space-invaders/` and `/games/space-invaders/index.html`.
Root must publish the actual game assets and tested web provenance through the
authorized site publisher. Keep the catalog entry unavailable until that succeeds.
Do not change site privacy or promote a merged source commit as a deployed build.

Linux F10 checks must intersect the native process PID and game window title
(`xdotool search --all`). An OR search can select the Hub window and falsely send
F10 there. Hold one start/stop key event; wait for the new recording to encode.
Sparse software-rendered frames prove recording/launch only, not smooth gameplay.
