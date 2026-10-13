# VoxelVehicles

VoxelVehicles is a **VoxelCore addon**, replacing the old HavenVehicles item registry, ItemsAdder dependency, ProtocolLib steering code and per-entity persistent speed flags.

## Architectural contract

- **One visible `ItemDisplay` per vehicle.** A single configurable `Interaction` is the click target; an invisible, marker-style armor stand exists **only while a driver is mounted**. This is one *visual* entity, not one entity total.
- Vehicle items, models, resource packs, definitions, and inherited config are owned by **VoxelCore**. VoxelVehicles reads `properties.vehicle`; there are no duplicate item definitions.
- One server-thread scheduler processes **occupied vehicles only**, using deterministic acceleration/steering and bounded block-collision sampling. No per-vehicle scheduled tasks or forced chunk loads.
- Persist the vehicle's logical UUID, owner, item ID, world and location in `plugins/VoxelVehicles/vehicles.yml`. Only transient entities are recreated after chunk loading/restart.
- VoxelVehicles does **not** depend on VoxelFurniture. A single item may contain either `properties.furniture` or `properties.vehicle`, but don't configure both on the same placeable item.

## Example VoxelCore item definition

Add to `plugins/VoxelCore/content/<pack>/content/items.yml` (use your normal VoxelCore pack layout and namespace):

```yaml
items:
  oak_car:
    material: PAPER
    display_name: "&6Oak Car"
    render:
      model: voxel:item/vehicles/oak_car
    properties:
      vehicle:
        type: LAND          # LAND or experimental PLANE
        model_item: voxel:oak_car
        drop: voxel:oak_car
        public: false
        scale: 1.0
        view_distance: 64
        hitbox:
          width: 1.8
          height: 1.3
          offset: {x: 0.0, y: 0.0, z: 0.0}
        seat: {x: 0.0, y: -0.7, z: 0.0}
        physics:
          speed: 0.35           # blocks per server tick
          reverse: 0.15
          acceleration: 0.025
          braking: 0.045
          drag: 0.94            # multiplier when not accelerating
          turn_rate: 4.0        # degrees per tick
          step_height: 0.6      # experimental
          fly_speed: 0.4        # PLANE only
          climb_speed: 0.22     # PLANE only
```

**Important:** The item model must be authored for ItemDisplay rendering; a former armor-stand helmet model may need a different resource-pack transform. Read the old HavenVehicles asset configs before migrating real vehicle models.

## Commands and controls

`/vv list`, `/vv spawn voxel:oak_car`, `/vv remove` (nearest within 6 blocks), and `/vv reload` require `voxelvehicles.admin`.

Right-click a block with a VoxelCore vehicle item to place; right-click the vehicle hitbox to mount. Use W/S for throttle, A/D for steering, and jump/sneak for plane ascent/descent (sneak may dismount as a vanilla control). Sneak-left-click a vehicle to reclaim its item if you own it or are an admin. Placed vehicles are private by default unless `public: true`.

## Version support and current limitations

This is a **first implementation PR, not the complete HavenVehicles migration**.

| Feature | 26.2 | 1.21.4–1.21.11 | 1.19.4–1.21.3 | 1.12–1.19.3 |
| --- | --- | --- | --- | --- |
| Single ItemDisplay + interaction | Implemented; needs server testing | API available; needs server testing | API available; needs testing | Requires legacy renderer |
| Native movement inputs | Implemented; needs server testing | API available; needs testing | Not available | Not available |
| Passenger-preserving seat movement | API supports default behavior | Needs version adapter/validation | Needs version adapter/validation | Needs version adapter |
| Parking, save/load and chunk lifecycle | Implemented; needs tests | Needs tests | Needs tests | Not implemented |

Older than 1.19.4: this build disables itself when display entities are absent. Earlier versions that lack native input events can load and place vehicles but **cannot drive until a legacy steering adapter is implemented**. Multi-seat passengers, trailers, storage, attachment animations, realistic plane lift, precise slab/stair voxel shapes, collision with other entities, vehicle-to-vehicle collisions and integration/load tests are follow-up work.

**Do not migrate existing HavenVehicles world data yet.** A migration utility should translate legacy storage into this logical-instance format after a full backup. Avoid running the old HavenVehicles plugin simultaneously against the same worlds.

### Planned next milestones

1. Verify CI, Paper 26.2 load, mounting, W/S/A/D, rendering and clean restart.
2. Introduce version-specific input and passenger adapters for earlier supported server versions, without forcing ProtocolLib on 26.2.
3. Improve collision sampling using voxel shapes, ramps/slabs, water and gravity, plus multi-seat support.
4. Convert HavenVehicles vehicle definitions, variants and trailers, then test migration against copied worlds and a performance benchmark.


## Dyeable vehicles

On Minecraft 1.21.4+, vehicle items can use the same color setup as VoxelFurniture's curtains.

Add to the VoxelCore tractor item:

```yaml
render:
  model: voxel:item/vehicles/tractor_overhead
  custom_model_data:
    color: '#FFFFFF'
  rule:
    model:
      id: voxel:item/vehicles/tractor_overhead
      tint: color
properties:
  dyeable:
    key: color
    color: '#FFFFFF'
  vehicle:
    type: LAND
    model_item: voxel:tractor
    drop: voxel:tractor
```

Keep your existing physics, seats, hitbox and access settings below the vehicle mapping.
Add `"tintindex": 0` only to the paintable faces of the tractor model, not the seat or tires.
The selected RGB color now travels with the placed vehicle, survives restart and is restored on pickup.
For a separate model_item definition, make that definition dyeable too.


## Vehicle offsets, rendering, pickup and cleanup

The `properties.vehicle` mapping now supports a **global local-space offset**.
It moves the visual, interaction hitbox and driver seat as one assembly.
The hitbox and seat offsets are measured relative to this translated/rotated visual origin:

```yaml
properties:
  vehicle:
    type: LAND
    offset: {x: 0.0, y: 0.0, z: 0.0, rotation: 0}
    hitbox:
      width: 1.8
      height: 1.6
      offset: {x: 0.0, y: 0.0, z: 0.0}
    seat: {x: 0.0, y: -0.7, z: 0.0}
```

For a tractor, set `offset.y: -0.25` to lower the **visual model, interaction box and seat**; collision/terrain driving stays anchored to the logical wheel-ground position. Adjust `seat.y` separately to position the driver relative to the model. `rotation` is degrees around the Y axis in addition to the driving direction.

The ItemDisplay now doubles as the player seat on supported servers: **no additional armor stand** and no change of world origin when mounting. Its model vertices are translated relative to the seat root in exactly the same way when parked and mounted. An invisible ArmorStand is used only as a compatibility fallback if direct riding is refused. This still requires testing on Paper 26.2.

**Left-click / punch** an unoccupied owned vehicle to pick it up as a dropped VoxelCore item; admins with `voxelvehicles.admin` can also pick it up. An arm-swing ray test works even where the Interaction entity does not fire Bukkit damage events. `/vv remove` deletes a nearby instance without an item drop.

**Live reload:** after `/vc admin content reload`, the plugin sees the updated VoxelCore revision and refreshes loaded vehicles' models, scale, offsets and hitboxes automatically on the next tick; unloaded vehicles refresh when their chunk loads. `/vv sync` triggers a refresh manually and `/vv cleanup` removes stale tagged vehicle entities in already loaded chunks. A periodic repair/orphan sweep runs every `maintenance.cleanup-ticks` (default 600).


### Mounted display smoothing

Mounted vehicles use the ItemDisplay as the movement root for the passenger chain. The
ItemDisplay must interpolate teleports over at least one tick, otherwise its visual
model snaps each tick while the player riding it is smoothly interpolated by the client.
The old renderer incorrectly set `setTeleportDuration(0)` on mount.

```yaml
render:
  interpolation-ticks: 1
  mounted-interpolation-ticks: 1
```

The mounted duration defaults to one tick, and is constrained to 1-3 ticks so it cannot
accidentally be disabled or configured with excessive latency. Use 1 for the tightest
player/model alignment; 2 or 3 can be tested if an unusually high packet-latency
connection still visibly snaps. Parked vehicle interpolation is unchanged.
A live VoxelCore reload reapplies the correct mounted/parked interpolation.


## Vehicle terrain physics and suspension

Ground vehicles now check **actual Bukkit block collision shapes** on modern servers, rather than treating slabs and stairs as full cubes. The movement solver:
- Drives onto lower/upper slabs and stairs using their physical collision surfaces.
- Climbs at most **one full block per step**; a two-block-high wall always blocks the vehicle.
- Drops onto lower terrain using existing grounded movement and gravity logic.
- Never queries unloaded chunks (does not trigger chunk loads).
- Shares a short-lived block-shape cache between collision, stepping and four-wheel visual terrain sampling for each active driver.

Only the model pitches/rolls using the sampled elevation under its front/rear and left/right wheels. The collision box, steering direction, and rider's seat stay upright so the camera doesn't tilt with the model. Visual tilt uses independent 2-tick metadata interpolation by default; mounted teleport interpolation remains 1 tick.

Recommended tractor configuration (under `properties.vehicle`):

```yaml
physics:
  speed: 0.28
  reverse: 0.12
  acceleration: 0.015
  braking: 0.04
  drag: 0.90
  turn_rate: 3.0
  step_height: 1.0   # 1.0 = full blocks; 0.5 = slabs only

suspension:
  wheelbase: 2.4      # Distance between front/rear probe points (blocks)
  track_width: 1.4   # Distance between left/right probe points (blocks)
  max_pitch: 20.0    # Degrees to visually lift/dip the nose
  max_roll: 12.0     # Degrees to visually lean sideways
  smoothing: 0.24    # 0.01..1; larger = faster tilt response
```

If your tractor currently uses `step_height: 0.6`, increase it to `1.0` to climb full blocks. The physics parser rejects values over 1.0. Other vehicle types can keep their old settings. `render.tilt-interpolation-ticks` in `plugins/VoxelVehicles/config.yml` defaults to 2.

