# Remote iPhone simulator checks

The VPS is Linux and cannot run Apple's simulator. `.github/workflows/ios-simulator.yml` uses the standard GitHub-hosted `macos-15` worker with read-only repository permissions, no supplied secrets, and no persisted checkout credentials. It dynamically selects an installed, available iOS runtime and a compatible iPhone type from `xcrun simctl list --json`, limiting runtime major/minor to the active `iphonesimulator` SDK reported by `xcrun`. A newer installed runtime cannot override an older active Xcode SDK. It creates a fresh device, waits for boot, captures evidence, and deletes that device afterwards. No macOS endpoint or Apple account is required by this workflow. When no compatible pair exists, the report returns `compatible_iphone_runtime_missing`; select an Xcode with a matching installed runtime before retrying. Failed commands report a fixed `errorStage` such as `create`, `boot`, `bootstatus`, or `screenshot`, with no raw command logs.

Local verification uses **Linux mock contract tests only**; environment readiness requires an actual successful hosted Mac run. A successful preflight proves the simulator and screenshot tools work. Its `preflight.png` is the simulator home screen, not a Voxel One client or gameplay test. No native client or diagnostic substitute is included here. Client mode intentionally fails while `ios/check-simulator.sh` is absent.

## Controller handoff

After the reviewed workflow is on the default branch, the controller dispatches `ios-simulator.yml` on that trusted branch with:

```json
{"expectedHead":"EXACT_40_LOWERCASE_HEX_COMMIT", "mode":"client", "requestId":"UNIQUE_CANONICAL_UUID"}
```

Use `preflight` for environment readiness only. The workflow validates inputs before checkout and checks out `expectedHead` in `source/`, including on a feature branch. It separately checks out the workflow commit (`github.sha`) in `validator/` and runs only that checkout's harness and unit tests. For controller dispatches, the workflow commit is on the trusted default branch; the feature cannot replace its verifier. Both checkouts disable credential persistence. Push/bootstrap and PR preflights use their workflow commit as the verifier and the exact requested/event source SHA as the client source. A checkout mismatch fails before provisioning.

The trusted entry point receives `--repo "$GITHUB_WORKSPACE/source" --evidence-dir "$GITHUB_WORKSPACE/source/ios-evidence"`; Git head/cleanliness checks and client script execution all use the requested source checkout. `--repo` defaults to the harness's own repository for local use. Relative evidence-directory paths resolve inside the selected source repository, and paths outside it are rejected. Artifact upload uses `source/ios-evidence/*`; the downloaded artifact retains `report.json` and media filenames at its root.

Each request must use a new UUID. The run display title is `Voxel iOS <requestId>`; bind the actual `workflow_dispatch` run by that title, event and post-dispatch timestamp. Download that run's artifact named `ios-evidence`, then verify `report.json` has the requested `sourceHead`, `requestId`, `mode`, `status: "passed"`, and (for client testing) `clientChecked: true`. A preflight report or an artifact from a different run cannot complete native-client acceptance. Evidence filenames, sizes and SHA-256 digests are recorded in the report.

Pushing `feature/ios-worker-bootstrap` runs preflight automatically. Pull requests run it only for changes to this workflow, `tools/ios/**`, or the client test script. The controller is responsible for exact-head native-client dispatches after developer publication and again during independent review. There is no automatic deployment, release or merge in this workflow.

## `ios/check-simulator.sh` contract

The native-client developer must add this script with the actual client implementation. The harness invokes:

```sh
bash ios/check-simulator.sh SIMULATOR_UDID ABSOLUTE_EVIDENCE_DIRECTORY EXACT_SOURCE_HEAD
```

The same values are supplied as `IOS_SIMULATOR_UDID`, `IOS_EVIDENCE_DIR`, and `IOS_SOURCE_HEAD`; `IOS_RUNTIME_ID` identifies the selected runtime. Use the supplied simulator destination, not a hardcoded iOS version or an existing simulator. The script must build the actual native Voxel One `.app` from the checked-out source using the installed `iphonesimulator` SDK, install it on that device, execute meaningful client workflow checks, and exit nonzero on failure. Do not change the source commit or tracked source files while testing. Do not ask this worker to publish or push evidence; the controller retrieves the artifact.

After successful tests, write `$IOS_EVIDENCE_DIR/client-result.json`:

```json
{
  "sourceHead": "EXACT_SOURCE_HEAD",
  "simulatorUdid": "SIMULATOR_UDID",
  "application": "voxel-one",
  "evidenceType": "native-client",
  "synthetic": false,
  "status": "passed",
  "bundleId": "com.example.voxelone.client",
  "appPath": "ios/build/VoxelOne.app",
  "tests": [{"name": "Describe an actually executed client workflow", "status": "passed"}],
  "videos": ["client-playtest.mp4"]
}
```

`appPath` is relative to the source checkout. The `.app` must contain a matching bundle identifier and native Mach-O executable. The outer harness verifies its installed app container, launches that bundle independently, and captures `client.png` with `simctl io ... screenshot`. `clientChecked` becomes true only after the script, native-app checks, launch and required screenshot succeed. A diagnostic harness, Safari page, synthetic image, missing app or mismatched source receipt cannot satisfy this contract. Independent review still checks the actual displayed client and its workflow evidence.

Videos are optional when the workflow is adequately demonstrated by stills; record motion with `xcrun simctl io "$IOS_SIMULATOR_UDID" recordVideo` when it helps demonstrate gameplay. Declare up to three plain `.mp4` filenames in `videos`, keep each below 6 MB, and stop recording before the script exits. Screenshot and declared video files are checked before acceptance. Keep artifact content limited to public test results and captures: never include credentials, configuration dumps, owner answers, private prompts or raw runtime logs. The report intentionally contains only source/runtime/device/test/evidence metadata and fixed error codes.

Run local contract tests with `python3 -m unittest discover -s tools/ios -p 'test_*.py' -v`. These tests mock Apple commands, binary fixtures and screenshots and are explicitly not proof of a running Mac simulator or a real native client. The owner will attach actual Mac CI results and captures after running the workflow.

References: [GitHub-hosted runner choices](https://docs.github.com/en/actions/how-tos/write-workflows/choose-where-workflows-run/choose-the-runner-for-a-job), [workflow dispatch syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#onworkflow_dispatchinputs), [Mac runner image inventory](https://github.com/actions/runner-images/blob/main/images/macos/macos-15-Readme.md), and [Apple simulator guidance](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices). The actual job's SDK/runtime report is authoritative for that run.
