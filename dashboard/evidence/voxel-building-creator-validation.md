Breakwater House exercises multi-part structure creation through a deterministic v1 recipe, a packaged CLI, and authenticated loopback endpoints. The native preview stages the compiled geometry in a temporary inspection plot.

Acceptance criteria and verification:

- A recipe can layer bounded parts, boxes, elliptical cylinders, precise air cuts, glass, and colored lights. The compiler reports actionable line errors and limits work and output.
- The local API and CLI return the same canonical edit stream. Tests cover authentication, origin, request limits, deterministic output, and engine voxel round-tripping.
- The example shows a curved corner, recessed portholes, a lighted roof sign, and an accessible porch and patterned lobby. On Windows the packaged CLI compiled the final recipe, and the game walked through the entrance into the lobby without creating a world save.
- Maven reported 36 tests, 0 failures, 1 skipped optional browser test. The example-specific test also passed after the final lettering correction. The F10 video in dashboard/evidence decoded all 34 frames.
