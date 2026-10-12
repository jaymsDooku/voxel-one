# Sandbox recipe book

Open inventory with its configured control (E by default). Select **Recipe Book**. A wide window shows the book beside the 36-slot inventory. Smaller windows use Inventory and Browse/Details tabs. The Sandbox window supports a minimum of 640x480; City keeps its existing minimum height. Slots 0–8 remain the nine-slot hotbar.

Search output, recipe, or ingredient names. Pickaxe finds wooden and stone versions. Categories are All, Tools, Materials, Building, Decorations, and Lighting. Craftable now limits the view to recipes for which you have ingredients. Leave it off to discover missing recipes. Cut and join recipes start folded into material groups; open a group to browse its depths, or search directly.

Select a tile to see its output, variant, ingredients, and exact owned/required totals. Tool diagrams explain the material arrangement; crafting draws from your inventory without a second drag-and-drop grid. Ingredient buttons open a recipe when one exists. Back returns to the previous recipe. Harvested ingredients show their source.

Use minus/plus to choose 1–64 batches, or Max to choose the ingredient-derived maximum, capped at 64. Review total costs and output before Craft. Missing materials disable Craft and show the shortfall. Multiplayer waits for the authority to finish before accepting another action. If inventory is full, excess output becomes nearby drops. If all excess cannot fit in the ground-drop capacity, the entire craft is rejected without consuming anything.

Tab and arrows move visible focus; Enter activates the focused control. Page Up/Down and the mouse wheel browse recipes. Search accepts text, Backspace/Delete, Left/Right, and Home/End. Escape leaves search focus first, then the book, then inventory. The inventory control closes inventory directly when search is not focused. The open inventory/book releases the mouse and blocks movement, breaking, and placing. Closing restores gameplay capture, or leaves the mouse free in sky view.

Tools stack to one. Move a tool into the hotbar to equip it. Q (or the configured Drop one held item control) drops one held item beyond immediate pickup range. Walk towards it to pick it up. Tools are carryable, saveable voxel items; they cannot be placed as terrain. This release implements no durability, combat damage, or mining-speed bonus.

| Output | Head material | Sticks | Batch output |
| --- | --- | --- | --- |
| Wooden / stone pickaxe | 3 planks / stone | 2 | 1 |
| Wooden / stone sword | 2 planks / stone | 1 | 1 |
| Wooden / stone shovel | 1 plank / stone | 2 | 1 |
| Wooden / stone axe | 3 planks / stone | 2 | 1 |
| Sticks | 2 planks | 0 | 4 |

Existing planks, bricks, glass, flower pots, LED lights, grass/compost, and fractional cut/join recipes remain available. Model and LED goods keep their placement behavior. This catalog is separate from City company manufacturing.

## Item and format compatibility

`Items.Definition` contains byte identity, readable name/description, category, stack limit, optional placeable-block reference, and optional immutable voxel model. World blocks continue to validate through `Blocks.valid`; non-placeable item IDs never become terrain types.

Legacy IDs 0–189 retain their meaning: custom models 9–136, fractional blocks 137–164, goods 165–167, fractional goods 168–179, LED 180, LED pieces 181–184, and water/mineral/road items 185–189. IDs 199–254 remain unassigned; 255 remains the world partial-cell marker.

| ID | Item |
| --- | --- |
| 190 | Sticks |
| 191–194 | Wooden pickaxe, sword, shovel, axe |
| 195–198 | Stone pickaxe, sword, shovel, axe |

Inventory stays 72 bytes (36 unsigned-byte type/count pairs). Each count must respect its item's limit, and empty type/count pairs must agree. Built-in tool models are looked up separately from the world's player-authored model library; they consume no custom IDs or serialized model entries.

Offline save version 8 and server-world marker -8 distinguish saves that allow the new item bytes. Readers still accept prior offline versions 3–7 and server markers, validating their legacy inventories/drops against legacy IDs. Terrain generator and City sidecar formats are unchanged. Older executables reject the new save version instead of silently reinterpreting items.

Protocol 29 adds identified batch craft requests and held-item drops. A current server rejects older client versions. Existing City compatibility connections to servers 14/15/22 remain available, but new crafting and dropping require server protocol 29; they never send the new request shape to an old server. Use the normal launcher update workflow for compatible builds.

## Registering recipes

The documented Java definition format is `Crafting.Recipe(id, name, inputs, output, count, category, group, diagram)`. Inputs map item IDs to positive per-batch counts. `group` is empty for ordinary goods/tools or a material name for folded cuts/joins. `diagram` is empty for processing or three strings of three cells using `H` (planks/stone head), `S` (sticks), and space. A five-argument convenience definition derives output category and uses no group or diagram.

Register definitions in the Sandbox catalog, then call `Crafting.validateCatalog` before exposing the catalog. Validation rejects duplicate/negative IDs, blank names, unknown/empty items, nonpositive quantities, arithmetic overflow at 64 batches, and invalid or inconsistent tool diagrams. IDs must never depend on presentation order. Existing fractional IDs 0–87 and goods IDs 88–94 are frozen; sticks is 95 and tools are 96–103. Offline and server authorities use this same catalog and checked batch calculation. Any catalog identity change requires a protocol compatibility change.

Craft wire requests contain request ID, recipe ID, batch count, and validated pose. No output item/count or ingredient count is supplied. Result identity and feedback follow the authoritative inventory update. Repeated/nonmonotonic request IDs are rejected per connection. Excess is split into stacks respecting the output limit, with capacity and ground ID bounds checked before committing consumed inputs, inventory output, and all drops.
