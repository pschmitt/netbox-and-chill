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
**How to apply:** `enableEdgeToEdge()` opts the activity out of the legacy
`windowSoftInputMode=adjustResize` behavior - fixed via `Modifier.verticalScroll(...).imePadding()`
on the onboarding Column, the standard Compose-with-edge-to-edge pattern.

Also added while in this screen (user requests, same area):
- [x] "Open API tokens page" trailing icon on the NetBox URL field - opens
  `<url>/user/api-tokens/` in the browser once a URL is entered.
- [x] "Paste from clipboard" trailing icon on the API token field.

Status: **done**, 2026-07-31. Verified on the Zenfone 10 - both fields and the Connect button
stay visible above the keyboard regardless of which field is focused.

## NBC-3: Device type images + image attachments (list + detail)

User: "Pictures, including image attachments are a MUST!" Show NetBox device-type stock photos
(front/rear) in the device list (thumbnail) and detail screen, plus any `extras.ImageAttachment`
images uploaded on the specific device, displayed on the detail screen.

**Why:** core to making the app feel like a real inventory browser, not just a text list - user
explicitly called this a hard requirement, multiple times.
**How to apply:** needs Coil (`coil3` per findroidplus's usage) wired to the same OkHttp client/
auth interceptor; NetBox endpoints are `GET /api/dcim/device-types/{id}/` (front_image/rear_image)
and `GET /api/extras/image-attachments/?object_type=dcim.device&object_id=<id>`. Room schema needs
image URL columns (device type) and either a join table or a separate cached list for
attachments. Watch for auth-on-media-requests (NetBox media may or may not require the API
token depending on deployment).

Scope grew after the first pass: user, verbatim, "we should sync these assets as well! ie full
offline mode. This includes docs too! (netbox-documents)" - so this isn't just "show an image URL
in an `AsyncImage`", it's downloading and caching the actual image/document bytes on-device
(Coil's disk cache alone isn't durable/guaranteed offline the way an explicit downloaded-files
store would be) so device-type photos and netbox-documents attachments are browsable with zero
connectivity, same as the rest of the app. That's real storage-management surface (download
triggers, cache eviction/size limits, sync-now vs. lazy-on-view) worth thinking through
deliberately rather than bolting on ad hoc - probably wants its own short design pass alongside
NBC-7 (they share the "binary asset synced for offline use" shape) rather than being purely an
extension of this entry.

**How the first pass (network display only) landed:** deliberately scoped down to just the
"show the image" half - the offline-sync/download-to-disk half above is still not started, see
follow-ups. Added Coil3 (`coil-compose` + `coil-network-okhttp`, pinned 3.5.0), wired to the same
authenticated `OkHttpClient` as Retrofit (`NetworkModule.provideImageLoader`, set as the app-wide
default via `NetBoxAndChillApp : SingletonImageLoader.Factory`) - confirms the TODO's own note
that media requests may need the API token. Two new typed endpoints on `NetBoxApi`
(`getDeviceType`, `listImageAttachments`), confirmed against NetBox 4.5's actual DRF serializers
(not guessed): `front_image`/`rear_image` are plain absolute-URL strings (`serializers.ImageField`),
and `image-attachments` filters by `object_type` as an `"app_label.model"` string (e.g.
`"dcim.device"`) + `object_id`, matching the TODO's endpoint shape. New Room tables
(`device_types`, `image_attachments`, DB version bumped to 3 - fine under the existing
`fallbackToDestructiveMigration`) plus a `deviceTypeId` column added to `DeviceEntity`. Two new
cache-first repositories (`DeviceTypeRepository`, `ImageAttachmentRepository`) mirroring
`DeviceRepository`'s `runCatching { api -> toEntity() -> dao.upsert }` shape rather than NBC-6's
generic-JSON approach, since these need typed image-URL fields. New shared
`ui/common/RemoteThumbnail.kt` (falls back to a generic device icon when no image is
cached/set yet) used by: the device list row (`DeviceRow` leadingContent, backfilled lazily per
distinct device-type id already in view - cheap no-op once cached), and the device detail screen
(front/rear stock photos side by side, plus a `LazyRow` of image-attachment thumbnails that open
full-size in the browser on tap - no in-app image viewer built, matches current "open in
browser" pattern elsewhere in this screen).

Known limitation flagged during development, resolved on merge: `DynamicBaseUrlInterceptor` would
otherwise prepend the configured base URL's path onto these already-absolute media URLs, double-
prefixing it for a subpath-reverse-proxied instance. NBC-16 (merged concurrently) landed a
`@DownloadClient`-qualified `OkHttpClient` for exactly this "already-absolute NetBox media URL"
case (auth still applied, base-URL rewrite skipped) - `provideImageLoader` was pointed at that
client instead of the plain one, so this never shipped as a live bug.

The durable offline-asset pass now stores image/document bytes under `filesDir` when the Settings
toggle is enabled, and all image/document views prefer those local files before using the network.

`just build`/`just lint`/`just test` all green on rofl-14. Installed on all three physical
devices (Zenfone 10, Mi Pad 4, Pixel 5) via `just deploy-all` - app launches cleanly on all three,
no crashes in logcat. Confirmed via the Mi Pad 4's logcat that the app issues the expected new
requests against the real instance (`GET .../api/dcim/devices/...`, followed by what would be the
new device-type/image-attachment calls once a device list loads) using the real configured host.

**Not independently confirmed:** actual image rendering against live data. netbox.brkn.lol was
flapping during this session's verification pass (HTTPS alternating between a 10s+ TLS-handshake
timeout and a 502 from its reverse proxy, confirmed via direct `curl` from outside the app too) -
an existing infrastructure issue unrelated to this change, not something introduced by it. Revisit
once the instance is healthy again to actually see the thumbnails/photos render, not just confirm
the app doesn't crash while trying.

Status: **done**, 2026-07-31 - durable image syncing, local-file rendering, and generic media
discovery are implemented; remote `just lint`, `just test`, and `just build` pass. Live visual
verification against current NetBox media remains a physical-device follow-up.

## NBC-4: New app icon - NetBox logo x raised-eyebrow emoji mashup

Current launcher icon is a placeholder (plain stroked box glyph on teal). User wants a proper
icon combining the NetBox logo with a raised-eyebrow emoji (🤨), matching the "NetBox and Chill"
branding.

**Why:** user's explicit design direction, replacing the placeholder from NBC-1.
**How to apply:** NetBox's logo is trademarked (see README's trademark notice) - a "mashup" for a
non-affiliated fan app needs the same care findroidplus took with the Jellyfin logo (README says
theirs is "a combination of the Jellyfin logo and the Android robot"). Produce as a vector
adaptive icon (foreground + background layers) like the current one, not a raster mashup image.

Status: **done**, 2026-07-31 - replaced the adaptive icon's raster foreground reference with a
repository-native vector recreation of the cyan/white NetBox raised-eyebrow mark. Remote debug
build and ktfmt validation passed; the Mi Pad 4 splash visually confirmed the new icon and launched
without an app crash.

## NBC-5: Editable objects (generic PATCH-based editing)

Allow editing object fields from the app (not just read-only browsing), via NetBox's REST PATCH.

**Why:** user request - the app should be a two-way tool, not just a lookup/scan viewer.
**How it landed:** built on top of NBC-6's generic engine rather than as a Device-specific
feature - `buildEditableFields` (`GenericFieldRenderer.kt`) picks out primitive (string/number/
boolean), reference, and choice top-level fields from the raw JSON, skipping a blocklist of
server-managed/computed ones
(`id`, `url`, `display`, `display_url`, `created`, `last_updated`, `custom_fields`). Edit mode on
`GenericDetailScreen` swaps the read-only field list for text inputs (a `Switch` for booleans),
Custom fields use the cached NetBox definitions and choice-set metadata to select text, long-text,
number, integer, boolean, choice, multi-choice, reference, and multi-reference editors; unsupported
custom-field types remain read-only. Save PATCHes only via `GenericNetBoxApi.patchObject`/
`GenericObjectRepository.updateObject`, which re-caches the server's response. **Verified against
the user's real NetBox instance** (via the
Mi Pad 4, which is already logged in): edited and saved a live Provider Account, confirmed the
`last_updated` timestamp actually changed server-side - full round trip works, not just
simulated/unit-tested.

- [x] Editing reference fields (site, rack, tenant, ...) and choice fields (status, ...) - generic
  edit mode now uses cached relation pickers and DRF `OPTIONS` choices, with current values still
  available when offline.
- [x] `custom_fields` editing - use cached definitions and choice sets for type-aware text,
  long-text, URL/date/datetime, number/integer, boolean, select/multi-select, and object/multi-object
  editors; unknown types remain read-only.
- [x] The legacy Device detail screen now exposes an Edit action that opens the generic,
  conflict-aware device editor while retaining the typed screen's cached/photos presentation.

Status: **done**, 2026-07-31. Custom-field editor coverage is unit-tested, and the legacy Device
detail now routes editing through the same generic flow live-verified on the Mi Pad 4.

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

**How it landed:** not OpenAPI-schema-driven after all - simpler than planned. `GET api/` lists
app namespaces, `GET api/<app>/` lists that app's models (including one extra nesting level for
`plugins/<name>/`, so plugin-provided types like netbox-documents show up automatically) -
`DirectoryRepository` walks this to build the sidebar tree, cached in Room
(`NetBoxModelEntity`/`NetBoxModelDao`). Detail screens render directly off the actual JSON API
*response* rather than any schema (`GenericFieldRenderer.kt`/`buildFieldRows`): nested objects
with `id`+`url` are tappable references to that object's own generic detail screen (recursively -
this is also why tags render as tappable chips, since NetBox tags are real objects too), choice
fields ({value,label}) show their label, arrays of references become a linked list, arrays of
primitives become a chip list. Generic object cache is one Room table
(`NetBoxObjectEntity`: endpointPath+id, display, raw JSON) rather than a typed entity per model.
Existing Device screens/DeviceEntity were deliberately left untouched (proven, tested, not worth
the regression risk) - only *other* object types route through the new generic screens; devices
still get their own bespoke list/detail. Sidebar is a `ModalNavigationDrawer` with per-app-group
sections, a pin/unpin star per model (pinned set lives in `SettingsRepository`, default just
Devices), a search field to filter sections, and per-category icons (`AppIcons.kt`). Scan/Settings
moved out of the drawer into a bottom `NavigationBar` (Devices/Scan/Settings) shared by the
device list and generic list screens. Scanning/deep-linking was generalized too
(`scanner/NetBoxUrlParser.kt`, replacing the device-only `DeviceUrlParser`) - any NetBox object
URL now resolves, not just `/dcim/devices/`, and the manifest intent-filter path patterns were
broadened to match (dcim/ipam/circuits/tenancy/virtualization/wireless/vpn/extras/plugins).

Follow-up noted during/after this landed:
- [x] "Linked items" on the *Device* detail screen (e.g. tapping its Rack/Site) now navigate to
  the existing generic detail screens. The typed cache persists the related IDs while retaining
  its proven device-specific rendering; full migration to the generic renderer remains optional.
- [x] Live verification - the Mi Pad 4 is logged into the user's real NetBox instance
  (netbox.brkn.lol). Confirmed against real data: directory discovery correctly builds the full
  sidebar tree (Circuits/Core/... groups, each with all their models, pin stars working), the
  generic list screen shows real synced objects (e.g. Provider Accounts), the generic detail
  screen renders real fields including a tappable Provider reference that navigates correctly,
  and Comments fields showing raw Markdown (`` `code` `` spans etc. as literal text) - confirms
  NBC-12 is a real, visible gap, not a hypothetical one.

Status: **done**, 2026-07-31. `just build`/`just test`/`just lint` green on rofl-14; installed,
launched without crashing, and live-verified against the real NetBox instance on the Mi Pad 4
(directory discovery, generic list, generic detail with reference-following all confirmed working
against real data) - also installed cleanly on the Zenfone 10 and Pixel 5.

## NBC-7: netbox-documents plugin support

User has a lot of documents stored via the `netbox-documents` NetBox plugin and wants them
accessible from the app.

**Why:** user's own NetBox instance relies on this plugin for document storage.
**How to apply:** plugin adds its own REST endpoints (`/api/plugins/netbox-documents/...` typically)
- need to check the actual plugin's API surface (not core NetBox API) once this is picked up.
Presence of the plugin isn't guaranteed for all NetBox instances users of this app might have, so
this should probably be optional/detected rather than assumed.

**Turns out most of this is already free.** NBC-6's directory discovery walks `api/plugins/`
generically, so `netbox-documents` (and any other installed plugin) already shows up as its own
sidebar section with no plugin-specific code - confirmed live on the Mi Pad 4: the "Documents"
section listed real PDF filenames from the user's instance via the plain generic list/detail
screens, no special-casing needed.

The generic detail screen now opens/downloads media-backed document fields and the optional offline
sync sweep stores them durably, so the plugin needs no special API code for ordinary document files.

- [x] Verify the live plugin API surface: `/api/plugins/documents/` exposes the standard
  `documents` collection, and its detail payload is a normal media URL plus filename, nested
  assigned-object reference, tags, and scalar metadata.
- [x] Verify there are no plugin-specific actions or nested structures requiring special handling:
  the collection's live `OPTIONS` response advertises only ordinary POST/PUT actions, while the
  generic renderer handles the observed detail payload.
- [x] Add a regression fixture for the observed `netbox-documents` detail shape.

Status: **done**, 2026-07-31 - live read-only API audit against netbox.brkn.lol, focused renderer
test, and the existing remote lint/test/build validation confirm generic list/detail, media opening,
and durable offline copies cover this plugin without special-case code.

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
path for any host in the meantime.

- [x] Routing beyond devices - NBC-6 generalized the scanner/deep-link parser
  (`scanner/NetBoxUrlParser.kt`) and the manifest intent-filter to any NetBox app namespace
  (dcim/ipam/circuits/tenancy/virtualization/wireless/vpn/extras/plugins), not just
  `/dcim/devices/`. Non-device links now resolve to the NBC-6 generic detail screen.
- [x] Add an exact-host `android:autoVerify` filter whose host is a compile-time setting (defaults
  to `netbox.brkn.lol`; override with `-PnetboxAppLinkHost=...` or `NETBOX_APP_LINK_HOST`); the
  wildcard chooser filter remains available for other configured NetBox instances.
- [ ] Publish the matching `/.well-known/assetlinks.json` on the NetBox host - still needs
  infrastructure work outside this repo.

Status: partially done, 2026-07-31 - the app-side verification filter is implemented and validated;
server-side Digital Asset Links publication remains required before Android can mark it verified.

## NBC-11: QR-code app configuration sharing (like findroidplus's setup codes)

Let the app be configured (base URL + token) by scanning a QR code generated by another instance
of the app (or shared some other way) - findroidplus has this already (see its
`findroidplus://setup` custom-scheme intent-filter and `QrConfigCodec` referenced in its
AndroidManifest.xml/AGENTS.md).

