v1.1.0

- Adds Thunderstore publishing support via `publishToThunderstore` and the new `thunderstore { ... }` DSL block.
- Auto-bundles the project's built jar into `mods/` when no content is configured, matching the Hytale Modding Thunderstore plugin guide.
- Includes content helpers for plugins, early plugins, asset packs, worlds, universes, and saves so each Hytale content type is one DSL call.