Modern collision shapes are available on Paper 1.19.4+; the older reflective fallback uses full solid cubes and therefore cannot represent precise stairs/slabs. Test against Paper 26.2 first. Visual slope animation is cosmetic, not a four-wheel rigid-body solver, and the current collision footprint remains axis-aligned even while the model tilts.

## Stable seat anchor and independent physical collision (PR #8 follow-up)

The **interaction hitbox is for mouse targeting only**. Its width/height/offset may overlap stone, and such overlaps must no longer reject vehicle placement. The terrain physics collision is a separate footprint anchored to the vehicle's logical ground position.

```yaml
properties:
  vehicle:
    type: LAND
    offset: {x: 0.0, y: 0.0, z: 0.0, rotation: 0}
    hitbox:
      width: 2.8
      height: 2.2
      offset: {x: 0.0, y: -1.0, z: 0.0}
    seat: {x: 0.0, y: -0.7, z: 0.0}
    collision:
      width: 1.0
      height: 1.5
      offset: {x: 0.0, y: 0.0, z: 0.0}
```

The new `collision` section is **optional**. Defaults are 1.0 block wide, 1.5 blocks tall, and no offset; tune it independently of your model/clickable bounds. A ground-level collision body may still legitimately prevent spawning *inside a solid obstacle*, but changing `hitbox.offset.y` or `vehicle.offset.y` no longer affects that decision.

To eliminate the jump on mount, the ItemDisplay's world root always equals the configured seat location. Its transformation matrix translates the visible model back towards the visual origin. When mounting, the player becomes a direct passenger of that existing root, with no origin switch or offset remeasurement. The model's world position should therefore remain exactly where it was before mounting. The passenger's vanilla riding height may need tuning with `seat.y`, particularly if a fallback ArmorStand is in use.

Test on Paper 26.2:
1. Place a vehicle with a clickbox partially inside stone: placement must succeed if its **physics body** has room.
2. Mount and dismount without movement: the tractor model must not jump or float.
3. Drive through a slab, stairs, a single step and toward a two-block wall.
4. Reload VoxelCore while the vehicle is parked, then mounted, and confirm offsets remain stable.


## Driving off cliffs: airborne gravity and nose/rear pitch

Land vehicles now use an accumulated **vertical velocity** rather than the old
fixed 0.12-block downward probe. After driving off a cliff, the tractor keeps
its horizontal momentum, accelerates downward under gravity, reaches a terminal
fall speed, and lands on the top of actual block collision shapes without
permanently hovering a fractional block above the ground.

The visual suspension switches smoothly to **airborne pitch**: driving forwards
off a ledge dips the nose; reversing off a ledge tilts the rear down. A
straight vertical drop has no artificially imposed forward/backward lean.
Landing returns to the existing four-wheel terrain tilt. The camera/seat,
physical collision body, resource-pack model and clickbox retain their
previous alignment; only the visual model matrix tilts.

Optional tuning keys under the existing `properties.vehicle`:

```yaml
physics:
  gravity: 0.08           # Downward acceleration in blocks/tick²
  terminal_velocity: 1.6 # Maximum fall speed in blocks/tick
  air_drag: 0.99          # Preserves horizontal momentum each airborne tick

suspension:
  airborne_pitch_factor: 0.8 # 0 disables in-air nose/rear tilt; 2 is strongest
```

These values are defaults; existing vehicle content files continue to load
without defining them. `suspension.max_pitch` also limits airborne pitch,
and `suspension.smoothing` controls how quickly it blends with terrain tilt.
Airborne land vehicles cannot steer or accelerate in mid-air: steering resumes
on contact with the ground. There is **no fall-damage or vehicle durability
system** in this version. The active driving tick still handles physics while
the vehicle has a driver; passive falling for unmanned vehicles is a separate
future improvement.

Validation: test forward and reverse departures, drops onto full blocks and
slabs, ledges bordering unloaded chunks, and landing immediately after driving
over a one-block obstacle on Paper 26.2.


## Configurable per-vehicle particle emitters (smoke, exhaust, effects)

Add `particles:` **directly under** `properties.vehicle`. It is a list, so
each vehicle can define several exhaust pipes, thrusters or decorative effects.
These are ordinary Minecraft particles; no new entities are spawned.

For the existing tractor, add this alongside `physics` and `suspension`:

```yaml
particles:
  - particle: SMOKE
    when: OCCUPIED
    interval_ticks: 4
    count: 2
    offset: {x: 0.0, y: 1.5, z: 1.2}
    spread: {x: 0.04, y: 0.08, z: 0.04}
    speed: 0.015
    follow_tilt: true
```

For a car with **two rear exhaust outlets**, use (adjust widths to fit the model):

```yaml
particles:
  - particle: SMOKE
    when: MOVING
    interval_ticks: 5
    count: 1
    offset: {x: -0.6, y: -0.35, z: -1.3}
  - particle: SMOKE
    when: MOVING
    interval_ticks: 5
    count: 1
    offset: {x: 0.6, y: -0.35, z: -1.3}
```

