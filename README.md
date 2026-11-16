# RaskolHUD

Client-side HUD mod for the RASKOL Minecraft RPG server.

## Repository roles

- This repository is the sole development home for the RaskolHUD client mod, shared HUD protocol, and optional Paper bridge.
- [RaskolClasses](https://github.com/hayferdahmer/RaskolClasses) is the authoritative source for current server-side classes, specializations, progression, HP, resources, and gameplay rules. It is treated as an upstream integration dependency; RaskolHUD changes do not belong in that repository.

## Initial target

- Minecraft 26.2
- Java 25
- Fabric Loader 0.19.3
- Fabric API 0.161.0+26.2
- Paper 26.2 for the optional server-side bridge

The HUD is display-only. The server remains authoritative for all gameplay calculations and mutations.

Development is in progress. No release artifact is published yet.
