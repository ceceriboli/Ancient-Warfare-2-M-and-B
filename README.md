# Ancient Warfare 2: M&B

A fork of [Ancient Warfare 2: Tweaked](https://github.com/blahthebiste/AncientWarfare2) for Minecraft 1.12.2 (Forge) that adds a Mount & Blade style layer of dynamic factions on top of the Ancient Warfare 2 NPCs and structures.

**Status: experimental.** It was developed and tested in single player in a dedicated test instance, with only Ancient Warfare 2 and CodeChickenLib loaded. It has not been tested on a dedicated server or in a large modpack yet. Back up your worlds before trying it.

## What it adds

* **Dynamic relations.** The factions of Ancient Warfare 2 are at war, at peace or allied, and this changes during the game: war exhaustion, truces, new wars, alliances against a common enemy. Operators can set relations with `/awrelation`.
* **Cities.** Every faction leader and structure with a leader spawner founds a city with a leader, a garrison and a manpower pool (killed units respawn at the cost of manpower). Cities have names, a boss bar with their manpower, and nested cities of the same faction are merged.
* **Raids and sieges.** Factions at war send waves of soldiers (cavalry, archers, siege engines with engineers) against cities, sized from the strength of the city and of the attacking faction. Attackers break through walls, climb with ladders and bridge gaps; defenders fight back. A city whose leader dies is conquered by the attacker, or falls.
* **Joining a faction.** Speak to the leader of a faction (a dialogue window) and pledge allegiance. The enemies of the faction become your enemies, its allies become friendly, its chests open to you when you are trusted.
* **Military service.** When your faction attacks or is attacked you are called to arms, with a muster time based on the distance. Serving earns standing and wages in Ancient Warfare coins, collected from the leader; ignoring the call costs standing, strikes and in the end expulsion. You can propose campaigns to your leader.
* **Your own kingdom.** Place a Town Hall far from other cities to found a kingdom: a faction of your own with a capital, a governor to talk to and a culture (the look, units and dialogues of an existing faction) that you choose. The governor handles your diplomacy (declare war, make peace, alliances) and orders campaigns, which cost manpower of your capital. Your own soldiers raise the capital's manpower, and the cities you conquer become part of your kingdom and pay tribute to the capital. A new kingdom is protected from attacks for its first days.

## Commands

`/awrelation`, `/awdiplomacy`, `/awfaction`, `/awmembership`, `/awsettlement`, `/awsiege`, `/awservice`, `/awkingdom`, `/awarmy` (status). Most of them are for operators, except the ones for players: `/awmembership`, `/awservice propose|wages` and `/awkingdom`.

## Configuration

Everything is in `config/ancientwarfare/AncientWarfareDiplomacy.cfg`, which is written on the first start with the default values and a description of each option (cities, sieges, diplomacy, military service, kingdoms, wages). All texts are in English only for now.


## Building

Java 8, then `gradlew build` (about 3 to 5 minutes). The jar is created in `build/libs`.

## Credits and license

All of the Ancient Warfare 2 code, art, structures and templates are the work of its authors (Shadowmage, GotoLink, P3pp3rF1y, blahthebiste and the other contributors of the upstream repository). The features above are new code written for this fork. Some ideas (the wave and horn behavior of raids, the siege behavior of raiders) were studied from the descriptions and sources of other mods, but no code was copied.

The mod is free software under the GNU General Public License version 3 or later, like the original (see `LICENSE.txt`), and the source of this fork is this repository.
## Ancient Warfare 2, upstream [![Discord](https://img.shields.io/discord/440863937777369088.svg?colorB=7289DA&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAHYAAABWAgMAAABnZYq0AAAACVBMVEUAAB38%2FPz%2F%2F%2F%2Bm8P%2F9AAAAAXRSTlMAQObYZgAAAAFiS0dEAIgFHUgAAAAJcEhZcwAACxMAAAsTAQCanBgAAAAHdElNRQfhBxwQJhxy2iqrAAABoElEQVRIx7WWzdGEIAyGgcMeKMESrMJ6rILZCiiBg4eYKr%2Fd1ZAfgXFm98sJfAyGNwno3G9sLucgYGpQ4OGVRxQTREMDZjF7ILSWjoiHo1n%2BE03Aw8p7CNY5IhkYd%2F%2F6MtO3f8BNhR1QWnarCH4tr6myl0cWgUVNcfMcXACP1hKrGMt8wcAyxide7Ymcgqale7hN6846uJCkQxw6GG7h2MH4Czz3cLqD1zHu0VOXMfZjHLoYvsdd0Q7ZvsOkafJ1P4QXxrWFd14wMc60h8JKCbyQvImzlFjyGoZTKzohwWR2UzSONHhYXBQOaKKsySsahwGGDnb%2FiYPJw22sCqzirSULYy1qtHhXGbtgrM0oagBV4XiTJok3GoLoDNH8ooTmBm7ZMsbpFzi2bgPGoXWXME6XT%2BRJ4GLddxJ4PpQy7tmfoU2HPN6cKg%2BledKHBKlF8oNSt5w5g5o8eXhu1IOlpl5kGerDxIVT%2BztzKepulD8utXqpChamkzzuo7xYGk%2FkpSYuviLXun5bzdRf0Krejzqyz7Z3p0I1v2d6HmA07dofmS48njAiuMgAAAAASUVORK5CYII%3D)](https://discord.gg/jNhkDfU) [![Build Status](https://github.com/P3pp3rF1y/AncientWarfare2/workflows/Mod%20Build/badge.svg)](https://github.com/P3pp3rF1y/AncientWarfare2/actions?workflow=Mod+Build)
This is a tweaked version of Ancient Warfare 2, with more focus on immersive, friendlier NPCs. Plus a bunch of bug fixes and config options.

For Minecraft for Minecraft versions 1.12.x+.

Modules initially available will be:

* Core (mandatory for all sub-modules)  

* Automation -- adds quarries, automated farms, tree-farms, etc.  

* NPCs -- adds workers, combat npcs. Complete faction-based NPC system for world-gen.  

* Structures -- the AW template system, packaged as a stand-alone. Has dynamically loaded plugins to handle interaction with other AW modules.  

* Vehicles -- Adds vehicles for transportation and warfare.

Latest and historical releases can be [found on CurseForge](https://minecraft.curseforge.com/projects/ancient-warfare-2/files).

Development unstable releases can be downloaded from Github packages.  
[Download mod builds](https://github.com/P3pp3rF1y/AncientWarfare2/packages/36069)

## Development

In order to develop on this project you will need to:  

1) Download the repository into a local folder.  

2) Locate this folder, run `gradlew setupDecompWorkspace` (or `./gradlew setupDecompWorkspace`). This sets up base minecraft and minecraftforge libraries and their dependencies.

3) Run `gradlew idea` or `gradlew eclipse` based on the IDE you use to setup project files.

At this point it should be completely setup to begin development.  

## License

Ancient Warfare 2.x - A mod for Minecraft
Copyright (C) 2013-2015  Shadowmage
  
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
  
This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.
  
You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
