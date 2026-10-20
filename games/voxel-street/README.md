# Voxel Street

Open the Voxel One website and select **Play Voxel Street** on the home page.
The site package includes the full browser game. Players need no source checkout,
Python server, install, account, console commands, or developer tools.

For local site validation, run `python3 deploy/build_site.py` and serve only
`website/dist` with a static web host. The home-page link opens
`games/voxel-street/index.html` within that package.

Click **Sit at trading desk** or press **E** to focus the desk. Read the brief,
select a company and whole-share order, and inspect its preview. **Open market**
starts a real-time 180-second session. Buy at ask, sell at bid, or hold cash.
At 1:00 a company publishes earnings; at 2:00 a capital-spending survey moves
all three companies with different sensitivities. Leaving the desk does not
pause time. The focused screen includes market research, history, news, order
entry and midpoint-valued portfolio accounting. The closing review includes
results, recorded impulses, actual executions and the largest position.
**Next trading day** preserves the account. **Restart scenario** restores
$10,000 and the seed entered in the visible seed field. Guidance is dismissible.

There are no profit gates. Market data are fictional. The account lasts only
until the page is closed or reloaded. Limit orders, shorting, leverage,
derivatives, staff, upgrades, multiplayer and live prices are outside this slice.

## Simulation and accounting

The model advances in fixed one-second steps. Each day derives its own PRNG
stream from the selected uint32 seed and day number. Position size and order
validation do not consume random values. Matching seeds and integer-second
actions reproduce all prices, events, fills and account state. Background timer
delays catch up fixed steps rather than skipping events. Execution timestamps
are the current simulation second, with array order preserving same-second fills.

A quote has a 0.2% bid/ask spread around a positive midpoint. There are no fees.
Buying adds actual ask cost to basis. Selling removes weighted-average basis
and realizes bid proceeds less that basis. Values retain full numeric precision;
the UI rounds currency to cents. Holdings are valued at midpoint. Daily return
is closing equity minus opening equity, divided by opening equity for percent.
Company daily contribution equals realized today plus change in unrealized
since opening. Closing unrealized is also shown separately, so carried gains
are not mistaken for new daily gains. The largest position is maximum midpoint
dollar value observed after each fill or fixed step, including opening holdings.

Earnings rotate Harbor → Forge → Lumen across days. Growth earnings have the
largest surprise sensitivity; Harbor has the smallest. Event records retain
before/after midpoints, exact impulse fractions, announcement expectations and
reported results. Ordinary noise also changes prices. Review text distinguishes
recorded mechanics from interpretation. Prior closes become the next opening;
research history retains the latest six actual daily closes, preceded initially
by each company's fictional historical closes.

## Validation

`node --test games/voxel-street/market.test.mjs`

The browser integration harness uses the installed Playwright package and a
loopback source server on port 8787:

```
TMPDIR="$PWD/.tmp/browser" PLAYWRIGHT_BROWSERS_PATH=/tmp/voxel-web-qa/browsers node deploy/test_voxel_street.cjs
```

The harness uses only player-facing controls and real wall time. It records the
full loop with an isolated synthetic browser profile. It makes no model state
edits and does not accelerate the clock. Playwright recording replaces F10
because this standalone browser slice does not embed the native engine recorder.
Local sanitized evidence is under `dashboard/evidence/voxel-street-*`; the
controller publishes it with the feature branch. Independent review is required.
Physical GPU performance is not qualified by a software-rendered VPS playtest.
