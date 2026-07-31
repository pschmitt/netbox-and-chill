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
- [x] Gradle project skeleton (single `:app` module, AGP/Kotlin/KSP/Hilt wiring, version catalog)
- [x] AndroidManifest, Material 3 theme + splash screen, adaptive launcher icon, deep-link intent-filter
- [x] NetBox API client (Retrofit + kotlinx.serialization, dynamic base URL, token auth) + Room offline cache
- [x] CameraX + ZXing barcode/QR scanner, device-URL parser
- [x] WorkManager periodic background sync
- [x] Compose screens: onboarding, device list, device detail, scanner, settings
- [x] CI (build/lint/release workflows), CI signing keystore (rbw + GitHub secrets), Obtainium README badge, fastlane metadata, PRIVACY.md, renovate.json
- [x] Build + smoke test on Zenfone 10 and Mi Pad 4, push to GitHub

Known follow-ups (not blocking, tracked here for the next session):
- A handful of non-fatal deprecation warnings on build (`hiltViewModel` and `LocalLifecycleOwner`
  moved packages upstream, `EncryptedSharedPreferences`/`MasterKey` deprecated in favor of the
  newer Jetpack Security Crypto APIs) - cosmetic, don't block compilation.
- Onboarding auto-focuses/pops the keyboard immediately on the URL field, confirmed via
  screenshot but not yet deliberately reviewed for polish.
- Only device browsing/lookup is covered - IPAM, circuits, cabling, etc. are out of scope for
  this MVP.

Status: **done** (MVP), 2026-07-31. Verified via `just build`/`just lint`/`just test` on
rofl-14.brkn.lol, installed and smoke-tested (launches without crashing, onboarding screen
renders correctly) on both the Zenfone 10 (USB) and Mi Pad 4 (SSH/adb).

Post-merge CI was actually broken (`material-icons-extended` pinned to a nonexistent version,
wrong `retrofit2-kotlinx-serialization-converter`/`PullToRefreshBox` import packages, a Kotlin
`weight()` explicit-import resolution quirk, Hilt 2.59.2 too old to read Kotlin 2.4.0's class
metadata, no Hilt binding for `WorkManager`, a CI signing keystore generated with mismatched
store/key passwords - PKCS12 silently ignores a distinct keypass - and `BuildConfig.GIT_REVISION`
getting constant-folded into a larger string so the release-verification grep never found it
standalone). All fixed same-day; `just build`/`just lint`/`just test` plus the GitHub Actions
Build/Lint/Release workflows are green as of commit `00337cb`.

## NBC-2: Onboarding keyboard covers the API token field

The soft keyboard overlaps the input fields on first launch instead of the screen scrolling/
resizing to keep the focused field visible.

**Why:** reported by the user testing on a real device; makes the token field hard to see while typing.
**How to apply:** likely needs `Modifier.imePadding()`/`verticalScroll` on the onboarding Column,
or `windowSoftInputMode` tuning - `MainActivity` currently sets `adjustResize` only on... (not set
at all currently, check manifest/theme edge-to-edge interaction with `enableEdgeToEdge()`).

Status: not started, 2026-07-31.

## NBC-3: Device type images + image attachments (list + detail)

User: "Pictures, including image attachments are a MUST!" Show NetBox device-type stock photos
(front/rear) in the device list (thumbnail) and detail screen, plus any `extras.ImageAttachment`
images uploaded on the specific device, displayed on the detail screen.

**Why:** core to making the app feel like a real inventory browser, not just a text list - user
explicitly called this a hard requirement, twice.
**How to apply:** needs Coil (`coil3` per findroidplus's usage) wired to the same OkHttp client/
auth interceptor; NetBox endpoints are `GET /api/dcim/device-types/{id}/` (front_image/rear_image)
and `GET /api/extras/image-attachments/?object_type=dcim.device&object_id=<id>`. Room schema needs
image URL columns (device type) and either a join table or a separate cached list for
attachments. Watch for auth-on-media-requests (NetBox media may or may not require the API
token depending on deployment).

Status: not started, 2026-07-31.

## NBC-4: New app icon - NetBox logo x raised-eyebrow emoji mashup

Current launcher icon is a placeholder (plain stroked box glyph on teal). User wants a proper
icon combining the NetBox logo with a raised-eyebrow emoji (🤨), matching the "NetBox and Chill"
branding.

**Why:** user's explicit design direction, replacing the placeholder from NBC-1.
**How to apply:** NetBox's logo is trademarked (see README's trademark notice) - a "mashup" for a
non-affiliated fan app needs the same care findroidplus took with the Jellyfin logo (README says
theirs is "a combination of the Jellyfin logo and the Android robot"). Produce as a vector
adaptive icon (foreground + background layers) like the current one, not a raster mashup image.

Status: not started, 2026-07-31.

## NBC-5: Editable devices

