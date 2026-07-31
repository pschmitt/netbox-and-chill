# AGENTS.md

Repository instructions for AI coding agents working on NetBox and Chill.

## Task tracking

- `TODO.md` is the running backlog/changelog for this project, one `## NBC-N:` entry per
  feature or fix, numbered sequentially (never reuse or renumber an id). Each entry has a
  checklist of sub-items (`- [ ]`/`- [x]`) and ends with a `Status:` line (`not started` /
  `in progress` / `mostly done` / `**done**`, plus a date and how it was verified).
- Before starting any non-trivial new feature or fix, add (or update) an `NBC-N` entry
  describing it - even if the same conversation immediately goes on to implement it. Update the
  checklist/status as work actually lands, rather than writing the whole entry retroactively once
  everything's finished. This keeps `TODO.md` an accurate record of what's done vs. still open,
  and lets another agent (or a future you) resume the work cold from just this file.
- Trivial one-off asks (a typo, a single-line tweak) don't need their own entry.

## Dev environment

- `nix develop` provides the full toolchain (JDK 21, Android SDK, `just`, `ktfmt`) and installs
  the repo's pre-commit hooks (see `flake.nix`'s `git-hooks.nix` integration - trailing
  whitespace, EOF fixer, merge-conflict/large-file checks, `nixfmt`, `statix`). The generated
  `.pre-commit-config.yaml` is gitignored - it's regenerated from `flake.nix` on every shell
  entry, don't hand-edit it.
- Prefer the `justfile` recipes over raw `./gradlew`/`ssh`/`adb` invocations - run `just --list`
  for the full set.

## Builds

- **Never run Gradle builds locally on this machine** - always build on `rofl-13.brkn.lol` or
  `rofl-14.brkn.lol` instead. The `justfile` automates this:
  - `just sync [host]` - rsync the working tree to the remote build host (excludes `.git`,
    `build/`, `.gradle/`). Namespaced per git worktree so parallel agents don't clobber each
    other's remote sync directory mid-build.
  - `just gradle [host] <tasks...>` - sync, then run arbitrary Gradle tasks remotely.
  - `just build [variant] [host]` - build an APK remotely. `variant` is `debug` (default) or
    `release`. Release builds are signed with the persistent CI keystore, fetched from the rbw
    entry `"NetBox and Chill CI Signing Keystore"`.
  - `just lint` - remote `ktfmtCheck` (mirrors `.github/workflows/lint.yaml`).
  - `just test` - remote unit test suite.
  - `just fetch [variant] [host] [abi]` - scp the built APK split back to `./dist/`.
  - `just build-fetch [variant] [host]` - build + fetch in one step.
  - `just format` runs the standalone `ktfmt` CLI locally over tracked `.kt`/`.kts` files - fast,
    but treat it as advisory only (see the flake.nix comment on why there's no ktfmt pre-commit
    hook); `just lint` is the authoritative check.

## Physical test devices

- **Zenfone 10** (`arm64-v8a`), connected directly over USB to this machine's adb. Recipes:
  `just zenfone-install <apk>`, `just zenfone-uninstall [pkg]`, `just zenfone-logcat [filter]`,
  `just deploy-zenfone [variant]` (build + fetch + install in one step).
- **Mi Pad 4** (`arm64-v8a`, rooted), reachable via SSH at `mi-pad-4.lan` port `8022` (Termux).
  Recipes mirror the Zenfone ones but go through `just mipad-connect` first (finds the port
  `adbd` is listening on via a root SSH shell, `adb connect`s to it): `just mipad-install <apk>`,
  `just mipad-uninstall [pkg]`, `just mipad-logcat [filter]`, `just deploy-mipad [variant]`.
- Signature mismatch gotcha: if a device already has a build signed with a different key than the
  one you're installing, install fails with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. Fix is
  `just zenfone-uninstall`/`just mipad-uninstall` then install fresh - this wipes local app data
  (Room DB cache, stored token). Confirm with the user before doing this if it's not their own
  throwaway data.

## Architecture

- Single `:app` Gradle module (Kotlin, Jetpack Compose + Material 3, Hilt DI) - no
  multi-module split, this app doesn't need one.
- NetBox API access via Retrofit + kotlinx.serialization, with a dynamic base-URL interceptor so
  the user's configured NetBox instance can change at runtime without rebuilding the Retrofit
  client (see `data/api/DynamicBaseUrlInterceptor.kt`).
- Offline cache via Room (`data/db`). `DeviceRepository` is cache-first: reads come from Room,
  writes/refreshes come from the API and upsert into Room.
- The whole point of the app is scanning the device-sticker QR codes (public NetBox device URLs
  like `https://<netbox-host>/dcim/devices/<id>/`) with the in-app CameraX/ZXing scanner, and via
  the `/dcim/devices/*` deep-link intent-filter when such a link is opened from another app. Both
  paths funnel through `scanner/DeviceUrlParser.kt`.
