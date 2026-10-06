# Cargo and aviation recovery handoff

Branch: feature/queue-63663864323833632d366236302d343366362d613866362d643262326334346335353430.
Base: 1ecbf565c449d7b5c96a621310c1a012b80ae1aa.

Return state: recover. dashboard/progress.json contents are resolved. The index remains unmerged for the controller to stage and continue the preserved rebase. Do not abort/reset, commit or push.

Aviation and cargo coexist in the resolved files. Protocol 22 and format-12 saves remain intact. 734 base source/test/fixture/evidence/deploy files match unchanged outside intended cargo paths. No staged deletion exists.

Pre-continuation checks: 38 targeted tests passed with zero failures/errors/skips, including aviation, regional save compatibility, multiplayer, cargo and controls. Complete format-12 save CLI roundtrip and protocol-22 assertion passed. git diff --check passed. Commands and report correction are in dashboard/evidence/cargo-aviation-recovery.md.

After controller continuation: inspect all replayed files. Correct cargo-final-validation.md, which is absent at this replay step, to retract its blanket no-deletion claim against the newer aviation base. Run final-head cargo and aviation native checks using inherited DISPLAY/XAUTHORITY and isolated synthetic profiles. Capture and inspect fresh media under dashboard/evidence. Preserve historical evidence and label it as historical. Do not reuse earlier cargo media as proof of the continued head.

All delegate tasks are resolved. No test is running. No Git index mutation, commit, push, merge or deployment was performed. Controller lease/budget gating remains required.
