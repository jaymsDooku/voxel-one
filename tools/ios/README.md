# Remote iPhone simulator checks

The VPS is Linux and cannot run Apple's simulator. `.github/workflows/ios-simulator.yml` uses the standard GitHub-hosted `macos-15` worker with read-only repository permissions, no supplied secrets, and no persisted checkout credentials. It dynamically selects an installed, available iOS runtime and a compatible iPhone type from `xcrun simctl list --json`, limiting runtime major/minor to the active `iphonesimulator` SDK reported by `xcrun`. A newer installed runtime cannot override an older active Xcode SDK. It creates a fresh device, waits for boot, captures evidence, and deletes that device afterwards. No macOS endpoint or Apple account is required by this workflow. When no compatible pair exists, the report returns `compatible_iphone_runtime_missing`; select an Xcode with a matching installed runtime before retrying. Failed commands report a fixed `errorStage` such as `create`, `boot`, `bootstatus`, or `screenshot`, with no raw command logs.

Local verification uses **Linux mock contract tests only**; environment readiness requires an actual successful hosted Mac run. A successful preflight proves the simulator and screenshot tools work. Its `preflight.png` is the simulator home screen, not a Voxel One client or gameplay test. No native client or diagnostic substitute is included here. Client mode intentionally fails while `ios/check-simulator.sh` is absent.

## Controller handoff

After the reviewed workflow is on the default branch, the controller dispatches `ios-simulator.yml` on that trusted branch with:

```json
{"expectedHead":"EXACT_40_LOWERCASE_HEX_COMMIT", "mode":"client", "requestId":"UNIQUE_CANONICAL_UUID"}
```

Use `preflight` for environment readiness only. The workflow validates inputs before checkout and checks out `expectedHead` in `source/`, including on a feature branch. It separately checks out the workflow commit (`github.sha`) in `validator/` and runs only that checkout's harness and unit tests. For controller dispatches, the workflow commit is on the trusted default branch; the feature cannot replace its verifier. Both checkouts disable credential persistence. Push/bootstrap and PR preflights use their workflow commit as the verifier and the exact requested/event source SHA as the client source. A checkout mismatch fails before provisioning.

The trusted entry point receives `--repo "$GITHUB_WORKSPACE/source" --evidence-dir "$GITHUB_WORKSPACE/source/ios-evidence"`; Git head/cleanliness checks and client script execution all use the requested source checkout. `--repo` defaults to the harness's own repository for local use. Relative evidence-directory paths resolve inside the selected source repository, and paths outside it are rejected. After client execution finishes, the verifier creates a fresh random artifact directory outside the requested source checkout under `--artifact-root "$RUNNER_TEMP"`. It copies only the generated report and media matching validated byte-count/SHA-256 receipts. The workflow uploads only this finalized directory from the verifier step output; source-directory wildcards and unreceipted files are never uploaded. The downloaded artifact retains `report.json` and media filenames at its root. The client subprocess does not receive GitHub output/environment control-file variables.

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

## Failed client build diagnostics

The native client script may redirect compiler/build/test output to exactly `ios/build/java-build.log`, `ios/build/native-build.log`, and `ios/build/native-tests.log`. If the script was actually invoked and the client run fails, the trusted verifier collects a narrow diagnostic summary from those three files. It never reads fixture, runtime, account, authentication, profile, ready or arbitrary caller-selected logs. Repository-relative file paths are retained; external absolute paths are reduced to an external basename and URLs are removed. Secret-bearing lines, private-key blocks, authentication material and environment dumps are excluded. Symlinked directories/files, hardlinked logs and non-regular files are not read.

The artifact includes reserved, verifier-written `client-diagnostics.json` and `client-diagnostics.txt` in that fresh external staging directory. Source files with these names, including directories or symlinks, are ignored. The directory is finalized after the client stops and its path is exported to the upload step only afterwards. A collector failure discards the entire partial directory, creates a fresh stage without diagnostics and records `diagnosticsStatus: "unavailable"`; success and preflight stages also contain no diagnostics. Both contain the exact `sourceHead`, `requestId`, `errorStage` and fixed `errorCode`; numeric `exitCode` or `timedOut` metadata is included when available. JSON `lines` entries provide the fixed `log` basename, `window` (`head` or `tail`), `lineInWindow`, diagnostic `severity` (`error`, `failure`, or compiler-context `info`) and sanitized `text`. A tail window's line number refers to that window, not an invented whole-file line count. Only compiler/build errors, missing build commands, compiler context and failed-test indicators are retained. `report.json` points to the summary through `diagnostics.path` and `diagnostics.textPath`, plus the JSON byte count (`diagnostics.bytes`) and SHA-256 (`diagnostics.sha256`), with corresponding `textBytes`/`textSha256` for the text summary; the controller can return these sanitized lines to the originating developer after validating the run/source/request identity. A failed report remains failed and `clientChecked` remains false.

Reads are capped at 128 KiB per log, using head/tail windows for larger files; each log exports at most eight unique lines of at most 320 UTF-8 bytes. Each summary artifact is capped at 16 KiB. Truncation and read/missing/unsafe source status are explicit. Raw logs, full command streams and environment values are never uploaded. Successful client runs and preflight failures do not collect these diagnostic logs. The Linux security tests use synthetic compiler errors and secret markers; an actual Mac client failure still needs a hosted rerun to produce its actionable diagnostics.

References: [GitHub-hosted runner choices](https://docs.github.com/en/actions/how-tos/write-workflows/choose-where-workflows-run/choose-the-runner-for-a-job), [workflow dispatch syntax](https://docs.github.com/en/actions/reference/workflows-and-actions/workflow-syntax#onworkflow_dispatchinputs), [Mac runner image inventory](https://github.com/actions/runner-images/blob/main/images/macos/macos-15-Readme.md), and [Apple simulator guidance](https://developer.apple.com/documentation/xcode/running-your-app-on-simulated-or-physical-devices). The actual job's SDK/runtime report is authoritative for that run.
