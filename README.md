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

For a tractor, set `offset.y: -0.25` to lower the *whole* model/collision/seat assembly; adjust `seat.y` separately to move just the driver. `rotation` is degrees around the Y axis in addition to the driving direction. The visual and hitbox follow the same yaw and offsets; the vehicle's physics direction does not change.

While occupied, the invisible driver seat rides the visible ItemDisplay, and the player rides the seat. The display is the only movement root, avoiding independently teleporting the model and rider. If a server rejects that link, VoxelVehicles logs a warning and uses the older fallback. This needs real-client validation, especially when using unusual model transforms.

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
