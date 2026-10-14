# City history

The mayor dashboard now reads city-owned history. Collection runs in the shared
`CitySimulation` for offline play and multiplayer, even with no open dashboard or
connected client. The existing snapshot, citizen, business, capital and district
screens remain. Click an Overview card or **Trends** for history. A workplace's
business detail links **Trends** to its company's revenue.

## Read the chart

Use Population, Businesses, City finances, Resources/production or Development.
**Find / compare** searches names, IDs and scopes. Choosing another compatible
metric adds it to the chart, up to four series. Choosing a different unit or a
balance instead of a period flow starts a separate chart. Click a legend entry to
hide its line. Colours and dash patterns stay tied to the selected series.

Choose the last day, 7, 30 or 90 days, or available history. Live follows simulation
time; clicking a sample, zooming or panning fixes the range. **Reset range** or
Home restores Live. C edits a custom range as `start,end` in elapsed simulation
seconds. Enter applies it. Left/right arrows fix the range and inspect samples, +/- or the wheel
zooms, and Page Up/Down pans. D/H/M chooses daily, hourly or detail resolution.
B cycles the default, zero-inclusive and automatic axis choices. Percentages
start at 0–100. Chart input stays inside the overlay. F10 still records normally.

Hover or keyboard inspection shows the configuration epoch, simulation date,
interval, value, unit, observed extrema and covered seconds. These are bucket
summaries, not raw points or pixels sampled from the line. Stocks use held
observations between captures; their means and extrema describe those observations,
not a claim of continuous measurement between them. Ratios use denominator/time
weights. Estimated medians remain estimates, including their interval summaries.
Missing values break the line. Empty, uncovered and partial intervals are labelled.
Faint vertical marks retain observed stock extrema.

Overview cards show current snapshot values and history sparklines. Changes compare
complete equivalent periods selected through Trends. Stocks compare covered period
means; flows compare period totals. Incomplete previous coverage is reported instead
of silently comparing a partial day with a full day. A frozen calendar uses elapsed
seconds for ranges and axes. History dates retain each epoch's clock configuration.

The mayor overlay uses logical window coordinates on scaled framebuffers. Optional
`-Dvoxel.dashboard.scale=1.25` enlarges its UI to 125%; accepted values are 1–1.5.
Invalid values fall back to 1. Other world and overlay controls keep their layout.

## Catalog and accounting

Each catalog entry has a stable machine ID, name, unit, domain, stock/counter/ratio/
estimate type, scope, precision, source, definition and aggregation rule. IDs include
company and workplace IDs, not their display names. Renames keep their series.
Inactive entities keep prior data and an observed inactivity date; a detail-cap omission is
labelled separately. Retained entity IDs reserve future local company/building IDs
on reload so a deleted tracked entity's ID cannot merge into a new entity's history.
Each city history has its own UUID. A fresh or copied named city starts a new history;
restoring a matching city/history pair retains its identity.

| Family | Source and meaning |
| --- | --- |
| Population | Local residents plus represented regional groups/agents, counted once; housed/unhoused residents, housing capacity, hungry residents, hunger reserve, savings and estimated median savings |
| Employment | Employed / **all residents**, including children; city, cohort and bounded district comparisons. This is not an eligible-worker unemployment statistic |
| City finances | Actual treasury balance; event-time public road spending and land receipts; separately labelled land-minus-road net flow, excluding initial budget and administrative grants |
| Companies | Operating cash; revenue, wages, input purchases, rent, costs and signed profit from workplace accounts counted once per company |
| Workplaces | The same account definitions at the location; assigned detailed staff, measured working activity, on-site shift staff and working/assigned share |
| Resources | Stocks, measured harvested/recipe output, consumed production inputs/power/local meals, and validated paid sales, separately by product |
| Development | Measured road cells, zone area by supported type, available/unfinished/active plots, completed buildings and vacant housing |

Regional needs and savings are cohort estimates. A district selector names that
scope. Hunger is a reserve: higher means better fed. Hungry counts based on a
regional cohort threshold are estimates. Unsupported arrivals/departures, eligible
employment denominators, shortage-duration events and traffic volume stay hidden.
Road cells are coverage, not vehicle throughput. There are no new taxes, debt,
production, zoning charges, traffic mechanics or public building expenses.

Revenue is not cash. Operating costs are wages + input purchases + rent; profit is
revenue minus those costs. Resource transfers never count as production. Initial
seed inventory is not production. Product quantities with different units cannot
share an axis. Local company accounting does not invent regional business receipts.
Event markers are optional and are not included in this implementation.

## Bounds, retention and saves