Offsets use **Minecraft blocks**, relative to the vehicle's **visual model
origin after its existing `vehicle.offset`**, before local yaw/tilt. At yaw
0, **+Z is forward**, **-Z is behind**, and +Y is up. These offsets are
already in world-block units, **not Blockbench pixels**, and are not
multiplied by `vehicle.scale`. Turn the vehicle to confirm each exhaust
pipe's position, then tune x/y/z to match the resource-pack model.

### Emitter keys

| Key | Default | Purpose |
|---|---|---|
| `particle` | Required | Vanilla particle name (e.g. `SMOKE`, `FLAME`, `CLOUD`, `CAMPFIRE_COSY_SMOKE` if supported) |
| `offset` | `{x: 0, y: 0, z: 0}` | Local world-block emission position, follows vehicle yaw and `vehicle.offset.rotation` |
| `when` | `OCCUPIED` | `ALWAYS`, `OCCUPIED`, `MOVING`, or `IDLE` |
| `min_speed` | `0.025` | Blocks/tick threshold separating moving from idling |
| `interval_ticks` | `4` | Emission period, 2–200 ticks (20 ticks = 1 second) |
| `count` | `1` | Particle count per burst, 1–16 |
| `spread` | `{x: 0.03, y: 0.04, z: 0.03}` | Random per-axis spawn spread in blocks |
| `speed` | `0.015` | Particle-specific extra speed |
| `follow_tilt` | `true` | Follow cosmetic tractor nose pitch/roll while driving/falling |

`OCCUPIED` emits while a driver is seated, including at idle, and stops
on dismount. `MOVING` emits only while driven faster than `min_speed`;
`IDLE` emits only below that threshold with a driver seated. `ALWAYS`
emits from parked loaded vehicles as well (e.g. enchanted machinery).
Particle aliases `SMOKE`/`SMOKE_NORMAL` map to whichever supported name
exists on the running server. Particles requiring block, item or dust data
are not supported yet and are rejected on content load with a clear error.

The main-thread manager tick drives particles after physics and visual
suspension updates. There is **no separate particle task per vehicle**;
only loaded, active instances emit, and the server does not load chunks
to display effects. Maximum eight emitters per definition, with bounded
density and intervals to prevent excessive particle traffic. Changes to
existing vehicles take effect on successful `/vc admin content reload`.
The emitter config is inherited normally by child items such as a
colored `tractor-yellow` variant.


## Vehicle inventory overlays and GUI model previews

Vehicle inventories use a **normal chest GUI**, three rows by default, allowing the resource
pack to draw the user's existing background glyphs in the inventory title:
`vehicle_inventory` (no storage) and `vehicle_inventory_storage` (storage).
These are **example aliases only**, configured in `plugins/VoxelVehicles/config.yml`
under `inventory.titles`; the Java plugin never hardcodes those alias names.
VoxelCore's live `TextPlaceholderService` resolves the title just before opening
the screen. All configured title offsets and glyphs are therefore hot-reloadable
with `/vc admin content reload` and adjustable without rebuilding the plugin.

The supplied mock-up uses 27 slots. The dark 3x3 preview window is left (columns 2–4),
the right-hand cargo slots are configurable, and the actual single preview
item is rendered at configurable slot 10 (one column to the left in the
updated artwork), locked from clicks and drags. All GUI positions
and title templates are editable in VoxelVehicles `config.yml`.

On each vehicle definition, configure `properties.vehicle.inventory`:

```yaml
# A vehicle with three rows of storage in the right-hand panel:
inventory:
  enabled: true
  storage_enabled: true
  rows: 3
  preview_item: voxel:tractor_icon

# For a preview-only menu use rows: 0 or omit inventory.rows.
# A vehicle can override the title for its menu type:
# inventory:
#   rows: 0
#   title: "&f:offset_-8::my_custom_vehicle_gui:"
```

The optional `preview_item` is a **separate VoxelCore item definition** whose
resource-pack model can supply an enlarged `display.gui` transformation. If
omitted, the menu previews `vehicle.model_item`. Use the existing tractor
model as a parent/resource-pack texture source, but give the GUI preview model
its own `display.gui` scale, rotation and translation so that the renderer can
bleed into the black area. **Minecraft still renders this as an inventory
ItemStack, not as a live entity**; it will not spin with the cursor unless
additional client functionality is added. The rendered item copies placed
vehicle dye color if both the item and its VoxelCore definition are dyeable.
The preview item is locked and never counted as cargo.

