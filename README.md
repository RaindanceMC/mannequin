# RaindanceMC/mannequin

- **`mannequin-module`** — Rain's Flexible Modules — Mannequin: A training dummy an owner spawns, dresses and never has to worry about losing for good: real health that tracks every hit and combo dealt to it, blocking with a shield when it holds one, drinking whatever potion lands at its feet, and respawning identically — same block, same loadout, same skin — a moment after it is actually killed.
- **`mannequin-standalone`** — Rain's Mannequins: Rain's Mannequins, as a plugin of its own. Nothing here but a build file and a descriptor: the mannequin module and the standard wrapper are shaded together, and that is the whole plugin.

Part of [RaindanceMC](https://github.com/RaindanceMC); built on [RainsCore](https://github.com/RaindanceMC/RainsCore) and the shared parent in [framework](https://github.com/RaindanceMC/framework). Split out of `rains-flexible-modules` on 16.10.2026, history kept.

## Build

```
mvn install
```

Needs Java 25 and read access to `packages.tstieh.de` (server ids `packages-releases` / `packages-snapshots` in `~/.m2/settings.xml`).

## Plugins

- `mannequin-standalone/target/RainsMannequins-1.14.0.jar` — also published as `de.raindancer.modules:mannequin-standalone:1.14.0`

## Versions

A module's version is a property in this repository's root `pom.xml`, a plugin's is `plugin.version` in its own pom. CI publishes every version that is not in the registry yet on the next push to master.
