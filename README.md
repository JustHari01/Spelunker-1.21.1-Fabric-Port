# Spelunker — Unofficial Fabric 1.21.1 Port

An unofficial Minecraft 1.21.1 Fabric port of Leximon's Spelunker mod. It adds a brewable potion that outlines configured ores nearby.

## Features

- Spelunker and extended Spelunker potions, brewed with amethyst dust and Night Vision potions; Redstone extends the effect.
- Nearby configured ores are outlined while the effect is active. The default scan radius is 16 blocks.
- Per-block outline color, radius, and transition settings in `config/spelunker.hjson`.
- Server-side scanning and synchronization by default, intended to avoid client-side x-ray exposure on multiplayer servers.
- Configurable abandoned mineshaft loot and amethyst-dust drops.

## Compatibility

- Minecraft 1.21.1
- Fabric Loader 0.16.14 or newer
- Fabric API
- Java 21 or newer

The 1.21.1 port keeps the original mod id and item/effect identifiers. Use this build in place of another Spelunker build; do not install both together.

## Source and issues

The port source and issue tracker are hosted at [JustHari01/Spelunker-1.21.1-Fabric-Port](https://github.com/JustHari01/Spelunker-1.21.1-Fabric-Port).

## Upstream and credits

Based on [Spelunker by Leximon](https://github.com/Leximon/Spelunker/tree/1.20), originally created for Minecraft 1.20.1. Original authors: Leximon and RasmusTV. Original contributors: Loki0500 (textures), Smato (French translation), and Rad233 (Chinese translation). The upstream source and its MIT license are retained in this project.

## License and third-party notices

The project uses the upstream MIT license in `LICENSE`. Hjson Java 3.1.0 is bundled for Hjson configuration support under the MIT license; its license text is included in `LICENSE-HJSON` and attribution is recorded in `NOTICE.md`.

The original 18×18 in-game icon remains unchanged in the mod JAR. `release-assets/spelunker-curseforge-logo.png` is a separate AI-generated CurseForge project avatar, not an in-game asset or screenshot; it is not intended for a Modrinth project page.

## AI disclosure

AI tools assisted with the 1.21.1 compatibility port, API migration, configuration adaptation, the CurseForge project avatar, and release documentation.