To open a vehicle inventory, **sneak + right-click** an unoccupied
vehicle **with any held item**; normal right-click mounts it. The pickup
fallback intentionally ignores **all crouching arm swings** so a right-click
animation cannot accidentally reclaim/drop the tractor; deliberate ordinary
left clicks still pick up an empty, unoccupied vehicle. VoxelVehicles also
processes sneaking entity interactions that other plugins pre-cancelled, with
a targeted block/air right-click fallback for custom Interaction hitboxes. While riding,
press **F (swap hands)** to open the vehicle menu without actually swapping
held items, or use `/vv inventory` at any time while riding or nearby.
The F shortcut can be disabled with `inventory.open-with-swap-hand: false`
in `plugins/VoxelVehicles/config.yml`. The shortcut only overrides F
for the driver of a vehicle with its GUI enabled; on foot and in other mounts,
F continues to swap hands normally. The inventory key (normally E) opens
the player's personal inventory entirely on the Minecraft Java client
when mounted on an ItemDisplay, so there is no server packet or
`InventoryOpenEvent` to intercept. Matching horse-style E behaviour
would require a different client-visible mount type or client mod, not just
Bukkit event handling. Permission `voxelvehicles.inventory` is granted
to players by default. Storage and
preview item can be configured independently per vehicle. The UI shows
the no-storage title if `inventory.rows: 0`, and the storage variant when
`inventory.rows` is 1–6. The visible cargo slots are determined directly
from the `storage-slots` list in VoxelVehicles config.yml. In the updated six-column default
layout each row adds 6 slots: 1 row = 6, 2 rows = 12, 3 rows = 18, up to
6 rows = 36. The first three rows now use slots 3–8, 12–17 and 21–26;
additional rows use slots 30–35, 39–44 and 48–53. Existing server
config.yml files are NOT automatically rewritten and may still use the old
five-column mapping; a per-vehicle `storage-slots` list overrides them.
GUI size defaults to a minimum of three rows to preserve the existing
preview window. A vehicle asking for four to six cargo rows opens a taller
GUI and needs a correspondingly taller resource-pack background glyph.
The `inventory.rows` in **VoxelVehicles config.yml** is the minimum GUI
height; the `inventory.rows` in **VoxelCore properties.vehicle** is the
number of available cargo rows when `storage_enabled` is true.

**Independent controls:** `inventory.enabled: false` disables the **entire**
vehicle menu, including the model preview. `storage_enabled: false` retains
the preview menu but switches to the **no-storage title** and locks all cargo
slots, even if `rows: 3` is still configured. Default `storage_enabled` is
true when `rows > 0`, preserving older content. Stored cargo is never
discarded if a vehicle is temporarily switched to preview-only; re-enable
storage to retrieve items. Nonempty cargo also continues to block vehicle pickup.

Vehicle storage is saved in `vehicles.yml` under the stable per-instance
UUID, using Bukkit ItemStack serialization. The current version supports a
single viewer per vehicle inventory. Shift-clicking between the player inventory and vehicle cargo now
works by moving items only into **authorized cargo slots**; cosmetic fillers
and the vehicle preview can never be used as storage. Manual cargo placement
and extraction continue to work. Double-click collection is also guarded. Pickup/removal of a
vehicle containing cargo is blocked so players cannot lose contents.
The menu does not currently migrate cargo into the picked-up vehicle item.

The new system introduces no client mod, fake horse, or server-rendered GUI
entities. Because the preview uses vanilla item rendering, your resource pack
must do the GUI scale/translation work.

### Invisible fill item and preview alignment

`plugins/VoxelVehicles/config.yml` supports a global `inventory.fill-item`
setting to display an invisible VoxelCore item in every locked/decorative
inventory slot. Set `fill-item: ""` to turn it off. An item ID such as
`voxel:ui-blank` or its uniquely identifiable model path such as
`voxel:ui/blank` is accepted; if a model path is ambiguous, specify
the actual registered item ID instead. Decorative filler stacks are
cloned, are never persisted as cargo, and cannot be taken by clicks,
hotbar swaps, double-click collection, drag, or shift-click. The setting
is optional and the actual model must be an authored and registered
VoxelCore item, not just an unregistered resource-pack JSON.

The preview item slot uses `inventory.preview-slot`; for the updated
left-shifted artwork set it to `10` (zero-based, second column, middle
row), instead of the previous `11`. This is not hardcoded Java logic.
Both `preview-slot` and `fill-item` may now be specified either in
`plugins/VoxelVehicles/config.yml` for all vehicles **or** in the
individual VoxelCore `properties.vehicle.inventory` to override the global
default. The per-vehicle keys intentionally use hyphens, matching the
existing plugin configuration and the user's YAML. Children inheriting
the parent's vehicle properties (e.g. `tractor-yellow`) can retain the
same custom overrides, so a color variant needs no duplication.

```yaml
# In farming.yml -> items.tractor.properties.vehicle.inventory
inventory:
  enabled: true
  storage_enabled: true
  rows: 3
  preview_item: voxel:tractor_icon
  preview-slot: 10
  fill-item: "voxel:ui/blank"
  storage_title: "&f:offset_-8::vehicle_inventory_storage::offset_-170:&rTractor"
  title: "&f:offset_-8::vehicle_inventory:&rTractor"
```

The per-vehicle `fill-item: ""` disables the filler even if the global
configuration supplies one. When a per-vehicle `preview-slot` overlaps a
cargo slot or falls outside the current GUI size, the inventory will
report an invalid layout instead of silently hiding or deleting cargo.
Shift-click from player inventory now only deposits to configured free
cargo slots, combining stacks where possible; shift-clicking from
cargo returns items to the player's inventory. When storage is disabled,
shift-click does not alter the preview or the fillers.

