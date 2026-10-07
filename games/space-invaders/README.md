# Voxel Space Invaders

A new, standalone Voxel One browser game. Open `games/space-invaders/index.html` through an HTTP server. The website builder adds a Play link to the landing page. No account, external assets, package downloads, or game server are needed.

Three voxel alien models and a voxel player spaceship are drawn as shaded cube cells. Play includes a 45-alien fleet, boundary reversal and descent, faster movement as aliens die, bottom-row enemy fire, destructible shields, a bonus saucer, score, three lives, brief respawn immunity, harder waves, game over, and restart. An invasion ends the game. The saucer awards 100 points.

Use Left/Right or A/D to move, Space to fire, P or Escape to pause. iPhone/iPad browser controls support holding movement and Fire together with separate fingers. Pointer release/cancel clears held input. Losing focus or hiding the tab pauses the game. Resume clears held controls. Portrait and landscape layouts respect safe areas. This is a web game, not a native iOS package; no Java/GLFW client runs on iOS.

Run rules checks with `node --test games/space-invaders/game.test.mjs`. Build with `python3 deploy/build_site.py`. Serve `website/dist` on port 8766, then run `deploy/test_space_invaders.cjs` with Playwright and its WebKit browser installed. `PLAYWRIGHT_MODULE` selects the installed module. Use a writable `TMPDIR` under the worktree for sandboxed browser profiles. Browser tests use synthetic profiles and no account data. Mobile WebKit emulation does not validate physical iOS devices.

Small landscape screens use three reserved grid columns: header/HUD, arena, and stacked touch controls. The canvas keeps its aspect ratio. Start, pause, and game-over overlays fit inside it. Run `deploy/test_space_invaders_layout.cjs` against the same local server to check start, playing, paused, and game-over states at 568×320, 667×375, 844×390, and 390×844. It checks region overlap, viewport bounds, overlay bounds, touch target size and reachability, and touch navigation through each state.
