# VoxelVehicles

Modern VoxelCore-powered vehicle addon, replacing HavenVehicles.

**Design goal:** one visual item-display entity per vehicle, a configurable interaction hitbox, and seat entities only when occupied. Vehicle metadata lives in VoxelCore item definitions under `properties.vehicle`. The runtime uses in-memory registries and a single shared movement tick.

This branch is an initial modernization milestone. Physical movement, persistence, version compatibility and test coverage must be completed before deployment.