Allow editing device fields from the app (not just read-only browsing), presumably via
`PATCH /api/dcim/devices/{id}/`.

**Why:** user request - the app should be a two-way tool, not just a lookup/scan viewer.
**How to apply:** depends on NBC-6's direction (generic vs. hand-written screens) - a hand-rolled
edit form per field type is a lot of duplicate work if NBC-6's generated-views approach lands
first. Probably sequence this after NBC-6.

Status: not started, 2026-07-31.

## NBC-6: Generic/generated object views (device types, regions, racks, sites, ...) + nav

NetBox has 100+ object types (dcim, ipam, circuits, virtualization, tenancy, ...). Rather than
hand-writing a screen per type, introspect NetBox's own API schema (OpenAPI spec at
`/api/schema/`, or the app/model listing at `/api/`) to drive generic list/detail (and eventually
NBC-5 edit) screens from field metadata. User, verbatim: "the more I think abt it the more I lean
towards us 'generating' the individual views." Pair with a NetBox-style sidebar/navbar (not just
a bottom nav) for navigating between object types - user also asked for this, referencing the
NetBox web UI's sidebar. The set of "main" sections shown should be configurable (user's words:
"navbar with the main ones (dev, dev types, rack - gotta be configurable)").

**Why:** the alternative (hand-coding a screen per NetBox model) doesn't scale to "a lot" of
views: schema-driven generation is the only realistic way to cover NetBox's full data model
without an enormous, ever-growing amount of near-duplicate screen code.
**How to apply:** this is the biggest architectural decision pending in this backlog - needs a
real design pass before implementation (how field types map to Compose form/display widgets,
how nested/related objects are shown vs. linked-to, caching strategy per generated model, how
NBC-5 editing plugs into the same generated forms). Should probably get its own design doc/plan
before coding starts, given how much of the future app structure hinges on it. Do this before
NBC-5 (editing) and before wiring more object types into NBC-8's deep-linking, since both build
on whatever this lands on.

Status: not started, 2026-07-31. **Needs a design discussion/plan before implementation** -
flagged explicitly, don't just start writing a generic-screen framework unprompted.

## NBC-7: netbox-documents plugin support

User has a lot of documents stored via the `netbox-documents` NetBox plugin and wants them
accessible from the app.

**Why:** user's own NetBox instance relies on this plugin for document storage.
**How to apply:** plugin adds its own REST endpoints (`/api/plugins/netbox-documents/...` typically)
- need to check the actual plugin's API surface (not core NetBox API) once this is picked up.
Presence of the plugin isn't guaranteed for all NetBox instances users of this app might have, so
this should probably be optional/detected rather than assumed.

Status: not started, 2026-07-31.

## NBC-8: App Links for the user's NetBox domain + deep link to specific object views

Register Android App Link (domain-verified, not just the generic non-verified intent-filter from
NBC-1) for the user's actual NetBox host so tapping e.g. `https://netbox.brkn.lol/dcim/devices/393/`
anywhere opens the app directly (no chooser prompt), and extend beyond devices to open directly
into whatever object type the link points at (device-type, rack, site, etc. - depends on NBC-6).

**Why:** user wants the "open with" friction removed entirely for their own instance, and wants
this to work for more than just devices.
**How to apply:** proper Android App Links need a `.well-known/assetlinks.json` served from the
NetBox host itself (or a reverse proxy in front of it) for domain verification - that's
infrastructure outside this repo (on brkn.lol's web server config), not just an app-side change.
The existing NBC-1 intent-filter (host="*", no autoVerify) still covers the "Open with" chooser
path for any host in the meantime. Depends on NBC-6 for routing to non-device object types.

Status: not started, 2026-07-31.

## NBC-9: Dashboard/home page

A home/dashboard screen: NetBox change log, bookmarks, stats, and NetBox news.

**Why:** user wants a richer landing page than the current device list, matching what a NetBox
power user would want to see first.
**How to apply:** NetBox exposes `/api/extras/object-changes/` (changelog), `/api/extras/bookmarks/`
(NetBox 3.5+), and various count endpoints for stats. "NetBox news" has no obvious API source yet
(NetBox's own release notes / blog?) - needs clarification on what "news" should pull from.

Status: not started, 2026-07-31.

## NBC-10: Label printing from the app

Print device labels directly from the app, reusing/integrating with the user's existing
[printlabel](https://github.com/pschmitt/printlabel) project.

**Why:** user already has label-printing logic built and wants it available from this app instead
of a separate tool - presumably so the QR stickers this whole app is built around can be
(re)printed directly after scanning/creating a device.
**How to apply:** need to look at printlabel's actual interface (CLI? library? network service?)
to figure out the integration shape - could be a shared Kotlin/native lib, a shelled-out call, or
a network call to a printlabel server instance. Not yet investigated.

Status: not started, 2026-07-31.
