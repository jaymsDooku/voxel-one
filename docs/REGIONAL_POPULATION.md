# Regional population simulation

Open the mayor dashboard with F9 and choose **Districts**. **Settle 1,000**, **Settle 100,000** and **Settle 1,000,000** create outlying districts. Settlers bring savings, regional firms bring working capital, and each resident brings four meals. This is an explicit immigration event; it does not debit or credit the mayor's treasury. The local settlement and its named citizens keep their existing simulation.

Each district starts with three socioeconomic groups. A group stores its district, cohort, regional employer, population, housed and employed counts, average hunger, total household savings, food stock and company cash. Each employed group earns prorated daily wages from its company's cash. Paid regional work produces food at a cost equal to its sale price. Hungry households buy available food from the company. Every transaction transfers money between the household and company accounts. Missing cash, jobs or food can cause hunger; the model does not grant free meals or wages.

Click a district row to activate nearby residents. **Inspect nearby resident** visits and inspects an individual in the world. Up to 64 regional residents have individual wallets, hunger and positions. They walk a small district street circuit. Moving the offline camera out of range returns them to aggregate simulation. Counts, housed and employed totals, money and weighted hunger are conserved when a pool is split or merged. The multiplayer city has one shared focused district; clients see the same active pool. District focus commands select this shared view.

The local city still simulates up to 128 named citizens, with its existing family, education, housing, work and road systems. Regional agents use a simpler district movement and provisioning model. They do not create millions of voxel buildings or retain individual family histories. District housing and employment are aggregate counts; regional supply, payroll and firms are separate from local construction, capital orders and material markets. Aggregation keeps cohort averages, so regional hunger distributions and savings medians are estimates. The dashboard marks this distinction. Food totals above 2,147,483,647 are capped at that value in the existing integer HUD indicator.

## Bounds and costs

The regional limit is 10,000,000 residents, 4,096 groups, 1,024 districts and 64 active residents. All groups advance each fixed step. Simulation, snapshots and weighted metrics cost work per group plus the bounded active pool. Rendering and picking use local residents plus nearby agents. No operation builds a list with one element per distant resident. The renderer reuses one city snapshot for its world and HUD passes.

All limits are checked before settlement. Invalid groups, references, counts and nonfinite values are rejected on input. Changing focus conserves the population and financial totals, including groups whose entire population is active. Runtime counts remain bounded; raising the old local birth limit is unnecessary.

## Saves and network

City format 11 (`0x4349543B`) stores groups, active residents, focus and the regional clock. Formats 1 through 10 load with an empty regional population. Writing a regional city to a pre-11 format is rejected rather than discarding its residents. Protocol 21 carries the same compact state and district commands. The client retains its established protocol 14/15 server fallback formats and cannot send district commands to those servers. Protocol 19, 20 and 21 peers require matching server versions. Existing format-10 paved roads retain their lane types; only format 11 appends regional state. Base-generated format-10 fixtures under `src/test/resources/city` cover empty and paved-road cities.

## Reproduce checks

Use a JDK and Maven. Keep temporary files inside the checkout when sandboxed:

```sh
mkdir -p target/tmp
/usr/share/maven/bin/mvn -q -Djava.io.tmpdir="$PWD/target/tmp" -DargLine="-Djava.io.tmpdir=$PWD/target/tmp" test
python3 deploy/run_regional_playtest.py --display "$DISPLAY"
```

The native check uses the assigned existing X11 display and inherits `XAUTHORITY`. It never creates an X server. It runs the full `Main` application with a fresh synthetic profile under `target/regional-playtest/profile`, exercises UI settlement and inspection, checks starvation and local-only behavior, saves and reloads the city, and captures PNGs plus an F10 MP4 under `dashboard/evidence`. Runtime logs and synthetic stores under `target` are not publication artifacts.

`RegionalPopulationTest` runs 6,000 steps with 10,000,000 residents in 30 groups and checks compact serialization, conservation, movement, capacity rejection and save migration. `RegionalMultiplayerTest` exercises real TLS snapshots, late join and server restart with temporary synthetic identities. Measured results and limitations are recorded in `dashboard/evidence/regional-scale-tests.json`; simulation benchmark timings are not a GPU frame-rate guarantee.