```yaml
inventory:
  rows: 3
  preview-slot: 10
  fill-item: "voxel:ui/blank"
  titles:
    without-storage: "&f:offset_-8::vehicle_inventory:"
    with-storage: "&f:offset_-8::vehicle_inventory_storage:"
```


### Vehicle-specific cargo layout and filler hover tooltip

An existing server config may still have only five cargo slots per row.
The new default is **six**, but an existing `config.yml` is not overwritten
when VoxelVehicles updates. In that case, the custom vehicle can explicitly
define the desired six-column mapping under `properties.vehicle.inventory`:

```yaml
inventory:
  enabled: true
  storage_enabled: true
  rows: 3
  preview-slot: 10
  preview_item: voxel:tractor_icon
  fill-item: "voxel:ui/blank"
  fill-copy-preview-meta: true
  storage-slots: [3, 4, 5, 6, 7, 8, 12, 13, 14, 15, 16, 17, 21, 22, 23, 24, 25, 26]
```

The per-vehicle `storage-slots` list takes priority over the global
`inventory.storage-slots` in `plugins/VoxelVehicles/config.yml`. Any
configured slot in the first `rows` rows becomes genuine storage and is
never populated with a filler. Thus 3, 12 **and 23** all become usable
cargo slots in this example. The currently active layout can be verified
by manually placing an item and confirming it survives closing/reopening.

`fill-copy-preview-meta: true` (global default) copies only the preview
ItemStack's **display name and lore** onto a cloned invisible filler item.
It does **not** copy the preview model, VoxelCore identifier, model
components or other render data. This makes hovering the black preview panel
feel like hovering the actual vehicle icon. Set the value to `false` globally
or per vehicle if you want filler tooltips to remain independent.

The default driving shortcut remains F (swap hands), or `/vv inventory`.
The E inventory key is client-handled for ItemDisplay mounts, so a Paper
listener cannot replace it with a server menu; a different client-visible
mount type or client mod is needed for native E integration.


## Optional installable chest storage (PR #11)

Only vehicles with `properties.vehicle.inventory.chest.enabled: true`
accept chest upgrades. Existing vehicle definitions without this property
preserve their normal always-available storage.

Add the following under the tractor's existing
`items.tractor.properties.vehicle.inventory` settings:

```yaml
inventory:
  enabled: true
  storage_enabled: true
  rows: 3
  preview-slot: 10
  preview_item: voxel:tractor_icon
  fill-item: "voxel:ui/blank"
  fill-copy-preview-meta: true
  title: "&f:offset_-8::vehicle_inventory:&rTractor"
  storage_title: "&f:offset_-8::vehicle_inventory_storage:&rTractor"
  storage-slots: [3, 4, 5, 6, 7, 8, 12, 13, 14, 15, 16, 17, 21, 22, 23, 24, 25, 26]

  chest:
    enabled: true
    item: CHEST               # Vanilla item required to install storage
    visual:
      mode: ITEM_DISPLAY      # NONE | ITEM_DISPLAY | MODEL_SWAP
      # item: voxel:tractor_chest  # Optional VoxelCore model for the chest
      offset: {x: 0.0, y: 1.0, z: -1.5}
      scale: 0.8
      yaw: 0.0
      follow_tilt: true
      interaction: true
      interaction_width: 0.8
      interaction_height: 0.8
```

### Vehicle interaction controls

| Interaction | Action |
| --- | --- |
| Normal right-click the tractor (even holding CHEST) | Mount, as usual |
| Shift + right-click while holding CHEST (only compatible vehicles) | Attach one chest; enable storage |
| Shift + right-click with any other item or an empty hand | Open the existing vehicle inventory |
| Shift + right-click with CHEST when chest is already installed | Open inventory; do not consume another chest |
| Click attached chest accessory with right-click | Open the vehicle inventory |
| Left-click attached chest accessory without crouching | Detach chest, only when completely empty |
| `/vv chest remove` (within six blocks or mounted) | Remove empty chest when no accessory exists or is difficult to target |
| F while mounted | Open vehicle inventory (default existing shortcut) |

**The vehicle GUI contains no chest installation button, reserved chest slot,
or equipment cell.** All non-cargo slots continue to use the existing
invisible filler. The command is intended as a removal fallback, not
required for normal use.

Chest installs are restricted to the vehicle owner and admins, including
for `public: true` vehicles. One item from the main-hand stack is consumed,
and its full original ItemStack data is saved with the vehicle. Removal
returns that same item to inventory, dropping it safely at the player if
inventory is full.

Without the upgrade, the ordinary no-storage background/title is used
and storage slots remain locked; after installing the chest the
`storage_title` becomes active and the configured slots unlock.
The upgrade persists through server restarts. Removal is denied if
any stored cargo remains (including hidden cargo), and the tractor itself
cannot be picked up until the chest is removed.

### Chest-specific vehicle inventory preview

An optional chest-specific VoxelCore icon can replace the normal inventory
preview when the storage upgrade is attached. Define the icon separately and
set `chest.preview_item` alongside the existing `inventory.preview_item`:

