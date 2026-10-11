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
the right-hand 15 cargo slots are configurable, and the actual single preview
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
from the `storage-slots` list in VoxelVehicles config.yml. In the example
layout each row adds 5 slots: 1 row = 5, 2 rows = 10, 3 rows = 15, up to
6 rows = 30. The first three rows (slots 4–8, 13–17 and 22–26) have not
been changed; additional rows use configurable slots 31–35, 40–44 and 49–53.
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
