# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## Unreleased

### Added

- Added robust Player Ownership & Inactivity Suppression system to prevent unattended multiplayer lag
- Added custom GUI screen for managing Primary Owners and adding trusted Co-Owners with real-time status display
- Automatic 72-hour inactivity suppression with server operator alerts and automatic ownership transfer after 20 days of absence
- Added configurable load-scaled **Distributed Cooldowns** (`enableDistributedCooldown` & `distributedCooldownChunkDivisor`), cutting inactivity timeouts proportionally for heavy multi-machine farms while capping at the default maximum
- Added configurable `coOwnerActivityMultiplier` (default 50%) to adjust active durations when relying on co-owner activity
- Added configurable per-player quota limit for tick-loading machines (`maxTickLoadingLoadersPerPlayer`, default 1) with automatic enforcement on GUI toggles and schematic placement
- Added `/powerloader status` player command to view personal configured capacity, tick loader limits, and remaining cooldown windows in real time
- Added `/powerloader status <player>` operator command for instant diagnostic inspection of any online player
- Added `/powerloader bypass <player>` operator command to grant permanent exemption for essential spawn or community builds
- Added `/powerloader owners` (and `/powerloader active`) command to summarize all active chunk loading players in chat with real-time countdown timers and clickable `[Unload]` buttons
- Added `/powerloader unload` interactive operator command to automatically find and confirm shedding chunk loaders from the most stale offline player
- Added `/powerloader unload <player>` and `/powerloader resume <player>` for manual real-time admin override (resumes automatically upon player login)
- Added per-loader "Tick Loading" toggle (sneak + right-click the chunk loader to toggle)
- When enabled, crops, saplings, and other random-tick blocks will grow in loaded chunks even without a nearby player
- Added `enableRandomTicks` server config option to globally allow or disallow this feature
- Shows actionbar feedback (green ON / red OFF) when toggling

## 2.0.3 - 2025-05-20

### Added

- Turkish translation (thanks @RuyaSavascisi)

### Fixed

- Crash when pondering fluid tanks due to buggy ponder plugin

## 2.0.2 - 2025-05-19

### Fixed

- Rendering glitch of the chunk loading core when assembled on a contraption

## 2.0.1 - 2025-05-19

### Fixed

- Incorrect stress impact registration affecting other kinetic blocks

## 2.0.0 - 2025-05-10 [1.21.1 only]

**Special thanks to @AyOhEe for the Neoforge 1.21.1 port!**

### Changed

- Updated to support Create 6.0.0 on Minecraft 1.21.1

## 2.0.0 - 2025-03-02 [1.20.1 only]

### Changed

- Updated to support Create 6.0.0
- Support for Create 0.5.1 or below has been dropped

## 1.5.0 - 2024-05-21

### Changed

- Target Forge version for 1.20.1 is now 47.2.0
- Target Create version for 1.20.1 is now 0.5.1.f-26
- Improved translations for Simplified Chinese (thanks @PopSlime)

### Fixed

- Incompatibility with other mods causing clients to be kicked from server when a train station is placed
    - This fix introduces breaking changes in the networking code of Power Loader. Update is required on both the client
      and server.

## 1.4.0 - 2024-01-27

### Added

- Translations for Japanese (thanks @Abbage230)

### Changed

- **Config overhaul**
    - All configs are now separate for andesite and brass chunk loaders
    - Allows the static mode to be disabled
    - Allows the contraption and train modes to be configured separately
    - Allows loading range to be configured separately for contraption, train and station modes

## 1.3.3 - 2024-01-12

### Added

- `/powerloader list` command to list all active chunk loaders
- `/powerloader summary` command to count all chunk loaders

## 1.3.2 - 2024-01-06

### Fixed

- Loaded chunks not updating if a train station is removed while an attached chunk loader is active
- Loaded chunks not updating if a chunk loader is placed before a train station

## 1.3.1 - 2024-01-05

### Fixed

- Crash on dedicated server due to client class loading (#7)

## 1.3.0 - 2024-01-04

### Added

- Chunk loaders attaching to Train Stations to only load chunks when a train arrives at the station

### Changed

- Chunk loaders on trains now function using the track graph, which increases reliability for interdimensional
  trains

### Fixed

- Loading incorrect chunks across dimensions
- Chunks not being loaded when a train is coming out of a nether portal and the portal is not already loaded
- Some internal data not being persisted across restart

## 1.2.4 - 2023-12-24

### Added

- Translations for Simplified Chinese (thanks @Huantanhua)

## 1.2.3 - 2023-12-14

### Added

- Advancement for recipe unlock

## 1.2.2 - 2023-11-30

### Added

- Compatibility with contraption controls

## 1.2.1 - 2023-11-30

### Fixed

- Crash if JEI is not installed (#2)

## 1.2.0 - 2023-11-15

### Added

- Empty chunk loaders which can capture ghasts to become functional chunk loaders
- Ponder scenes for ghast capturing
- Configs to control whether chunk loaders work on contraptions

### Changed

- Chunk loader core texture to include a ghast

## 1.1.1 - 2023-11-14

### Fixed

- Mod incompatibility due to partial models being loaded too late (#1)

## 1.1.0 - 2023-11-12

### Added

- Comparator output for chunk loaders
- Config for chunk update interval
- Config for delay before chunk unload
- Ponder scenes for redstone and unloading delay

### Fixed

- Block entity lighting for the chunk loader cores
- Missing datagen

## 1.0.1 - 2023-11-11

### Fixed

- I18n for ponder scenes

## 1.0.0 - 2023-11-11

### Added

- **Andesite chunk loader**
- **Brass chunk loader**
- Ponder scenes
- Crafting recipes
- Speed multiplier config