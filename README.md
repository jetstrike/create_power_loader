<p align="center"><img src="https://raw.githubusercontent.com/hlysine/create_power_loader/main/src/main/resources/create_power_loader_icon.png" alt="Logo" width="200"></p>

<h1 align="center">Create: Power Loader</h1>

<p align="center">
    <a href="https://www.curseforge.com/minecraft/mc-mods/create-power-loader/files"><img src="https://cf.way2muchnoise.eu/versions/936020_all.svg"></a>
    <a href="https://modrinth.com/mod/create-power-loader/"><img src="https://img.shields.io/modrinth/dt/wPQ6GgFE?style=flat&label=Modrinth"></a>
    <a href="https://www.curseforge.com/minecraft/mc-mods/create-power-loader"><img src="https://img.shields.io/curseforge/dt/936020?style=flat&label=CurseForge"></a>
</p>

A Create mod add-on adding immersive andesite and brass chunk loaders to Minecraft.

[![BisectHosting](https://www.bisecthosting.com/partners/custom-banners/cd02548b-be01-4a01-b707-ffcb913f5299.webp)](https://bisecthosting.com/lysine)

> **Want more Create-esque QoL items? Check out [Create: Connected](https://modrinth.com/mod/create-connected)**

[![Available for Forge](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/forge_vector.svg)](https://modrinth.com/mod/create-power-loader/) [![Available for Fabric](https://cdn.jsdelivr.net/npm/@intergrav/devins-badges@3/assets/cozy/supported/fabric_vector.svg)](https://modrinth.com/mod/create-power-loader-fabric)

## Create 6 and Minecraft 1.21

**Minecraft 1.20.1: ✅ Available**

**Minecraft 1.21.1: ✅ Available**

## What's new in 2.0.0

**Support for Create 6.0.0**

Support for Create 0.5.1 and Minecraft 1.18/1.19 has been dropped. Please use Create: Power Loader v1 if you are using Create 0.5.1.

## Advanced Player Ownership & Server Administration

Create: Power Loader features a complete **Player Ownership and Multiplayer Protection System** designed to keep server lag in check without sacrificing player convenience:

### Player Ownership & Co-Owners
- **Automatic Ownership Claiming**: Chunk loaders automatically bind to the placing player. Unclaimed loaders do not force chunks, preventing server abuse.
- **Co-Owner Management UI**: Open the dedicated in-game interface to view ownership status and add trusted friends as co-owners. As long as any owner or co-owner is online (or active within the threshold), machines continue loading seamlessly.
- **Automatic Ownership Transfer**: If a primary owner stops playing, ownership automatically transfers to an active co-owner after 20 days of absence.

### Lag Prevention & Inactivity Suppression
- **72-Hour Inactivity Timeout**: If all owners and co-owners of a chunk loader remain offline for 72 hours, their loaders automatically suspend operation to preserve server resources and alert operators.
- **Load-Scaled Distributed Cooldown**: Configurable dynamic timeout scaling (`enableDistributedCooldown`). Players running expansive farming networks with excessive loaded chunks (e.g., four 5x5 loaders = 100 chunks) receive proportional reductions to their active offline cooldowns (e.g., cut down from 72 hours to 18 hours), incentivizing optimized automated footprints!
- **Co-Owner Cooldown Multipliers**: When relying on an active co-owner after the primary owner logs out, active windows are scaled by a configurable multiplier (`coOwnerActivityMultiplier`, default 50%).
- **Tick-Loading Quotas**: Administrators can cap how many machines a single player can run with random-tick farm growth enabled simultaneously (`maxTickLoadingLoadersPerPlayer`, default 1).
- **Permanent Bypass Exemption**: Operators can permanently exempt public infrastructure (such as spawn rail networks or community farms) using `/powerloader bypass <player>`.

### Interactive Diagnostic & Management Commands
Inspect, diagnose, and manage chunk loaders directly in Minecraft chat with real-time timers and clickable action buttons:
- **`/powerloader status`** (Available to all players): Instantly inspect your personal automation dashboard showing total loaded chunk capacity, tick-loading machine usage (`1 / 1 max`), and real-time countdown timers for both primary and co-owner logout cooldowns!
- **`/powerloader status <player>`**: Allow server administrators to inspect any online player's timers and quotas on demand.
- **`/powerloader owners`** (or `/powerloader active`): Displays a clean dashboard of every player currently keeping chunks loaded, sorted by chunk count, complete with online status, remaining active countdowns, and an inline clickable **`[Unload]`** button!
- **`/powerloader unload`**: Automatically pinpoints the player who has been offline the longest while running automated loaders, offering a simple **`[Click to Confirm Unloading]`** chat button to instantly free up server capacity.
- **`/powerloader unload <player>`** & **`/powerloader resume <player>`**: Manually suspend or reactivate any individual player's machines on command. Forcefully unloaded machines automatically restore themselves the moment the owner logs back into the game!

## Features

> Check out the [Wiki](https://github.com/hlysine/create_power_loader/wiki) for more info!

- 2 tiers of chunk loaders
    - Brass chunk loader: configurable loading range (1x1 to 5x5)
    - Andesite chunk loader: loads a single chunk
- Advanced multiplayer lag protection with player ownership, co-owners, and inactivity timers
- Interactive operator diagnostic chat tools for monitoring and shedding server load on demand
- Works on the ground, on trains and on contraptions
    - Configurable via server configs
    - Toggleable in-game via contraption controls
- Attaches to Train Stations for lag-friendly chunk loading
- Reliable chunk loading and unloading
- Lots of configs for customization
- Complete ponder scenes

![Train Attachment Ponder](https://cdn.modrinth.com/data/wPQ6GgFE/images/0cdf2fecd6253f267cf32103e51a062b78ffaace.png)

![powerloader list command](https://github.com/hlysine/create_power_loader/assets/25472513/e28c9b7c-fa27-4ac1-aaf5-2500771439bd)

*If the provided crafting recipes do not suit your needs, you can override the provided recipes by creating your own
datapack.*

## Download

Find this mod on [**Modrinth**](https://modrinth.com/mod/create-power-loader) or
[**CurseForge**](https://legacy.curseforge.com/minecraft/mc-mods/create-power-loader).

## Usage

**In modpacks:**

- You can include this mod in any modpacks.
- You can make any modifications to the mod with the goal of distributing it in a modpack.

**In other cases:**

- You can use this mod however you like as long as you obtain the mod via its Modrinth or CurseForge page.
- You can make any modifications to the mod, but you cannot redistribute it unless you have modified a substantial
  portion of the mod's code. Changes to resource packs/data packs/mod metadata do not count as code modification.

This mod is open to suggestions, so if you have made any modification to the mod, please leave an issue/PR so I can
consider adding your use case to the mod.

## Support

The best way to support my work is to simply download this mod on [**Modrinth**](https://modrinth.com/mod/create-power-loader).
Enjoy a smoother download experience and support open source software with a single click.

If you would like to offer more direct support, you can [![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/O4O2TL8YV)

## Credits

**Source code**

- [AyOhEe](https://github.com/AyOhEe) - Neoforge 1.21.1 port

**Translation**

- [Huantanhua](https://github.com/Huantanhua) for Simplified Chinese translations
- [Abbage230](https://github.com/Abbage230) for Japanese translations

**Inspiration**

The [Create mod](https://github.com/Creators-of-Create/Create) and
the [Create Chunkloading mod](https://github.com/embeddedt/CreateChunkloading)
