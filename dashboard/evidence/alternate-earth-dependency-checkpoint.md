# Alternate-Earth dependency checkpoint

Published master checked with `git ls-remote origin refs/heads/master`:
`aac5ca8ee0fa5ed6e5f84949c66e9949085f1d85`.
`gh pr view 63 --json state,mergedAt,headRefName` reports OPEN and mergedAt null.
The published game uses wave parity for classic/cockpit rendering. Level three
has no reviewed ground mission API. No unpublished dependency source was copied.

Prepared independent, unconnected module `games/space-invaders/alternate-earth.mjs`:
explicit chapter descriptors; original Cinder Quay story/mission data; swept local
portal aperture test; immutable resource handoff. These contracts do not yet alter
the game, implement the portal lifecycle, create meshes, or provide a playable scene.

Validation on Linux VPS, Node test runner:
`node --test games/space-invaders/alternate-earth.test.mjs games/space-invaders/game.test.mjs`
Expected: contract and existing game tests pass. Observed: 17 passed, zero failed.
Report: `alternate-earth-contract-tests.txt`.

Playtest: No affected runtime workflow exists yet. CLI contract tests exercise both
crossing directions, low-frame sprint, frame/side/vertical misses, immutable resource
snapshots and same-world rejection. Existing game unit regression passes. Browser
journey, destination combat, touch, pointer lock, context loss and image/video capture
remain unexecuted; they require the reviewed ground mission. No prototype or old
media is offered as journey evidence.

Resume after reviewed descent (PR 63) and ground FPS work
`7d0ea9d5-c9e3-4f16-80fa-2b07c154a91d` reach master. Controller integrates master;
developer audits reviewed APIs, builds destination prefabs and portal coordinator,
then executes the full acceptance and browser media suite. All edits remain local.
No commit, push, deployment or master mutation was performed. Proposed changes have
no deletions. Session-only persistence is explicit; reload restoration is not claimed.
