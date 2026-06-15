v1.1.8

- Added `replaceExisting` option to `ModtaleConfig` — when set to `true`, uploading the same `versionNumber` for overlapping `gameVersions` replaces the matching existing version instead of failing. Non-overlapping game version targets on the old entry are left unchanged.