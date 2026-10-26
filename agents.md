# Project Guidelines

## Mod Changes and Release Documentation

- For every change to mod code, assets, or behavior, update `README.md` and `CURSEFORGE.md` in the same change so their feature descriptions and release notes remain accurate.
- Bump `mod_version` in `gradle.properties` for each mod change, following Semantic Versioning. Keep version references in the README, CurseForge description, changelog, and packaged artifact instructions consistent with that value.
- Maintain `CHANGELOG.md`: add an entry for every mod change under the new version, grouped under the appropriate Keep a Changelog headings (`Added`, `Changed`, `Fixed`, or `Removed`). Do not replace or discard previous release entries.
- Before finishing, check these files together for stale descriptions, mismatched versions, and missing changelog entries.