**Why:** user wants parity with findroidplus's existing setup-code flow - faster onboarding
across devices without retyping the URL/token, and referenced findroidplus as the precedent to
follow.
**How to apply:** look at findroidplus's actual `QrConfigCodec` implementation
(`~/devel/private/pschmitt/findroid.git`) for the encoding scheme/format to mirror. Needs a
custom URI scheme intent-filter (e.g. `netboxandchill://setup?...`) alongside the existing
onboarding flow, plus a way to *generate*/display the QR code from Settings for the sharing side
(scanning is already covered by the existing camera scanner, assuming the encoded payload is
recognized by NetBoxUrlParser/a new parser branch). Sensitive: the payload includes the API
token, so treat the generated QR code/settings-share flow with the same care as the token itself
(e.g. don't log it, consider a short display-only affordance rather than anything persisted).

- [x] Encode and decode versioned, URL-safe setup payloads without logging or persisting them.
- [x] Generate a display-only setup QR from Settings behind biometric/PIN authentication.
- [x] Import setup URIs from Android deep links and the existing scanner, pre-filling onboarding.
- [x] Validate with remote unit tests/lint/build and a Mi Pad 4 smoke test using a dummy payload.

Status: **done**, 2026-07-31 - remote `just test`, `just lint`, and debug build passed; Mi Pad 4
verified the biometric/PIN gate and dummy setup-URI onboarding import without connecting to NetBox.

## NBC-9: Dashboard/home page

A home/dashboard screen: NetBox change log, bookmarks, stats, and NetBox news.

**Why:** user wants a richer landing page than the current device list, matching what a NetBox
power user would want to see first.
**How to apply:** NetBox exposes `/api/extras/object-changes/` (changelog), `/api/extras/bookmarks/`
(NetBox 3.5+), and various count endpoints for stats. "NetBox news" has no obvious API source yet
(NetBox's own release notes / blog?) - needs clarification on what "news" should pull from.

**Scoped down for this pass** (matching NBC-3/NBC-17's habit of a scoped-down first pass with the
rest tracked as follow-ups): changelog + bookmarks + stats only. "NetBox news" is deliberately
**deferred/out of scope** - still no obvious API source (confirmed again this session against the
real instance's `/api/` root: nothing resembling news/announcements), not invented.

**Confirmed against the real instance (netbox.brkn.lol, NetBox 4.5.10) before writing any code:**
- The changelog endpoint has **moved**: it's `GET /api/core/object-changes/` in NetBox 4.x, not
  `/api/extras/object-changes/` as the original ask assumed (that was true pre-4.0) - `extras`'s own
  app root (`GET /api/extras/`) doesn't list it at all; `GET /api/core/` does. Shape: paginated,
  each row has `time`, `user` (nested ref), `action` ({value,label}), `object_repr`,
  `changed_object_type` (content-type string), and `changed_object` - a nested `{id, url, display,
  ...}` ref **when the object still exists**, `null` for deletes (confirmed live: a delete-action
  row's `changed_object` was absent from a same-session create/update sample - handled as nullable).
- `GET /api/extras/bookmarks/` confirmed exactly as expected: `{id, display, object_type, object_id,
  object: {id, url, display, ...}, user, created}` - `object` is the same `id`+`url`+`display` shape
  NBC-6's generic reference-field renderer already knows how to turn into a navigable target.
- Stats: confirmed `count` on `dcim/devices/`, `dcim/device-types/`, `dcim/sites/`, `dcim/racks/`
  (`382`/live count/`5`/`1` on the real instance) - picked these four as "a handful of key models,"
  not an exhaustive sweep; cheap (`?limit=1`, only `count` read, no full sync needed).

**How it landed:** new cache-first `DashboardRepository` (mirrors `GenericObjectRepository`'s
shape) backed by three new Room tables/DAOs (`bookmarks`, `object_changes`, `dashboard_stats` -
`AppDatabase` bumped to version 4, fine under the existing `fallbackToDestructiveMigration`).
Bookmarks/changelog are a full clear-then-replace on each refresh (small, bounded result sets - 50
bookmarks / most-recent 25 changes - so there's no reason to keep stale rows around); stats are a
plain upsert keyed by endpoint path. Reused `GenericNetBoxApi.listObjects(url, query)` (the same
schema-free call `GenericObjectRepository`/`JournalEntryRepository` already use) rather than adding
new typed Retrofit endpoints - no new API surface needed. Extracted the URL->endpointPath and
endpointPath->app-icon-key logic that used to live as private functions inside
`GenericFieldRenderer`/`GenericListScreen` into a shared `data/schema/NetBoxRef.kt` object, so the
dashboard's bookmark/changelog rows resolve navigation targets and pick icons (`AppIcons.
forAppKey(...)`) exactly the same way NBC-6's reference-field rendering already does, instead of
duplicating that logic a third time - both original call sites now delegate to it.

Bookmark/changelog rows navigate via the *same* `onNavigateToReference(endpointPath, id) ->
Route.Generic(...)` callback `GenericDetailScreen` already uses for reference fields - deliberately
**not** special-cased for devices, matching the existing precedent that reference fields elsewhere
(e.g. an IP address's assigned device) already route through the generic detail screen, not the
typed one. Rows with no resolvable target (a changelog delete, chiefly) render non-clickable rather
than silently going nowhere. Changelog row icon is action-based (add/edit/delete glyph) rather than
object-type-based, since a delete has no object to derive an icon from anyway.

**Navigation placement decision:** made the dashboard **both** the default post-login/post-onboarding
landing destination *and* a third bottom-nav tab (`Dashboard`/`Devices`/`Scan`, was just
`Devices`/`Scan` since NBC-14) rather than only one or the other - a "home page" that isn't also
where you land by default doesn't really function as one, but it still needs to be reachable
on-demand from anywhere the bottom bar shows (device list, generic list screens), so both.
**Stat tiles:** tapping the Devices tile specifically navigates to the existing typed `Route.
DeviceList` (richer, already-synced screen with thumbnails/status chips) rather than `Route.
GenericList("api/dcim/devices/", ...)` - a deliberate one-off special case (unlike the
bookmark/changelog reference-navigation decision above) since the generic object cache for that
endpoint path may well be empty until a user has separately visited it, whereas the typed Device
cache is very likely already populated; the other three stat tiles (device types/sites/racks) go
through the generic list route since there's no typed alternative for them.

- [x] Cache-first `DashboardRepository` + Room tables (bookmarks, changelog, stats)
- [x] `DashboardScreen`/`DashboardViewModel` (stat tiles, bookmarks list, recent-changes list, all
  icon-covered per `AGENTS.md`)
- [x] Wired into navigation as both the default landing destination and a third bottom-nav tab
- [x] Bookmark/changelog rows navigate into NBC-6's generic detail screen, reusing its existing
  reference-navigation callback
- [ ] "NetBox news" - deliberately deferred, no API source identified

Status: **done** (changelog + bookmarks + stats), 2026-07-31. `just build`/`just lint`/`just test`
green on rofl-14 - see below. **Not live-verified visually** - no physical device/live-instance
visual check was available in this session (this agent has no adb/device access); the live-instance
checks above were API-shape confirmation via direct `curl` against netbox.brkn.lol (real data,
real response bodies), not an in-app check. Needs an install + real look on a device next session,
same caveat as several other recent entries. "NetBox news" is out of scope for this pass, not
forgotten - no obvious API source exists on this NetBox instance.

## NBC-10: Label printing from the app

Print device labels directly from the app, reusing/integrating with the user's existing
[printlabel](https://github.com/pschmitt/printlabel) project.

**Why:** user already has label-printing logic built and wants it available from this app instead
of a separate tool - presumably so the QR stickers this whole app is built around can be
(re)printed directly after scanning/creating a device.

**Investigated:** `printlabel` is a ~2000-line **local Bash/Python CLI**
(`printlabel`/`labelmaker.py`/`ptcbp.py`/`ptstatus.py` in that repo), distributed via a Nix flake
(`nix run github:pschmitt/printlabel -- ...`). It talks **directly over Bluetooth** to a
paired Brother P-Touch Cube using its own reimplementation of Brother's PT-CBP protocol, and shells
out to `jq` and to a separate `nbx` CLI (not a NetBox HTTP call of its own) for its `--netbox
QUERY` mode. There is **no daemon, server, or HTTP surface anywhere in it** - confirmed by reading
the full script source (`usage()`/`--help` text and the actual option parsing), not just the
README. The app therefore ports the small RFCOMM/PTCBP transport and raster path directly rather
than trying to call the Linux CLI or adding a network service.

**Implemented:** the app now ports the printlabel PTCBP transport directly: it discovers paired
Brother/P-touch printers, connects over RFCOMM, checks the printer's 32-byte ready status, sends a
128-dot QR-plus-asset-tag raster using PackBits compression, and waits for the printer's completion
status. The print action is available from both the typed device detail screen and the generic
device detail screen. Android Bluetooth runtime permission and printer selection are handled in-app.
The cached device web URL and label text are the only inputs; printing never writes to NetBox and
continues to work from cached data while offline.

- [x] Port the PTCBP status/configuration/print transport and PackBits raster encoding.
- [x] Render the cached device URL as a QR code with the asset tag beside it.
- [x] Add paired-printer discovery, Bluetooth permissions, selection, and progress/error feedback.
- [x] Replace the detail-screen share-sheet action with a real in-app print job.
- [x] Add protocol tests and pass remote unit tests, lint, and debug build.
- [x] Verify physical output with the user's paired Brother printer (user confirmed printing works).

Status: mostly done, 2026-07-31 - native implementation, remote validation, and an initial physical
print passed; inversion/clipping changes still need a follow-up paper print.

## NBC-12: Render markdown fields properly

NetBox `comments`/`description` (and similar) fields support Markdown; the app currently shows
them as raw text (both on the old Device detail screen and NBC-6's generic
`FieldRow.PlainText`).

**Why:** raw `**bold**`/`- lists`/etc. as literal text is a poor reading experience for exactly
the fields (comments, descriptions) most likely to be long-form/formatted. Confirmed as a real,
visible gap (not hypothetical) while live-testing NBC-5 against the user's actual NetBox data - a
real Comments field was full of literal backtick/list markup.
**How it landed:** added `com.mikepenz:multiplatform-markdown-renderer(-m3)`, pinned to **0.41.0**
rather than latest (0.43.0 bumps `minCompileSdk` to 37; we're on 36 - see the version catalog
comment). Only the `comments` field is treated as Markdown (`description` is plain short text per
NetBox's own docs, deliberately excluded) - both NBC-6's generic detail screen
(`FieldRow.Markdown`) and the legacy Device detail screen's Comments field now render through the
same `com.mikepenz.markdown.m3.Markdown` composable. Edit mode is unaffected - editing still shows
the raw Markdown source in a plain text field, which is correct (you edit source, not rendered
output).

Live-verified on the Mi Pad 4 against the real NetBox instance: a Comments field with a bullet
list and several `` `inline code` `` spans now renders as an actual bulleted list with proper
monospace code chips, not literal asterisks/backticks.

Not covered: `custom_fields` values that are Markdown-typed per NetBox's custom field type system
- custom fields aren't rendered at all yet (see NBC-5's out-of-scope note), so this is moot until
custom field support exists.

Status: **done**, 2026-07-31. `just test`/`just lint` green; live-verified on the Mi Pad 4 against
real Markdown content.

## NBC-13: Global search

A single search that queries across NetBox object types, not just within one model's list screen.

**Why:** user wants to find something without first knowing/navigating to which object type it
lives under - the sidebar's search (NBC-6) only filters the *list of sections/categories* by
name, it doesn't search object data itself.

**Investigated first, per this entry's own note not to blindly trust the `/api/extras/search/`
guess:** checked the real instance (netbox.brkn.lol, NetBox 4.5) directly - `GET /api/extras/`'s
own root listing has no `search` key, `GET /api/extras/search/` itself 404s, and the full
`/api/schema/` OpenAPI document has zero paths containing "search". So there is no global-search
REST endpoint on this NetBox version - the TODO's original guess doesn't hold. Confirmed the
fallback instead: per-model list endpoints (`/api/dcim/devices/`, `/api/dcim/sites/`,
`/api/dcim/racks/`, `/api/circuits/circuits/`, ...) all accept `?q=<term>` and return 200 with
filtered results, verified live against each of those four. **Landed on client-side fan-out**:
query a curated set of endpoint paths in parallel via the existing `GenericNetBoxApi.listObjects`
(the same call `GenericObjectRepository.syncAll` already uses, just with a `q` query param instead
of pagination-only), merge, sort by display name.

- [x] `GlobalSearchRepository` (`data/repository/GlobalSearchRepository.kt`) fans a search term out
  across a baseline curated list of endpoint paths (devices, device-types, sites, racks,
  ip-addresses, prefixes, circuits, virtual-machines, tenants - covers the TODO's own suggested set
  plus a few equally common ones) in parallel via `coroutineScope`/`async`/`awaitAll`, one model's
  failure logged and skipped rather than failing the whole thing (mirrors
  `DirectoryRepository.refresh`'s per-app `runCatching`).
- [x] **Cache-first, not network-only** - the first same-day pass made results transient/
  network-only ("not written into the cache... since the point is a live merge, not another sync
  path"), which is a direct violation of this app's whole premise (`AGENTS.md`: "reads come from
  Room, writes/refreshes come from the API") - a search that stops working the moment NetBox is
  unreachable is exactly the regression NBC-18 exists to prevent elsewhere, caught in review before
  this ever shipped standalone. Reworked so results come from Room, like every other screen:
  `NetBoxObjectDao.searchAll(query, limit)` (new: cross-endpoint, unlike the existing per-endpoint
  `search`, so anything ever cached under *any* endpoint is instantly findable offline - the 9-model
  baseline now only bounds the network refresh below, not the cached read) combined with
  `DeviceDao.search(query)` (devices are cached in their own typed table, not `netbox_objects`, per
  NBC-6). `listObjects(endpointPath, mapOf("q" to term, "limit" to "15"))` is now purely a
  best-effort background *refresh* (`GlobalSearchRepository.refresh`) that upserts hits into
  `netbox_objects` via a new `GenericObjectRepository.cacheSearchResults` (reuses the same private
  `toEntity` mapping `syncAll` already uses) instead of returning them directly - devices are
  skipped in this refresh entirely, since `DeviceDao` already gets a full periodic sync
  (`DeviceRepository`/`SyncWorker`), so a redundant `?q=` round trip would add nothing.
- [x] `GlobalSearchViewModel.results` reads reactively straight from the cache-combining Flow above
  (renamed `isSearching` -> `isRefreshing`, since it now describes network activity, not whether
  results exist) - mirrors `GenericListViewModel`'s `objects`/`refresh()` split. Fixed a state-
  priority bug from the same first pass: `GlobalSearchScreen`'s `when` checked `isSearching` before
  `results.isEmpty()`, so a background refresh would hide already-available cached hits behind a
  full-screen "Searching…" - reordered so non-empty cached results always win, with a non-blocking
  `LinearProgressIndicator` for the refresh-in-flight hint instead.
- [x] `GlobalSearchViewModel` unions the baseline set with the user's *pinned* model paths
  (`SettingsRepository.pinnedModelPaths`) so anything a user has explicitly starred in the sidebar
  is searchable too, not just the fixed baseline - reuses `DirectoryRepository.observePinned(...)`
  (despite the "pinned" name, it's just a generic `WHERE endpointPath IN (...)` lookup) to resolve
  each hit's endpoint path back to a humanized model label + `appKey` for the icon.
- [x] Input is debounced 300ms (`Flow.debounce` + `collectLatest`, so a fast typist's earlier
  in-flight fan-out is dropped, not raced) before firing; empty query shows a hint, no-cached-hits-
  yet-with-a-refresh-in-flight shows "Searching…", zero results (refresh settled, still nothing)
  shows an explicit "No results" state.
- [x] New `GlobalSearchScreen` (`ui/search/`) - a dedicated full-screen search (not a dropdown),
  reachable via a new search `IconButton` added to the top bar `actions` of both `DeviceListScreen`
  and `GenericListScreen` (the two screens users land on most, per the bottom nav / sidebar model
  clicks) - deliberately separate from NBC-6/14's existing sidebar search field, which still only
  filters section/category *names* and is untouched. Result rows show the object's display name,
  its model label + optional secondary line (status/description), and `AppIcons.forAppKey(...)` for
  the icon - tapping navigates to `Route.Generic(endpointPath, id)`, the same generic detail route
  scanning/deep-links already use.
- [x] Reused `NetBoxRef.appKeyFromEndpointPath` (endpointPath -> appKey for `AppIcons.forAppKey`,
  extracted by NBC-9 into `data/schema/NetBoxRef.kt`) rather than writing a second copy - this
  entry's own first pass independently extracted an identical duplicate into `AppIcons.kt` while
  NBC-9 was landing the same helper concurrently in a separate worktree; reconciled on merge by
  keeping the one `NetBoxRef` copy and pointing `GlobalSearchScreen` at it.

- [x] Debounce-level refresh cancellation now propagates coroutine cancellation through the
  repository and ViewModel instead of swallowing `CancellationException`, so stale fan-out calls
  are cancelled with the outdated query.
- [x] Results now rank exact display matches, display prefixes, display substrings, and secondary
  field matches before deterministic alphabetical tie-breaking; duplicate cache hits are removed.

Status: **done**, 2026-07-31. `just build`/`just lint`/`just test` all green on rofl-13
(lint re-verified with `--rerun-tasks` to rule out a stale up-to-date cache hit) both before and
after the cache-first rework above; ranking and cancellation have focused unit coverage. Live API
verification of the underlying approach (no global-search endpoint exists; `?q=` works on per-model
endpoints) *was* done directly against the real netbox.brkn.lol instance via `curl`, see above.

## NBC-14: UI polish batch (sidebar, comments, custom fields, share, scanner)

A run of small, concrete UI/UX requests landed together in one pass:

- [x] **Sidebar sections collapsed by default**, like the NetBox web UI - was an "absurdly long"
  flat list before. Tapping a section header (app-icon row) toggles it; expand state is
  per-session (`expandedApps` local state, not persisted). Searching auto-expands every matching
  section, since collapsing search results you're actively looking for makes no sense.
- [x] **Settings moved from the bottom nav into a static sidebar footer** - `NetBoxBottomBar` is
  now just Devices/Scan (2 tabs, not 3). Footer layout: app icon | "Version X.Y.Z" + NetBox base
  URL (stacked, truncated) | settings cog - pinned below the scrollable `LazyColumn`, not part of
  it, so it never scrolls away.
- [x] **Comments re-styled as a card**, not a plain inline text row - `ui/common/CommentCard.kt`
  wraps the Markdown composable in a `Surface` with `surfaceContainerHigh` tonal background and
  rounded corners, used by both the generic detail screen and the legacy Device detail screen.
- [x] **`custom_fields` are now actually displayed** - previously silently dropped for anything
  non-primitive (object/multi-select custom fields) and crudely flattened into one row for
  primitives. Now each custom field expands into its own row via the same generic field renderer
  used for top-level fields (handles reference-typed and multi-select custom fields correctly,
  not just plain text ones). Still not *editable* - see NBC-5's out-of-scope note, unchanged.
- [x] **Share button** on both detail screens (`ui/common/ShareIntent.kt`, plain `ACTION_SEND` of
  the object's web URL). Incidentally fixed a real bug found while wiring this up: the legacy
  Device detail screen's "Open in browser" was opening the *API* URL
  (`.../api/dcim/devices/393/`, DRF's browsable API), not the actual NetBox web page - it now
  derives the correct web URL the same way NBC-6's generic screen already did.
- [x] **QR/barcode scanner viewfinder overlay** - a dimmed frame around a centered square cutout
  (`ScannerViewfinder` in `ScannerScreen.kt`), purely cosmetic like most scanner apps have; the
  analyzer still scans the whole camera frame regardless of what's inside the square.

Also surfaced while testing this batch: **netbox-documents plugin objects already list correctly**
through NBC-6's generic engine with zero plugin-specific code (see NBC-7, updated).

**Broader direction noted, not yet acted on:** user wants the generic views to feel less like a
"simple list of key/values" and more ergonomic/pretty in general, once field-type icons exist -
this batch is a step in that direction (cards, icons, collapsing) but there's more to do here;
no dedicated entry yet, revisit once there's a clearer concrete shape for it.

Status: **done**, 2026-07-31. `just build`/`just test`/`just lint` green; installed on all three
devices; sidebar collapse/expand and the netbox-documents discovery live-verified on the Mi Pad 4
against the real instance. Comment card, custom fields, share button, and scanner viewfinder
verified via successful compile+test only (not individually screenshotted against live data).

## NBC-15: NetBox Journal entries for an object

Show an object's Journal (`/api/extras/journal-entries/`) on its generic detail screen - NetBox's
free-form timestamped notes attached to any object, distinct from the auto-generated changelog.

**Why:** user request - journal entries are a normal part of how NetBox users track
context/history on an object, not currently visible anywhere in the app. Follow-up user request -
"journal entries should prolly be a separate 'tab' on the device view" - moved it out of the
inline field list into its own tab.
**How to apply:** `GET /api/extras/journal-entries/?assigned_object_type=<app.model>&assigned_object_id=<id>`
- the `assigned_object_type` filter takes a `"app_label.model"` string (e.g. `"dcim.device"`), which
isn't derivable from `endpointPath` (`api/dcim/devices/`) by a fixed string transform alone
(`devices` -> `device` is easy, but e.g. `ip-addresses` -> `ipaddress` isn't a plain de-pluralize).
`JournalEntryRepository.resolveAssignedObjectType()` instead fetches the real choice list from
journal-entries' own `OPTIONS` response (`GenericNetBoxApi.getJournalEntryOptions()`, cached
in-memory) and matches our discovered model segment against it using a small set of candidate
singular forms (strip `-`/`_`, then try as-is / drop trailing `s` / drop trailing `es` / `ies`->`y`)
- validated against the real instance for plain de-pluralization cases; plugin models resolve via
their URL plugin key as a best-effort app_label guess, not guaranteed to match. `GenericDetailScreen`
now shows a Material3 `TabRow` ("Details"/"Journal") instead of a single scrolling list, only when
there's at least one journal entry to show (kept the previous single-list layout when there are
none, to avoid an empty tab for object types that never carry journal entries). Each entry renders
via `CommentCard` (NBC-12/14) with a kind icon (info/success/warning/danger, using Material icons
already wired in via the extended icon set) + timestamp header. Posting new journal entries (not
just reading) not investigated/implemented.

Status: **done**, 2026-07-31. `just test`/`just lint` green on rofl-14. Live-verified end-to-end on
the Mi Pad 4 once netbox.brkn.lol's outage (NBC-18) resolved: navigated to the real "Office light
(retired)" device, tapped the Journal tab, and its real warning-kind entry rendered correctly -
kind icon, timestamp, and the full Markdown body (headers, bullets, inline code spans) via
`CommentCard`.

## NBC-16: download and open file/document attachments (PDFs etc.)

Detail screens now render any NetBox-hosted file field (documents, images, ...) as a tappable
"FileAttachment" row instead of a raw media URL. Tapping it downloads the file to the app's cache
dir and hands it to Android's normal `ACTION_VIEW` resolution via a FileProvider content URI - so
known types (PDF, images, ...) open directly in whichever app the user has set as default, and
anything ambiguous/unhandled falls through to the standard "Open with" chooser. Deliberately not
using `createChooser` - always forcing a chooser would fight Android's own default-app handling.

**Why:** user request - "we need a way to actually support displaying documents, esp. pdf!" plus a
follow-up constraint - "attachments with unsupported filetypes should go through the regular 'open
with' android dialog" - ruling out a custom in-app viewer or a forced chooser.
**How to apply:** `GenericFieldRenderer.isMediaUrl()` flags any field whose value is an http(s) URL
under a `/media/` path as `FieldRow.FileAttachment`; `GenericDetailViewModel.downloadAttachment()`
pulls it via `FileDownloadRepository` (a dedicated `@DownloadClient` OkHttpClient - auth header
only, no `DynamicBaseUrlInterceptor`, since NetBox's returned media URLs are already
complete/correct and must not be re-prefixed) into `cacheDir/downloads/`, then
`FileOpener.fileViewIntent()` builds the `ACTION_VIEW` intent via
`FileProvider`/`res/xml/file_paths.xml`. Plain (non-media) http(s) fields instead render as
`FieldRow.ExternalLink` and open in the browser.

Status: **done**, 2026-07-31. `just test`/`just lint`/`just build` green on rofl-14; installed on
Mi Pad 4 and Pixel 5; live-verified end-to-end against the real instance - opened a real
netbox-documents PDF (LG monitor dismantling instructions) from the "Documents" section, confirmed
the natural Android "Open with" chooser appears (multiple PDF-capable apps installed) and the PDF
renders correctly once opened. Zenfone 10 not reachable over adb this session - install there next
time it's available. Durable pre-sync of attachments is now covered by NBC-17's opt-in `filesDir`
sweep; this entry describes the original on-demand cache behavior.

## NBC-17: full offline sync - attachments, sync-on-edit, scheduled background sync, error handling

Extends the NBC-3/NBC-6/NBC-16 offline foundation from "objects sync, attachments download
on-demand" to a real offline-first experience: attachments optionally pre-synced to local disk,
sync triggered automatically (not just manually) on edits and on a schedule, and sync
failures surfaced to the user instead of failing silently.

**Why:** user request, in four parts across one message - "we should add a settings option to sync
attachments to local disk when we sync. off by default. But I want the full offline experience to
be possible", "we should sync on edits, AND schedule regular syncs - in the background. Kinda like
findroid handles auto-downloads" - then a follow-up - "we need to handle sync errors. With an
appropriate toast message if we are in the app. Maybe even retries."
**How to apply:**
- New `SettingsRepository` boolean pref (default off), e.g. `syncAttachmentsToDisk`, surfaced as a
  switch in `SettingsScreen` - mirrors the existing settings patterns there.
- When on, `GenericObjectRepository.syncAll()` (or a new pass after it) should walk the synced
  objects' `FieldRow.FileAttachment`-eligible fields (reuse `GenericFieldRenderer`'s `isMediaUrl()`
  detection) and pull each through `FileDownloadRepository` into a durable location - NOT
  `cacheDir` (NBC-16's download target), since cache can be evicted by the OS at any time and the
  whole point here is durable offline availability; needs its own `filesDir`-backed directory and
  a lookup so `GenericDetailScreen` prefers the locally-synced copy over re-downloading when
  present.
- Sync-on-edit: trigger a `refreshObject`/attachment-sync pass after a successful
  `GenericDetailViewModel.save()`, not just on manual pull-to-refresh.
- Scheduled background sync: turns out NBC-1 already shipped this half unnoticed - `sync/SyncWorker.kt`
  + `sync/SyncScheduler.kt` (`WorkModule.kt` wires the `HiltWorkerFactory`) already run a
  network-constrained 6-hourly `PeriodicWorkRequest` plus a manual `syncNow()` one-time request,
  matching findroidplus's `AutoBackupScheduler`/`AutoBackupWorker` shape (`Result.retry()` on
  failure already gets WorkManager's default exponential backoff, no extra tuning needed) - it just
  only syncs the legacy `DeviceRepository`, not the NBC-6 generic-object cache or attachments yet.
- Error handling: sync failures (manual or background) should surface as a Snackbar/Toast when the
  app is in the foreground (reuse the `errorMessage`/`SnackbarHostState` pattern already used by
  `GenericDetailViewModel`/`GenericDetailScreen`).

**Slice 1 (this pass):** `SettingsRepository.syncAttachmentsToDisk` pref + `SettingsScreen` switch
row (off by default, doesn't yet do anything downstream - the actual attachment-download sweep is
still slice 2, deliberately deferred: it needs live testing against real cached data to be sure the
walk/download/dedup logic actually behaves, and netbox.brkn.lol is unreachable this session - see
NBC-18). `SettingsViewModel.syncNow()` now surfaces `deviceRepository.syncAll()` failures via a
Snackbar instead of discarding the `Result` (was previously silent - the concrete gap the "handle
sync errors" request was pointing at for the one sync path already wired to the UI).
`GenericDetailViewModel.save()` now calls `syncScheduler.syncNow()` on a successful edit
(sync-on-edit), enqueuing the *existing* `SyncWorker` in the background - inert/safe to ship even
while offline, since it's just a WorkManager enqueue.

**Slice 2 (now done):** the attachment-to-disk download sweep extends `SyncWorker` through a shared
coordinator that scans cached generic objects and typed image metadata, downloads detected media
through a durable (not cache-dir) `FileDownloadRepository` method, and makes detail/list image views
prefer an already-synced local copy. The coordinator also syncs the NBC-6 generic-object cache,
not just the legacy device list. The "surface background sync failures via a `Notification` (a
background `WorkManager` failure has no `Activity` to show a `Snackbar` in, unlike the manual-sync
case slice 1 covers)" part of this slice has been split out and done as NBC-23, since it turned out
to overlap with that task's app-wide sync indicator - see NBC-23 for the notification/permission/
channel work; `SyncWorker` posts via `SyncNotifier` on exhausted retries.

Status: **done**, 2026-07-31 - the existing toggle now drives a durable `filesDir` attachment sweep;
manual and WorkManager sync share one coordinator that refreshes typed devices, the directory, all
generic object collections, device-type/image-attachment metadata, and media bytes. Successful
edits continue to enqueue sync-on-edit, and NBC-23 covers background failure notifications. Remote
`just lint`, `just test`, and `just build` pass; live offline rendering remains a device follow-up.

## NBC-18: show cached data immediately when the server is unreachable at launch

Don't let a down/unreachable NetBox instance block the app on an empty state - if there's cached
data from a previous sync, show it right away and let the (failed) refresh just report an error
around it, rather than the user seeing nothing until the network call resolves or times out.

**Why:** user request - "if the server is down at app launch we really should be displaying the
offline cache we have and not just fail." Directly hit this live while verifying NBC-15: this
session's network lost the route to netbox.brkn.lol entirely partway through (confirmed via a raw
`curl` timeout to the instance from both the dev host and the Mi Pad 4 over SSH, not an app bug),
which is exactly the scenario this todo describes.
**How to apply:** most of this may already work as intended - `DeviceListViewModel.devices` and
`GenericDetailViewModel`'s `decodedObject`/`fields` are Room-`Flow`-backed and independent of
`refresh()`'s success, so cached rows should already render regardless of a failed refresh; verify
that's actually true end-to-end once connectivity is back (this session's outage hit right as a
fresh install had zero cached rows to begin with, so "does a *populated* cache still show through a
failed refresh" wasn't actually provable this session - it needs its own real check, not just
reasoning about the code). Also check `DirectoryViewModel` (drives the sidebar's app/model
sections) - it only calls `refresh()` when `cachedModelCount() == 0`, so an already-populated
sidebar shouldn't be affected by a down server either, but same caveat: unverified this session.
If the reasoning above doesn't hold up once retested, the fix is ensuring every list/detail
ViewModel always emits from its Room `Flow` first and treats `refresh()` purely as a
best-effort background update, never a gate on what's rendered.

**Follow-up code audit (this session):** read every list/detail ViewModel line-by-line (not just
re-reasoned about) against the "Room `Flow` first, `refresh()` is a pure side effect" shape, since
NBC-13's global search had already shown once that reasoning-without-reading can be wrong:
- [x] `DeviceListViewModel.devices`/`DeviceDetailViewModel.device` - both `stateIn` a Room `Flow`
  (`DeviceRepository.observeDevices`/`observeDevice`) directly; `refresh()`/`refreshDevice()` only
  toggle `isRefreshing`/`errorMessage` on failure, never touch what's rendered. `DeviceRepository`'s
  `syncAll`/`refreshDevice` only `dao.upsert(...)`, no `dao.clear()` anywhere - a failed sync can't
  wipe existing rows.
- [x] `GenericListViewModel.objects`/`GenericDetailViewModel`'s `decodedObject`/`title`/`fields` -
  same shape, backed by `GenericObjectRepository.observeObjects`/`observeObject`. `syncAll`/
  `refreshObject` are `runCatching { ...; dao.upsert...() }` - the upsert is *inside* the
  `runCatching` after the network call, so a network throw never reaches the DB write; nothing to
  clear beforehand either. Confirmed via `GenericListScreen`/`GenericDetailScreen`: both key their
  empty/loading text off `objects.isEmpty()`/`title == null` first, `isRefreshing` only picks the
  wording within that branch - a non-empty cache always wins.
- [x] `DirectoryViewModel.modelsByApp` - `stateIn`s `DirectoryRepository.observeAll()` (Room)
  directly; `Sidebar.kt` renders `modelsByApp` with no loading/refresh gate at all. Confirmed
  `init` still only calls `refresh()` when `cachedModelCount() == 0` (unchanged from when this was
  originally flagged) - and confirmed `DirectoryRepository.refresh()`'s `dao.clear()` sits *after*
  the `api.getApiRoot()` call inside the same `runCatching`, so a network throw there returns
  before `clear()` runs and an already-populated sidebar survives a down server untouched.
- [x] `DashboardViewModel` (NBC-9, built after this entry was written, not previously audited
  against this rule) - `stats`/`bookmarks`/`changelog` all `stateIn` Room flows off
  `DashboardRepository`; `refresh()` fans out to three independent `runCatching` calls
  (`refreshBookmarks`/`refreshChangelog`/`refreshStats`), each only replacing its own DAO table
  *after* its own successful fetch, so one endpoint being unreachable can't blank the other two,
  let alone all three. `DashboardScreen`'s per-section `EmptyHint` only changes wording based on
  `isRefreshing`, never hides already-loaded rows.
- [x] `GlobalSearchViewModel` (NBC-13, fixed earlier the same day) - re-verified `results`
  `stateIn`s `GlobalSearchRepository.observeCached` (Room, cross-endpoint), `isRefreshing` is a
  separate best-effort network signal, and `GlobalSearchScreen`'s `when` checks
  `results.isNotEmpty()` before `isRefreshing` - still correct, holds up.
- [x] Broader sweep (`grep -rn "fun refresh\|fun sync" app/src/main/kotlin`) turned up no other
  screen-backing ViewModel with a refresh/sync path: `SettingsViewModel.syncNow()` and
  `ScannerViewModel.onCodeScanned()` both only ever trigger a repository upsert, no rendering
  gated on it; `OnboardingViewModel.connect()` is the pre-cache initial-setup flow (no cache can
  exist yet at that point, so the "cache-first" rule doesn't apply there by definition).
- [x] Noted but out of scope for this entry: `GenericDetailViewModel`'s Journal tab
  (`JournalEntryRepository`, NBC-15) is not Room-cached at all - a failed fetch just leaves
  `journalEntries` empty and the tab doesn't render, silently, per the "or is silently skipped"
  clause in `AGENTS.md`'s offline-first rule. It doesn't block or replace the main object view
  either way, so it's compliant, just not itself cache-first; a future entry could extend NBC-15 to
  cache journal entries in Room if that's wanted.

**Result: no bugs found.** Every read path already followed the required shape before this session
started - the reasoning in the original entry (above) held up under a full line-by-line read, not
just re-reasoning about it. No production code changes were made for this entry.

**Verification limitation (explicit, matching this repo's honesty convention):** this pass is a
**code audit only** - grep/read of every ViewModel and the repositories/DAOs behind them, confirming
the Room-`Flow`-first / `refresh()`-as-side-effect shape and that no failure path clears or
replaces cached rows before a successful network response lands. It is **not** a live
device/network test: there was no way in this session to physically kill connectivity mid-run
against a populated cache (same limitation the original entry hit with the netbox.brkn.lol
outage). A real device check - populate the cache, then kill/blackhole the route to the NetBox
instance and confirm each screen still renders its last-synced data with only a non-blocking
error/snackbar - is still owed next time a device and a controllable network are both available.

**Live device attempt (separate, concurrent session):** with netbox.brkn.lol's outage resolved,
a populated cache (382 real devices) was available to actually test this against - but the
attempt was aborted before producing a result: disabling WiFi on the Mi Pad 4 to simulate offline
severed its only remote-control path too (wireless adb depends on the same WiFi), and it was only
recoverable via an unrelated Home Assistant automation on that device, not anything done here. Not
a code problem, just a tooling gap in how offline was simulated - worth a safety net before the
next attempt (arm a delayed self-re-enable first, e.g.
`ssh <device> 'nohup sh -c "sleep 30 && svc wifi enable" &'`).

Status: **done** (code-audited, not live-device-verified), 2026-07-31 - every list/detail
ViewModel in the app read line-by-line and confirmed to already follow the Room-`Flow`-first,
best-effort-`refresh()` shape; no gating/clearing bugs found, so no code changes were needed.
`just build`/`just lint`/`just test` run clean on the (unchanged) codebase. A live device
network-kill test was separately attempted this session and aborted for tooling reasons (wireless
adb losing its own transport when WiFi is disabled), not a code issue - still needs a clean re-test
with a self-re-enable safety net once a device/connection is available.

## NBC-19: icon audit - buttons, ListItems, and a new AGENTS.md convention

Every labeled `Button`/`OutlinedButton` and every `ListItem` that names a distinct thing should
carry a leading icon, not just text - and this should stay true going forward, not just as a
one-time cleanup pass.

**Why:** user request - "make sure we use icons pretty much everywhere it makes sense to do so. On
buttons, on overflow menu items etc etc. Where there is text I expect a relevant icon as well!",
plus a same-thread follow-up - "pls update the agents.md as well, so that we do not end up with new
buttons/text widgets w/o icons in the future."
**How to apply:** audited every screen (`grep` for `Button(`/`IconButton(`/`ListItem(` across
`ui/`). Sidebar, `GenericDetailScreen`'s top bar, `DeviceDetailScreen`, `ScannerScreen`, and
`DeviceListScreen`'s row (`RemoteThumbnail` leading image, from NBC-3) already had full icon
coverage - no changes needed there. Gaps found and fixed: `OnboardingScreen`'s "Connect" button
(added `Icons.AutoMirrored.Filled.Login`); `SettingsScreen`'s "Sync now"/"Disconnect" buttons
(`Sync`/`Logout`) and its four `ListItem`s (NetBox instance/cached devices/app info/build - `Dns`/
`Storage`/`Info`/`Tag`); `GenericListScreen`'s row (`ObjectRow` had no leading icon at all - now
uses `AppIcons.forAppKey(...)` derived from the route's endpoint path, the same lookup the sidebar
uses, so a given NetBox object type reads with the same icon in both places). Added a "UI
conventions" section to `AGENTS.md` codifying the icon-everywhere rule for future work, including
pointing at `AppIcons.forAppKey` as the thing to reuse rather than picking new icons ad hoc.

Status: **done**, 2026-07-31. `just test`/`just lint` green on rofl-14; installed on the Mi Pad 4
and visually confirmed (Settings screen icons, Onboarding "Connect" button icon) - screenshots
match the intended layout with no crash. Installed on Pixel 5 too; Zenfone 10 not reachable this
session. Mi Pad 4 was reconnected once netbox.brkn.lol's outage resolved.

## NBC-20: tap an image to view it full-size with pinch/swipe zoom

Device-type stock photos and image attachments (NBC-3) currently just sit inline at a fixed
thumbnail size - tapping one should open a full-screen viewer with pinch-to-zoom/pan, not require
falling back to "open in browser" the way a document attachment does.

**Why:** user request - "images need to be clickable -> show in full size + swipe to zoom" - then
two follow-ups: "image attachments should open a popup (the kind you slide down to dismiss) when
clicked. on there i would expect the img to be displayed and the metadata of the img attachment.
btw pls refrain from renaming stuff. in netbox these are image attachments, not 'photos'. and: we
should be able to swipe left and right to see the next/prev img attachment."
**How to apply:** needs a full-screen image viewer shown as a swipe-to-dismiss popup/`Dialog`
(vertical drag-down closes it, matching the common photo-viewer gesture - not just a tap-to-close),
not a navigation route. Content: the image itself (Coil3 `AsyncImage` + zoom/pan - hand-rolled via
`detectTransformGestures`/`graphicsLayer`, or a small Zoomable-style dependency if one's already
idiomatic for Coil3 - check findroidplus's usage before picking) plus the `extras.ImageAttachment`'s
own metadata (name, size, upload/created date, content type - whatever the API response actually
carries, don't guess the field list) shown alongside/below it. Horizontal swipe moves between the
image attachments already loaded in `DeviceDetailScreen.imageAttachmentRow`'s `LazyRow` (a
`HorizontalPager` over that same list, opened to the tapped index, is the natural fit). Applies to
the image-attachment row (`imageAttachmentRow`'s `RemoteThumbnail`, currently opening the external
browser via `clickableIfUrl` - replace with this popup) - the device-type front/rear photos
(`deviceTypePhotos`) are a separate, single-image, non-"image attachment" case and may not want
the same swipe-between-siblings behavior; decide when implementing whether they get the popup too
or stay as-is. Terminology note for this whole entry and anywhere else in the app: NetBox calls
these "image attachments," not "photos" - keep using NetBox's own name for the object type, not a
friendlier paraphrase (this app should read like a faithful NetBox companion).

- [x] Full-screen viewer is a swipe-to-dismiss `Dialog` (`usePlatformDefaultWidth = false`), not a
      navigation route - vertical drag-down closes it like a standard photo-viewer gesture.
- [x] Pinch-to-zoom/pan on the image itself, hand-rolled (`AsyncImage` + a custom pointer-input
      gesture + `graphicsLayer` scale/translate) - no new dependency added.
- [x] Image attachment metadata (name, dimensions, description, created/last-updated) shown
      alongside/below the image, sourced from the real `extras.ImageAttachment` fields (not
      guessed) - `ImageAttachmentDto`/`ImageAttachmentEntity` extended to actually carry them.
- [x] Horizontal swipe between image attachments via `HorizontalPager` over the same list already
      shown in `imageAttachmentRow`'s `LazyRow`, opened to the tapped index.
- [x] `imageAttachmentRow`'s thumbnails open this viewer instead of `clickableIfUrl`'s external
      browser intent (`clickableIfUrl` removed, no longer used anywhere).
- [x] Explicit decision (documented below) on whether `deviceTypePhotos` (front/rear) get the same
      popup or stay as-is.
- [x] Terminology: "image attachment(s)", never "photo(s)", in any new code/comments/UI strings
      this task adds - also fixed the pre-existing user-visible `imageAttachmentRow` section label
      from "Photos" to "Image attachments" since it's directly in this feature's path (left the
      Kotlin identifiers `deviceTypePhotos`/`imageAttachmentRow` themselves alone, out of scope).

**Real `extras.ImageAttachment` API shape** (confirmed live against netbox.brkn.lol, not guessed):
`id`, `url`, `display` (server-derived filename when `name` is blank), `object_type`, `object_id`,
`parent`, `name`, `image`, `description`, `image_height`, `image_width`, `created`, `last_updated`.
No `size` (bytes) or `content_type` field exists on this serializer at all - the TODO's original
"name, size, upload date, content type" wishlist doesn't fully match reality; the viewer shows
what's actually there instead (name/display, description, `image_height`×`image_width`, created,
last updated).

**Device-type front/rear photos decision:** they get the *same* full-screen zoomable viewer, not
external-link-only. Reasoning: today they have no click handler at all (not even
"open in browser" - only `imageAttachmentRow` had that), and the user's underlying ask ("images
need to be clickable") is about images in general, not specifically the image-attachments table;
there's no reason to leave the front/rear stock photos inert once the zoom/pan viewer exists. They
don't carry `ImageAttachment` metadata (no `created`/`description`/dimensions - `DeviceTypeEntity`
only has the image URL and the type's model name), so their viewer instance shows only a
title ("Front of `<model>`" / "Rear of `<model>`") and no metadata panel rows - a deliberately
smaller reuse of the same `ImageViewerDialog`, not a separate feature.

**How it landed:** new `ui/common/ImageViewerDialog.kt` - `ImageViewerDialog(items: List<ImageViewerItem>, initialIndex, onDismiss)`, deliberately decoupled from `ImageAttachmentEntity` (an `ImageViewerItem` is just a URL + title + optional metadata rows) so it covers both the image-attachment row and the device-type front/rear photos with one composable. A plain `Dialog` (`usePlatformDefaultWidth = false`) hosts a `Column` of `HorizontalPager` (image area) + a metadata panel below; the pager's `userScrollEnabled` and the outer vertical dismiss-drag detector are both gated off a shared `isZoomed` flag so pinch-zoom, page-swipe, and swipe-to-dismiss don't fight each other - custom pinch/pan gesture detector (`detectZoomPan`, built on `awaitEachGesture`/`calculateZoom`/`calculatePan` from `androidx.compose.foundation.gestures`) only consumes pointer events while actually zoomed or mid-pinch, leaving a plain single-finger drag at 1x scale unconsumed so it bubbles up to the pager/dismiss-drag instead. Swipe-to-dismiss uses `detectVerticalDragGestures` + an `Animatable` (snap while dragging, `animateTo(0f)` spring-back if released under the 120dp threshold) plus a background-scrim fade tied to drag distance; an explicit `Close` `IconButton` (Material icon, per AGENTS.md) is also always present. `DeviceDetailScreen.kt`'s `imageAttachmentRow`/`deviceTypePhotos` now build `ImageViewerItem` lists and open the dialog on tap (`clickableIfUrl` removed entirely, no longer used); `ImageAttachmentDto`/`ImageAttachmentEntity`/`ImageAttachmentRepository` extended with `display`, `description`, `imageHeight`, `imageWidth`, `created`, `lastUpdated` (Room DB version bumped 4 -> 5, fine under the existing `fallbackToDestructiveMigration`) to actually carry the metadata shown. Device list row thumbnails (`DeviceListScreen.DeviceRow`) intentionally untouched - still no click handler, per NBC-3's original call that the list row probably shouldn't open a viewer.

Status: **done**, 2026-07-31. `just build`/`just lint`/`just test` all green on rofl-13 (only
pre-existing unrelated deprecation warnings, e.g. `hiltViewModel`/`EncryptedSharedPreferences`).
**Not verified this session:** the actual pinch/pan/swipe-to-dismiss/horizontal-page-swipe gesture
feel on a real device or emulator - no physical device was available in this session (this worktree
had no adb-connected device), so this is compile-clean and logically reviewed but not interactively
tested. The gesture-arbitration approach (consume only while zoomed, otherwise let the pager/dismiss
detectors see the event) is a known, common hand-rolled pattern but should get a real finger-on-glass
check on the Zenfone/Mi Pad/Pixel 5 before calling the interaction itself confirmed, not just "builds."

## NBC-21: scanner tap-to-focus + flashlight toggle

The QR/barcode scanner (CameraX + ZXing, NBC-1) has no manual focus control and no way to turn the
torch on in a dark rack room - both standard expectations for a barcode-scanning camera view.

**Why:** user request - "qr code reader view show allow us to tap-to-focus and a flashlight on/off
button would be nice too."
**How to apply:** CameraX's `CameraControl.startFocusAndMetering(FocusMeteringAction)` built from a
`MeteringPointFactory` (`PreviewView.getMeteringPointFactory()`) for tap-to-focus - hooked onto
`PreviewView.setOnTouchListener` directly (a Compose `pointerInput` modifier on the `AndroidView`
risks the embedded native view swallowing the gesture before Compose's gesture detector sees it -
this is CameraX's own documented recipe). `CameraControl.enableTorch(Boolean)` (gated on
`CameraInfo.hasFlashUnit()`, since not every device has one) for the flashlight, with an `IconButton`
(`Icons.Default.FlashOn`/`FlashOff` per the AGENTS.md icon convention from NBC-19) in the scanner's
top bar. The bound `Camera` object (from `ProcessCameraProvider.bindToLifecycle`, needed for both
features) is threaded out of `CameraPreview`'s `AndroidView` factory via a new `onCameraReady`
callback into `ScannerScreen`'s Compose state.

Status: **done**, 2026-07-31. `just test`/`just lint` green on rofl-14 (zero warnings beyond the
two pre-existing unrelated deprecations); installed on Mi Pad 4 and Pixel 5. Live-verified on the
Mi Pad 4 once reconnected: camera preview renders live video, tapping the preview to focus doesn't
crash (checked logcat directly), and the flashlight button correctly does NOT appear - this tablet
has no rear flash unit, confirming the `hasFlashUnit()` gate works as intended (couldn't verify the
torch actually turning on/off without a device that has one).

## NBC-22: bigger device-list thumbnails + un-crop the detail-screen photos

Two related sizing complaints about the NBC-3 image work: the device list row's `RemoteThumbnail`
is too small to be useful, and the detail screen's front/rear stock photos get hard-cropped past a
fixed height instead of scaling to fit.

**Why:** user request - "we should make the list items on like the dev list view bigger, so that
the images are bigger. and the images displayed on the dev view page should be scaled, instead of
hard-cropped off past a certain height."
**How to apply:** `DeviceListScreen.DeviceRow`'s `RemoteThumbnail` is currently a fixed
`Modifier.size(48.dp)` `ListItem` `leadingContent` - bump the size (and check `RemoteThumbnail`
itself/`ListItem`'s own min-height don't silently reclip a larger size). The detail screen's
front/rear photos need their `Image`/`AsyncImage` `contentScale` checked - `Crop` (or a fixed
`.height(...)` combined with the default `Fit` behavior clipping at the container bounds) is likely
the culprit; `ContentScale.Fit` (or `FillWidth` with no fixed height) shows the whole image instead.

Status: **done**, 2026-07-31. `RemoteThumbnail` gained a `contentScale` parameter (default `Crop`,
unchanged everywhere else); `DeviceDetailScreen`'s front/rear photo row now passes `Fit`;
`DeviceListScreen.DeviceRow`'s thumbnail bumped from 48.dp to 72.dp. Live-verified on the Mi Pad 4
against real device-type photos - the PDU and 8-inch-monitor detail pages show their full stock
photos un-cropped, and list-row thumbnails are visibly bigger.

## NBC-23: "sync in progress" indicator + surfaced sync errors (background, not just manual)

A sync happening in the background (the periodic `SyncWorker`, or a future sync-on-edit/full offline
sync per NBC-17) is currently invisible to the user - no progress indicator while it runs, and no
way to learn a background sync failed at all (only the manual "Sync now" button surfaces errors,
per NBC-17 slice 1).

**Why:** user request - "we should probably display a 'sync in progress' notification when we sync,
right? and surface sync errors. Esp when we add propper change sync (ie we edit an item offline,
and then sync again) this will be very very useful." - explicitly framed as more valuable once
NBC-17's sync-on-edit/full offline sync lands, since a background sync becomes a much more common
occurrence once edits queue and flush automatically rather than only firing on an explicit tap.
**How to apply:** two distinct pieces - an in-app "syncing" indicator (a small persistent
indicator/badge, not just the existing per-screen `PullToRefreshBox` spinners which only show while
that specific screen is visible) for when `SyncWorker` is actively running, and a background-capable
error surface for when it fails - `WorkManager`'s `WorkInfo`/`getWorkInfoByIdLiveData` can be
observed app-wide to know when `SyncWorker` is running/failed regardless of which screen is open. A
failure with no foreground `Activity` (the gap flagged in NBC-17 slice 2) likely needs an actual
Android `Notification`, not just a `Snackbar` - overlaps directly with NBC-17 slice 2's own
"surfacing background sync failures" follow-up, should probably be designed together with it rather
than as a fully separate feature.

- [x] `sync/SyncStatusRepository.kt` - new `@Singleton` wrapping `WorkManager.getWorkInfosForUniqueWorkFlow`
  for both of `SyncScheduler`'s unique work names (`PERIODIC_WORK_NAME`/`ONE_TIME_WORK_NAME`, made
  non-private so this can reference them), combined into a single `isSyncing: Flow<Boolean>`
  (`true` while either has a `WorkInfo.State.RUNNING` entry). Reads WorkManager's own
  locally-persisted state, so it's correct offline too - only the sync work itself needs
  connectivity, not observing whether it's running.
- [x] `ui/common/SyncStatusViewModel.kt` + `ui/common/SyncStatusIndicator.kt` - a thin
  `hiltViewModel()`-backed composable, a `LinearProgressIndicator` that `AnimatedVisibility`-shows
  only while syncing. Hosted once in `MainActivity`, layered in a `Box` above `NetBoxNavHost` (not
  inside any individual screen's own `Scaffold`), so it reflects sync state regardless of which
  screen is on-screen - deliberately structured this way instead of touching each screen's own
  top bar, since several of those screens (`DeviceListScreen`, `DeviceDetailScreen`,
  `DashboardScreen`, `Sidebar`) had other in-flight changes elsewhere this session.
- [x] `sync/SyncNotifier.kt` - new `@Singleton`, creates a `background_sync` `NotificationChannel`
  (called once from `NetBoxAndChillApp.onCreate`, idempotent) and posts a `Notification` (tapping
  it opens `MainActivity`) via `notifySyncFailed(message)`. Silently no-ops if `POST_NOTIFICATIONS`
  isn't granted on API 33+ instead of crashing the worker - this is a nice-to-have surface, not a
  hard requirement.
- [x] `AndroidManifest.xml` - added the `POST_NOTIFICATIONS` permission; requested at runtime from
  `MainActivity` on API 33+ (same `rememberLauncherForActivityResult`/`ActivityResultContracts.RequestPermission`
  shape `ScannerScreen` already uses for `CAMERA`), fire-and-forget - denial just means the
  notification silently doesn't show later.
- [x] `sync/SyncWorker.kt` - only notifies on *exhausted* failure, not every transient retry: caps
  retries at 3 attempts via `runAttemptCount` before switching from `Result.retry()` to
  `syncNotifier.notifySyncFailed(...)` + `Result.failure()`. Note this cap is per-run - a
  `PeriodicWorkRequest`'s attempt count resets at its next scheduled period regardless, so this
  bounds retries *within* one run, not across the whole periodic schedule.
- [x] Added a small `drawable/ic_stat_sync_problem.xml` vector (Material "sync_problem" glyph) as
  the notification's small icon - status-bar/notification icons must be simple alpha-only
  silhouettes, so a launcher-style icon wasn't reusable here.

**Deliberately out of scope:** no unit tests were added for the WorkManager/`Notification`-touching
pieces (`SyncWorker`, `SyncStatusRepository`, `SyncNotifier`) - they need `androidx.work:work-testing`
(or Robolectric) for meaningful coverage, neither of which is wired into this project yet, and the
existing test suite only covers plain-Kotlin logic (`NetBoxUrlParserTest`,
`GenericFieldRendererTest`, `EditableFieldTest`). Left as a follow-up rather than bringing in new
test infra as a side effect of this task.

Status: **done**, 2026-07-31 - `just build`/`just lint`/`just test` all green on rofl-14. Not
verified on a physical device this session (no device was reachable to confirm the indicator
actually renders live or that a real background-sync-failure `Notification` fires and looks right
in the tray) - reasoned through the WorkManager/Notification APIs and matched existing in-repo
patterns (permission-request shape from `ScannerScreen`, `ViewModel`/`hiltViewModel()` shape from
every other screen) instead. Should get a live check (deny/allow the permission prompt, force a
sync failure, confirm the top progress bar shows during a real sync) next time a device is
available.

## NBC-24: list-view scrolling performance (device list is the worst)

Scrolling the device list is janky/slow - the worst offender among the app's list screens.

**Why:** user request - "improve scrolling performance of the list view (device list is the worst
atm)."
**How it landed:** profiled on the Mi Pad 4 with `dumpsys gfxinfo` while scrolling the real
383-device cache. The list was launching device-type metadata backfills for every cached device/type,
not just visible rows, while each row also repeated the durable-file lookup during recomposition.
The list now uses stable row content types, memoizes the local-image lookup, and observes visible
lazy-list indices so only on-screen device types are backfilled. A warm post-change scroll trace
still showed device-specific jank (29% in the short sample), but the image rows render correctly and
the unbounded prefetch/recomposition churn is removed; further profiling can tune Coil/device
hardware behavior separately.

Status: **done** (targeted performance pass), 2026-07-31 - remote `just lint`, `just test`, and
debug build passed; installed and exercised on the Mi Pad 4 with real production data read-only.

## NBC-25: a way to view/copy the currently-configured API token

There's no way to see the API token the app currently has stored - useful when setting up a new
device and wanting to reuse (or manually re-derive) an existing token rather than generating a new
one from scratch.

**Why:** user request/observation while helping debug why a NetBox REST-API-created token wouldn't
authenticate - NetBox 4.x tokens use a "token pepper" scheme where the full secret is
`nbp_<TOKEN_NAME>.<KEY>`, and the REST API's `key` field on a `Token` object only ever returns the
raw `<KEY>` suffix, never the full prefixed value - the complete secret is shown exactly once, in
the web UI, at creation time (the API's own `token` field comes back `null` even for a token you
just created for yourself, confirmed live against netbox.brkn.lol). User's framing: "A little
button to display the current api token on the login page would be a great start."
**How to apply:** the app can only ever show back what NetBox already gave *this* app instance when
it was first configured (`SettingsRepository`'s stored `token` - EncryptedSharedPreferences, already
plaintext-accessible in-process) - it can't retroactively recover a full `nbp_...` value NetBox
never showed the app in the first place, and can't ask NetBox for it again later either. The login
page gets an ordinary eye-icon toggle for the token currently being entered. Settings gets reveal
and copy actions for the stored token, but both actions must first pass Android biometric/device
credential authentication (fingerprint or PIN); without an enrolled device credential the token
stays inaccessible.

- [x] `OnboardingScreen`: show/hide the token currently being entered; it remains local UI state and
  is never persisted or logged.
- [x] `SettingsScreen`: add masked stored-token display plus reveal and copy actions.
- [x] Gate both Settings actions with `BiometricPrompt` allowing a strong biometric or device
  credential fallback, and keep the token masked when authentication is unavailable or cancelled.

Follow-up, same thread: added a placeholder (not label) on `OnboardingScreen`'s API token field
showing the real format, `nbt_xxxxxxxxxxxx.xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx` - confirmed
against netbox.brkn.lol's actual `Token` model source (`TOKEN_PREFIX = 'nbt_'`,
`TOKEN_KEY_LENGTH = 12`, `TOKEN_DEFAULT_LENGTH = 40`) while debugging why a REST-API-created token
wouldn't authenticate (see below) - the user's own recollection of the prefix was "nbp_", the actual
constant is "nbt_".

Notes from that same debugging session, useful context for whoever eventually builds the
view/copy-token feature above: NetBox 4.x v2 tokens never return their plaintext secret via the
REST API under any circumstances (confirmed by creating a token for the *AI agent's own account* via
API and getting `"token": null` back regardless) - the only place the full secret is ever shown is
the web UI at creation time. The only way it was recoverable this session was direct
`netbox-manage shell` (Django ORM) access on the actual NetBox host, which the app obviously can't
do. Also: `Authorization: Token <value>` and `Authorization: Bearer <value>` are BOTH accepted by
this instance for v2 tokens (confirmed live) - the app's `AuthInterceptor` hardcodes `"Token "`,
which is fine, no change needed there.

Status: **done**, 2026-07-31 - verified with remote `just lint`, `just test`, and `just build` on
rofl-13; biometric/device-credential behavior was code-reviewed but not exercised on a physical
device in this session.

## NBC-26: narrower sidebar + real app icon in the footer

The navigation drawer is wider than it needs to be, and its footer currently shows a generic
`AppIcons.Devices` glyph instead of the app's own icon.

**Why:** user request - "sidebar - can we make it less wide? and we should display our app icon in
the bottom right (left of the version info and netbox url)" (the existing footer layout is
ICON | version/URL | settings cog, so "bottom right" here means the already-present leading icon
slot, not a new position).
**How to apply:** `Sidebar.kt`'s `ModalDrawerSheet` had no explicit width (Material3's default,
which reads wide on a phone) - constrained to `Modifier.width(280.dp)`, the Material Design minimum
recommended drawer width. `SidebarFooter`'s leading `Icon(AppIcons.Devices, ...)` swapped for the
actual app icon. Note: NBC-4 (a real custom app icon design - NetBox logo × 🤨 emoji mashup) is
still not started, so this currently surfaces whatever placeholder/default launcher icon exists
today, not a finished design - revisit this footer once NBC-4 lands.

**Real bug caught live, not just theoretical:** the first attempt used
`Image(painterResource(R.mipmap.ic_launcher), ...)` directly, which crashed the app on every launch
- `ic_launcher.xml` is an `<adaptive-icon>` (separate background/foreground layers), and Compose's
`painterResource()` only supports VectorDrawables and flat raster assets, not that wrapper format
(`IllegalArgumentException: Only VectorDrawables and rasterized asset types are supported`). This
wasn't caught by `just test`/`just lint` (a Compose runtime failure, not a compile error) - only
surfaced when actually installed on the Zenfone 10, which then repeatedly crash-looped and got
force-killed by Android, making it *look* like the device's launcher (`projekt.launcher`) was
blocking the app from ever opening - a real, embarrassing dead end chased for a while before
checking logcat properly. Fixed by rendering the drawable through `ContextCompat.getDrawable(...)
?.toBitmap()?.asImageBitmap()` first (works for any drawable type, adaptive icons included) instead
of `painterResource`. Same pattern reused for NBC-28's onboarding-screen app icon.

Status: **done**, 2026-07-31. `just test`/`just lint` green; live-verified crash-free on the
Zenfone 10 after the fix, sidebar narrower and footer showing the real launcher icon.

## NBC-27: unify the app's three separate search boxes

There are currently (at least) three different search entry points that all feel like they should
be one thing: the sidebar's "Search sections" box (filters the model/section list itself), NBC-13's
global search (searches NetBox object data), and each list screen's own "Search devices"/etc. box
(filters within that one object type). Confusing to a user who just wants "search" without knowing
which of the three they need.

**Why:** user request - "we should combine our searchbar somehow. There'd only 1 search ideally.
currently we have at least 3: section search in the navbar, global search and the search on the
(device/item) list pages. not sure how to best marry them, but it's a bit confusing atm."
**How to apply:** genuinely needs a design decision, not just a mechanical merge - the three
searches operate on different scopes (sidebar sections/model names vs. NetBox object data
everywhere vs. one object type's already-cached rows) and a single box needs a clear model for
which scope applies when. Options worth weighing rather than picking blind: (a) one global-search
entry point reachable from everywhere (e.g. promoted into the top app bar) that also offers to
jump to a matching sidebar section, retiring the sidebar's own local filter box; (b) keep the
per-list-screen search (it's filtering already-loaded local data, cheap and fast, arguably a
different job than "find something anywhere") but merge just the sidebar section-search into global
search. Needs its own look at how NBC-13 actually shipped (this session didn't build it - another
concurrent session did) before deciding.

Status: **done**, 2026-07-31 - verified with the generic renderer unit tests plus remote `just lint`,
`just test`, and `just build` on rofl-13.

## NBC-28: real app icon on the onboarding screen + dashboard stat-card overflow fix

Two small fixes landed together with NBC-26's sidebar-icon work, same session: the onboarding
screen's "Connect to NetBox" header used a generic `Icons.Default.Inventory2` glyph instead of the
app's own icon, and NBC-9's dashboard stat cards (fixed-height from NBC-22's own uniform-sizing
fix) were clipping longer labels like "Device Types" instead of wrapping them cleanly.

**Why:** user requests - "on the login page we should display our app logo instead of the random
icon you put there", and a live-testing catch of the dashboard card issue right after connecting a
freshly-provisioned device and seeing "Device Types" visibly cut off mid-word.
**How to apply:** `OnboardingScreen`'s icon replaced with the same `ContextCompat.getDrawable(...)
?.toBitmap()?.asImageBitmap()` pattern from NBC-26 (not `painterResource` - same adaptive-icon crash
risk). `DashboardScreen.StatTile`'s fixed card size bumped from 110×120dp to 110×136dp and its label
`Text` given `maxLines = 2` + `TextOverflow.Ellipsis` as a safety net for even longer labels in the
future.

Status: **done**, 2026-07-31. `just test`/`just lint` green; live-verified on the Zenfone 10 -
onboarding shows the real launcher icon, dashboard cards render uniform height with no clipping.

## NBC-29: manufacturer/model (and similar) fields should link to their own object

On the legacy (non-generic) device detail screen, fields like "Manufacturer" and "Model" render as
plain text - unlike NBC-6's generic detail screen, which already turns any NetBox reference field
into a tappable link to that object's own page.

**Why:** user request - "stuff like 'manufacturer', 'model' etc should be clickable and open the
relevant page (The manufacturer, or model page for instance in this example)."
**How to apply:** `DeviceDetailScreen.kt`'s `detailField(...)` helper renders plain
`Text`/`ListItem`-style rows from typed `DeviceEntity` columns (`manufacturerName`,
`deviceTypeModel`, ...) which only ever stored the *display string*, not the referenced object's
id/endpoint - there's currently no id to navigate to even if the row were made clickable. Either (a)
extend `DeviceEntity`/`DeviceDto`/the sync mapping to also capture each reference's id (manufacturer
id, device-type id already exists via `deviceTypeId`, site/rack/role ids do not), then make those
specific rows navigate via the same `onNavigateToReference`-style callback `GenericDetailScreen`
uses, or (b) simplest and most consistent with how the rest of this app has been trending (NBC-6
onward): route the legacy device detail screen through the generic engine entirely instead of
maintaining two parallel detail-rendering implementations, which would get this "for free" the same
way NBC-15's Journal tab and NBC-16's file attachments did. Worth deciding which before starting -
option (b) also happens to be the fix for NBC-30 below, for the same reason.

Status: **done**, 2026-07-31 - verified with the generic renderer unit test, remote `just lint`,
`just test`, and `just build` on rofl-13, plus a live Mi Pad 4 device-type detail screenshot showing
the un-cropped front image.

## NBC-30: device/item title belongs in the page body, not the top app bar

Long device/item names make the `TopAppBar` title wrap and grow the header to an awkward height.

**Why:** user request - "device/item view page -> we should move the title of the device/item from
the header back to the body/content of the page. We have some items with long names, that make the
header weirdly large in height."
**How to apply:** applies to whichever detail screen(s) currently put the object's full name in
`TopAppBar`'s `title` - move it into the scrollable body instead (a `Text` at the top of the content
column, `TopAppBar` keeping just a short/generic title or none) so a long name wraps within the page
instead of stretching the fixed app bar. Check both `DeviceDetailScreen` and `GenericDetailScreen`
(NBC-15's `title` `StateFlow` currently feeds the `TopAppBar` title directly) - likely wants the same
treatment in both, which is also another point in favor of NBC-29's option (b) (route everything
through the generic engine) rather than fixing this twice.

Status: **done**, 2026-07-31 - typed and generic detail titles now render at the top of the
scrollable page body while the app bars keep short labels; remote verification is recorded in this
pass.

## NBC-31: copy-to-clipboard icons on identifier fields (Serial, Primary IP, Asset tag, ...)

Fields that are short identifiers someone would realistically want to copy elsewhere (Serial,
Primary IP, Asset tag, and similar) have no quick copy action - currently requires long-press
text selection.

**Why:** user request - "we should 'copy-to-clipboard' icons next to the fields (Serial, Primary IP,
Asset tag etc etc...)."
**How to apply:** needs a small trailing `IconButton` (`Icons.Default.ContentCopy`, matching the
icon-everywhere convention from NBC-19/AGENTS.md) next to specific field rows that copies the
value via `ClipboardManager` (same API already used for "Paste from clipboard" on
`OnboardingScreen`'s token field). Open question worth deciding before implementing: *which* fields
get this - the user named Serial/Primary IP/Asset tag specifically (identifier-shaped values), not
every field indiscriminately; on the generic engine (NBC-6) that likely means opt-in per field *key*
(a small allowlist: `serial`, `asset_tag`, `primary_ip4`, `primary_ip6`, ...) rather than every
`FieldRow.PlainText`, to avoid cluttering fields where copying doesn't make sense (e.g. `comments`,
free-text descriptions).

- [x] Added the identifier allowlist to the generic renderer and copy icons to generic reference/
  text rows and the typed device detail screen.
- [x] Added coverage for serial, asset tag, primary IP, and non-copyable fields.

Status: **done**, 2026-07-31 - verified with the generic renderer unit tests plus remote `just lint`,
`just test`, and `just build` on rofl-13.

## NBC-32: detect and resolve edit conflicts (offline edit vs. server-side change)

No conflict handling exists today: if an object is edited offline in the app, then also changed on
the server (or by someone else) before the app's edit syncs, the last write silently wins - the
user gets no warning and no way to see or resolve what actually differs.

**Why:** user request - "how do we handle conflicts atm? ie i change something offline and in the
app in parallel. How do we reconcile this? I expect a warning on the home page and a view to
properly resolve the merge conflict (with diffs and all)."
- [x] Capture the last-synced base object and compare `last_updated` before PATCHing; fall back to
  a full JSON comparison when the API response has no version field.
- [x] Add a durable Room outbox for offline edits and process it before ordinary cache refreshes,
  so queued local changes are not silently overwritten.
- [x] Show a conflict count warning on the dashboard and provide a resolver with base/local/server
  values plus a keep-local/keep-server choice for each changed field.
- [x] Re-check the server snapshot before applying a resolution and preserve the conflict if the
  server changes again.
- [x] Add focused three-way-diff tests and complete remote unit-test/lint/build validation.
- [x] Keep validation free of deliberate conflicts against the production NetBox instance; the live
  end-to-end conflict path remains unverified by design.

Status: **done**, 2026-08-01 - implementation and focused tests are in place; remote tests, lint,
and debug build passed. No production NetBox writes or deliberately induced live conflict were used
for validation.

## NBC-33: confirm a manual refresh on the detail screen with a toast/snackbar

Tapping the refresh icon on a device/item detail page gives no feedback on success - only a
failure shows anything (the existing error Snackbar). A successful refresh just silently updates
the fields, easy to miss.

**Why:** user request - "we should at least show a little toast msg when we hit the refresh button
on a device view page."
**How to apply:** `GenericDetailViewModel.refresh()`/`DeviceDetailViewModel.refresh()` already have
an `onFailure` branch wired to `_errorMessage`/`SnackbarHostState` (NBC-17-adjacent pattern) - add
an `onSuccess` branch that shows a brief confirmation the same way (reusing the existing Snackbar
host rather than a separate `Toast`, for visual consistency with how errors are already shown on
these screens).

Status: **done**, 2026-07-31. `refresh()` on both `GenericDetailViewModel` and
`DeviceDetailViewModel` gained a `showConfirmation: Boolean = false` parameter (default false, so
the automatic `init{}`-time refresh stays silent - only the explicit refresh-button tap passes
`true`) driving a new `refreshedMessage` Snackbar, mirroring the existing `errorMessage` pattern on
both screens. `just test`/`just lint` green; not yet live-verified on a device this round.

## NBC-34: render markdown in custom fields NetBox itself marks as markdown-type

Custom fields configured as markdown type in NetBox (e.g. a `purchase_store` field) render as plain
text in the app instead of formatted markdown - only the hardcoded `comments` field gets Markdown
treatment today (NBC-12).

**Why:** user request - "we should support markdown formatting in the fields that explicitly do
support it, such as our 'purchase_store' custom field for example."
**How it landed:** `CustomFieldRepository` fetches and caches NetBox's per-instance custom-field
definitions in Room. `GenericDetailViewModel` combines that offline Flow with the cached object;
custom fields whose server type is `markdown`, `text`, or `longtext` become `FieldRow.Markdown`,
while the existing hardcoded `comments` behavior remains unchanged.

Status: **done**, 2026-07-31 - custom-field definitions are cached in Room and refreshed
best-effort, textual custom fields now render through the existing Markdown card, and the renderer
has unit coverage. Remote `just lint`/`just test` and a debug build passed; the Mi Pad 4 live check
opened a generic detail without errors.

## NBC-35: comment/markdown card had excess top/bottom padding from blank lines

`CommentCard` (NBC-12/14) looked like it had too much vertical padding - actually blank leading/
trailing lines in the source markdown being rendered as real empty paragraphs by the Markdown
renderer, stacking with the card's own 16dp padding.

**Why:** user request - "there seems to be a bit too much top and bottom padding on the comments
widget. Looks like there are trailing newlines this way. make it more compact." - correctly
self-diagnosed the actual cause.
**How to apply:** `CommentCard` now calls `content.trim()` before handing it to the `Markdown`
composable, stripping leading/trailing blank lines before they're parsed into paragraphs.

Status: **done**, 2026-07-31. `just test`/`just lint` green; not yet live-verified against a
real comments field with trailing newlines this round.

## NBC-36: clickable count summaries (rack count, VM count, ...) filter into the list view

Summary count fields like "Rack Count"/"Virtual Machine Count" on a location (or similar rollup
counts on other object types) render as plain numbers - tapping one should jump to that object
type's list, pre-filtered to the item being viewed (e.g. tapping a location's rack count shows
that location's racks).

**Why:** user request - "the 'Rack count', 'Virtualmachine count' etc items that are displayed on
the location view should be clickable. This should also be the case for the other views that
display such summaries. clicking on it should bring us to the list view - prefiltered with the
current location (or the other relevant item we are coming from)."
**How to apply:** NetBox's own object detail pages compute these as reverse-relation counts (not
regular fields NetBox's API necessarily returns inline on every object - needs checking exactly
what `buildFieldRows()` currently receives for a location and whether counts like this are even
present in the raw API response, or whether they'd need a separate `?location_id=<id>`-filtered
count query per relation). If the data's there, rendering it as a `FieldRow.Reference`-like tappable
row that navigates to `GenericListScreen` with a pre-applied filter needs `GenericListViewModel`/
`GenericListScreen` to support an incoming filter query param in the first place - check whether
that exists yet (NBC-6's list screens currently only support the user's own free-text search box)
before assuming it's just a navigation-argument plumbing job.

Status: **done**, 2026-07-31 - known location/site reverse counts (`rack_count`, `device_count`, and
`prefix_count`) now render as tappable filtered-list actions. The generic list keeps the relation
filter over cached JSON for offline use and performs a best-effort server refresh using the matching
`*_id` query. Remote tests/lint/build passed; Mi Pad 4 live verification opened Office and confirmed
Rack Count filtered to the one Office rack without any write request.

## NBC-37: device view should link to its device type page

The device detail view shows the device type (e.g. as a "Model" field) but doesn't link to that
device type's own page.

**Why:** user request - "devices views currently lack the link to their dev type."
**How to apply:** overlaps directly with NBC-29 (manufacturer/model fields should be tappable
references) - device type is exactly one of the fields NBC-29 already covers. On the generic
engine (NBC-6) this may already work if the raw device object's `device_type` field comes back as
a full nested reference object (id + url), since `buildFieldRows()` already turns those into
tappable `FieldRow.Reference`s automatically - needs checking whether it's actually missing there
too, or only on the legacy `DeviceDetailScreen` (which is the one NBC-29 diagnosed as lacking ids
for its typed fields, `deviceTypeModel` being a display-string-only column).

Status: **done**, 2026-07-31 - the legacy device detail's Model field now opens the cached/network
generic device-type detail; the action was verified through the shared navigation route and the
remote lint/test/debug validation pass.

## NBC-38: device-type page should render front/rear images like the device page does

The device-type detail page's front/rear stock photos don't render the same way NBC-22 fixed them
to on the device page (un-cropped, `ContentScale.Fit`).

**Why:** user request - "on the dev-type page the front/rear images should render similarly to how
they do on the dev page."
**How to apply:** NBC-22 fixed `DeviceDetailScreen.deviceTypePhotos()`'s `RemoteThumbnail` calls to
use `ContentScale.Fit` instead of the default `Crop`. Find wherever the device-*type* detail page
(likely reached via NBC-29/37's device-type link, or already existing as its own generic-engine
screen) renders its own front/rear images and apply the same `contentScale = ContentScale.Fit`
`RemoteThumbnail` parameter (added in NBC-22 specifically to support this).

- [x] Generic device-type `front_image`/`rear_image` fields now render as inline image rows with
  `RemoteThumbnail(..., contentScale = ContentScale.Fit)`; other media fields keep their existing
  download-row behavior.
- [x] Added renderer coverage for both device-type image fields.

Status: **done**, 2026-07-31 - verified with the generic renderer unit test plus remote `just lint`,
`just test`, and `just build` on rofl-13; visual rendering was not exercised on a physical device
in this session.

## NBC-39: Settings screen has no way to change the configured NetBox server

The "NetBox instance" row on `SettingsScreen` only ever displays the currently-configured base
URL as read-only text - there's no way to point the app at a different NetBox instance without
disconnecting entirely and going back through onboarding from scratch.

**Why:** user request/observation - "the settings page currently does not allow changing the
netbox server."
**How to apply:** `SettingsRepository.save(baseUrl, token)` already exists and is exactly what
`OnboardingViewModel.connect()` uses - the dynamic base-URL interceptor picks up a saved change at
runtime with no rebuild needed (per `AGENTS.md`'s architecture note), so the plumbing already
supports this, it's just never been exposed as an edit affordance post-onboarding. Needs: an edit
icon/dialog on the "NetBox instance" row (`OutlinedTextField` pre-filled with the current URL),
validate reachability against the *new* URL before committing to it (mirror
`OnboardingViewModel.connect()`'s save-then-validate-then-revert-on-failure shape, not a blind
save), and - important, not just cosmetic - the local Room cache must be treated as
server-specific: switching to a different NetBox instance while keeping old cached
devices/objects around would silently mix data from two different servers (same object ids
meaning different things), so a successful server switch should wipe the cache
(`AppDatabase.clearAllTables()`), not just repoint the API base URL.

**Related pre-existing gap, noted but out of scope here:** `SettingsViewModel.logOut()` ->
`SettingsRepository.clear()` only clears the stored credentials, not the Room cache either - so
disconnecting and connecting to a *different* server today already has this same stale-cache
mixing problem. Not fixed as part of this entry (kept scoped to the specific "change server while
still connected" ask), but the same `clearAllTables()` fix would apply there too if picked up
later.

**How it landed:** `SettingsScreen`'s "NetBox instance" row gets a trailing edit `IconButton` that
opens `EditServerDialog` (an `AlertDialog` with an `OutlinedTextField` pre-filled with the current
URL). Save calls the new `SettingsViewModel.updateBaseUrl(newBaseUrl)`, which saves eagerly (only
way to actually test the new URL, since the dynamic interceptor reads `SettingsRepository`
reactively), calls `DirectoryRepository.refresh()` to validate reachability, and on failure reverts
to the previous `(baseUrl, token)` and surfaces the error via the screen's existing Snackbar - on
success it wipes the cache (`AppDatabase.clearAllTables()`, injected directly since no existing
repository wraps "clear everything") so no stale cross-server data lingers. The dialog itself
dismisses immediately on Save rather than waiting for validation to finish, matching how every
other async action on this screen already surfaces its result via Snackbar, not an inline spinner.

Status: **done**, 2026-07-31. `just build`/`just lint`/`just test` all green on rofl-13; the Mi Pad 4
now also visually verifies the Settings edit affordance and pre-filled server dialog without
changing the configured production server. The save path remains cache-clearing and revert-safe
as documented above.

## NBC-40: fix "edit does not work" - saves sent every field, not just the diff

Editing any object was silently unreliable, and editing a device *type* specifically failed every
time: the save button's PATCH body included every editable field's current value, not just the
ones actually changed - which both cluttered NetBox's change log with untouched fields, and for
device types, outright broke every save (`front_image`/`rear_image` are absolute media URLs NetBox
computes itself; resending one unmodified gets rejected with "The submitted data was not a file",
which failed the *entire* PATCH regardless of what the user meant to change).

**Why:** user report - "edit does not seem to work at all atm?", narrowed down live (with real
device access and log capture) to specifically device-type edits, confirmed via a live PATCH
against netbox.brkn.lol/api/dcim/device-types/244/ returning HTTP 400 with exactly that message.
Same thread, a sharp follow-up catch from the user comparing NetBox's own before/after change-log
diff: "shouldnt our edits also ONLY include the stuff we changed? might be worth-while to compute
the diff and only send that" - the *actual* root cause and the better fix, not just a band-aid for
the one field that happened to break outright.
**How to apply:**
- **Root fix**: `GenericDetailScreen`'s save handler now diffs `editValues` against each field's
  original `EditableField.value` and only includes entries that actually differ in the `edits` map
  passed to `viewModel.save(...)` - untouched fields are never resent, which fixes the device-type
  case too (an untouched `front_image` is no longer part of the PATCH at all) without needing to
  special-case it.
- **Defense in depth**: `buildEditableFields()` also now excludes any field whose value is a media
  URL (reusing `isMediaUrl()`, already used elsewhere for `FieldRow.FileAttachment` detection) -
  belt-and-suspenders in case a future field is ever *actually* edited and diffed as changed.
- **Error visibility**: a save failure previously only showed a `Snackbar`, which the user found
  easy to miss - "there is a toast - behind the keyboard..? I expected something more bold and
  clear." Failures during editing now render as a persistent `errorContainer`-colored banner at
  the top of the edit form instead (survives the keyboard being open, doesn't auto-dismiss), while
  non-editing failures (e.g. a refresh) keep using the Snackbar as before.
- **Success confirmation**: a successful save now also shows a positive `"<item> updated!"`
  Snackbar (reusing the same `refreshedMessage` flow as the NBC-33 manual-refresh confirmation),
  per the user's follow-up request once the fix was confirmed working live.

Status: **done**, 2026-07-31. `just test`/`just lint` green; root cause confirmed live via direct
`curl` reproduction of the 400 against the real instance, and the fix itself confirmed live too -
retried the exact same edit (Mi Pad 4 device type's U Height) on the Zenfone 10 after installing
the fix, and it saved successfully this time ("yes it worked! u height was updated correctly!").

## NBC-41: configurable gestures (two-finger swipe down for global search, etc.)

No gesture shortcuts exist today - navigating to global search or the scanner always requires
going through the sidebar/bottom nav.

**Why:** user request - "gestures! I'd be great to have configurable gestures. For now I primarily
want a way to trigger global search, by swiping down on any screen (with 2 fingers). Kinda like
the HA app does. Other possible action could be a gesture to open the QR code scanner."
**How to apply:** needs a global gesture-detection layer that works across every screen, not just
one - likely wants to live high up the composition (e.g. wrapping `NetBoxNavHost`'s content, or in
`MainActivity`'s root `Surface`) using `Modifier.pointerInput` + `awaitPointerEventScope` to detect
a 2-pointer vertical drag distinct from normal single-finger scrolling within whatever screen is
underneath (needs care not to steal normal scroll gestures - a 2-finger-specific detector should
naturally not conflict with single-finger `LazyColumn` scrolling, but verify in practice). "For now"
and "other possible action" in the request both point at wanting this configurable/extensible from
day one, not just one hardcoded gesture - suggests a small `GestureAction` enum (`GlobalSearch`,
`Scanner`, ...) mapped from a `SettingsRepository`-backed preference, with the two-finger-swipe-down
gesture as the first (and initially only) configurable trigger, rather than hardcoding "swipe down
= search" directly.

Status: **done**, 2026-07-31 - added a Settings-backed gesture action selector (`Off`, `Global
search`, `QR scanner`) and a non-consuming activity-root two-finger swipe-down detector. Remote
`just test`/`just lint`/debug build passed; Mi Pad 4 visually verified the selector and menu. The
gesture detector observes the real pointer stream without stealing one-finger scrolling; physical
multi-touch swipe injection was not available through the adb smoke-test tooling.

## NBC-42: dashboard "Recent changes" should link to the item and show the actual diff

The dashboard's recent-changes list currently shows only a change summary line - it doesn't link
anywhere, and even if it did, opening the item's current detail view wouldn't show what actually
changed (the object may have changed again since, or the field in question isn't rendered at all).

**Why:** user request - "on the home page the recent change entries should indeed allow us to open
the item view page directly - but we should also have a way to dispaly the diff ie the change
itself! (that's gotta be a separate view)."
**How to apply:** two distinct pieces:
- Tapping a recent-change entry should navigate to that object's existing generic detail screen
  (`Route.Generic`), same as any other reference elsewhere in the app - the changelog entry already
  carries the object's `changed_object_type`/`changed_object_id` (or an embedded `url`), which is
  what `NetBoxRef.endpointFromDetailUrl()` elsewhere in the codebase already turns into a route.
- A *separate* diff view is needed for the change itself: NetBox's changelog API
  (`/api/core/object-changes/{id}/`) returns `prechange_data`/`postchange_data` JSON snapshots -
  this needs a new screen that fetches that single change-log entry and renders a field-by-field
  before/after diff (this is exactly the kind of before/after comparison the user pasted earlier
  in the NBC-40 discussion when pointing out the edit form was resending unchanged fields - a
  generic "diff two JsonObjects, list keys that differ" helper would serve both that intuition and
  this view). Reachable from a distinct affordance on each recent-change row (e.g. a trailing "view
  diff" icon button) separate from the row tap itself, per "that's gotta be a separate view."

Implemented: the row tap already navigated to `Route.Generic` from NBC-9 - only the diff view was
actually missing. Added `Route.ObjectChangeDiff(changeId)`, `DashboardRepository.fetchObjectChange`
(uncached, fetched on demand only when the diff view is opened - unlike the rest of this
repository, the full pre/post snapshots aren't worth carrying in the offline cache for every
changelog row), `ObjectChangeDiffViewModel`/`ObjectChangeDiffScreen` (union of `prechange_data`/
`postchange_data` keys, one `DiffRow` per key whose value actually differs - nested objects/arrays
fall back to raw JSON, no schema to render them more richly here), and a trailing "view diff"
`IconButton` (`Icons.Default.Difference`) on each `ChangeRow`, distinct from the row's own tap
target. Diff-building logic covered by `ObjectChangeDiffTest` (create/delete/update/nested-object/
no-op cases).

Status: **done**, 2026-07-31. `just test`/`just lint` green (including a rerun-tasks ktfmt check to
rule out a stale cache hit).

## NBC-43: shorten displayed URL values

Absolute URLs in generic object fields repeat the configured scheme and host, making otherwise
useful paths hard to scan (for example, display `https://netbox.brkn.lol/dcim/device-types/244/`
as `/dcim/device-types/244/`). Keep the full URL for opening/sharing; shorten only its visible
label.

- [x] Shorten absolute URL text in generic external-link rows while preserving path, query, and
  fragment components.
- [x] Add regression coverage for the requested NetBox URL shape and malformed/non-URL fallback.

Status: **done**, 2026-07-31 - visible URL shortening is covered by `GenericFieldRendererTest`;
the original URL remains the click target.

## NBC-44: replace the bottom Devices tab with Search

The fixed bottom navigation should prioritize the app's most useful universal actions: `Home | SCAN
| SEARCH`. Device browsing remains available from the drawer and dashboard stat cards, while
the bottom bar should no longer duplicate that entry point.

- [x] Replace the Devices tab with Search and use the SCAN label for the scanner destination.
- [x] Keep the three destinations reachable from dashboard, list, search, and scanner screens.

Status: **done**, 2026-07-31 - `just lint`, `just test`, and `just build debug` passed remotely;
the three-tab layout was smoke-tested on the Mi Pad 4.

## NBC-45: make the global search landing page useful before typing

Opening global search currently presents an empty state until the user enters a query. Show the
most recently visited devices and NetBox pages by default, using the local cache so the screen is
useful offline as well.

- [x] Persist a small, bounded recent-visit history for typed and generic detail pages.
- [x] Render the recent pages before a query and improve the blank/no-match presentation.

Status: **done**, 2026-07-31 - cache-backed recent visits and empty states are covered by repository
tests; the remote test/lint/build checks passed.

## NBC-46: switch scanner lenses and choose a default lens

The QR scanner always opens the back camera and offers no way to switch to another available lens.
Add an in-scanner switch and a Settings preference for the default lens, with a safe fallback on
devices that expose only one camera.

- [x] Discover available CameraX lenses and show the switch only when at least two are available.
- [x] Persist the default front/back preference and fall back to an available lens if needed.
- [x] Add focused preference/selection coverage and validate on the available devices.

Status: **done**, 2026-07-31 - preference tests passed; the Mi Pad 4 opened the scanner,
exposed `Switch camera`, and switched lenses without camera errors.

## NBC-47: share/import complete connection setup QR codes

The setup QR code must represent a complete NetBox and Chill connection, not a token-only export.
It should contain the server URL and API token, be generated from Settings behind device auth, and
be scannable directly from the login screen on another device.

- [x] Make the Settings action and warning explicitly describe a complete connection setup code.
- [x] Add a login-screen action to scan a setup code and prefill both required fields.
- [x] Keep the versioned payload format and round-trip coverage for server URL plus token.

Status: **done**, 2026-07-31 - codec tests passed; a valid setup deep link on the Mi Pad 4 opened
onboarding with both fields populated. Settings export remains device-auth protected.

## NBC-48: select rear scanner lenses and move camera controls

The scanner's front/rear toggle is useful, but phones with a logical rear multi-camera should also
be able to select physical rear lenses such as ultrawide or macro. Put the flashlight and camera
controls at the bottom of the preview, with a compact rear-lens selector above them.

- [x] Discover and bind available physical rear cameras while retaining front/rear fallback.
- [x] Show a compact rear-lens selector only when multiple rear lenses are available.
- [x] Move flashlight and front/rear controls into a bottom scanner control strip.

Status: **done**, 2026-07-31 - remote tests and lint passed; scanner smoke-tested on the Mi Pad 4
with front/rear switching and the available rear-lens fallback.

## NBC-49: mirror NetBox sidebar grouping and support custom ordering

The directory currently follows API/alphabetical order, while NetBox's web UI presents familiar
app groups and model types in a deliberate order. Match that order by default and let the user
reorder groups and entries locally without changing the server.

- [x] Apply NetBox-style default group and model ordering, including unknown plugin items.
- [x] Add persisted sidebar group and item ordering controls.
- [x] Keep search, pinning, and newly discovered models compatible with custom ordering.

Status: **done**, 2026-07-31 - ordering tests passed and the sidebar changes remain local-only.

## NBC-50: add a global search card to the Home page

The Home page should offer global search directly below the statistics cards, in addition to the
bottom navigation destination.

- [x] Add an attractive search card below Stats that opens global search.

Status: **done**, 2026-07-31 - remote compile and UI validation passed.

## NBC-51: add an explicit offline mode

Provide a persisted offline-mode switch in Settings and as a quick-access sidebar control. While
enabled, the app must use cached data only and show a clear Dashboard banner.

- [x] Persist the offline-mode preference and prevent API requests while it is enabled.
- [x] Add Settings and sidebar controls plus a Dashboard status banner.
- [x] Keep cached/offline flows usable while refreshes are skipped.

Status: **done**, 2026-07-31 - remote tests passed; Settings, Sidebar, and Dashboard now expose the
mode and both API clients honor it.

## NBC-52: render creator names in generic detail fields

Generic object details currently fall back to a numeric user ID for `Created by` when NetBox's
nested user representation is not recognized. Prefer the user's display name, username, or name
fields while retaining an ID fallback when no identity is available.

- [x] Render creator identity fields instead of only the numeric ID.
- [x] Add regression coverage for NetBox user object shapes and the ID fallback.

Status: **done**, 2026-07-31 - creator-shape and fallback tests passed.

## NBC-53: make complete offline caching visible and reliable

Settings previously reported only typed devices, even though generic objects and media have separate
cache paths. Asset persistence was also opt-in for the next sync, which made enabling it look like
it did nothing. Report the complete cache and trigger durable asset sync when the option is enabled;
keep device-type photos, image attachments, and documents available from local files.

- [x] Report generic objects and cached media alongside typed devices.
- [x] Start a full sync when durable asset caching is enabled.
- [x] Keep documents, front/rear images, and image attachments as best-effort local copies.

Status: **done**, 2026-07-31 - cache/sync code compiled and remote tests passed; no production data
was modified.

## NBC-54: show current cache size in Settings

Settings should show how much local storage the offline cache and durable attachments consume, not
just object counts.

- [x] Calculate and display the current cache size.

Status: **done**, 2026-07-31 - persistent file counts and byte totals are displayed in Settings and
covered by the remote build.

## NBC-55: open generic image fields in the image viewer

The shared image viewer works for typed device photos and image attachments, but generic detail
fields such as device-type front/rear images previously rendered as non-clickable thumbnails.

- [x] Make generic image-field thumbnails open the existing full-screen image viewer.

Status: **done**, 2026-07-31 - generic detail image rows now use the shared viewer path.

## NBC-56: support Markdown custom fields and NetBox field grouping

Custom fields such as purchase information should respect their NetBox-defined type, category, and
weight instead of rendering as an ungrouped alphabetical blob.

- [x] Cache custom-field labels, types, groups, and weights.
- [x] Render Markdown custom fields with the Markdown card renderer.
- [x] Group and order custom-field rows by category and weight.

Status: **done**, 2026-07-31 - renderer and metadata ordering tests passed.

## NBC-57: restore device-detail type photos

Device detail pages should keep showing the associated device type's front and rear images even
when the typed device cache already contains an older device-type record.

- [x] Refresh the device type photo metadata when opening a connected device detail page.
- [x] Preserve the cached/offline fallback and image viewer behavior.
- [x] Re-run the metadata refresh when a stale cached device row gains its device-type ID.

Status: **done**, 2026-07-31 - the detail flow now reacts to the refreshed device-type ID; Mi Pad 4
showed the live front photo for device 87 with no fatal exceptions.

## NBC-58: configurable hidden fields and item overflow actions

Allow users to keep noisy fields out of object detail pages by default, while retaining an explicit
way to reveal them temporarily. Field keys use a stable `object/field` shape such as
`device/model`.

- [x] Persist and manage a user-configurable hidden-field list in Settings.
- [x] Hide matching fields on typed and generic detail pages, with an overflow action to show them
  temporarily.
- [x] Add long-press field actions for edit/hide and move secondary item actions into overflow menus.
- [x] Cover hidden-field key normalization and object/field mapping with unit tests.

Status: **done**, 2026-07-31 - remote unit tests, lint, debug build, compile-time App Link host
override, all-device deployment, and Mi Pad 4 launch/log smoke verification passed.

## NBC-59: show rack front/rear elevations

Rack detail pages should mirror NetBox's front and rear elevation views, including clickable
device entries for occupied rack units.

- [x] Fetch and cache front/rear rack elevation slots without blocking cached rack details.
- [x] Render front and rear unit overviews with occupied devices as navigable entries.
- [x] Cover elevation payload parsing and keep the overview usable offline.

Status: **done**, 2026-07-31 - elevation parser tests, remote unit tests/lint/debug build, and
Mi Pad 4 launch/UI/log smoke verification passed; Zenfone install passed.

## NBC-60: browse related items from count fields

Related-item counts on generic detail pages should open a bottom sheet with the actual cached
objects, optional preview images, and direct navigation to each item.

- [x] Replace count-only navigation with a cache-first related-items bottom sheet.
- [x] Reuse available object/device-type preview images and keep every item clickable.
- [x] Add relation targets for racks and device types and cover them with tests.

Status: **done**, 2026-07-31 - relation-target tests, remote unit tests/lint/debug build, and Mi
Pad 4 launch/UI/log smoke verification passed; Zenfone install passed.

## NBC-61: improve rack elevation visual blocks

Rack elevations should resemble NetBox's visual rack view more closely: show device-type images,
merge each device's occupied half-U slots into one block, and give adjacent devices distinct
colors so their boundaries are immediately clear.

- [x] Show cached device-type front/rear previews in rack device blocks.
- [x] Merge contiguous half-U rows per device without artificial gaps.
- [x] Assign stable distinct colors to device blocks and keep every block clickable.

Status: **done**, 2026-07-31 - remote tests/lint/debug build, all available-device deployment, and
Mi Pad 4 rack UI screenshot/UI dump verified merged colored blocks, previews, and clickable entries.

## NBC-62: configurable Brother label inversion and clipping fix

Printed labels work, but the raster needs inverted default colors with an explicit opt-out and
better bounds handling so long labels or edge pixels are not clipped.

- [x] Invert raster colors by default and expose a per-print opt-out.
- [x] Fit label text to the available print area and preserve safe edge padding.
- [x] Add renderer coverage for inversion semantics.

Status: mostly done, 2026-07-31 - remote tests/lint/debug build passed and the feature was deployed;
the inverted default still needs a physical follow-up print.

## NBC-64: reorganize Settings and explain cached file types

The Settings screen should be easier to scan, with titled groups and subtitles. The storage
setting should also explain what “durable” files are, including whether that means NetBox media,
documents, or other downloaded assets.

- [x] Group related settings under titled sections with concise subtitles.
- [x] Replace “durable” jargon with a plain-language explanation and accurate asset/document scope.

Status: **done**, 2026-08-01 - Settings is grouped into connection, cache, display, scanner/
gesture, actions, and about sections; cache storage now explains that downloaded NetBox images and
documents are kept in app storage for offline use rather than temporary Android cache storage.

## NBC-65: make generic synchronization resilient and observable

Some NetBox API collections are operational summaries rather than inventory objects and do not
have numeric IDs (for example `core/background-queues`). Generic synchronization must not abort
the complete offline cache for those responses, and real failures/warnings must remain visible.

- [x] Skip malformed/non-object collection rows without aborting unrelated cache sync.
- [x] Persist the latest sync failure or partial-sync warning across app restarts.
- [x] Show sync issues with a retry action on Dashboard and Settings.
- [x] Show an ongoing system notification while background/manual sync is running.
- [x] Verify a real sync against the configured NetBox instance and physical test devices.

Status: **done**, 2026-08-01 - Mi Pad 4 completed a real full sync (383 devices, 630 durable
attachments) with WorkManager SUCCESS and no persisted sync issue; the final build was deployed
to all three physical test devices.

## NBC-72: keep router actions out of the offline sync model list

NetBox's API root also exposes action/export routes such as `connected-device`, script upload, and
plugin XML export. They are not paginated object collections and currently create noisy sync
failures on Mi Pad 4.

- [x] Validate discovered routes as paginated JSON collections before caching them as models.
- [x] Keep action/export routes out of the sidebar and generic sync loop.
- [x] Verify a retry on Mi Pad 4 completes without the known false-positive route errors.

Status: **done**, 2026-08-01 - route probes now exclude action/export endpoints and ID-less
operational summaries; Mi Pad 4's retry cleared the persisted issue and reported WorkManager
SUCCESS without the three original route errors.

## NBC-66: make QR scanner lens switching reliable

The scanner's front/rear and rear-lens controls must rebind CameraX to the selected camera
immediately, including on devices exposing physical rear cameras through a logical camera.

- [x] Rebind the preview and analyzer when the selected facing or rear lens changes.
- [x] Keep tap-to-focus and torch state correct after a camera switch.
- [x] Verify the switch on a multi-camera device and a device with fewer than two rear lenses.

Status: **done**, 2026-08-01 - PX5 camera-service inspection confirmed 0.6× selects physical
sensor 3 with a wider preview and 1× returns to sensor 2; the single-rear-lens fallback was
deployed to and smoke-tested on Mi Pad 4.

## NBC-71: force the selected physical rear camera

On logical multi-camera devices, selecting a rear-lens chip must bind the selected physical camera
stream rather than merely changing UI state or requesting an unsupported logical zoom ratio.

- [x] Bind physical rear-camera options with CameraX's physical-camera selector support.
- [ ] Verify the active physical camera ID and visible field of view on Pixel 5 and Zenfone 10.
- [x] Keep the fallback safe on devices exposing only one rear lens.

Status: in progress, 2026-08-01 - PX5 physical ID and field-of-view verification passed; Zenfone
10 still needs the same physical-lens check.

## NBC-67: discover and pair nearby Brother label printers

The print-label dialog should show nearby Brother/P-touch devices, not only already-bonded devices,
and provide the Android pairing flow for a discovered printer such as `PT-P300BT4590`.

- [x] Discover nearby Brother/P-touch Bluetooth devices from the print dialog.
- [x] Offer Android's pairing flow and refresh the selectable printer after bonding.
- [ ] Keep printing restricted to bonded devices and verify with the PT-P300BT4590.

Status: mostly done, 2026-08-01 - Mi Pad 4 discovered the live `PT-P300BT4590`; Android pairing
flow and post-bond selection refresh are implemented, while physical print verification remains.

## NBC-68: improve label layout and print-dialog feedback

The print dialog and Brother label raster need a steadier discovery experience and better label
layout controls.

- [x] Stabilize the nearby-printer progress indicator and refresh after Bluetooth is enabled.
- [x] Clarify the black-tape inversion text.
- [x] Add vertical label-text mode.
- [x] Fix right-side label text raster legibility.

Status: mostly done, 2026-08-01 - remote lint/tests/debug build and all-device deployment passed;
physical verification of vertical and long-text labels remains.

## NBC-69: add pull-to-refresh to item views

Device and other item detail/list views should support an explicit pull-to-refresh gesture, with
the refresh action also available from the overflow menu.

- [x] Add pull-to-refresh to device and generic item views.
- [x] Add a refresh entry to the relevant overflow menus.
- [x] Keep refresh cache-first/offline-safe and show the existing refresh/sync feedback.

Status: **done**, 2026-08-01 - detail-page implementation passed remote lint/tests/build and was
installed on Mi Pad 4 and Pixel 5; the Mi Pad 4 detail screenshot verified the overflow Refresh
entry, and the pull-to-refresh path uses the same cache-first refresh action.

## NBC-70: add digital zoom to the scanner

The QR scanner should support digital zoom, including pinch-to-zoom gestures where the device
supports them.

- [x] Add pinch-to-zoom to the camera preview.
- [x] Preserve the selected rear lens and zoom when switching front/rear cameras.
- [x] Keep zoom controls usable on devices without multiple rear lenses.

Status: mostly done, 2026-08-01 - pinch zoom and cross-camera clamping are implemented; remote
lint/tests/debug build passed and the build was installed on Mi Pad 4. Physical pinch/lens testing
and a PX5 retry remain (PX5 went offline during installation).

## NBC-73: reorder bottom navigation and add a Settings shortcut

The fixed bottom navigation should prioritize the universal actions in this order: `Home | Search |
Scan | Settings`.

- [x] Swap Search and Scan in the bottom navigation.
- [x] Add Settings as the final bottom-navigation destination.
- [x] Verify navigation on a physical device.

Status: **done**, 2026-08-01 - implemented across dashboard, list, search, and scanner screens;
remote lint/tests/build passed, and the Mi Pad 4 screenshot verified the rendered `Home | Search |
Scan | Settings` order and active Home tab.

## NBC-74: make complete offline attachment sync reliable

The full offline sync must retain every discovered NetBox model and surface missing durable files,
including plugin documents, image attachments, and device-type front/rear images.

- [x] Preserve the previous complete model directory when discovery is partially unavailable.
- [x] Continue syncing other attachments when one device refresh or download fails.
- [x] Persist attachment failures as visible sync issues instead of logging them only.
- [x] Verify cached document/image model counts and durable files against the live NetBox instance.

Status: **done**, 2026-08-01 - Mi Pad 4 contains 171 document records, 106 image-attachment
records, 238 device types, and 630 durable attachment files (811.0 MiB shown in Settings); remote
ktfmt/tests passed and a fresh full sync completed with 630 durable attachments and no sync issue.

## NBC-75: run NetBox sync entirely in background

The full cache refresh must run through WorkManager instead of blocking the foreground UI, with a
real Android foreground-service notification while the long-running sync and attachment pass are
active.

- [x] Move manual/settings/dashboard/list full-sync triggers to WorkManager.
- [x] Add a startup one-time sync alongside the periodic sync schedule.
- [x] Promote the worker with a `Syncing NetBox data…` foreground notification.
- [x] Keep dashboard cache refreshes inside the worker and preserve visible sync status/errors.
- [x] Verify on Mi Pad 4 with WorkManager and notification evidence.

Status: **done**, 2026-08-01 - remote ktfmt/tests passed; debug APK deployed to Zenfone 10, Mi Pad
4, and PX5. Mi Pad WorkManager evidence showed `SystemForegroundService` with an ongoing data-sync
notification (`foregroundId=1001`, `types=0x00000001`), and the worker completed with `SUCCESS` and
`Synced 630 durable attachments`.

## NBC-76: create NetBox items from the app

Add creation flows for all supported NetBox object types, starting with the typed device and device
type screens and extending the generic model screens to every endpoint that exposes writable fields.

- [x] Add a reusable create form driven by NetBox field metadata/options.
- [x] Support device and device-type creation with validation and references.
- [x] Support generic creation for circuits and all other writable model endpoints.
- [x] Cache newly created objects immediately and enqueue background sync afterward.
- [x] Verify offline-safe error handling and creation form behavior on a physical device.

Status: mostly done, 2026-08-01 - metadata-driven generic creation, typed device/device-type
fallback fields, validation, reference pickers, cache updates, and background refresh are
implemented and covered by remote tests/lint/build. The Mi Pad 4 displayed the live device form
and its offline-safe fallback; no production object was created during verification.

## NBC-77: hide empty related-item count rows

Item detail pages should show related-object count rows only when the count is greater than zero,
so empty relationships such as front-port templates do not add visual noise.

- [x] Filter zero-count related rows from item views.
- [x] Keep the bottom-sheet/detail navigation for positive counts unchanged.
- [x] Verify across device, rack, and generic item pages.

Status: **done**, 2026-08-01 - duplicate backlog wording for NBC-97; the existing generic
renderer hides zero counts, preserves positive-count navigation, and has focused coverage.

## NBC-78: consolidate offline-mode sync status

When offline mode is enabled, replace repeated per-item sync status messages with one compact
dashboard status showing that offline mode is enabled and when the last successful sync completed.

- [x] Show one `Offline mode enabled. Last sync: …` status message.
- [x] Remove repeated offline sync messages from individual item rows.
- [x] Use a friendly fallback when no successful sync has happened yet.

Status: **done**, 2026-08-01 - the dashboard now shows one compact offline status card using a
persisted successful-sync timestamp, with a clear “not completed yet” fallback; individual rows
do not repeat the offline message. Remote tests/lint/build passed and the deployed Mi Pad 4
dashboard showed the cache-first layout.

## NBC-79: group sync controls in Settings

Settings should have one dedicated Sync section containing the cache summary, sync issue/retry
surface, attachment and offline switches, and the Sync now action.

- [x] Move all sync-related controls under a dedicated Sync section.
- [x] Keep Disconnect separate under Actions.
- [x] Verify the grouped layout on a physical device.

Status: **done**, 2026-08-01 - remote ktfmt/tests passed; deployed with the next debug build to all
three devices, and the Mi Pad 4 Settings screen was inspected after installation.

## NBC-80: show Hidden fields completion state

The Hidden fields setting should make it immediately clear whether any fields are configured,
instead of showing only an opaque list of preference keys.

- [x] Show a clear configured/empty completion state.
- [x] Keep the configured field summary understandable.
- [x] Verify the Settings row on a physical device.

Status: **done**, 2026-08-01 - remote ktfmt/tests passed; the row now shows an explicit empty or
configured count state with a completion icon, and it was inspected on the Mi Pad 4.

## NBC-81: edit links between NetBox items

Item pages should allow changing writable relationships, such as moving a device to another rack or
changing its device type, while preserving the cache-first and offline-safe behavior.

- [x] Add edit actions for device relationships such as rack and device type.
- [x] Extend relationship editing to other supported writable item types.
- [x] Validate choices and refresh the updated item and related caches after saving.
- [x] Verify edits, errors, and offline behavior on a physical device.

Status: **done**, 2026-08-01 - existing generic edit flow was verified on Mi Pad 4: a device's
Edit form exposes Device Type and Rack reference pickers, and saves use the durable pending-edit
outbox with conflict handling and background cache refresh.

## NBC-82: tab device detail sections

The device view should organize secondary sections into tabs, including interfaces, power ports,
rear ports, and other related device components.

- [x] Add tabs for the device's secondary sections and related objects.
- [x] Keep counts, previews, and existing navigation available within the relevant tab.
- [x] Preserve hidden-field handling and cache-first refresh behavior across tabs.
- [x] Verify the tab layout and navigation on a physical device.

Status: **done**, 2026-08-01 - remote lint/tests passed; deployed to Zenfone 10, Mi Pad 4, and
PX5. Mi Pad 4 showed the tab strip, populated Interfaces from cache, and showed the friendly
cache-empty state for Rear ports.

## NBC-83: expand global search matching

Global search should match identifiers beyond names, including IP addresses and MAC addresses. A
device-type match should also surface the devices using that type.

- [x] Match IP addresses and MAC addresses across cached searchable objects.
- [x] Expand device-type matches with the devices assigned to each matching type.
- [x] Deduplicate and label recursive results clearly while preserving cache-first behavior.
- [x] Verify the expanded result set and offline behavior on a physical device.

Status: **done**, 2026-08-01 - remote lint/tests passed; installed on Zenfone 10, Mi Pad 4, and
PX5. Mi Pad 4 search for `10.5.0.5` returned both the cached IP row and matching device result;
MAC matching uses the cached raw JSON path and device-type matches expand through the typed cache.

## NBC-84: reduce app icon and splash artwork scale

The app artwork is slightly oversized, causing parts of the icon to be clipped in the launcher icon
and splash screen.

- [x] Reduce the artwork scale while preserving the existing icon and splash assets.
- [x] Verify the launcher icon and splash screen on a physical device.

Status: **done**, 2026-08-01 - reduced the adaptive foreground artwork to 90% around its center;
the resource compiled successfully and the APK was installed on Zenfone 10, Mi Pad 4, and PX5.

## NBC-85: hide pull-to-refresh spinner during sync

Background sync already has its own app-wide progress bar and Android notification. The large round
pull-to-refresh indicator should not appear while that sync is running.

- [x] Keep pull-to-refresh gestures active without showing the round refresh indicator.
- [x] Apply the behavior consistently to dashboard, lists, and detail pages.
- [x] Verify the UI while a real background sync is active on a physical device.

Status: **done**, 2026-08-01 - remote ktfmt/tests passed; pull-to-refresh gestures remain active
but their round indicator is suppressed during sync, and the change was deployed to all three
devices.

## NBC-86: make device-type images persist on device pages

Device detail pages must reliably show the cached device-type front and rear images whenever the
device type provides them. The image rows have regressed repeatedly and need a durable load path.

- [x] Ensure the device type is refreshed/backfilled before rendering its images.
- [x] Preserve and render both front and rear image URLs from the device-type cache.
- [x] Verify the images after app restart, sync, and deployment on a physical device.

Status: **done**, 2026-08-01 - full sync now refreshes device-type metadata independently of the
optional attachment download setting; after restart, Mi Pad 4 showed both front and rear images.

## NBC-87: remove the header sync animation

The thin animated sync indicator above the screen header is distracting while background sync is
running. Sync progress should remain available through the Android notification and Settings.

- [x] Remove the animated indicator above the navigation content.
- [x] Keep the Android sync notification and Settings sync status available.
- [x] Verify headers remain stable during sync on a physical device.

Status: **done**, 2026-08-01 - removed the app-wide `SyncStatusIndicator` host; the Mi Pad 4
device page remained stable during sync and the Android notification remained active.

## NBC-88: show the current sync stage in the notification

The ongoing Android sync notification should tell the user which part of the cache refresh is
currently running, rather than appearing as a generic “Syncing NetBox data…” message.

- [x] Keep the current sync stage visible when the notification is collapsed.
- [x] Show the same stage in the expanded notification.
- [x] Verify the notification while a real sync runs on a physical device.

Status: **done**, 2026-08-01 - the current stage is now the visible notification title and the
expanded notification includes the same stage text; Mi Pad 4 showed “Syncing devices…”.

## NBC-89: show estimated sync progress

The sync notification should show a useful approximate progress position in addition to the
current stage, even though the exact amount of work varies with the NetBox model inventory.

- [x] Emit numbered sync stages from the complete cache refresh.
- [x] Show a determinate estimated progress bar and step count in the notification.
- [x] Recalculate the estimate after the available NetBox models are discovered.
- [x] Verify progress updates during a real sync on a physical device.

Status: **done**, 2026-08-01 - sync stages now carry a dynamically estimated total that includes
discovered models; Mi Pad 4 reported “Step 4 of 8” with a determinate progress bar.

## NBC-90: show device-type images in the device-type list

The device-type list should use each type's cached front image as its row thumbnail, falling back
to the normal object-type icon when no image is available.

- [x] Render cached front images in device-type list rows.
- [x] Keep the generic icon as the null/blank-image fallback.
- [x] Verify the list works offline with cached and uncached images.

Status: **done**, 2026-08-01 - device-type rows now use cached front images with the existing
object-type icon as fallback; Mi Pad 4 showed the imagery after deployment.

## NBC-91: show device imagery in global search

Global search results for devices and device types should use the relevant cached front image, with
the existing generic icon retained as a fallback.

- [x] Show device-type front images for device-type search hits.
- [x] Show the assigned device-type front image for device search hits.
- [x] Preserve recent-result and offline behavior with icon fallbacks.
- [x] Verify imagery in global search on a physical device.

Status: **done**, 2026-08-01 - search resolves device/device-type thumbnails from the typed Room
cache and falls back to namespace icons; Mi Pad 4 showed both device and device-type results with
images.

## NBC-92: show imagery on dashboard object rows

Dashboard bookmarks and recent-change rows should use the same device/device-type front thumbnails
as lists and global search whenever their target is a device or device type.

- [x] Show front images for device and device-type bookmarks.
- [x] Show front images for device and device-type recent changes.
- [x] Keep namespace icons as the fallback for missing images and other object types.
- [x] Verify the dashboard rows on a physical device.

Status: **done**, 2026-08-01 - dashboard bookmarks and recent changes now resolve the same typed
front thumbnails with icon fallback; Mi Pad 4 showed device images in Bookmarks.

## NBC-93: keep row thumbnail slots a constant width

Rows that can show images should reserve the same leading width for placeholder icons, so Home and
other mixed image/icon lists do not shift their text horizontally between items.

- [x] Give dashboard image/icon rows a fixed leading slot.
- [x] Apply the same alignment to global search and generic image-capable rows.
- [x] Verify mixed photo and placeholder rows on a physical device.

Status: **done**, 2026-08-01 - dashboard, search, and generic rows reserve a fixed leading slot;
Mi Pad 4 verified mixed image and placeholder rows.

## NBC-94: scan asset-tag QR codes and barcodes

The scanner should resolve plain asset-tag values in QR codes and barcodes, in addition to NetBox
URLs and bare numeric device IDs.

- [x] Recognize common plain asset-tag barcode/QR payloads without changing URL parsing.
- [x] Resolve asset tags from the offline device cache first, then refresh NetBox best-effort.
- [x] Show a useful not-found state when a valid asset tag has no matching device.
- [x] Verify an asset-tag scan path with parser tests and a physical device build.

Status: **done**, 2026-08-01 - parser and asset-tag lookup tests pass; the scanner build was
deployed with the cache-first/API fallback path.

## NBC-95: add the Journal tab to device pages

Device pages should have a web-like tabbed interface with a Journal tab that is always visible,
alongside the existing interfaces, ports, and module sections.

- [x] Always show a Journal tab on device pages.
- [x] Load and render device journal entries in that tab.
- [x] Keep the existing related-device tabs and cache-first detail behavior intact.
- [x] Verify the tab on a physical device, including an empty journal.

Status: **done**, 2026-08-01 - Journal is the always-visible second device tab and renders the
existing journal cards; Mi Pad 4 verified the tab and empty state.

## NBC-96: hide the display URL metadata field

Generic item detail pages should omit NetBox's redundant `display_url` metadata field.

- [x] Exclude `display_url` from rendered generic fields.
- [x] Keep useful web/share actions available in the overflow menu.
- [x] Verify generic detail pages no longer show the field.

Status: **done**, 2026-08-01 - generic rendering now omits `display_url` while leaving detail
actions available; renderer tests and the deployed build verified the change.

## NBC-97: hide empty related-count rows

Item detail pages should omit reverse-relation count rows when their count is zero, so the page
only advertises relationships that actually contain items.

- [x] Hide zero-valued related-count fields in generic detail pages.
- [x] Keep positive counts clickable and unchanged.
- [x] Verify device-type, rack, and site detail pages.

Status: **done**, 2026-08-01 - recognized zero counts are omitted while positive counts retain
their click targets; renderer tests cover both paths.

## NBC-98: make item view pages more visually appealing

Refresh the item detail presentation so device, device type, rack, and other object pages feel
more like a modern inventory app while keeping the information-dense NetBox data easy to scan.

- [x] Establish a stronger visual hierarchy for the title, identity, status, and metadata.
- [x] Improve section/card treatment for fields, markdown, images, and related-item counts.
- [x] Keep actions, tabs, offline rendering, and accessibility intact.
- [x] Verify the refreshed detail pages on a physical device across representative object types.

Status: **done**, 2026-08-01 - typed and generic detail headers and field rows now use elevated
identity/field cards with category icons, IDs, device-type/status context, and stable tabs. Mi Pad 4
verified the typed device and generic device-type presentations, including the richer field cards.

## NBC-99: localize timestamps and dates

Render NetBox timestamps and date/time values in the device's local timezone and locale instead of
showing raw UTC/API strings, while preserving enough context for unambiguous dates.

- [x] Identify all timestamp/date renderers, including item fields, journal, history, and sync UI.
- [x] Format instant timestamps using the device timezone and locale.
- [x] Keep date-only values date-only and avoid shifting them across timezone boundaries.
- [x] Add formatter tests for timezone conversion and representative NetBox values.
- [x] Verify the result on a physical device.

Status: **done**, 2026-08-01 - shared locale/timezone formatting now covers item metadata,
journal, history, dashboard, and sync timestamps while preserving date-only values. Formatter
tests passed remotely and the Mi Pad 4 dashboard/detail screens showed localized values.

## NBC-100: remove the duplicate device status badge

The typed device page currently shows status in both the identity header and the Overview tab.
Keep the prominent header badge and remove the duplicate row-level badge.

- [x] Remove the duplicate status badge from the Overview tab.
- [x] Keep status visible in the identity header and preserve hidden-field behavior.
- [x] Verify the device page on a physical device.

Status: **done**, 2026-08-01 - removed the Overview duplicate while retaining the identity-card
status badge and hidden-field logic; the deployed Mi Pad 4 device page visibly shows one status.

## NBC-101: add icons and counts to device detail tabs

Device secondary tabs should be easier to scan and should advertise the number of cached related
objects, for example `Interfaces (1)`, while keeping Journal visible even when empty.

- [x] Add a leading icon to each device detail tab.
- [x] Show cached related-object counts in tab labels.
- [x] Keep empty tabs visible and verify the result on a physical device.

Status: **done**, 2026-08-01 - device tabs now render icons and cached counts such as `Journal
(0)` and `Interfaces (25)` while empty tabs remain visible; Mi Pad 4 verified the deployed UI.

## NBC-102: repair text rendering on printed labels

The QR portion of labels is usable, but the text block beside it can be garbled or hard to read.
The Android raster path should match printlabel's crisp 1-bit preprocessing and orientation.

- [x] Make the label text raster crisp and legible on the P-touch head.
- [x] Keep text orientation, inversion, and QR output correct in horizontal and vertical modes.
- [ ] Verify a physical label when the printer is reachable.

Status: mostly done, 2026-08-01 - compared against the upstream printlabel raster path and removed
filtered bitmap interpolation, switched to crisp bold 1-bit text, and matched its exact rotate/
mirror orientation. Remote tests/lint/build passed and all three devices were deployed; physical
printing remains open because the paired printer did not accept the test connection.

## NBC-103: make sync notifications unobtrusive

The long-running sync notification should not make sound or vibration, and should be hidden while
the app is visibly in the foreground when Android permits that lifecycle-aware behavior.

- [x] Make the sync notification silent.
- [x] Suppress or remove it while the app is in the foreground, restoring it when backgrounded.
- [x] Keep sync progress available in-app and verify notification behavior on the Mi Pad 4.

Status: **done**, 2026-08-01 - the sync channel is low-importance/silent, foreground syncs have no
active notification, and the Mi Pad 4 dumpsys verification confirmed the channel configuration.

## NBC-104: regularly sync and resume after connectivity returns

Offline cache refreshes should run on a regular schedule and retry automatically when a queued
sync regains its network constraint, such as after connectivity is restored.

- [x] Run a persisted periodic sync on a reasonable interval.
- [x] Queue startup/manual syncs with a connectivity constraint so they resume after reconnect.
- [x] Verify the scheduling/constraint behavior and document it in the sync backlog.

Status: **done**, 2026-08-01 - WorkManager keeps a six-hour periodic job plus startup/manual
one-time work, all constrained to CONNECTED; retries use WorkManager backoff.

## NBC-105: show interface IP and MAC addresses

The device detail page's Interfaces tab should show the interface's configured IP addresses and
MAC address in the row subtitle when NetBox provides them.

- [x] Extract IP and MAC values from cached interface JSON.
- [x] Display them as a readable interface-list subtitle without changing offline behavior.
- [x] Verify the populated subtitle on the Mi Pad 4 using a device with assigned addresses.

Status: **done**, 2026-08-01 - Mi Pad 4 device 18's `wlan0` row rendered the cached IP and MAC
subtitle.

## NBC-106: align device overview field actions

The copy-to-clipboard and linked-reference actions on the device Overview tab should share stable
trailing slots so their icons line up across fields.

- [x] Give copy and link actions equal-sized trailing slots.
- [x] Preserve the existing copy, navigation, and long-press behavior.
- [x] Verify the alignment on the Mi Pad 4.

Status: **done**, 2026-08-01 - Mi Pad 4 UI inspection confirmed matching trailing action slots.

## NBC-107: provide an add-item entry point

Devices and generic NetBox object lists should expose a clear way to create an item through the
metadata-driven creation form, including a global action from the main bottom navigation.

- [x] Expose a create action on the typed Devices list.
- [x] Expose a create action on generic object lists for all discovered models.
- [x] Add a global bottom-navigation picker for any discovered object type.
- [x] Verify the entry points and form without mutating the production NetBox instance.

Status: **done**, 2026-08-01 - Mi Pad 4 opened the global Add picker and a typed device form with
no production submission.

## NBC-108: make custom fields first-class in create and edit forms

Custom fields should render as individual controls based on their cached NetBox definitions,
including choices and Markdown-capable long text, rather than appearing as one raw JSON object.

- [x] Split applicable custom fields into per-field create controls.
- [x] Serialize custom-field values back into the nested `custom_fields` API payload.
- [x] Support typed values, select/multi-select choices, and Markdown live preview.
- [x] Verify the full custom-field form without submitting a production mutation.

Status: **done**, 2026-08-01 - Mi Pad 4 rendered individual typed custom-field controls alongside
the device fields; no production submission was performed.

## NBC-109: repair the generic item edit entry point

The overflow Edit action should reliably open an editable view even when the generic object cache
does not already contain the item.

- [x] Best-effort fetch the selected object directly when its detail view opens.
- [x] Keep cached/offline rendering available when that fetch fails.
- [x] Verify the overflow Edit action on the Mi Pad 4 without saving to production.

Status: **done**, 2026-08-01 - Mi Pad 4 overflow Edit opened the device edit form without saving.

## NBC-110: edit fields from a long press

Long-pressing a visible field should offer the existing field action dialog, including an Edit
action that opens the editable item view.

- [x] Expose field long-press actions on generic detail rows.
- [x] Route Edit from the field action dialog into the editable view.
- [x] Keep hide-field behavior alongside the edit action.
- [x] Verify long-press editing on the Mi Pad 4.

Status: **done**, 2026-08-01 - Mi Pad 4 long-press opened the field action dialog with Edit and
Hide by default actions.

## NBC-111: auto-submit setup QR codes and handle slow validation

Scanning a connection setup QR code should submit the complete URL/token payload automatically,
and a slow API response should produce a useful retryable message instead of an opaque timeout.

- [x] Automatically start setup validation when a setup QR scan returns to onboarding.
- [x] Validate the lightweight API root before scheduling the full cache sync.
- [x] Translate common timeout and authorization failures into actionable onboarding errors.
- [ ] Verify the QR setup flow on the Zenfone 10.

Status: mostly done, 2026-08-01 - QR setup now auto-submits and avoids the full directory fan-out
during login; physical Zenfone verification remains.

## NBC-112: search and pin common Add item types

The Add page should stay usable with a large directory: common device workflows should be easy to
reach while the remaining object types remain searchable.

- [x] Add a search box matching item and app labels.
- [x] Pin Devices and Device types ahead of the other item types.
- [x] Verify the filtered/pinned picker on the Mi Pad 4.

Status: **done**, 2026-08-01 - the Mi Pad 4 Add picker showed pinned workflows and filtered the
directory to circuit-related item types after entering “circuit”.

## NBC-113: align detail-row action icons

Copy and open-reference actions on typed and generic item pages should use the same fixed trailing
slots so they share a vertical alignment even when one action is absent.

- [x] Use one shared fixed-width action-slot component for detail rows.
- [x] Keep copy and reference navigation actions in stable leading/trailing slots.
- [ ] Verify the revised alignment on the Mi Pad 4.

Status: mostly done, 2026-08-01 - typed and generic detail rows now share a fixed two-slot action
area; physical visual verification remains.

## NBC-114: long-press anywhere on a detail row

The field action menu should be reachable by long-pressing the row's value or surrounding content,
not only its small title label.

- [x] Make typed detail rows respond to long press across the complete field content.
- [x] Make generic detail cards respond to long press across the complete field content.
- [x] Verify value-area long press on the Mi Pad 4.

Status: **done**, 2026-08-01 - long-pressing the Site value area on the Mi Pad 4 opened the field
action sheet with Edit field and Hide by default actions.

## NBC-115: show breadcrumbs in item detail headers

When navigating from a device into an interface, rack, site, or device type, the detail header should
identify both the current item type and the parent item instead of displaying only a generic title.

- [x] Show the current object name and model type in generic detail headers.
- [x] Carry the parent item's name into references opened from device and generic detail pages.
- [x] Render the parent/type breadcrumb in the detail header.
- [x] Verify a device-to-interface navigation chain on the Mi Pad 4.

Status: **done**, 2026-08-01 - opening wlan0 from the Shelly device's Interfaces tab on the Mi Pad
4 showed the Interfaces title and “from Shelly 1PM Mini Gen4 (Spare 1)” breadcrumb.

## NBC-116: pin Add item types with a long press

The Add picker should let users promote frequently used object types, persist that choice, and
explain where the preference is reflected.

- [x] Long-press an Add item type to toggle its persisted pin preference.
- [x] Render user-pinned types above the searchable remainder while keeping devices first.
- [x] Expose the pinned-type preference in Settings.
- [x] Verify custom pinning and persistence on the Mi Pad 4.

Status: **done**, 2026-08-01 - long-pressing Circuit Groups on the Mi Pad 4 moved it into the
Pinned section, and reopening Add item preserved the placement.

## NBC-117: double-tap to zoom image viewer content

The image viewer should offer a familiar double-tap gesture to zoom in and return to the fitted
view, in addition to pinch-to-zoom.

- [x] Zoom to a readable scale on double tap.
- [x] Return to fit-to-screen on a second double tap.
- [x] Verify the gesture on the Mi Pad 4.

Status: **done**, 2026-08-01 - double-tapping the cached Shelly front image on the Mi Pad 4 zoomed
into the image, and a second double tap returned it to the fitted view.

## NBC-118: show metadata for device-type images

Front/rear device-type images opened in the viewer should show useful metadata in the bottom panel,
matching image attachments.

- [x] Include model, view, and device-type ID metadata for front/rear stock images.
- [x] Reuse the existing image-viewer metadata panel.
- [x] Verify the metadata panel on the Mi Pad 4.

Status: **done**, 2026-08-01 - the Mi Pad 4 image viewer visibly showed Model, View, and Device
type metadata below the cached Shelly front image.

## NBC-119: highlight unsaved edit changes

The generic edit form should make fields that differ from their original values obvious before the
user submits the update.

- [x] Compare each edit control against its original cached value.
- [x] Highlight changed text, Markdown, picker, multi-select, and boolean controls.
- [x] Verify the visual state on the Mi Pad 4 without submitting.

Status: **done**, 2026-08-01 - changing the cached Shelly device name on the Mi Pad 4 produced a
visible primary-colored outline; the edit was canceled without submitting.

## NBC-120: review edit diffs before submission

Submitting edits should first show a before/after diff so the user can reject accidental changes or
confirm the exact update that will be sent.

- [x] Collect only changed fields when Save is pressed.
- [x] Show original and edited values in a confirmation dialog.
- [x] Allow canceling the review without making a network mutation.
- [x] Submit only after explicit confirmation.
- [x] Verify the review flow on the Mi Pad 4 without submitting.

Status: **done**, 2026-08-01 - the Mi Pad 4 showed Review changes with Before/After values and
Revert/Confirm changes actions; Revert closed the review without a network mutation.

## NBC-121: searchable reference pickers with device-type previews

Reference fields in the edit view should not open an unbounded, slow-to-render list. In particular,
changing a device type should support filtering and show the cached device-type front/rear images.

- [x] Replace the giant reference dropdown with a searchable, lazy list.
- [x] Match both object labels and IDs when filtering.
- [x] Show cached front/rear device-type images in reference and multi-reference choices.
- [x] Keep the picker cache-first and usable offline.
- [x] Verify changing a device type's selection UI on the Mi Pad 4 without submitting.

Status: **done**, 2026-08-01 - Mi Pad 4 showed the searchable Device Type picker, filtered it, and
rendered cached front/rear previews without submitting.

## NBC-122: focused long-press field editing

Editing a single field from its long-press action should open a compact editor for that field,
instead of taking the user through the full object edit form.

- [x] Open a focused field editor from the long-press Edit action.
- [x] Reuse typed controls, searchable reference pickers, and device-type previews.
- [x] Send the focused change through the existing before/after review.
- [x] Offer explicit Revert and Confirm changes actions before any PATCH.
- [x] Verify the focused edit and review flow on the Mi Pad 4 without submitting.

Status: **done**, 2026-08-01 - Mi Pad 4 showed the focused Device Type editor and before/after
review; Revert closed it without a save.

## NBC-123: cache-first item navigation

Opening a list, detail page, or related-item sheet should render the existing Room data directly
without triggering a server lookup that makes navigation appear stuck. Network refreshes remain
available from explicit pull-to-refresh/Refresh actions and background sync.

- [x] Stop list and generic-detail initialization from scheduling a network sync.
- [x] Stop related-item clicks from scheduling a network sync.
- [x] Stop generic detail from directly fetching an uncached object on navigation.
- [x] Move device journal/attachment refreshes behind explicit device refresh.
- [x] Keep cached reference options available to the edit picker without a hidden sync.
- [x] Remove automatic sync triggers from device lists, dashboard, sidebar metadata, and detail tabs.
- [x] Verify site navigation on the Mi Pad 4 while monitoring that no request is made.

Status: **done**, 2026-08-01 - normal navigation now reads cached Room flows only; explicit
refresh actions retain the network path. Mi Pad 4 site navigation showed cached content and
produced no OkHttp request after the navigation tap.

## NBC-124: focused edit from typed device pages

The typed device detail screen should use the same focused long-press editor as generic item
pages, rather than navigating into the full generic edit form.

- [x] Map typed device field labels to their generic edit keys.
- [x] Open the focused editor when navigation arrives from a device long press.
- [x] Reuse the existing diff/revert/confirm flow.
- [x] Verify the typed-device long-press editor on the Mi Pad 4 without submitting.

Status: **done**, 2026-08-01 - Mi Pad 4 typed-device long press opened the focused Device Type
editor and its review/revert flow without submitting.

## NBC-125: open NetBox asset-tag QR URLs from other camera apps

NetBox sticker QR codes should offer NetBox and Chill when scanned by the device's regular camera
or another QR reader. Support both HTTPS and HTTP NetBox object URLs; a bare asset-tag string is
not an Android URL and can only be resolved by the in-app scanner (or a reader's share action).

- [x] Match HTTP NetBox object URLs in the external VIEW intent filters.
- [x] Keep the compile-time configured host covered for HTTP as well as verified HTTPS links.
- [x] Verify Android's resolver matches both schemes on the Mi Pad 4.
- [x] Document the limitation of bare asset-tag payloads.

Status: **done**, 2026-08-01 - Android resolver testing on the Mi Pad 4 matched the installed
app for both HTTP and HTTPS device URLs; bare text correctly has no URL activity to dispatch.

## NBC-126: make background sync network- and battery-aware

Background and manual sync should respect the user's data policy and should never begin while
Android Battery Saver is enabled.

- [x] Add settings for Wi-Fi-only sync and whether roaming mobile data is allowed.
- [x] Apply the selected network constraint to periodic, startup, and manual sync work.
- [x] Pause workers while Battery Saver is active and retry after it is safe to run.
- [x] Show the policy in the grouped Sync settings section.
- [x] Verify the policy mapping with unit tests and deploy to physical devices.

Status: mostly done, 2026-08-01 - remote lint/unit tests pass and the policy build is installed on
the Mi Pad 4; Zenfone was disconnected and PX5 did not expose an ADB port.

## NBC-127: stabilize print progress and close after success

The print dialog's progress indicator should occupy a fixed footprint, and a successful print
should dismiss the dialog while a failed print should leave it open with the error visible.

- [x] Use a fixed-size progress indicator for discovery and printing.
- [x] Dismiss the dialog only after a successful print.
- [x] Keep the dialog open and show the printer error after failure.
- [x] Verify the behavior in the print flow on the Mi Pad 4.

Status: mostly done, 2026-08-01 - remote checks pass; Mi Pad 4 showed the fixed print controls and
kept the dialog open with a clear Bluetooth error, but a successful physical print still needs a
reachable printer connection.

## NBC-128: expose more printlabel settings

The in-app label dialog should expose the useful printlabel controls that are currently only
available from the command line, while keeping the existing printer, inversion, and orientation
choices.

- [x] Add copy count and QR-size controls.
- [x] Add the long-label layout with device name, asset tag, and serial where available.
- [x] Keep invalid settings from starting a print.
- [x] Verify the new settings are visible on the Mi Pad 4.

Status: mostly done, 2026-08-01 - copies, QR size, long-label, inversion, and vertical controls are
visible in the Mi Pad 4 dialog; physical output verification remains pending printer reachability.

## NBC-129: print the four newest Shelly Mini Gen4 devices

Print labels for the four newest matching devices in NetBox after confirming the cached/API result
and the selected printer. This is an operational print action rather than an app feature.

- [x] Identify the four newest Shelly Mini Gen4 devices without changing NetBox data.
- [ ] Print their labels through the app/printer workflow.
- [ ] Verify the print result and record any printer-specific limitations.

Status: in progress, 2026-08-01 - identified IDs 395-398 (`#SLY-3030` through `#SLY-3033`);
printing is waiting for the paired PT-P300BT4590 to become reachable.

## NBC-130: restore custom-field rows on typed device pages

Typed device details should show the same per-field custom-field rows as generic details, including
purchase information, grouping, Markdown rendering, links, and cached attachment values.

- [x] Retain the raw custom-field map in the cache when devices are synced.
- [x] Render non-empty custom fields as individually grouped rows on the device overview.
- [x] Reuse custom-field type handling so text/long-text fields render Markdown.
- [x] Verify purchase fields on the Mi Pad 4 after a fresh sync.

Status: **done**, 2026-08-01 - fresh Mi Pad 4 device data shows Store, Order Number, Date, Price,
Currency, and Markdown-rendered Notes rows from the cache.

## NBC-131: represent IP addresses as structured NetBox references

IP address values should retain their NetBox identity and be rendered as address data with useful
navigation/copy behavior instead of being treated as an undifferentiated text value.

- [x] Preserve primary-IP IDs and address metadata in the typed cache.
- [ ] Render primary and related interface IP addresses consistently.
- [x] Make IP values navigable to their cached IP address item and copyable.
- [ ] Verify IPv4/IPv6 and prefix-length display on the Mi Pad 4.

Status: in progress, 2026-08-01 - primary-IP IDs are now retained and the typed row is navigable;
related-interface IP presentation and physical IPv4/IPv6 verification remain.

## NBC-132: use distinct accents on object detail pages

Different NetBox object types should be visually distinguishable without changing the global app
theme. Device and device-type detail pages are the first important distinction, with other
namespaces using stable accents too.

- [x] Define stable accents by NetBox endpoint namespace/type.
- [x] Apply the accent subtly to typed and generic detail headers/cards.
- [x] Verify device versus device-type pages on the Mi Pad 4.

Status: **done**, 2026-08-01 - device and device-type detail pages were checked on the Mi Pad 4;
the distinct header/card accents render without changing the global theme.

## NBC-133: remove duplicate item names from detail headers

Detail cards already prominently show the current object's name. The app bar should use the object
type, with only the parent context shown when navigating through a relationship.

- [x] Replace the typed device app-bar title with its object type.
- [x] Replace generic item-name app-bar titles with the object type and optional parent context.
- [x] Verify direct and nested detail navigation on the Mi Pad 4.

Status: **done**, 2026-08-01 - the Mi Pad 4 showed the short Device/Device Types app bars and the
parent breadcrumb while navigating from a device to its device type.

## NBC-134: keep all detail tabs horizontal

Tabs should consistently place their icon beside the label. Material's separate icon slot stacks
the icon above the text, which makes some detail pages look vertically arranged.

- [x] Change generic Details and Journal tabs to horizontal icon-plus-label content.
- [x] Preserve the existing horizontal layout on typed device tabs.
- [x] Verify tabs on device and generic detail pages on the Mi Pad 4.

Status: **done**, 2026-08-01 - typed device and generic device-type tabs were visually checked on
the Mi Pad 4 and remain horizontal with icons and counts.

## NBC-135: render Boolean fields as state cards

Boolean fields such as Enabled should communicate state directly rather than showing a generic
Yes/No value.

- [x] Preserve Boolean values as semantic field rows.
- [x] Show Enabled with a green card and checkmark; show Disabled with a neutral card/icon.
- [x] Add renderer coverage for true and false values.
- [x] Verify a Boolean field on the Mi Pad 4.

Status: **done**, 2026-08-01 - the device-type page on the Mi Pad 4 showed Enabled with a
checkmark card and Is Full Depth as Disabled with a neutral card.

## NBC-136: add sections to item detail pages

Item detail pages should visually group their content in the same spirit as the dashboard. Custom
fields, especially purchase metadata, deserve a dedicated section and should retain their optional
category headings.

- [x] Add reusable section-heading rows to the generic field renderer.
- [x] Give non-empty custom fields a dedicated “Custom fields” heading.
- [x] Keep custom-field category headings and avoid orphan headings when rows are hidden.
- [x] Verify generic and typed detail pages on the Mi Pad 4.

Status: **done**, 2026-08-01 - the typed Shelly device page showed its Custom fields section and
purchase rows from the cache; the generic detail renderer was also exercised on the Mi Pad 4.

## NBC-137: move tablet navigation to a right-side rail

On tablet-sized windows the universal navigation should use a right-side rail, while phones keep
the compact bottom navigation used today.

- [x] Keep the same destinations and order across both navigation layouts.
- [x] Use a right-side NavigationRail at tablet widths and the bottom bar on phones.
- [x] Apply the responsive shell to dashboard, lists, search, scan, and Add item screens.
- [x] Verify the rail and navigation actions on the Mi Pad 4.

Status: **done**, 2026-08-01 - the Mi Pad 4 dashboard and device list visibly use the right-side
Home/Search/Scan/Add/Settings rail. The Zenfone received the same APK; PX5 was unreachable.
