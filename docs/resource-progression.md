# Industrial resource progression

Open the Mayor dashboard, choose **Businesses**, then **Resource progression**. The view shows the current era, the next required products and each era's capabilities. Complete one batch of every listed prerequisite to unlock the next era. Selling goods does not remove an unlock. The completed batch history survives saves and network snapshots.

Zone industrial land next to roads. Private developers buy land and reserve the exact building materials. New industries get plots only after their era unlocks. Factory work needs funded companies, workers with technical education and finished premises. Early clay pits, mines and oil extraction can start in field yards. Build a technical college to train factory staff. Agriculture and commercial food markets keep their existing roles.

| Era | New chains | Proof needed for this era | Working capability |
| --- | --- | --- | --- |
| 1. Settlement | Logging, stone quarrying and farming | Starting era | Houses, basic roads and storage |
| 2. Early Industry | Soil → clay; stone → ore; stone + wood → coal; ore + coal → iron; clay + coal → bricks; iron → tools and carts | Planks | Owned iron tools give quarry/logging crews 3× extraction speed. Carts extend freight reach from 96 to 128 cells. |
| 3. Heavy Industry | Iron + coal → steel; sand + coal → glass; ore + coal → copper; steel + copper + tools → machinery; steel + planks → rail tracks | Iron tools and kiln bricks | Owned machinery gives manufacturing 1.5× throughput. Track kits fund later rail fleet and station construction. |
| 4. Rail Age | Steel + machinery → engines; engine + steel + tracks → freight trains; bricks + tracks + machinery → station kits | Machinery and steel | A private freight train and station kit extend delivery reach to 512 cells and burn owned coal. |
| 5. Oil Age | Sand + machinery → oil; oil + coal → fuel; oil + fuel → rubber; engines + steel + rubber → trucks and buses | Freight train and station kit | Trucks haul 192 cells using fuel. Privately owned buses carry road commuters at 6 cells/second, scaled with the game clock, and charge fares. |
| 6. Automotive Age | Steel + machinery + fuel → engines; steel + rubber + glass → vehicle parts; engines + parts + fuel → cars | Fuel and rubber | Road freight reaches 256 cells. Prosperous citizens can buy cars and fuel for road travel at 7 cells/second. |
| 7. Electrification | Coal + fuel → electricity in an equipped power plant; copper + glass + electricity → electrical equipment; equipment + existing rail/vehicle products → trams, electric rail and metro | Car and vehicle parts | Powered factory equipment doubles manufacturing and consumes electricity per completed batch. Electric freight reaches 512 cells. Trams and metro serve road-network commuters at 8 and 10 cells/second using electricity and fares. |
| 8. Advanced Industry | Oil + electrical equipment → plastics; copper + plastics + equipment → electronics; steel + copper + electricity → alloys; these goods upgrade electric trains and cars | Electrical equipment and electricity | Modern freight trains reach 768 cells; advanced vehicles carry road freight 384 cells and their owners travel at 9 cells/second. Electronics and alloy production enable two-storey, eight-resident housing on the original footprint. |

Freight is a private market service. Vehicle products and station kits represent installed fleet, track and depot investment. This release models delivery range, owned assets, fuel/electricity use and passenger fares; it does not add a separate rail route editor or render vehicle models. Local deliveries within 96 cells use the existing hand-delivery service. Remote deliveries fail when the buyer has no suitable fleet, enough range or enough energy. Failed deliveries move neither cargo nor cash. Fleet assets are retained; energy is consumed. Companies buy available fleet upgrades from other private producers during paid work and stock purchases, checking at most once per quarter game-hour.

Extraction inputs remain finite: the soil quarry removes live dirt; the stone and sand quarries remove live terrain. Clay, ore, coal, copper and oil processing consume these purchased raw stocks. Deposits are represented through the existing terrain materials rather than new voxel ore blocks. Starter stone masonry, basic glass and lighting recipes remain available so existing city construction and saved worlds can bootstrap the new industries.

New games use the full catalog. Saves matching the previous default agricultural catalog gain the added industries without replacing existing company identities or balances. Recorded logging output imports the earlier earned plank milestone so a full timber store cannot stall an upgraded city. Other saved catalogs keep their own rules. Product IDs 1201–1230 and business IDs 32–49 are stable identities. `config/city-production.properties` mirrors the default catalog; custom data can override its products, recipes, business types and starting companies.

The native regression harness uses only a fresh synthetic city with trained crews and seeded input stocks:

```sh
python3 deploy/run_resource_progression_smoke.py --display "$DISPLAY" --profile target/resource-progression-profile --evidence dashboard/evidence
```

Run `mvn -Dlwjgl.natives=natives-linux test-compile` first. The harness uses the assigned display and inherited X authentication. It clicks the actual game dashboard, places an industrial zone, earns every era through paid simulation shifts, checks locked production and freight energy/range, saves and reloads the exact state, and captures the implementation's native UI.