The clock is authoritative elapsed simulation time. Speed changes do not change
sample units. Rollback removes future coverage and begins a new epoch. Configuration
changes retain separate epoch metadata. Paused time creates no samples. No population
or finance data is backfilled from a current snapshot or an old session. Legacy city
saves start collection at load; this version does not import old business day records.

| Resolution | Width in a configured simulation day | Maximum retained buckets per series |
| --- | --- | --- |
| Detail | 1/288 day (5 simulation minutes) | 289, including a partial current bucket |
| Hourly | 1/24 day | 721, covering up to 30 days plus a partial bucket |
| Daily | 1 day | 366, covering up to 365 days plus a partial bucket |

Calendar buckets align to the configured midnight. Frozen calendars use elapsed
intervals. Cadence is checked inside simulation steps; the 0.1-second step quantizes
capture time. Samples cannot fabricate coverage across a large jump. Exact flows
use their accounting event time in half-open `[start,end)` buckets. New business
accounts register their bounded catalog entries before the first accounting event.
Flow coverage uses observed intervals plus only the continuous, unsampled tail
(up to 1.5 detail intervals); a missed capture marks every affected retained
bucket missing at all three resolutions. Event totals remain stored through gaps,
but charts do not present those incomplete totals as fully measured values. Negative profit
is valid. A reset, invalid counter correction or nonfinite source marks missing
coverage rather than a false spike. A custom range that cuts a completed bucket marks that edge as unavailable at the chosen resolution; choose a finer resolution. Each query reports its actual retained start;
these limits do not promise a full year for a new or truncated history.

The hard cap is 576 retained series. Configurable detail caps are:

| Java property | Default / maximum |
| --- | --- |
| `voxel.history.companies` | 32 |
| `voxel.history.products` | 32 |
| `voxel.history.districts` | 16 |
| `voxel.history.workplaces` | 4 |

Each accepts 1 through its maximum. City/cohort totals are retained first. Omitted
entity detail is visible as a cap boundary, not a closure or a fabricated zero.
Storage and query cost depend on represented groups and bounded local entities,
not on allocating a million individual regional citizens.

History is a version-1 binary sidecar beside the city save: `WORLD.city.history`.
It stores catalog, UUID, epochs, bucket summaries, prior observations and CRC32.
A SHA-256 fingerprint binds it to the saved city envelope. Atomic replacement happens
with the existing save lifecycle, not at every metric sample. City-save format 17
is unchanged. Loads enforce a 64 MiB file cap, series/bucket/epoch bounds, finite
values, ordering, version, checksum and matching world/clock. A bad sidecar starts
new coverage and reports the rejection; it does not invalidate or erase the world.
A copied named city deliberately starts fresh coverage, not a merged series.

## Networking and migration

Protocol 30 adds authenticated, read-only history query/reply messages. History is
never attached to `CityFrame` broadcasts. Requests carry city UUID, request ID,
metric IDs, start/end, resolution and point limit. They accept at most four unique
compatible series and 1,000 points each. Empty IDs request the bounded catalog.
Unsupported units, UUIDs, IDs, ranges, resolutions and limits are rejected.

Queries run on server workers. The series lock protects a bounded snapshot; network
writes use the existing output queue. Each peer has at most one pending query and a
250 ms request rate limit. The UI waits at least 400 ms between requests, caches fixed
ranges, refreshes Live at one second and discards superseded replies. A new provider
or city clears its caches. Responses include schema/revision/request IDs, catalog,
epochs, ordered summaries, coverage, units and quality metadata.

Old clients are rejected before login by a protocol 30 server. The new client can
reconnect to a protocol 29 server and retains its snapshot and clearly labelled
nonpersistent client-session Trends; it sends no history bytes to that server.
Existing protocol 14/15 and 22 fallback rules remain. Protocol migration tests still
check shipping, roads, aviation and saved city formats; their current-version
assertions now expect 30.

## Validation

Run the focused history, accounting, chart-state and compatibility tests with a
worktree-local temp directory. Run `python3 deploy/run_history_dashboard_smoke.py
--display "$DISPLAY"` for the native workflow. The harness uses only the inherited
role display and an isolated synthetic profile. It captures fresh screenshots and
an F10 clip inside `dashboard/evidence`. Reports label actual scale, platform,
coverage and publication state. A 125% UI test is not a claim of physical HiDPI
hardware testing. The controller publishes source and evidence, then integrates
master before final-head checks and independent review.

Overview keeps complete card rows inside the content viewport. At small or scaled
sizes, the wheel, Up/Down, Page Up/Page Down, Home/End and Previous/Next buttons
reach all 12 KPI cards. Drawing and card hit regions use the same row offset;
margins, gutters and off-screen rows cannot open a chart. The native Overview
check exercises the four bottom-card links at 1280x720, 900x600 and 900x600 with
125% UI scale, including scroll controls and return to world input.
