# Configuring Voxel City One businesses

Use `--production-config config/city-excavation.properties` with the server, or with the desktop game using `--offline --game city`. This example uses a focused starting roster with construction suppliers and two excavators harvesting dirt and sand. A city has a finite starting workforce, so adding more companies does not guarantee a crew for every company. No engine edits are needed to add another extraction company or a manufacturing type. For engine integration, pass `ProductionCatalog.load(path)` to `CitySimulation`, `CityEconomy`, or `LocalGame.startGame`.

The configuration is a UTF-8 Java properties file. Start by copying `config/city-production.properties`, which provides the normal products, recipes and equipment. Business fields are optional; omitting them preserves the default city.

```properties
business.types=15
business.15.name=Excavator
business.15.harvest=2,4
business.15.rate=32
business.15.capacity=512
companies=earth-movers,sand-crew
company.earth-movers.name=Earth Movers
company.earth-movers.type=15
company.earth-movers.cash=1500
company.sand-crew.name=Sand Crew
company.sand-crew.type=15
company.sand-crew.cash=1500
```

`business.types` lists the types to add or override. IDs 0–14 retain the standard city roles; use 15–63 for extensions. IDs are permanent identities in saved worlds. `name` is the sector label shown in business dashboards and building inspectors. `harvest` lists natural block IDs: dirt **2**, stone **3**, sand **4**, wood **6**. The current terrain has no ore blocks; adding ores requires terrain/block support and registering them as supported natural materials in the engine. Empty harvest lists create manufacturing types when paired with recipes.

`rate` defaults to 256 cycles per paid worker-hour and accepts positive finite values up to 4096. Pure harvesters attempt one block of each listed material per cycle, bounded to 512 cycles per simulation update. `capacity` defaults to 512 blocks per material (1–4096). Each extracted block must exist in the live world and is removed before it enters company stock. Protected and occupied land stays protected. If one material fills storage, other materials can continue. Deposit search is bounded and resumes across updates.

For logging or other combined extraction/processing types, list the raw material and add recipes referencing the same numeric type through `recipe.<id>.company`. Recipe rates/capacities govern processing; the first harvest material supplies raw stock. Existing recipe and equipment configuration works with new type IDs. Agriculture continues to use its existing crop/livestock rules on types 7, 9 and 10; changing a sector label does not change those rules.

`companies` adds starting companies to the normal companies by default. Each key must be unique, 1–48 letters, digits, underscores or hyphens. Set `companies.inherit-defaults=false` to replace the starting roster, then list all desired companies, including property developers (type 0) and markets (type 1) if the city needs them. Company names must be nonblank, at most 48 characters, and unique within their type. Cash defaults to 1500 and accepts finite values from 0 to 1,000,000,000. Multiple companies can share a business type; each has its own accounts, stock and production. New industrial types participate in industrial demand and receive workers through the normal company-yard/workplace system.

The complete catalog is embedded in city saves and multiplayer snapshots. An existing save keeps its saved configuration and balances even if the external file changes; use a new world to apply a changed configuration. Legacy saves receive the matching built-in definitions. City save format 7 and protocol 16 carry business data; use matching updated clients and servers. Validation rejects duplicate IDs, undefined company/recipe/equipment types, invalid amounts, and unsupported extraction materials at load time.
