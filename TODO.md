# TODO

Running backlog/changelog for NetBox and Chill. One `## NBC-N:` entry per feature or fix,
numbered sequentially (never reuse or renumber an id). See `AGENTS.md` for the full convention.

## NBC-1: Initial project scaffold + MVP

Offline-first NetBox companion app: token login, device list with a Room cache, QR/barcode
scanning of the device-sticker URLs (`https://<netbox>/dcim/devices/<id>/`), Material 3 UI,
Obtainium distribution.

- [x] Public GitHub repo (pschmitt/netbox-and-chill), GPL-3.0
- [x] flake.nix (JDK 21, Android SDK, just, ktfmt, git-hooks pre-commit)
- [x] justfile (remote build on rofl-13/rofl-14, install to Zenfone 10 / Mi Pad 4, logcat, format/lint)
- [ ] Gradle project skeleton (single `:app` module, AGP/Kotlin/KSP/Hilt wiring, version catalog)
- [ ] AndroidManifest, Material 3 theme + splash screen, adaptive launcher icon, deep-link intent-filter
- [ ] NetBox API client (Retrofit + kotlinx.serialization, dynamic base URL, token auth) + Room offline cache
- [ ] CameraX + ZXing barcode/QR scanner, device-URL parser
- [ ] WorkManager periodic background sync
- [ ] Compose screens: onboarding, device list, device detail, scanner, settings
- [ ] CI (build/lint/release workflows), CI signing keystore, Obtainium README badge, fastlane metadata, PRIVACY.md
- [ ] Build + smoke test on Zenfone 10 and Mi Pad 4, push to GitHub

Status: in progress, 2026-07-31.
