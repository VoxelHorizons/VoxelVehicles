# WorldGuard and Lands protection integration

Both hooks are optional and enabled by default in `config.yml` under `protection.worldguard` and `protection.lands`. If either plugin is installed and active, its permission check must succeed; one plugin's ALLOW does **not** override another's DENY.

## WorldGuard

Custom region flags are registered during `onLoad()`, before WorldGuard locks its registry. Keep `WorldGuard` in `softdepend`.

| Plugin | Place | Break / pickup | Interact / use | Storage | Modify |
|---|---|---|---|---|---|
| VoxelFurniture | `voxel-furniture-place` | `voxel-furniture-break` | `voxel-furniture-interact` | `voxel-furniture-inventory` | `voxel-furniture-dye` |
| VoxelVehicles | `voxel-vehicle-place` | `voxel-vehicle-break` | `voxel-vehicle-interact` | `voxel-vehicle-inventory` | `voxel-vehicle-modify` |

For an interactive but secure Hub (replace `voxel_hub` with the actual world if needed):

```text
/rg flag -w voxel_hub __global__ voxel-furniture-interact allow
/rg flag -w voxel_hub __global__ voxel-vehicle-interact allow
```

Do **not** blindly allow place, break, dye, modify, or inventory flags in the public Hub. If visitors should sit or trigger furniture animations, allow **interact** only. For public storage, grant `-inventory` explicitly and review the WorldGuard `chest-access` flag: an explicit chest-access DENY still blocks custom storage access.

The effective region query respects priority and player region groups. No code un-cancels the ordinary event to grant general block placement.

## Lands permissions

Lands uses its native role checks, including trusted roles and sub-areas, via the public API when installed.

| Custom action | Lands role flag |
|---|---|
| Furniture/vehicle placement | `BLOCK_PLACE` |
| Furniture/vehicle removal | `BLOCK_BREAK` |
| Furniture/vehicle storage access | `INTERACT_CONTAINER` (treated as `CHEST`) |
| Furniture animation, seating or commands | `INTERACT_GENERAL` |
| Vehicle riding/mounting | `VEHICLE_USE` |
| Vehicle chest installation | `BLOCK_PLACE` |
| Furniture recoloring or vehicle chest removal | `BLOCK_BREAK` |

A Lands-denied inventory cannot be opened just because WorldGuard grants `voxel-*-inventory`. Conversely, a Lands-trusted player must also satisfy the WorldGuard checks where WorldGuard protects the same coordinates.

VoxelVehicles also retains its existing per-vehicle owner UUID and `publicAccess` logic. A Lands member cannot override a private vehicle's personal owner access rule.

## Manual acceptance test matrix

1. Install **neither** plugin: existing furniture/vehicle behavior remains intact.
2. Install **WorldGuard only**: protect the Hub, DENY vanilla build/use, allow only both custom interact flags; seats/animations and vehicle mounting should work, while placement, pickup, recoloring and inventories stay denied.
3. Set each custom flag to ALLOW and DENY independently; verify region priorities, parent/child inheritance and region members.
4. Install **Lands only**: an untrusted player cannot place/break furniture, use a furniture chest, attach/detach a vehicle chest, or open vehicle storage on someone else's claimed area. Grant appropriate Lands role flags and verify the action works without granting other flags.
5. Install **both**: denial by either provider wins.
6. Multi-block furniture crossing a claim/region boundary must not be placed or dismantled through a neighboring permitted region.
7. Verify the VoxelFurnitureShop controlled-fixture visitor restrictions remain effective, and shop editor right-click block placement is still possible.
8. Confirm normal block and chest use is never newly permitted just because a custom furniture/vehicle flag is enabled.

These are integration tests to carry out on a test server; GitHub Actions compilation cannot simulate external WorldGuard/Lands runtime region behavior.
