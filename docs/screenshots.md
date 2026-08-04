# Play Store screenshot automation (POC)

Captures Play Store listing screenshots with [fastlane screengrab][screengrab], driven by the
`StoreScreenshotTest` instrumented test
(`app/src/androidTest/kotlin/dev/pschmitt/nyetbox/StoreScreenshotTest.kt`). Scope is intentionally
narrow for now: **en-US only**, dashboard + device detail + search + settings.

Fastlane regenerates `fastlane/README.md` itself on every run, so this doc lives outside
`fastlane/` to avoid being overwritten.

[screengrab]: https://docs.fastlane.tools/actions/screengrab/

## Why an emulator, and why a disposable NetBox

Nyetbox only has real data to show once it's connected to a NetBox instance, and this project's
physical test devices (Zenfone 10, Mi Pad 4, Pixel 5) are the user's own daily-driver hardware
connected to their real NetBox instance - store screenshots must never show that inventory data.
Rather than inventing a NetBox mocking layer, `just screenshots` reuses the disposable
docker-compose NetBox fixture already built for `.github/workflows/android-e2e.yaml` (see
`ci/netbox/docker-compose.yml`): same pinned images, same throwaway CI-only credentials. It's
seeded with its own demo data (`ci/netbox/seed_screenshots.py`, not the E2E workflow's
`seed.py` - see "Demo data" below) and created fresh and torn down
(`docker compose down --volumes`) at the end of every `just screenshots` run, success or
failure, so nothing persists between runs and nothing real is ever at risk.

Screenshots run against a local Android emulator rather than a physical device for the same
reason `android-e2e.yaml` uses one: a scripted, disposable target that starts from a known-clean
state every time, rather than juggling app-data wipes on a device you also use for other testing.
This machine has `/dev/kvm`, so the emulator boots in well under a minute (in the ~35-40s range),
much faster than CI's software-rendered fallback.

## How it fits the existing build split

Per `AGENTS.md`, Gradle/Android SDK work stays on the remote build hosts. `just screenshots`
respects that split - only `adb`/`emulator`/`fastlane` (no Gradle) run locally:

1. Starts the disposable NetBox fixture (`just netbox-up`) and seeds it (`just netbox-seed`).
2. Creates the screenshot AVD once if needed (`just screenshots-avd-create`) and boots it
   (`just screenshots-emulator-start`), API 34 google_apis x86_64 - the same profile
   `android-e2e.yaml` uses.
3. `just screenshots-build` builds `app-x86_64-debug.apk` and `app-debug-androidTest.apk`
   remotely (same as `just build`) and fetches both into `./dist/`.
4. Installs the app, clears its data, and re-grants `POST_NOTIFICATIONS` (MainActivity requests it
   at startup on API 33+; an unhandled permission dialog would interrupt the Compose test
   mid-journey - same reason `android-e2e.yaml` grants it before the first launch).
5. `fastlane screengrab` (via `nix develop .#screenshots`) drives `StoreScreenshotTest` over adb
   and pulls the results into `fastlane/metadata/android/`.
6. Always tears the NetBox fixture back down (`just netbox-down`, via a shell `trap`), even on
   failure.

Run the whole thing with:

```console
just screenshots
```

Output lands in `fastlane/metadata/android/en-US/images/phoneScreenshots/`.

## Running it more than once against the same emulator

`just screenshots` clears the app's data before every run (`adb shell pm clear`), so re-running it
against an emulator that's still up from a previous capture starts from onboarding again rather
than failing because the app is already connected. Keeping the emulator running between runs (it's
only started if nothing is already listed under `emulator-*` in `adb devices`) is what makes
iterating on `StoreScreenshotTest` fast - only `screenshots-build` and the fastlane step need to
re-run, not the ~40s emulator boot.

## Multiple devices attached

If other Android devices/emulators are also attached (the physical Zenfone/Mi Pad/Pixel 5, or a
stray leftover emulator), `just screenshots` still targets the right one: it discovers the
`emulator-*` serial itself and passes it to every `adb` call and to fastlane via
`SCREENGRAB_SPECIFIC_DEVICE`.

## Demo data

`ci/netbox/seed_screenshots.py` seeds a small realistic-looking (but obviously fake) rack rather
than reusing `android-e2e.yaml`'s `ci/netbox/seed.py` - that script's exact-match assertions
(`CI E2E Device`, `CI E2E Manufacturer`, ...) exist for deterministic E2E test assertions, not to
look good in a store listing. `seed_screenshots.py` creates one manufacturer (Acme Networks), one
site (Berlin Data Center), one rack (Rack A1), and four devices (`core-sw-01`/`-02`, `edge-rtr-01`,
`fw-01`) with distinct roles and device types, giving the dashboard richer stats (3 device types,
4 devices, 1 rack) than a single bare device would.

## Known issue: the search screenshot is best-effort

`StoreScreenshotTest` waits for a fact that's only true once each screen's real data has rendered,
not a generic app-bar title, an asset tag also shown on the list row we just left, or (for search)
the search field's own typed text - all of those render before the underlying fetch/search
actually completes and can make the test capture a loading/empty placeholder instead of real
content. This was fixed for the dashboard, device detail, and settings screenshots, which
reliably show real content, and the device detail screen additionally needed an explicit
"Refresh" click (via its overflow menu) to work around what looks like a race between navigating
in and that screen's own per-device fetch actually starting - the NetBox API itself responds in
well under a second even right after `just netbox-up`, so this isn't a NetBox performance problem.

The search screenshot still intermittently comes out as "No matches yet" for a reason not fully
root-caused - most likely a further variant of the same residual-composition race. Rather than
keep chasing it, the wait around it is wrapped in `runCatching` so a slow/empty search result
can't block the settings screenshot after it; treat `03_search` as best-effort until someone
digs further (a good next step: run the test with `adb shell dumpsys activity` or a UI Automator
dump captured right at the wait boundary to see what's actually satisfying the wait check).

## Extending beyond the POC

- Fixing the search race properly (see above).
- More screens: add further `Screengrab.screenshot("...")` calls to `StoreScreenshotTest`,
  applying the same "wait on a fact unique to the loaded screen, not one already visible on the
  screen you're leaving" rule documented in the code comments.
- More locales: add entries to `locales(...)` in `fastlane/Screengrabfile` - screengrab switches
  the device locale for each one via `LocaleTestRule`, which is already wired into the test.
- Tablet screenshot buckets (7"/10" for the Play Store listing): create an additional AVD with a
  larger profile and repeat `just screenshots` against it; screengrab buckets output by the
  target's screen size automatically.
- Uploading straight to Play Console: a further `lane` could call `upload_to_play_store` with the
  captured `fastlane/metadata/android` directory.

## Verified POC run, 2026-08-04

Ran end to end on this machine (KVM-accelerated Pixel 2 profile, API 34 google_apis x86_64):

```console
just screenshots
```

`01_dashboard`, `02_device_detail`, and `04_settings` were repeatedly verified showing real
seeded content, not loading placeholders. `03_search` is best-effort (see above) and sometimes
still shows an empty state. The disposable NetBox fixture was confirmed torn down
(`docker compose ... down --volumes`) after every run, including failed ones.
