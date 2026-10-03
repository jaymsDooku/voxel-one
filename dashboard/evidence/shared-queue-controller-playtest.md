# Controller shared-queue playtest

Run from the assigned feature worktree on Node22.14.0, using the saved packaged implementation. The operator supplies a JSON object containing generalUrl, generalBearer and bridgeKey through an ephemeral pipe/echo-disabled stdin to the first command. Credentials are not written to files, command arguments, reports or browser artifacts. The test harness pins the General URL and authenticates only the fixed scoped bridge route.

```sh
/home/debian/.local/bin/node deploy/check_shared_queue_controller.mjs
PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers /home/debian/.local/bin/node deploy/capture_shared_queue_controller.mjs
```

Both commands were executed by the controller. The first returned real upstream HTTP200 with49 items, packaged route HTTP200 with49 matching item IDs and no credentials, and unauthenticated route HTTP401. The second served the exact packaged client with the real controller-fetched projection and rendered all49 items in Chromium and390px Safari-WebKit. It asserts the live status is visible before saving the phone capture. Private titles/descriptions/evidence are sanitized; actual statuses, ordering and counts are retained.

The private projection lives only in /tmp/voxel-controller-projection.json (mode0600). Browser dependencies were restored with Playwright install chromium webkit at /tmp/voxel-web-qa. A local authenticated fixture exercises the exact packaged worker against the real upstream; this does not claim production browser authentication or publication. Current implementation hashes and results are in shared-queue-controller-verified.json. The runner must publish scripts, report and capture with the exact submitted feature head before independent review.