```yaml
inventory:
  enabled: true
  rows: 3
  preview-slot: 10
  preview_item: voxel:tractor_icon
  fill-item: "voxel:ui/blank"
  chest:
    enabled: true
    item: CHEST
    preview_item: voxel:tractor_chest_icon
    visual:
      mode: MODEL_SWAP
      model_item: voxel:tractor_chest
```

While no chest is installed, the menu continues to render
`inventory.preview_item` (or `vehicle.model_item` if that is omitted).
Once the chest is installed, the menu renders `chest.preview_item`
regardless of the chest's visual mode. Detaching the chest reverts the
preview on the next inventory open. The icon receives the vehicle's stored
dye color exactly as the original preview did. Its hover name, lore,
enchantments/glint, item flags and unbreakable presentation now come from
the **original placed vehicle ItemStack**, saved in `vehicles.yml` (PR #16),
not the generic `tractor_icon` or `tractor_chest_icon` definition.
The preview keeps its own render identity/model and structured custom model
data, and the invisible filler copies the resulting name/lore. For vehicles
saved before `original-item` existed, the plugin reconstructs the concrete
vehicle type (and stored dye) as it does for pickup, rather than using the
configured generic model/drop item. If the
optional `chest.preview_item` is omitted, existing menus behave exactly
as before. The chest visual's `model_item` and this GUI-only preview are
independent.

### Visual positioning and alternatives

Chest `visual.offset` is specified in blocks in the vehicle model's local
coordinate space; **+Z is forward and -Z is behind**. The sample coordinates
are a starting point only. The accessory can track vehicle yaw, movement
and optional suspension tilt. A separate click Interaction is optional;
it does not change the physical tractor collision body or rider position.

**Accessory alignment (PR #11 follow-up):** The chest and tractor ItemDisplays
now share the exact same world anchor at the tractor's permanent seat root
on every movement tick. Both interpolate over the same teleport interval,
including when a player mounts/dismounts. The chest gets its offsets, tilt,
local yaw and scale entirely from a transform matrix; it does **not** orbit
the tractor through separately rotated world-space teleports when steering.
The chest's independent Interaction hitbox still follows its calculated
world-space location, so players can click on the actual accessory. The
`visual.offset` settings do not need to change when upgrading the JAR;
reposition them only if the chest needs to sit elsewhere on your model.


Use `visual.mode: MODEL_SWAP` with
`visual.model_item: voxel:tractor_with_chest` to replace the tractor model
while installed, or `visual.mode: NONE` to enable chest storage without
a world-space chest display. A model-swap item must exist as a registered
VoxelCore content ID. `visual.item` in ITEM_DISPLAY mode likewise accepts
a registered VoxelCore item, and defaults to displaying the vanilla chest.
Set `visual.interaction: false` to render an accessory without its own
clickbox.

A legacy `chest.slot` setting from earlier development builds is ignored
but accepted, to avoid breaking existing configurations. It no longer
affects the inventory layout and can be removed.

The original ItemDisplay mount still cannot intercept the vanilla E key.
F and `/vv inventory` remain available.


## Large-server vehicle performance (PR #12)

The movement loop is designed to keep each driver's collision and display
state correct **every server tick**. For large concurrent rider populations it
now amortizes block collision shape decoding across all riders in the same world
for the duration of a **single** tick (no stale cross-tick geometry). Clearance
and grounded checks use an allocation-free contact query instead of building
temporary overlap lists. The chest and tractor still share their existing
render root, with position deduplication and no artificial frame skips.

Vehicles with no particles and unloaded entities are excluded from the
particle-emission hot list. Optional particle effect timing is unchanged.

Placed vehicles are also indexed by **world and origin chunk**. Loading or
unloading a chunk only visits vehicles in that chunk (instead of every
vehicle ever placed). Interaction ray targeting checks neighboring chunk
buckets, preserving the normal reach check. The index follows the vehicle
across chunk boundaries and is removed on pickup.

The periodic maintenance task repairs **missing** ItemDisplays and chest
accessories, but no longer needlessly refreshes every healthy parked or
mounted display on each maintenance interval. Explicit `/vv sync` and
VoxelCore content revision changes still reapply all authored models, tint
and rendering settings.

The periodic orphan-entity sweep now inspects a bounded number of loaded
chunks per server tick (default `maintenance.cleanup-chunks-per-tick: 16`,
configurable from 1 to 256). Large numbers of player-loaded chunks are no
longer scanned all at once during the maintenance callback. Partial sweeps
are carried forward instead of restarted. `/vv cleanup` still performs
the explicit full sweep when an admin requests it.

### Observability

An admin can run:

```
/vv perf
```

This reports rolling metrics from the **last 200 VoxelVehicles ticks**:

- mean, 95th-percentile and peak **VoxelVehicles task** time (ms; includes
  scheduled synchronous autosaves, collision, rendering and particle processing)
- count of active riders, loaded and total placed vehicles
- number of collision blocks decoded and reused per tick
- actual entity teleports and redundant teleports avoided per tick

The command does not log each tick and does not require external dependencies.
**This is not whole-server MSPT**; use spark for whole-server profiling.

### Benchmarking large groups

Use several concurrent clients/bots or trusted real players to drive 25,
50, 100, 200, and 300 vehicles while monitoring both `/vv perf` and
`/spark profiler`. Keep render distance, tick settings, terrain and the
number of other entities comparable across runs. Include steering, terrain
steps, suspension and chest-installed vehicles.

Check `/vv perf` while riders move and repeat after the population doubles.
Watch the **p95** and peak vehicle task duration: 50ms is the total budget
for every server subsystem combined, NOT the allowed vehicle-only workload.
Also examine network bandwidth and the client's entity render load; low
server MSPT cannot guarantee smooth client FPS for 300 display models.

Avoid synthetic game-spawn/load commands on production; use a test server.
Chunk generation/loading, main-thread `vehicles.yml` saving and Paper's
entity teleport implementation remain important workloads. This PR does
not bypass Bukkit entity safety, send raw movement packets, asynchronously
mutate worlds, or reduce collision and passenger correctness in order to
meet an unmeasured vehicle-count target.


## Reclaiming vehicles with chest storage (PR #13)

**Left-click the vehicle body** to pick it up, even if a chest upgrade is
installed and the vehicle's inventory contains items. The vehicle drops
its normal (including dyed) VoxelCore item **alongside the exact installed
chest item and every stack of stored cargo** at the vehicle's location.
Cargo in hidden, currently disabled or later GUI rows is also returned.
Nothing needs to be emptied or detached first.

- Owner/admin checks and existing right-click mount/shift-right-click
  inventory controls are unchanged.
- Left-clicking the **chest accessory itself** retains its existing
  separate-detach behavior (only when storage is empty). To pick up the
  whole vehicle and all cargo, left-click the main vehicle body.
- If a vehicle inventory is open, it is captured and closed before items
  are prepared. Invalid drop items, rejected world spawns or failed
  persistence cancel the pickup instead of silently discarding cargo.
- `/vv remove` remains an administrator-only removal command, not
  the player's vehicle reclaim action.


## Seamless attached chest movement

When a vehicle has an installed chest with `chest.visual.mode: ITEM_DISPLAY`,
the chest now rides the **same ItemDisplay movement root** as the tractor and
its driver. The client receives one movement/interpolation source rather than
two independently teleported displays. The chest model's local offset, scale,
optional yaw and suspension tilt are unchanged. Its separate Interaction
hitbox still moves independently so the chest remains clickable.

This is enabled automatically; existing tractor YAML requires no changes.
For troubleshooting or compatibility with servers that reject display
passengers, use the optional setting:

```yaml
inventory:
  chest:
    enabled: true
    visual:
      mode: ITEM_DISPLAY
      attach_to_vehicle: true  # default; false uses old independent teleports
```

The player retains mounting priority if a server restricts passenger count.
The plugin falls back to synchronized chest teleports when the extra passenger
cannot be attached, without changing player/vehicle seating. Dismount now
removes only the rider, never ejects the chest attachment.

Run `/vv perf` as an admin to see **Chest displays mounted: N / M**.
A mounted count equal to the number of active chest displays confirms that
the shared-client-movement path is in use. This does not automatically
guarantee smooth rendering for every client/protocol implementation; verify
in-game while steering, driving up slopes, dismounting and re-entering.


### Chest turning stability (PR #15)

When the chest is mounted as a passenger of the main tractor ItemDisplay,
the tractor is the *only* owner of world yaw and movement. Do not send
independent chest yaw packets or add another heading correction to its local
matrix: the latter rotates the attachment offset around the seat, visibly
making the chest orbit the cabin. The chest's matrix keeps only its authored
offset, tilt and scale.

Cosmetic tilt now uses the same interpolation duration as the main vehicle
model. All of the existing physics, chest hitbox, inventory and passenger
lifecycle behavior remains unchanged. Cached JOML method lookups avoid
reflecting the matrix operations on every suspension update.

For compatibility, `attach_to_vehicle: false` still selects independent
teleports if a particular server/client cannot render passenger displays.

Verify in-game by driving and steering both directions, including tight
turns and 0°/360° yaw crossings, then driving on sloped terrain. Test the
chest's physical mounting position and clicking the separate hitbox.

## Original vehicle item metadata on pickup

Vehicle placement now preserves a **single-item clone of the actual VoxelCore
item held by the player** in `vehicles.yml` (format 4). Picking the vehicle
back up returns the original custom item identity, inherited/derived name,
lore, custom model/components and any other Bukkit ItemMeta, rather than
reconstructing `vehicle.drop` (which might refer to a generic parent item).

This also survives server restarts and chunk unloading. If an existing
vehicle was placed before this change and has no saved `original-item`,
pickup rebuilds `vehicle.type` (the concrete vehicle ID, e.g.
`voxel:yellow_tractor`) and reapplies its separately saved color, rather
than returning the parent/model drop. The rebuilt item reflects the current
VoxelCore item definition; pre-upgrade metadata that was never saved
cannot be recovered exactly.

The chest and all storage stacks continue dropping separately, as in
PR #13. New source metadata is a defensive clone and is never changed by
the player's remaining stack, inventory, or chest modifications.
