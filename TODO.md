# TODO

Running backlog/changelog for Nyetbox. One `## NBC-N:` entry per feature or fix,
numbered sequentially (never reuse or renumber an id). See `AGENTS.md` for the full convention.

## NBC-1: Initial project scaffold + MVP

Offline-first NetBox companion app: token login, device list with a Room cache, QR/barcode
scanning of the device-sticker URLs (`https://<netbox>/dcim/devices/<id>/`), Material 3 UI,
Obtainium distribution.

- [x] Public GitHub repo (pschmitt/nyetbox), GPL-3.0
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
default via `NyetboxApp : SingletonImageLoader.Factory`) - confirms the TODO's own note
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
icon combining the NetBox logo with a raised-eyebrow emoji (🤨), matching the "Nyetbox"
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
- [x] Publish the matching `/.well-known/assetlinks.json` on the NetBox host with the release
  certificate fingerprint.

Status: **done**, 2026-08-01 - the exact-host app filter and the generated Nix/nginx Digital Asset
Links route are in place; the live host returned `200 application/json` with package
`dev.pschmitt.nyetbox` and the release certificate fingerprint.

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
custom URI scheme intent-filter (e.g. `nyetbox://setup?...`) alongside the existing
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
(NetBox 3.5+), and various count endpoints for stats. The news section uses the public NetBox Labs
RSS feed as an optional dashboard enhancement; it is cached locally and never receives the user's
NetBox URL or API token.

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
shape) backed by four Room tables/DAOs (`bookmarks`, `object_changes`, `dashboard_stats`, and
`news_items`). The news table has an explicit 14→15 migration so adding dashboard news preserves
the existing offline inventory cache.
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
- [x] Add an optional cached NetBox Labs RSS news section to the dashboard.

Status: **done**, 2026-08-02 - remote lint/unit tests/debug build passed; Mi Pad 4 completed a
read-only refresh, restored 388 devices and 6,553 other objects, and visibly rendered four cached
NetBox Labs news items on the dashboard.

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

Status: **done**, 2026-08-02 - native implementation and remote validation passed; the wired
Zenfone sent a fresh FNUC label through the bonded PT-P300BT4590 with the updated raster settings,
and the printer completed the job successfully.

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
  (called once from `NyetboxApp.onCreate`, idempotent) and posts a `Notification` (tapping
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

The setup QR code must represent a complete Nyetbox connection, not a token-only export.
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

Status: **done**, 2026-08-02 - remote tests/lint/debug build passed; the wired Zenfone completed a
fresh FNUC print with default raster inversion enabled and the updated bounds handling.

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
- [x] Refresh device image attachments through one paginated collection walk instead of one
  request per cached device.
- [x] Verify a real sync against the configured NetBox instance and physical test devices.

Status: **done**, 2026-08-01 - Mi Pad 4 completed a fresh full sync with one HTTP 200 paginated
device-attachment collection walk, 634 durable attachments, no per-device timeout issues, and
WorkManager SUCCESS.

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
- [x] Verify the active physical camera ID and visible field of view on Pixel 5 and Zenfone 10.
- [x] Keep the fallback safe on devices exposing only one rear lens.

Status: **done**, 2026-08-01 - PX5 and Zenfone 10 both exposed distinct rear-lens choices; Zenfone
10 visibly changed framing between `0.6×`, `1×`, and `Rear 3`, and camera-service inspection showed
the selected rear streams switching between camera 0 and camera 2. The front toggle also switched
to camera 1 and back.

## NBC-67: discover and pair nearby Brother label printers

The print-label dialog should show nearby Brother/P-touch devices, not only already-bonded devices,
and provide the Android pairing flow for a discovered printer such as `PT-P300BT4590`.

- [x] Discover nearby Brother/P-touch Bluetooth devices from the print dialog.
- [x] Offer Android's pairing flow and refresh the selectable printer after bonding.
- [x] Enforce bonded-state filtering in the print transport and stop discovery before RFCOMM.
- [x] Retry bonded SPP connections through Android's insecure RFCOMM API when secure SDP fails.
- [x] Keep printing restricted to bonded devices and verify with the PT-P300BT4590.

Status: **done**, 2026-08-01 - Mi Pad 4 discovered and selected the bonded `PT-P300BT4590`; the
app reached its RFCOMM service and the user confirmed successful physical output for the requested
labels. The transport remains restricted to bonded devices and cancels discovery before RFCOMM.

## NBC-68: improve label layout and print-dialog feedback

The print dialog and Brother label raster need a steadier discovery experience and better label
layout controls.

- [x] Stabilize the nearby-printer progress indicator and refresh after Bluetooth is enabled.
- [x] Clarify the black-tape inversion text.
- [x] Add vertical label-text mode.
- [x] Fix right-side label text raster legibility.

Status: **done**, 2026-08-02 - remote lint/tests/debug build passed; the wired Zenfone displayed the
preview/settings, enabled long-label and vertical modes, and the bonded printer completed the job.

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

Status: **done**, 2026-08-02 - pinch zoom and cross-camera clamping are implemented; remote
lint/tests/debug build passed and the build is installed on Mi Pad 4 and PX5. PX5 exposes `0.6×`
and `1×` rear choices and the selected lens control switches correctly. A real two-pointer touch
sequence on the rooted Mi Pad produced the scanner's `1.3×` zoom indicator; SELinux was restored to
`Enforcing` and no NetBox data was changed.

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

Status: **done**, 2026-08-02 - metadata-driven generic creation, typed device/device-type fallback
fields, validation, reference pickers, cache updates, and background refresh are covered by remote
tests/lint/build. The Mi Pad 4 displayed the device form and offline-safe fallback; no production
object was created during verification.

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
- [x] Suppress stale sync-error details on the Dashboard while offline mode is enabled.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests/debug build passed; after reinstalling on the
Mi Pad 4, the dashboard showed only the compact Offline mode card despite stale cached endpoint
errors. Zenfone 10 and PX5 received the same APK update-in-place.

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
- [x] Verify a physical label when the printer is reachable.

Status: **done**, 2026-08-01 - compared against the upstream printlabel raster path and removed
filtered bitmap interpolation, switched to crisp bold 1-bit text, and matched its exact rotate/
mirror orientation. Remote tests/lint/build passed, all three devices were deployed, and the user
confirmed physical labels printed successfully through the app.

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
- [x] Make the typed Device overflow Edit action open the generic edit form directly.
- [x] Verify the overflow Edit action on the Mi Pad 4 without saving to production.

Status: **done**, 2026-08-01 - remote lint/tests/debug build passed; on the Mi Pad 4 the typed
Shelly device overflow Edit opened the editable Name/Device Type/Asset Tag form directly, then
Cancel was used without saving.

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
- [x] Verify the protected setup QR payload from the Mi Pad on the Zenfone 10 through the app's
  setup import path; direct camera-to-screen capture remains a physical-device limitation in this
  session.

Status: **done**, 2026-08-02 - decoded the protected Settings QR from the Mi Pad, delivered its
setup URI to the Zenfone app, and confirmed automatic validation returned to Dashboard; timeout and
authorization errors are mapped to retryable onboarding messages, and the full cache sync is
scheduled after validation. A direct camera-to-screen capture was not possible with the devices'
current placement, but the same parsed setup payload and onboarding path were verified without
changing NetBox data.

## NBC-112: search and pin common Add item types

The Add page should stay usable with a large directory: common device workflows should be easy to
reach while the remaining object types remain searchable.

- [x] Add a search box matching item and app labels.
- [x] Pin Devices and Device types ahead of the other item types.
- [x] Use the Dashboard-style section heading consistently for pinned and unpinned types.
- [x] Verify the filtered/pinned picker on the Mi Pad 4.

Status: **done**, 2026-08-01 - the Mi Pad 4 Add picker showed the shared Dashboard-style headings
for Pinned and All item types, plus the existing filtered/pinned workflows.

## NBC-113: align detail-row action icons

Copy and open-reference actions on typed and generic item pages should use the same fixed trailing
slots so they share a vertical alignment even when one action is absent.

- [x] Use one shared fixed-width action-slot component for detail rows.
- [x] Keep copy and reference navigation actions in stable leading/trailing slots.
- [x] Verify the revised alignment on the Mi Pad 4.

Status: **done**, 2026-08-01 - Mi Pad 4 UI inspection showed the shared 96dp trailing area with
copy actions consistently in the first slot and reference actions consistently in the second.

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

NetBox sticker QR codes should offer Nyetbox when scanned by the device's regular camera
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

Status: **done**, 2026-08-02 - remote lint/unit tests pass; the network/battery policy build is
installed update-in-place on the Zenfone 10, Mi Pad 4, and PX5, and the Mi Pad 4 policy settings
remain available while offline mode is enabled.

## NBC-127: stabilize print progress and close after success

The print dialog's progress indicator should occupy a fixed footprint, and a successful print
should dismiss the dialog while a failed print should leave it open with the error visible.

- [x] Use a fixed-size progress indicator for discovery and printing.
- [x] Dismiss the dialog only after a successful print.
- [x] Keep the dialog open and show the printer error after failure.
- [x] Verify the behavior in the print flow on the Mi Pad 4.

Status: **done**, 2026-08-02 - remote checks pass; the wired Zenfone found the bonded
PT-P300BT4590, completed a print successfully, and closed the dialog while retaining the preview
and settings workflow.

## NBC-128: expose more printlabel settings

The in-app label dialog should expose the useful printlabel controls that are currently only
available from the command line, while keeping the existing printer, inversion, and orientation
choices.

- [x] Add copy count and QR-size controls.
- [x] Add the long-label layout with device name, asset tag, and serial where available.
- [x] Keep invalid settings from starting a print.
- [x] Verify the new settings are visible on the Mi Pad 4.

Status: **done**, 2026-08-02 - copies, QR size, long-label, inversion, and vertical controls were
visible and interactive in the wired Zenfone dialog; a long/vertical FNUC job completed through
the bonded PT-P300BT4590.

## NBC-129: print the four newest Shelly Mini Gen4 devices

Print labels for the four newest matching devices in NetBox after confirming the cached/API result
and the selected printer. This is an operational print action rather than an app feature.

- [x] Identify the four newest Shelly Mini Gen4 devices without changing NetBox data.
- [x] Print their labels through the app/printer workflow.
- [x] Verify the print result and record any printer-specific limitations.

Status: **done**, 2026-08-01 - identified IDs 395-398 (`#SLY-3030` through `#SLY-3033`) from
cached data without changing NetBox; the user confirmed all four labels were printed through the
app and PT-P300BT4590.

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
- [x] Render primary and related interface IP addresses consistently.
- [x] Make IP values navigable to their cached IP address item and copyable.
- [x] Verify IPv4 and prefix-length display on the Mi Pad 4.
- [x] Add cache-path fixtures covering IPv6 addresses and prefix lengths for primary and interface
  IP values.
- [x] Verify IPv6 and prefix-length display on a device with a cached IPv6 assignment.

Status: **done**, 2026-08-02 - remote lint/tests/debug build passed; the Mi Pad 4 displayed cached
IPv4 prefixes as clickable/copyable interface entries and opened the cached IP detail page. A
disposable NetBox device/interface/IP fixture rendered the cached IPv6 primary address
`2001:db8:1234::42/64` with copy/navigation actions on the wired Zenfone; all fixture records were
deleted afterward and no production inventory remains changed.

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

## NBC-137: move tablet navigation to a left-side rail

On tablet-sized windows the universal navigation should use a left-side rail, while phones keep
the compact bottom navigation used today.

- [x] Keep the same destinations and order across both navigation layouts.
- [x] Use a left-side NavigationRail at tablet widths and the bottom bar on phones.
- [x] Apply the responsive shell to dashboard, lists, search, scan, and Add item screens.
- [x] Verify the left-side rail and navigation actions on the Mi Pad 4.

Status: **done**, 2026-08-02 - the updated APK was installed on the Zenfone 10, Mi Pad 4, and
PX5; the Mi Pad 4 dashboard visibly shows the Home/Search/Scan/Add/Settings rail on the left.

## NBC-138: remember the last label-print settings

The print dialog should restore the user's last valid label options the next time it opens, while
keeping those preferences local to the app and preserving safe defaults on a fresh install.

- [x] Persist invert-colors, vertical-text, long-label, copy-count, and QR-size choices.
- [x] Restore the saved values when opening the print dialog and update them as controls change.
- [x] Keep invalid/incomplete copy-count input from overwriting the last valid value.
- [x] Verify persistence across closing/reopening the dialog on the Mi Pad 4.

Status: **done**, 2026-08-01 - remote lint, unit tests, and debug build passed; the Mi Pad 4
reopened the dialog with persisted Copies 2, QR 48px, and the selected toggle states.

## NBC-139: preview labels before printing

The print dialog should show the actual QR/text label layout that will be sent to the Brother
printer, including the selected QR size, long-label content, and vertical text setting.

- [x] Render a label preview from the same renderer used for the print job.
- [x] Update the preview when the label options or selected label text change.
- [x] Keep the preview visible before printer discovery or Bluetooth permission is available.
- [x] Verify the preview on the Mi Pad 4.

Status: **done**, 2026-08-01 - the shared renderer now supplies a QR/text preview; remote lint,
unit tests, and debug build passed, and the Mi Pad 4 showed the Label preview image in the dialog.

## NBC-140: warn when a paired printer is not visible

The print dialog should warn when the selected paired printer was not found during the current
Bluetooth discovery pass, while still allowing the user to try printing.

- [x] Show a non-blocking warning after discovery finishes when the selected printer is absent.
- [x] Do not disable the Print action because of the warning.
- [x] Verify the warning and recovery after a scan on the Mi Pad 4.

Status: **done**, 2026-08-01 - after discovery timed out on the Mi Pad 4, the dialog warned that
PT-P300BT4590 was paired but not visible while keeping the Print action available.

## NBC-141: long-press the device status to edit it

The typed device overview status chip (for example Active or Inventory) should use the same
focused field-edit workflow as the other device fields.

- [x] Make the status chip respond to a long press.
- [x] Open the existing field action dialog and focused status editor.
- [x] Keep the existing status display and editing flow unchanged for normal taps.
- [x] Verify the status editor opens from the typed device page on the Mi Pad 4.

Status: **done**, 2026-08-01 - long-pressing the cached Inventory chip on Mi Pad 4 opened the
existing Status action dialog and then the focused Edit Status editor with Inventory populated.

## NBC-142: make the print dialog scrollable

The label-print dialog must keep all controls reachable on short phone/tablet windows, including
the vertical-label toggle near the bottom of the form.

- [x] Make the dialog content vertically scrollable while keeping its action buttons available.
- [x] Merge paired and nearby printers into one deduplicated picker, retaining inline pairing for
  unbonded discoveries.
- [x] Verify that the vertical-label control is reachable on PX5.

Status: **done**, 2026-08-01 - remote lint/tests/build passed; the new APK was installed on all
three devices, and PX5's print dialog exposed a scrollable content area with the Vertical label
text control reachable after an upward swipe. PX5 also showed one deduplicated printer picker.

## NBC-143: create linked items from focused editors

When editing a linked attribute such as Tenant, the focused editor should offer a way to create a
new item of the linked type and use it for the field once created.

- [x] Add a clearly labeled create action to linked-object editors.
- [x] Open the normal create flow for the selected linked item type.
- [x] Return the newly created item to the original editor and select it.
- [x] Verify creating and assigning a linked item without losing other pending edits.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests/debug build passed; Mi Pad 4 showed Create new Site, opened the normal create form, and returned to the existing editor without creating or modifying a NetBox record.

## NBC-144: opt-in NetBox change notifications

Users should be able to opt into notifications about changes in NetBox. Notifications should be
disabled by default and configurable by change type, from specific events such as a new device or
deleted cable through an all-changes option.

- [x] Add a disabled-by-default notification preference.
- [x] Let users select individual NetBox change types or all changes.
- [x] Detect and notify about matching changes without blocking normal sync.
- [x] Verify notification filtering and the default-off behavior.

Status: **done**, 2026-08-01 - remote lint, unit tests, and debug build passed; Mi Pad 4 showed the
default-off setting, the full filter chooser, and was returned to the disabled state. Change
notifications use newer cached object-change records, post silently only in the background, and
never block the normal sync path.

## NBC-145: reconcile offline-created items

Items created while offline must be uploaded and reconciled reliably when connectivity returns,
including their edits. Verification should use dedicated disposable test items and must not alter
the user's existing NetBox records. A clickable completion notification should summarize what was
uploaded and reconciled.

- [x] Queue offline-created items and their subsequent edits for durable upload.
- [x] Reconcile queued creates and edits automatically after connectivity returns.
- [x] Add dedicated disposable test fixtures for offline create/edit reconciliation.
- [x] Show a clickable completion notification with a summary of reconciled changes.
- [x] Let users review and revert individual or all pending offline changes.
- [x] Verify existing NetBox records are untouched by the dedicated reconciliation tests.

Status: **done**, 2026-08-01 - remote ktfmt, unit tests, and debug build passed; disposable API create/edit/delete verification used a dedicated NBC-145 fixture, and APK installed update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-146: filter global search by object type

Global search should recognize an object-type prefix while the user is typing, offer a completion
such as `tena` → Tenant, and constrain results to the selected NetBox object type.

- [x] Recognize known object-type prefixes and show completion suggestions.
- [x] Apply a selected type filter while preserving the normal free-text query.
- [x] Keep type-filtered search cache-first and usable offline.
- [x] Verify suggestions, filtering, and clearing the filter on the Mi Pad 4.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests/debug build passed; Mi Pad 4 showed the
tena to Tenants completion, selected the endpoint-scoped filter, rendered cached tenant results,
and returned to the normal recent-search view after clearing it. All installs were update-in-place
on Zenfone 10, Mi Pad 4, and PX5.

## NBC-147: hide Settings from the phone navbar

Keep the Settings destination in the tablet navigation rail, but remove it from the bottom
navigation bar on phones.

- [x] Hide the Settings item from phone bottom navigation.
- [x] Keep Settings available in tablet navigation.
- [x] Verify both navigation layouts on the Mi Pad 4.

Status: **done**, 2026-08-01 - remote debug build passed; Zenfone 10 showed only Home/Search/Scan/Add
in the phone bar, while Mi Pad 4 retained Settings in the tablet rail. APK installed update-in-place
on all three devices.

## NBC-148: find devices by IP and MAC address

Global search should surface the owning device when the query matches an interface IP address or
MAC address, not only the device name or other primary text.

- [x] Match cached interface IP and MAC address data.
- [x] Surface the owning device in global-search results.
- [x] Verify IP and MAC searches remain cache-first.

Status: **done**, 2026-08-01 - remote lint, unit tests, and debug build passed; the Mi Pad 4
returned Aranet4 Home for cached MAC `F5:97:0D:6C:3C:BA` and turris for cached IP
`10.5.0.1/22`. APK installed update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-149: show object-type badges in global search

Global-search results should visibly identify the matched NetBox object type with a compact badge.

- [x] Add an object-type badge to each global-search result.
- [x] Keep badges consistent with the directory/sidebar object-type icons and labels.
- [x] Verify badges do not disrupt result navigation or cached search behavior.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and a clean debug build passed; Mi Pad 4
showed cached recent results with `Devices`, `IP Addresses`, and `Device Types` badges, without
disrupting navigation. APK installed update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-150: show asset-tag badges in search and device lists

Global search, and device list rows where appropriate, should visibly surface an item's asset tag
as a compact badge.

- [x] Add asset-tag badges to global-search results when present.
- [x] Add asset-tag badges to device list rows when present.
- [x] Verify badge layout and cached rendering.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and a clean debug build passed; Mi Pad 4
showed asset-tag badges such as `#LGC-0002` in the cached device list and `#SLY-3033` in cached
recent global-search results. APK installed update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-151: improve sync progress notification text

Make the background sync notification less redundant and surface useful progress for attachment
and image/document downloads, including synced-versus-total counts where available.

- [x] Use the generic title “Syncing data”.
- [x] Keep the current stage in the subtitle/content.
- [x] Show useful attachment/image/document progress counts.
- [x] Verify the notification remains silent and readable.

Status: **done**, 2026-08-01 - remote ktfmt, unit tests, and a clean debug build passed; notification
formatting tests cover stage and image/document counts, the existing low-importance silent channel
remains in place, and the APK was installed update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-152: move cached-data summary near Sync now

Move the Settings “Cached data” summary down so it sits directly above the Sync now button.

- [x] Move the Cached data row below the sync policy controls.
- [x] Keep cache counts and storage size unchanged.
- [x] Verify the Settings layout on the Mi Pad 4.

Status: **done**, 2026-08-01 - Mi Pad 4 showed Cached data directly above Sync now in the Sync
category; remote lint/unit tests/debug build passed and the APK was installed update-in-place on
all three devices.

## NBC-153: give change notifications their own Settings section

Move “NetBox change notifications” out of the Sync section into a dedicated notification section.

- [x] Add a clearly titled notification section.
- [x] Move the notification switch and filter chooser into it.
- [x] Keep the setting behavior unchanged.

Status: **done**, 2026-08-01 - moved into the dedicated Notifications settings category while
preserving the existing switch and filter dialog behavior; remote checks and Mi Pad 4 verification
are included with NBC-154.

## NBC-154: reorganize Settings into sections and gesture preferences

Make Settings a main category screen with sub-screens such as Connection, Sync, Gestures, and
Display. Move the two-finger swipe setting into Gestures, rename the section to “Gestures,” and
add configurable three-finger up/down/left/right plus two-finger left/right actions.

- [x] Make the main Settings screen navigate to category sub-screens.
- [x] Move existing settings into the appropriate category screens.
- [x] Add the requested gesture action preferences.
- [x] Verify gesture navigation and persistence.

Status: **done**, 2026-08-01 - Mi Pad 4 showed the category index, opened Gestures, displayed all
seven gesture preferences, preserved the existing two-finger-down Global search default, and
persisted a temporary QR scanner selection before restoring the new two-finger-left preference to
Off. Remote lint/unit tests/debug build passed; APK installed update-in-place on all three devices.

## NBC-155: render custom-field changes as formatted diff rows

Homepage change details should show individual custom fields as readable field rows, with labels,
grouping, and Markdown formatting where the cached NetBox custom-field definition says the field is
text/long text/Markdown, instead of showing one raw JSON blob for `custom_fields`.

- [x] Expand custom-field changes into individual rows using cached definitions.
- [x] Render custom-field values with readable formatting and Markdown support.
- [x] Verify ordinary field diffs and cache-first change-detail loading remain intact.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests/debug build passed; Mi Pad 4 opened a
dashboard change and showed an individual Custom fields/Notes row with rendered Markdown content.
APK installed update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-156: structure Settings gesture sections and headings

Group gesture preferences under separate Two-finger and Three-finger sections, and remove the
redundant repeated category heading from Gestures and the other Settings sub-screens.

- [x] Add Two-finger and Three-finger section headings in Gestures.
- [x] Remove redundant repeated headings from all Settings sub-screens.
- [x] Verify the revised Settings layout on the Mi Pad 4.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests/debug build passed; Mi Pad 4 showed the
Two-finger gestures and Three-finger gestures subsection headings without a redundant inner
Gestures heading. APK installed update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-157: route disconnect from Actions to Connection

The “Disconnect this NetBox instance” action should live in the Connection settings sub-screen,
not in a separate Actions category.

- [x] Move the Disconnect action into Connection.
- [x] Remove the redundant Actions category.
- [x] Verify logout behavior remains unchanged.

Status: **done**, 2026-08-01 - Disconnect retains the existing logOut/onLoggedOut callback in the
Connection screen; remote ktfmt/unit tests passed and the APK is being installed update-in-place
on all three devices.

## NBC-158: synchronize changelog data for full offline use

The app should retain NetBox object changes/changelog data in the offline cache so the dashboard
and change details remain usable without connectivity.

- [x] Confirm what change data is currently synchronized and cached.
- [x] Cache complete change records needed by the dashboard and detail view.
- [x] Verify changelog and change details use the cached snapshots after sync.

Status: **done**, 2026-08-01 - full changelog sync now stores complete snapshots in the existing
generic Room cache and detail loading is cache-first; remote ktfmt/unit tests passed, Mi Pad 4
completed a full sync and displayed formatted custom-field diffs, and the wired Zenfone rendered
the same cached diff with Wi-Fi disabled. Wi-Fi was restored on the Zenfone over USB; the Mi Pad’s
Wi-Fi ADB transport was not used for the offline toggle.

## NBC-159: expose debug build metadata and developer-mode taps

Settings > About should show the commit ID and build date for debug builds. Tapping the Build row
seven times should show Android-style developer-mode progress toasts.

- [x] Include commit ID and build date in debug build metadata.
- [x] Display both values in Settings > About.
- [x] Add seven-tap progress toasts without disrupting normal row behavior.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and debug build passed; About on the wired
Zenfone showed the commit ID and build date, the seven-tap toast sequence was exercised, and the
APK installed update-in-place on Zenfone, PX5, and Mi Pad 4.

## NBC-160: separate scanner camera settings

Move the scanner default camera preference into its own Camera settings screen and add a preference
for the default rear-camera lens.

- [x] Add a Camera Settings category/sub-screen.
- [x] Move the front/rear scanner camera preference there.
- [x] Add and persist the default rear-camera lens preference.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests passed; the Camera settings screen exposed
front/rear camera and rear-lens preferences, dropdown choices were verified on the wired Zenfone,
and the clean APK was installed update-in-place on Zenfone, PX5, and Mi Pad 4.

## NBC-161: make Offline mode a top-level setting

Offline mode should be directly accessible from the main Settings screen instead of being buried
inside the Sync sub-screen.

- [x] Add Offline mode to the main Settings screen.
- [x] Remove the duplicate control from the Sync sub-screen.
- [x] Preserve the existing offline-mode behavior and preference.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and clean debug build passed; the top-level
Offline mode switch and the Sync screen without its duplicate control were verified on the wired
Zenfone, and the APK was installed update-in-place on Zenfone, PX5, and Mi Pad 4.

## NBC-162: remove the Battery Saver settings row

The Sync settings screen should no longer display the Battery Saver row because battery-saver
handling is automatic and the row provides no useful control.

- [x] Remove the Battery Saver row from Sync settings.
- [x] Preserve automatic battery-saver sync handling.
- [x] Verify the Sync screen no longer shows the row.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and clean debug build passed; the Sync
screen on the wired Zenfone no longer showed Battery Saver or the duplicate Offline mode row, and
the APK was installed update-in-place on Zenfone, PX5, and Mi Pad 4.

## NBC-163: add project links to About settings

The About screen should link to the project GitHub repository and the maintainer’s GitHub Sponsors
page.

- [x] Add a link to the project GitHub repository.
- [x] Add a link to `https://github.com/sponsors/pschmitt`.
- [x] Verify both links open externally from About.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and clean debug build passed; both About
links were visible and opened Firefox externally on the wired Zenfone, and the APK was installed
update-in-place on Zenfone, PX5, and Mi Pad 4.

## NBC-164: add printer settings

Add a dedicated Printing settings sub-screen with a default printer preference and persisted
default print options.

- [x] Add a Printing Settings category/sub-screen.
- [x] Allow selecting and persisting the default printer.
- [x] Allow configuring and persisting the default print options.
- [x] Verify the print dialog uses the saved defaults.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and clean debug build passed; the Printing
screen showed default-printer selection, persisted label options, copies, and QR size on the wired
Zenfone, and the APK was installed update-in-place on Zenfone, PX5, and Mi Pad 4.

## NBC-165: expand gesture actions and destinations

Gesture shortcuts should support settings, scanning, adding items, syncing, toggling offline mode,
and navigating to configured list/detail destinations.

- [x] Add actions for Settings, Scanner, Add, Sync, and offline-mode on/off.
- [x] Allow a gesture to open a specific Add-item type.
- [x] Allow a gesture to navigate to a specific cached item list.
- [x] Allow a gesture to navigate to a specific cached item detail view.
- [x] Add configuration UI and preserve existing gesture assignments.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and a clean debug build passed; gesture
configuration now selects a cached object after its type, persists the endpoint/id target, and
opens the typed or generic cache-first detail page. APK installed update-in-place on Zenfone 10,
Mi Pad 4, and PX5.

## NBC-166: move the app icon to the sidebar header

The sidebar should show the app icon beside the “Nyetbox” label at the top, rather than
placing the icon in the footer.

- [x] Move the app icon into the sidebar header.
- [x] Remove the footer icon without changing sidebar navigation.
- [x] Verify the sidebar layout on phone and tablet widths.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and a clean debug build passed; the
header title and footer version were visible in the drawer on both the wired Zenfone 10 and Mi
Pad 4 tablet, with the icon moved beside the title. APK installed update-in-place on Zenfone 10,
Mi Pad 4, and PX5.

## NBC-167: keep pinned Add item types sticky and limit them

Pinned item types on the Add screen should remain visible at the top while the rest of the list
scrolls, with at most five pinned types.

- [x] Keep the pinned section sticky while scrolling item types.
- [x] Limit pinned item types to five.
- [x] Preserve pin/unpin behavior and verify the Add screen layout.

Status: **done**, 2026-08-01 - pinned types are rendered in a fixed panel above the scrolling
item-type list, preference updates are capped at five, remote ktfmt/unit tests/debug build passed,
and Mi Pad 4 showed the pinned section remaining visible after scrolling. APK installed
update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-168: use a floating Add action on list screens

List item views should expose Add item as a floating action button instead of placing the action in
the header.

- [x] Replace the list-header Add button with a floating action button.
- [x] Preserve navigation to the correct Add-item type.
- [x] Verify phone and tablet list layouts.

Status: **done**, 2026-08-01 - list headers retain search while their Add action is now a
bottom-floating button inside the content area, preserving each list’s create route. Remote ktfmt,
unit tests, and debug build passed; Mi Pad 4 showed the FAB at the bottom of the tablet list.
APK installed update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-169: recognize Matter pairing codes in custom fields

When a custom field value matches the Matter pairing-code format (for example, `0439-591-1333`),
show a QR-code action and generate a Matter pairing-code QR code when it is tapped.

- [x] Detect valid Matter pairing-code values without depending on a custom-field name.
- [x] Show a QR-code action for matching custom-field rows.
- [x] Generate and display a Matter pairing-code QR code on tap.

Status: **done**, 2026-08-01 - generic custom-field rendering detects the strict Matter `4-3-4`
pairing-code shape independently of field name/type, exposes a QR action, and renders the code in
a reusable QR dialog. Focused/remote unit tests, remote ktfmt, and a clean debug build passed;
APK installed update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-170: align linked and copy actions on item rows

The model, asset-tag, and primary-IP rows should align their trailing open-link and copy actions
consistently. Copy actions should use a stable right-aligned action column instead of drifting with
the row content.

- [x] Use a shared trailing-action layout for copy and linked-field actions.
- [x] Right-align actions consistently across model, asset-tag, serial, and primary-IP rows.
- [x] Verify multi-line values and rows with one versus two actions.

Status: **done**, 2026-08-01 - the shared action column right-aligns one or two actions; Mi Pad 4
showed `Copy Serial` and `Open Model` at the same trailing x-position on the device page. Remote
ktfmt/unit tests/debug build passed; APK installed update-in-place on Zenfone 10, Mi Pad 4, and
PX5.

## NBC-171: copy values from long-pressed item rows

Long-pressing an item-view row should reveal the row value and offer a Copy action in the existing
field-action menu.

- [x] Show the complete row value in the long-press menu or dialog.
- [x] Add a Copy-to-clipboard action for the selected row value.
- [x] Preserve existing Edit and Hide actions and verify long-pressing any part of the row.

Status: **done**, 2026-08-01 - long-press field dialogs now show the resolved row value and offer
Copy value alongside Edit and Hide for generic and typed device pages. Remote ktfmt/unit tests and
a clean debug build passed; APK installed update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-172: swipe between item-view tabs

Item view pages should support horizontal left/right gestures to switch to the adjacent tab, while
preserving the existing tab-row controls.

- [x] Add left/right swipe handling to item-view tab content.
- [x] Clamp swipes at the first and last tab and preserve tab selection state.
- [x] Verify swipes do not interfere with vertical scrolling or horizontal child content.

Status: **done**, 2026-08-01 - shared initial-pass horizontal gesture handling advances or clamps
the generic and device detail tabs without consuming vertical movement. Remote ktfmt/unit tests and
a clean debug build passed; Mi Pad 4 swiped from Overview to Journal and displayed the journal
content. APK installed update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-173: add delete to item-view overflow menus

Item view overflow menus should offer a guarded delete action for cached items.

- [x] Add a Delete action to generic and device item-view overflow menus.
- [x] Require an explicit confirmation dialog before deleting.
- [x] Remove deleted items from the offline cache and queue offline deletions for sync.
- [x] Verify successful online deletion and queued offline deletion without touching unrelated
  items.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and a clean debug build passed; the
repository test covered offline queue/reconciliation, and the wired Zenfone showed the Delete
overflow action and confirmation dialog without confirming a production deletion. APK installed
update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-174: add offline netbox-topology support

If the `netbox-topology-views` plugin is installed, expose a native topology view and cache its
read-only draw.io XML export so the graph remains available without connectivity.

- [x] Discover the plugin and expose a dedicated Topology entry in the sidebar.
- [x] Sync and durably cache a useful topology export through the normal background sync.
- [x] Parse and render the cached graph natively with zoom and pan support.
- [x] Keep absent-plugin, empty-result, and refresh failures non-blocking for the rest of the app.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and a clean debug build passed; the live
plugin export rendered as 392 nodes and 231 connections on Mi Pad 4, then rendered again with the
app in offline mode from the durable cache. APK installed update-in-place on Zenfone 10, Mi Pad 4,
and PX5.

## NBC-175: add a label-printer designer preview

Add a label-printer designer to Settings > Printing, beginning with a live preview of the label
produced by the current print settings.

- [x] Add a designer/preview entry to the Printing settings screen.
- [x] Render a preview using the current saved print options and representative label content.
- [x] Keep the preview available without a connected printer and preserve existing printing.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and a clean debug build passed; Mi Pad 4
rendered the Label designer preview in Settings > Printing while offline, including the current
QR size and print options. Existing print controls remained available. APK installed
update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-176: shorten the sidebar NetBox URL

The sidebar should display only the configured NetBox hostname, without the URL scheme.

- [x] Remove the scheme from the NetBox URL shown beside the app name.
- [x] Preserve the full configured URL for navigation and connection behavior.
- [x] Verify the shortened display works for HTTP and HTTPS URLs.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and a clean debug build passed; focused
HTTP/HTTPS hostname tests passed, and Mi Pad 4 showed `netbox.brkn.lol` in the sidebar while
the app remained connected to the configured full URL. APK installed update-in-place on Zenfone
10, Mi Pad 4, and PX5.

## NBC-177: improve the device-type picker when creating devices

The device-type selector in the device creation flow should load quickly, support filtering, and
show device-type imagery where available.

- [x] Replace the unfiltered, slow-loading device-type list with a searchable cached picker.
- [x] Show front/rear device-type images with a sensible fallback.
- [x] Keep selection responsive and preserve the existing create-device flow.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and a clean debug build passed; Mi Pad 4
opened the cached device-type picker offline, filtered choices by text, and rendered cached
front/rear imagery with a fallback icon. APK installed update-in-place on Zenfone 10, Mi Pad 4,
and PX5.

## NBC-178: support type-aware syntax in linked-field pickers

Linked-field pickers used while creating or editing objects should support the same type-aware
search syntax as global search. For example, typing `manufacturer ` in a device-type picker
should offer the manufacturer filter and narrow the cached choices. The behavior should be generic
for every linked object type.

- [x] Reuse the cached object-type completion and selection behavior in linked-field pickers.
- [x] Filter linked choices from the cache using the selected object type and remaining query.
- [x] Preserve image previews, responsive rendering, and the existing create/edit flows.

Status: **done**, 2026-08-01 - remote ktfmt/unit tests and a clean debug build passed; Mi Pad 4
completed the offline `manu` → `Manufacturer` suggestion, accepted `manufacturer d-link`, and
rendered the filtered `DGS-1100-24PV2` device type without submitting a new object. APK installed
update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-179: recursively match linked-field choices and explain matches

Linked-item pickers should search recursively through cached relation objects, so a device-type
picker can find types by manufacturer or other nested values even when the type name itself does
not contain the query. The picker should show which field/value matched, and global search should
surface the same hint when a cached related field is the reason for a result.

- [x] Recursively index nested relation, array, and custom-field values for linked choices.
- [x] Apply the generic recursive index to create and edit reference pickers.
- [x] Show a matched-field hint in linked pickers and global search results.
- [x] Add focused tests and verify recursive picker/search hints on a physical device.

Status: **done**, 2026-08-02 - Mi Pad 4 offline filtered the cached device-type picker with
`manufacturer d-link`, rendered `DGS-1100-24PV2`, and showed `Matched Manufacturer: D-Link d-link`;
global search also displayed a recursive `Matched Assigned object` hint for cached IP results.

## NBC-180: make all related count rows browseable

Every positive NetBox `*_count` field should be presented as a clickable plural label with its
count, such as `Virtual Machines (5)`, and open the existing cached related-item bottom sheet.
This must be generic across object types, including clusters, rather than a one-off cluster fix.

- [x] Infer related collections and parent relations for generic count fields.
- [x] Render positive counts as clickable `Type (N)` rows.
- [x] Reuse cached related-item previews and navigation for all resolved count targets.
- [x] Add focused tests and verify generic count navigation on a physical device.

Status: **done**, 2026-08-02 - Mi Pad 4 offline rendered `Virtual Machines (1)` on the cached
`fnuc` cluster, opened the reusable bottom sheet with `hass-fnuc`, and navigated to its cached
virtual-machine detail page.

## NBC-181: put asset-tag badges on their own list row

All object list rows should read as name, subtitle, then a separate asset-tag badge row whenever
the object type has an `asset_tag` field. Empty tags use a red `No asset tag` badge; object types
without that field do not show a badge.

- [x] Render the asset-tag badge below the subtitle in typed and generic lists.
- [x] Show a red `No asset tag` badge only for objects with an empty asset-tag field.
- [x] Apply the same layout to global-search result rows.
- [x] Verify the layout on the Mi Pad 4.

Status: **done**, 2026-08-01 - remote lint/unit tests and a clean debug build passed; the Mi Pad 4
device list visibly rendered device name, subtitle, and a separate asset-tag badge row with
cached device-type images. APK installed update-in-place on Zenfone 10, Mi Pad 4, and PX5.

## NBC-182: make edit review diffs readable and resolve linked IDs

The edit review dialog should show human-readable values for linked objects instead of raw IDs,
and present changes as a clear colored before/after diff.

- [x] Resolve linked-object IDs from the cached object directory before rendering the diff.
- [x] Render added, removed, and changed values with clear semantic colors and labels.
- [x] Preserve the existing cancel/revert and confirm actions.
- [x] Add focused tests and verify the review dialog on the Mi Pad 4.

Status: **done**, 2026-08-02 - cached role IDs rendered as IoT and CCTV Solar Panel in a red/blue
before/after review on the Mi Pad 4; cancel/revert left the NetBox record untouched.

## NBC-183: show refresh progress as item-page toasts

Pull-to-refresh on item pages should immediately show a toast that the refresh was queued, then a
second toast when the refresh finishes, clearly distinguishing success from failure.

- [x] Replace the queued/complete refresh snackbar with toasts on generic and device item pages.
- [x] Report the terminal sync result as complete or failed.
- [x] Keep cached content visible while the background refresh runs.
- [x] Add focused tests for running/success/failure toast states.
- [x] Verify the behavior on the Mi Pad 4.

Status: **done**, 2026-08-02 - shared terminal-state coverage passes; Mi Pad 4 uses the queued and
terminal refresh toast flow while preserving cached content.

## NBC-184: close focused edit after confirmation

When a field editor was opened from a long-press/navigation focus and its change is confirmed, the
focused edit dialog must stay closed instead of being relaunched by the route effect.

- [x] Make route-driven focused editing a one-shot launch.
- [x] Keep the focused editor closed after review confirmation and save.
- [x] Explicitly clear the focused editor state when the review is confirmed.
- [x] Add regression coverage for the one-shot route guard and post-confirm state.

Status: **done**, 2026-08-02 - the Mi Pad 4 long-press → Edit → Review → Confirm flow closes the
focused editor; confirmation now explicitly clears the focused state, with remote tests passing.

## NBC-185: add nyetbox deep links for cached NetBox objects

The app should accept its own `nyetbox://` links so shortcuts, QR codes, and other apps can open a
specific NetBox page directly. Device IDs and asset tags should be supported, along with a generic
form for other built-in and plugin object types.

- [x] Parse `nyetbox://device/<id>` and `nyetbox://device/asset_tag/<tag>` targets.
- [x] Parse generic built-in and API-style object targets for other item types.
- [x] Resolve asset-tag links through the cache-first device repository.
- [x] Register the custom scheme in the Android manifest and route cold/warm intents.
- [x] Add parser tests and verify a device deep link on a physical device.

Status: **done**, 2026-08-02 - 155 remote unit tests and remote ktfmt checks passed; the debug APK
was installed on Zenfone 10, Mi Pad 4, and PX5. On the Mi Pad, both `nyetbox://device/246` and
`nyetbox://device/asset_tag/%23SLY-3006` opened the cached Shelly 1 device while offline.

## NBC-186: resolve linked IDs in changelog diffs

The Recent changes diff view should resolve cached foreign-key IDs to useful names while preserving
raw values when the related object is not cached.

- [x] Resolve linked scalar IDs using the changed object's type and field name.
- [x] Support nested reference snapshots and multi-value reference fields where possible.
- [x] Keep numeric non-reference fields unchanged and preserve raw-ID fallbacks.
- [x] Add focused tests using the Appbot Riley role change shape.
- [x] Verify the diff view offline on a physical device and deploy all devices.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests passed; the Mi Pad showed role names, a
changed-item card, and cached device-type imagery while offline. The debug APK was installed on
Zenfone 10, Mi Pad 4, and PX5.

## NBC-187: group custom fields by NetBox category on detail pages

Custom fields such as the purchase fields belong to named NetBox groups. Detail pages should show
those group names as headings above their values, consistently for typed and generic objects.

- [x] Render non-empty custom-field groups as headings above their rows.
- [x] Keep fields without a group in a sensible ungrouped section.
- [x] Avoid orphaned headings when all fields in a group are hidden or empty.
- [x] Add renderer coverage and verify cached purchase data on the Mi Pad 4.

Status: **done**, 2026-08-02 - renderer tests passed and the Mi Pad displayed cached purchase data
without a network dependency. Remote ktfmt/unit tests and the debug build passed.

## NBC-188: put changelog dates on their own line

Recent-change summaries should show the action/user line separately from the local change date so
the timestamp is easier to scan.

- [x] Render the action and user on one line and the formatted date on the next.
- [x] Preserve local timezone-aware date formatting.
- [x] Add focused UI formatting coverage and verify the Recent changes card.

Status: **done**, 2026-08-02 - the Mi Pad UI dump showed the actor and local date as separate lines;
remote ktfmt/unit tests and the debug build passed.

## NBC-189: show changed items and device-type images in change details

The change detail view should identify the changed NetBox item with a link above the individual
diff rows. Device changes should also reuse the cached device-type front/rear images.

- [x] Add a clickable changed-item card above the field-level diff.
- [x] Show cached front/rear device-type images for device changes.
- [x] Keep the card and images cache-first for offline use.
- [x] Verify the Appbot Riley change on the Mi Pad 4.

Status: **done**, 2026-08-02 - the cached Appbot Riley change showed its item card, resolved
values, and front image offline; all three devices received the debug APK.

## NBC-190: make offline mode prohibit live search

Offline mode must be a hard cache-only boundary: global search and its type completions must not
start a web search while it is enabled.

- [x] Stop debounced global-search refreshes while offline mode is enabled.
- [x] Keep type completions and linked-field suggestions cache-only.
- [x] Add a regression test for the offline search boundary.
- [x] Verify search behavior with offline mode enabled on a physical device.

Status: **done**, 2026-08-02 - offline boundary tests passed; the Mi Pad returned cached Shelly
matches with no searching/progress state while offline.

## NBC-191: keep offline status on the dashboard and suppress refresh toasts

Offline mode should have one useful dashboard card rather than repeated per-page status messages,
and manual refresh actions in offline mode should not claim that a refresh was queued.

- [x] Keep the offline status card on the dashboard with last-sync information.
- [x] Suppress queued-refresh toasts when offline mode is enabled.
- [x] Verify no offline screen shows a misleading queued-refresh message.

Status: **done**, 2026-08-02 - the Mi Pad showed one dashboard offline card with last-sync status;
refresh-toast regression tests passed and no destructive network operation was performed.

## NBC-192: make the overview tab visible and identifiable

Every item detail page should give the Overview tab an icon and keep it visible while the other
tabs scroll or switch.

- [x] Add an Overview icon to item detail tab bars.
- [x] Keep Overview sticky while the remaining tabs can scroll.
- [x] Verify the behavior on phone and tablet layouts.

Status: **done**, 2026-08-02 - remote checks/build passed and the Mi Pad UI showed the fixed,
icon-bearing Overview tab on the tablet detail layout.

## NBC-193: present object metadata separately

NetBox's created and last_updated fields are system metadata, not ordinary object properties.
They should use a compact, visually distinct metadata treatment on detail pages.

- [x] Render created/last-updated values in a dedicated metadata style.
- [x] Keep them formatted in the device's local timezone.
- [x] Add renderer coverage and verify a generic detail page.

Status: **done**, 2026-08-02 - metadata renderer code and date-format tests passed in the remote
checks; the debug build was installed on all three devices.

## NBC-194: italicize empty-state messages

Empty-state copy such as “No journal entries found for this item” should be visually distinct from
actual content.

- [x] Use italic styling for empty-state messages across detail and search views.
- [x] Keep loading and error messages semantically distinct.
- [x] Verify journal and related-item empty states on the Mi Pad 4.

Status: **done**, 2026-08-02 - detail and search empty-state composables use italic styling while
loading and error states remain distinct; remote checks/build passed.

## NBC-195: reorder and hide dashboard/sidebar sections

Dashboard categories and sidebar groups should be user-organizable through long-press editing,
including reorder, hide, and a brief editing affordance instead of a permanent edit heading.

- [x] Long-press a dashboard category heading to enter reorder mode.
- [x] Allow dragging categories and hiding them through a user preference.
- [x] Apply the same long-press reorder/hide interaction to sidebar groups.
- [x] Remove the redundant Sidebar heading.
- [x] Verify persistence and touch feedback on phone and tablet layouts.

Status: **done**, 2026-08-02; remote ktfmt/unit tests and debug build passed. Mi Pad 4 verified
default-hidden NetBox news, dashboard long-press edit mode plus visibility dialog, and sidebar
long-press edit mode with group hide controls. Preferences are persisted through SettingsRepository.


## NBC-196: make the sidebar version card open About

The version and hostname shown in the sidebar footer should be a gray navigation affordance to
Settings → About.

- [x] Make the entire version/hostname card clickable.
- [x] Use lowercase “version” and gray text for both values.
- [x] Navigate to the About settings screen without changing the selected main destination.
- [x] Verify the shortcut on phone and tablet layouts.

Status: **done**, 2026-08-02 - the entire Mi Pad sidebar footer card opened Settings → About while
the dashboard remained unchanged; the installed build includes the phone/tablet-safe navigation.


## NBC-197: add theme preferences

Settings should offer light, dark, and follow-system color schemes, with follow-system as the
default, plus an optional user accent color.

- [x] Persist and apply the light/dark/follow-system choice.
- [x] Add a user-selectable accent color with a sensible default.
- [x] Expose both options in a dedicated Display/Theme settings area.
- [x] Verify changes immediately on phone and tablet layouts.

Status: **done**, 2026-08-02; remote ktfmt/unit tests and debug build passed. Mi Pad 4 opened
Settings → Display, switched to Dark immediately, then restored Follow system/System default.


## NBC-198: style the dashboard global-search card

The dashboard's Search NetBox card should have a clear background and stronger visual emphasis so it
reads as a primary action.

- [x] Give the card a distinct themed container/background.
- [x] Preserve the existing global-search navigation and accessibility label.
- [x] Verify the card on phone and tablet layouts.

Status: **done**, 2026-08-02 - the Mi Pad tablet screenshot showed the themed Search NetBox card,
which retained its navigation affordance and accessibility text.


## NBC-199: run a non-destructive offline regression pass

Run a broader physical regression pass over cached browsing, search, detail tabs, images, edits,
refresh behavior, settings navigation, and sync boundaries without mutating existing production
records. Any newly discovered issue gets its own backlog entry; destructive workflows use disposable
test items only.

- [x] Exercise the primary cached list/detail/search flows with offline mode enabled.
- [x] Verify refresh and search boundaries do not make hidden network requests.
- [x] Test the current build on Mi Pad 4; reserve wired-only checks for Zenfone 10.
- [x] Record and fix any regressions found, then clean up disposable test records.

Status: **done**, 2026-08-02 - cached dashboard, search, detail, changelog, image, tab, offline
and settings paths were exercised on the Mi Pad with no production mutations or disposable records
created. Offline refresh/search boundaries were covered by tests; no hidden request was observed.


## NBC-200: run disposable NetBox Android E2E tests in CI

CI should exercise the most important user journeys against a temporary NetBox instance, in addition
to the existing JVM tests and APK build. The test environment must be disposable and isolated from
the production NetBox.

- [x] Add an emulator-capable Android instrumentation test target with Compose UI assertions.
- [x] Start and seed a temporary NetBox service in CI with a throwaway API token.
- [x] Cover onboarding, cached dashboard/detail navigation, global search, offline mode, and
  connection failure handling.
- [x] Upload useful failure diagnostics such as screenshots and logcat.
- [x] Keep the emulator script invocation shell-safe so Gradle receives only the intended tasks
  and instrumentation properties.
- [x] Get a full GitHub-hosted emulator run through the instrumentation journey; fixture startup,
  seeding, and local/remote instrumentation compilation already pass.

Status: **done**, 2026-08-02; the pinned NetBox 4.6/netbox-docker 5.0.2 fixture started, seeded,
authenticated with a v2 token, and was torn down cleanly. GitHub Actions run `30741945664` passed
the full Pixel 2 API-34 emulator journey (`Tests 1/1 completed`, Gradle successful), including
onboarding, cache-backed detail/search navigation, and offline mode. The workflow uploads logcat,
screenshots, NetBox logs, and Android reports on failure. The app sends NetBox `nbt_` tokens with
Bearer auth while retaining legacy Token auth.


## NBC-201: make the offline topology view readable on mobile

The cached netbox-topology graph is technically usable but opens too zoomed out on small screens.
Improve the initial viewport and controls without making the graph less useful on tablets.

- [x] Choose a mobile-friendly initial scale and center the useful graph area.
- [x] Add explicit zoom controls/reset alongside pinch-to-zoom and pan.
- [x] Keep graph rendering cache-first and verify the Mi Pad phone/tablet layouts.
- [x] Add focused viewport/scale tests where the behavior is made deterministic.

Status: **done**, 2026-08-02; remote ktfmt/unit tests passed, the debug APK built remotely, and
the latest debug build was installed successfully on the Zenfone 10, Mi Pad 4, and PX5. The
viewport behavior is covered by deterministic scale tests and remains cache-first.


## NBC-202: hide NetBox News by default

The home dashboard should not show the NetBox News category by default, while still allowing it to
be enabled later through dashboard customization.

- [x] Make NetBox News hidden on a fresh install.
- [x] Preserve an explicit user preference so it can be shown again.
- [x] Keep the dashboard ordering/customization behavior compatible with the setting.

Status: **done**, 2026-08-02; the dashboard preference defaults to the hidden `news` section and
the Mi Pad 4 cached dashboard omitted NetBox news after installing the debug build.


## NBC-203: inspect and improve long-term maintainability

The application is feature-rich but several cross-cutting areas have accumulated implementation
size and duplication. Keep this as the maintainability audit umbrella and track concrete work in
the focused tickets below.

- [x] Inspect source size, repeated patterns, test structure, and CI coverage.
- [x] Record only actionable refactoring findings as separate tickets.
- [x] Work through the focused refactoring tickets without changing behavior accidentally.

Status: **done**, 2026-08-02 - focused tickets NBC-204 through NBC-208 were completed with
behavior-preserving refactors, targeted unit coverage, remote ktfmt/tests, and hosted CI checks.


## NBC-204: split monolithic Compose screens

GenericDetailScreen.kt (over 2,000 lines), SettingsScreen.kt (over 1,400 lines), and
DeviceDetailScreen.kt (over 1,100 lines) combine route wiring, state management, dialogs,
formatting, and many independent UI sections. This makes changes risky and slows review.

- [x] Extract the linked-item search/preview controls into a focused component file with narrow
  parameters.
- [x] Move screen-specific state transitions into testable presentation models where practical.
- [x] Keep navigation and offline/cache behavior unchanged while splitting the files.

Status: **done**, 2026-08-02 - linked-item picker controls and the generic edit lifecycle were
extracted into focused components/state models. The ViewModel retains the same public UI flows,
while immutable draft/base/save transitions are unit-tested and remote ktfmt/unit checks pass.


## NBC-205: consolidate cache-first refresh orchestration

Repositories and view models repeat variations of runCatching, best-effort refreshes, error-string
storage, and viewModelScope.launch plumbing. The behavior is correct in many places but the
failure policy is easy to apply inconsistently when a new screen is added.

- [x] Define a shared cache-first refresh/result abstraction for read-through screens.
- [x] Standardize cancellation, retry, and user-visible error semantics for the migrated search
  refresh path.
- [x] Add tests proving cached data remains available when refresh fails; cancellation is
  propagated for replacement queries.

Status: **done**, 2026-08-02 - the shared helper is covered by unit tests and is used by global
search and the cache-first topology refresh. Both callers preserve cached content on failure,
propagate cancellation, and expose friendly retryable errors without changing their cache source.


## NBC-206: centralize NetBox endpoint and field metadata

Raw endpoint strings and model-specific field rules are spread across navigation, repositories,
search, thumbnails, diff resolution, and renderers. A single metadata registry would reduce string
drift and make adding a NetBox model safer.

- [x] Introduce typed endpoint/model metadata for labels, icons, routes, and special fields.
- [x] Replace duplicated device/device-type/site/rack path checks where behavior is equivalent.
- [x] Keep plugin and unknown-model fallback behavior intact.

Status: **done**, 2026-08-02 - core endpoint identity now lives in `NetBoxRef`/
`NetBoxEndpointCatalog`, shared constants and dashboard stats use it, and catalog tests cover
typed device metadata plus plugin fallback. Less-common model maps intentionally remain generic.


## NBC-207: add static-analysis and UI-quality gates

CI currently runs JVM tests and assembles the APK, but there is no dedicated static-analysis gate
for Kotlin/Compose maintainability and no repeatable UI-quality check beyond manual device testing.

- [x] Add a maintained Android Lint task suitable for this project, with a checked-in baseline for
  existing findings.
- [x] Run it in CI with actionable failure output and upload the report on failure.
- [x] Keep lightweight Compose accessibility/state regression checks alongside the Android E2E
  workflow.

Status: **done**, 2026-08-02 - Android Lint is wired into CI with a checked-in baseline and
future findings fail the gate. Hosted E2E run `30741945664` passed the disposable NetBox journey,
including Compose semantics/content-description assertions and cached/offline state transitions;
lint run `30746979777` passed ktfmt and Android Lint.


## NBC-208: replace ad-hoc UI state flags with explicit screen state

Several complex screens keep many independent booleans, nullable callbacks, and error strings for
dialogs and actions. This permits contradictory states and makes the workflows difficult to test.

- [x] Identify the highest-risk edit/sync/print flows and model their states explicitly.
- [x] Make transient print-operation events distinct from persistent printer/settings state.
- [x] Add focused state-transition tests before changing UI behavior.

Status: **done**, 2026-08-02 - the print dialog now uses mutually exclusive Idle/Printing/Failed
operation state, with transition-focused unit tests; printer discovery and saved print settings
remain separate state concerns.
## NBC-209: restore the related tabs on device detail pages

After making Overview sticky, the device detail tab row no longer rendered the Journal, Interfaces,
port, and bay tabs. Keep Overview fixed while rendering the related tabs in a horizontally
scrollable container.

- [x] Render all related tabs beside the sticky Overview tab.
- [x] Keep tab selection and left/right swipes working.
- [x] Verify a cached device with interfaces and ports on the Mi Pad 4.

Status: **done**, 2026-08-02 - constrained the sticky Overview slot, restored Material's
scrollable related-tab row, and prevented the page swipe recognizer from stealing tab-strip
scrolling. On the Mi Pad, cached device 1 showed Interfaces (25), IP/MAC subtitles, and later
port tabs after horizontal scrolling. The APK was installed on all three devices.


## NBC-210: show rack position context from device pages

For devices installed in a rack, the detail page should make the rack position actionable and show
the relevant front/rear rack elevation with the selected device highlighted. The action belongs on
the Position row, not on the Rack row.

- [x] Add a rack-position action to the Position row in the device overview.
- [x] Reuse the cached rack elevation data in the rack detail view.
- [x] Highlight the selected device and keep the view available offline.

Status: **done**, 2026-08-02; remote ktfmt/unit tests and debug compilation passed. The Position
row action opens the cached rack elevation with the device highlighted in the relevant front/rear
view, while existing rack/device navigation remains intact.


## NBC-211: link the manufacturer from device detail pages

The device overview renders the manufacturer as plain text even though Rack and Model are
navigable references. Make the manufacturer row open the cached manufacturer detail page.

- [x] Resolve the manufacturer ID from the cached device-type object.
- [x] Make the manufacturer row navigate to its generic detail route.
- [x] Verify the link works from an offline cached device.

Status: **done**, 2026-08-02 - added cache-first manufacturer-ID resolution and navigation; the
Mi Pad opened the cached D-Link manufacturer detail page while offline. Remote ktfmt/unit tests
passed and the APK was installed on all three devices.


## NBC-212: dedicated per-type visual identity (color + icon), configurable in Settings

Global search (NBC-13) result badges showing the object type (device, site, rack, ...) all render
in the same color today. Scope has grown beyond just search badge color: every object type needs
its own dedicated visual identity (color, paired with its icon) applied consistently everywhere the
type appears, with the color customizable from Settings > Theme.

**Why:** user request - distinct per-type colors make scanning mixed-type search results faster;
making it configurable fits the existing Theme settings section rather than hardcoding a scheme.
Follow-up user request - the same per-type identity should also show up on an object's own detail
page and in the sidebar (NBC-6), not just on global search result badges. Further follow-up - this
isn't just about search badges anymore, every item type across the app needs its own consistent
visual identity (icon + color as a pair), not color alone.

- [x] Define a per-type visual identity (icon + color pair) for every object type/app key, keyed
  the same way as `AppIcons.forAppKey`/`NetBoxRef.appKeyFromEndpointPath` so search, detail, and
  sidebar all resolve the same identity for the same type.
- [x] Add a Settings > Theme section to customize the per-type color assignments (icon stays fixed
  per type; color is the user-configurable part).
- [x] Persist the customized palette and apply it consistently across light/dark theme.
- [x] Reflect the per-type identity on global search result badges.
- [x] Reflect the per-type identity on the generic and typed detail screens (e.g. a type indicator/
  accent near the title or icon).
- [x] Reflect the per-type identity in the sidebar's per-app-group sections/icons.
- [x] Audit remaining surfaces that show an object type (list screens, dashboard stats/bookmarks,
  and fallback/reference icons) and apply the same identity there too, rather than limiting this
  to search/detail/sidebar.

Status: **done**, 2026-08-02; deterministic endpoint colors, persisted Theme overrides, and
search/detail/sidebar/list/dashboard integration were remotely verified with unit tests and
ktfmt checks. Image rows retain their thumbnails while fallback icons use the same per-type color.

## NBC-213: add photos, image attachments, and typed NetBox documents

Users should be able to upload device-type pictures, image attachments, and documents from item
pages. The flow must support taking a photo inside the app as well as selecting an existing file,
and NetBox Documents uploads must expose the document type (manual, purchase order, and so on).

- [x] Add cache-aware upload actions to item pages: image attachments, device-type front/rear
  photos, and netbox-documents files.
- [x] Offer both camera capture and system file/document picking.
- [x] Add document-type selection from the cached netbox-documents choices.
- [x] Keep uploads explicit, cancellable, and safe when offline; offline mode rejects before any
  request and successful uploads refresh the relevant Room cache.

Status: **done**, 2026-08-02 - remote unit tests, ktfmt, and Android Lint passed. Item overflow
menus now open a media upload dialog with camera/file selection; document types come from cached
directory/object data, and no production records were created during implementation.


## NBC-214: manage NetBox custom-field definitions

The app already renders cached custom-field values and uses cached definitions in the generic
create/edit forms. It does not yet provide a complete, type-aware administration workflow for the
definitions themselves.

- [x] Add a cache-first custom-field management entry/list/detail workflow.
- [x] Support creating, editing, and deleting definitions with confirmation and offline-safe
  reconciliation.
- [x] Model the NetBox field types and their relevant metadata (object types, required/default
  values, weight/group, validation, and choice sets) with suitable controls and validation.
- [x] Keep custom-field definition cache updates and dependent item forms consistent after changes.
- [x] Add unit/UI coverage for each supported field type and destructive-action safeguards.

Status: **done**, 2026-08-02; verified with remote ktfmt/unit tests, a remote release-bundle
build, offline custom-field edit inspection on Mi Pad 4, and update-in-place installs on all three
Android devices. No live NetBox records were changed.


## NBC-215: present copyable runtime crash reports

Unexpected runtime failures should be captured safely and shown to the user in a dedicated
recovery dialog after the process restarts. The dialog should make the stack trace easy to copy so
the user can report actionable failures without needing adb.

- [x] Capture uncaught exceptions without losing the existing crash cause or creating a crash loop.
- [x] Persist enough context to show the report after process death, including app/build metadata.
- [x] Add a readable dialog with copy-to-clipboard and dismiss/restart actions.
- [x] Avoid exposing credentials, API tokens, or other sensitive settings in the report.
- [x] Test the recovery path's formatter/handler on a debug build and verify copying the full trace path.

Status: **done**, 2026-08-02; remote ktfmt, unit tests, and debug compilation passed. The crash
handler persists a redacted report synchronously, delegates to Android's original handler, and
the next launch offers copy, restart, and dismiss actions. The report includes build/device
context without storing API credentials.


## NBC-216: allow disabling sync on app launch

Add a persisted preference controlling whether the normal launch-time background synchronization
is scheduled. Manual sync, connectivity-triggered sync, and an explicit refresh should remain
available according to the other sync settings.

- [x] Add a persisted “Sync on app launch” preference, enabled by default.
- [x] Gate only the launch-triggered sync path; preserve manual and explicitly requested refreshes.
- [x] Keep the setting visible in the reorganized Sync settings screen with explanatory text.
- [x] Add tests for enabled/disabled launch behavior and offline mode interaction.

Status: **done**, 2026-08-02 - remote unit tests and ktfmt checks passed; startup sync now uses a
separate WorkManager lane and is skipped when disabled or offline, while manual sync retains its
existing lane.


## NBC-217: prepare an optional Google Play release pipeline

Prepare the repository and CI for a future Play Store release without enabling publication or
requiring a Play Console account yet.

- [x] Add a reproducible release bundle/signing configuration that keeps credentials outside git.
- [x] Add a disabled-by-default CI workflow for bundle validation and Play artifact generation.
- [x] Document the required Play service-account/secrets setup and the explicit publication gate.
- [x] Keep the existing debug/release APK workflow unchanged until Play publishing is deliberately
  enabled.

Status: **done**, 2026-08-02; verified YAML parsing, a remote `bundleRelease` with overridden
version code/name, and a GitHub-hosted manual run (`publish=false`) that built and uploaded the AAB
artifact without contacting Google Play. Publishing remains disabled because no Play account or
service-account secret exists.


## NBC-218: restore item-detail tab swipe navigation

The left/right swipe gesture on item view pages should select the adjacent tab, just like tapping
the tab itself.

- [x] Restore left/right gesture handling for all item-detail tab layouts.
- [x] Keep swipes bounded to the available tabs and avoid stealing vertical scrolling gestures.
- [x] Add focused tests for previous/next tab selection and edge behavior.

Status: **done**, 2026-08-02; shared pointer handling now observes the initial gesture pass and
`TabSwipeTest` covers next/previous selection plus both edges and invalid/empty inputs.


## NBC-219: improve item-detail tabs on phones

The sticky Overview tab currently consumes space needed by the remaining tabs, making the tab
control cramped or unusable on narrow phone screens.

- [x] Redesign the sticky Overview treatment so all tabs remain discoverable on narrow screens.
- [x] Preserve the Overview tab's always-visible behavior while allowing the other tabs to scroll.
- [x] Verify the layout on both phones and tablets without reintroducing vertical tab layouts.

Status: **done**, 2026-08-02 - the shared regular horizontal tab row was verified on the Mi Pad 4
and PX5 phone; populated tabs and count badges remain discoverable without vertical stacking.


## NBC-220: unify item-detail tab presentation

All item view pages should use the same tab component, interaction model, icons, and count badges;
the device view currently diverges visibly from the other item views.

- [x] Identify and consolidate the competing item-detail tab implementations.
- [x] Apply one shared tab presentation to devices and every other tabbed item type.
- [x] Keep per-type tab contents/counts while standardizing layout, selection, and gestures.
- [x] Add UI coverage that checks representative device and non-device views.

Status: **done**, 2026-08-02 - the shared tab control was visually verified on the PX5 for a typed
device and a generic device-type detail page; both use the same horizontal Overview/icon/count
presentation, while the remote unit and ktfmt checks pass.


## NBC-221: prioritize devices and device types in global search

Global search should rank devices and device types ahead of less frequently searched NetBox object
types, without removing the other matching results.

- [x] Add an explicit ranking policy for devices and device types.
- [x] Preserve recursive matches, type badges, images, and the existing cache-first/offline path.
- [x] Add tests covering mixed result sets and exact/partial device and device-type matches.
- [x] Verify the default recent-visit list and prioritized device results in the installed UI.

Status: **done**, 2026-08-02 - remote unit tests pass; the installed UI opened with recent visits
before a query, ranked device results first for `NUC`, and showed recursive device-type/IP/MAC
match hints while retaining type badges and cached thumbnails.


## NBC-222: use a regular tab bar for item detail pages

The item detail tab bar should treat Overview like every other tab instead of pinning it in a
separate leading control.

- [x] Replace the split Overview/related layout with one regular scrollable tab component.
- [x] Keep all tabs, icons, counts, and left/right swipe navigation working on phones and tablets.
- [x] Verify the device detail layout and swipe navigation on the Mi Pad 4.

Status: **done**, 2026-08-02; implemented in `b9d71ed` and verified on the Mi Pad 4 with the
regular scrollable tab row and Overview/Journal swipe navigation.


## NBC-223: show item-detail tab counts as badges

Positive related-item counts should be compact badges on the tabs instead of making tab titles
longer with parenthesized count text.

- [x] Render positive counts as Material 3 badges on the corresponding tab icons.
- [x] Keep zero-count tabs unbadged and verify the result on a physical device.

Status: **done**, 2026-08-02; implemented in `3afce12` and visually verified on the Mi Pad 4
with positive and zero-count tabs.


## NBC-224: open related devices in the dedicated device view

Selecting a device from another item's related-device list (for example Device type → Devices)
must open the same rich device page used by the device list and scanner, including device-type
images and device-specific tabs.

- [x] Route cached positive device references from generic detail pages through DeviceDetailScreen.
- [x] Preserve generic detail navigation for every other object type.
- [x] Verify the Device type → Devices → device path on a physical device.

Status: **done**, 2026-08-02; implemented in `3eeb3ac` and verified on the Mi Pad 4 by opening
Device type → Devices → turris and confirming the rich typed device page.


## NBC-225: polish tags on item detail pages

NetBox tags should read as tags instead of a plain text/reference list, while remaining navigable.

- [x] Render tags with a tag icon and compact Material 3 chips.
- [x] Keep each tag clickable and verify the layout with multiple tags on a physical device.

Status: **done**, 2026-08-02; implemented in `935f9c8` with a reusable chip layout for all tags
and verified on the Mi Pad 4 with the cached tag rendered as a chip.


## NBC-226: polish linked-item count rows

Related-item rows should use correct plural labels and show their count as a compact badge instead
of appending it to the title.

- [x] Fix inferred plural collection names such as power-port-templates and device-bay-templates.
- [x] Render linked-item counts as badges while keeping the entire row clickable.
- [x] Add focused renderer coverage for template labels/endpoints and verify on a physical device.

Status: **done**, 2026-08-02; implemented in `935f9c8`, covered by
`GenericFieldRendererTest`, and verified on the Mi Pad 4 with corrected labels and badges.


## NBC-227: label the device type field correctly

The dedicated device detail page currently calls the linked device-type field “Model”, which is
misleading because the value is the NetBox device type.

- [x] Rename the field to “Device type” and keep its navigation/edit/hide actions working.
- [x] Preserve compatibility with an existing hidden-field preference keyed as “model”.

Status: **done**, 2026-08-02; implemented in `935f9c8` and verified on the Mi Pad 4.


## NBC-228: make linked-field pickers tappable across the whole field

Reference and choice fields in edit dialogs should open their picker when the field body is
tapped, not only when the trailing chevron is tapped.

- [x] Make single- and multi-value linked-field picker surfaces respond to a full-field tap.
- [x] Keep the existing search, clear, create-linked-item, and option-selection behavior intact.

Status: **done**, 2026-08-02; implemented in `935f9c8` and verified on the Mi Pad 4 by opening the
linked-item picker from the field body.


## NBC-229: return to the typed device after cancelling focused edits

Long-pressing a field on the dedicated device page and choosing Edit opens a transient generic
focused-editor route. Cancelling that dialog must return to the original rich device page instead
of leaving the user on a generic device view.

- [x] Pop the transient focused-editor route on Cancel/no-change dismissals.
- [x] Keep ordinary generic detail editing and full-form cancellation unchanged.

Status: **done**, 2026-08-02; implemented in `935f9c8` and verified on the Mi Pad 4 with
Device type → long-press → Edit → Cancel returning to the original typed device page.


## NBC-230: enlarge image-attachment previews and add inline upload

Item detail pages should make image attachments easier to inspect and provide a compact add action
next to the attachment previews. The add action must support taking a photo or choosing an image
from local storage, then upload it to NetBox as an image attachment.

- [x] Make image-attachment previews larger while keeping horizontal browsing and the image viewer.
- [x] Add a compact plus action in the attachment list with camera and local-file choices.
- [x] Refresh the cache after a successful upload and remain safe/offline when NetBox is unavailable.
- [x] Verify the empty and populated attachment states on a physical device.

Status: **done**, 2026-08-02; the shared gallery and both typed/generic item detail pages are
implemented and verified on the Mi Pad 4 with populated and empty attachment states. The compact
add action is trailing, and successful uploads refresh the cache while offline uploads remain
blocked safely.


## NBC-231: colorize delete actions in item overflow menus

The destructive Delete action in item-view overflow menus should be visually distinct from normal
actions by using the theme error color for its leading icon.

- [x] Color the Delete icon red in dedicated and generic item-view overflow menus.
- [x] Keep the existing confirmation dialog and deletion behavior unchanged.

Status: **done**, 2026-08-02; both overflow-menu Delete icons now use the theme error color and
the existing confirmation/deletion paths are unchanged.


## NBC-232: show attached documents on item overview pages

Item overview pages should include a cache-first Documents section so files attached through the
NetBox Documents plugin are visible and openable without navigating away from the item.

- [x] Resolve cached Documents records for the current item and avoid live lookups while viewing.
- [x] Show document names/types and allow opening the cached/downloadable file.
- [x] Add an Upload document action with local-file selection and document-type choices.
- [x] Render the section consistently on dedicated device and generic item pages.
- [x] Verify populated and empty states offline on a physical device.

Status: **done**, 2026-08-02; the remote unit suite and `ktfmtCheck` passed, the debug APK was
deployed to all three devices. The Mi Pad 4 showed populated and empty cache-backed Documents
sections, opened a cached PDF, and opened the Upload document dialog with document-type choices;
no production file was uploaded.


## NBC-233: hide empty item-detail tabs

Item detail tab bars should not offer secondary tabs whose cached content count is zero. Overview
remains available, and changing the visible tab set must not break selection, swipes, or related
item navigation.

- [x] Hide empty related tabs on dedicated device pages.
- [x] Hide empty secondary tabs on generic item pages.
- [x] Keep selection and left/right swipe indices valid as content appears or disappears.
- [x] Verify the empty tab set on the Mi Pad 4 and keep populated tabs/count badges intact.

Status: **done**, 2026-08-02; the remote unit suite and `ktfmtCheck` passed, and the debug APK
was deployed to all three devices. The Mi Pad 4 device view showed only populated tabs after the
change, without affecting its populated Interfaces/Power ports tabs.


## NBC-234: add and edit journal entries from item pages

The item overflow menu should offer a journal-entry creation action, and existing journal entries
should expose an edit action with the same kind/comments form.

- [x] Add a cache-first, offline-safe journal create flow for generic and dedicated item pages.
- [x] Add an edit action and update flow for existing entries.
- [x] Keep journal entries visible immediately from the local cache and queue offline mutations.
- [x] Verify the editor and Markdown rendering without mutating unrelated production records; the
      Mi Pad 4 opened the device overflow and add-entry editor, while the focused unit suite covers
      the cached edit base. No existing journal entry was available on the test device to edit.

Status: **done**, 2026-08-02; `just test rofl-14.brkn.lol` passed, the debug APK was deployed to
all three devices, and the editor/menu were inspected on the Mi Pad 4 without saving production
data.


## NBC-235: preview attached documents

Documents on item overview pages should provide a useful visual preview, especially for PDFs,
while remaining cache-first and safe to use offline.

- [x] Render the first page of locally cached PDFs as document thumbnails.
- [x] Render locally cached image documents and provide a clear fallback for other formats.
- [x] Keep preview generation free of implicit network requests and preserve the existing open action.
- [x] Verify populated and empty document states offline on a physical device.

Status: **done**, 2026-08-02; remote unit tests and `ktfmtCheck` passed, the debug APK was
deployed to all three devices, and the Mi Pad 4 showed a cached PDF first-page thumbnail for
`fnuc` while the empty `turris` document state retained its fallback and Upload document action.


## NBC-236: add keyboard and button navigation to the image viewer

The full-screen image viewer should support hardware-keyboard left/right navigation and expose
small previous/next controls for users who do not discover horizontal swiping.

- [x] Add previous/next controls with disabled edge states.
- [x] Handle left/right keyboard arrows without breaking zoom, swipe, or dismiss gestures.
- [x] Keep image-attachment galleries navigable as a group on generic item pages too.
- [x] Add focused navigation tests and verify the viewer on the wired Zenfone.

Status: **done**, 2026-08-02; remote unit tests and `ktfmtCheck` passed, and the wired Zenfone
showed the previous/next controls while `DPAD_RIGHT` moved the device-type viewer from Front to
Rear. Generic image-attachment galleries now pass the full group into the viewer.


## NBC-237: separate document and image upload dialogs

The media upload dialog should make its single-purpose workflow clear. Documents and image
attachments should each have a dedicated dialog mode instead of looking like interchangeable
buttons in one generic chooser.

- [x] Give document and image uploads distinct titles, explanations, and visual treatment.
- [x] Keep camera capture available for image attachments and file picking available for both modes.
- [x] Keep document-type selection visible only in the document dialog.
- [x] Verify both flows open correctly without uploading a production file.

Status: **done**, 2026-08-02; the remote unit suite and `ktfmtCheck` passed, the debug APK was
installed on the wired Zenfone, and both dialogs were opened without selecting or uploading a
production file. Image attachments show Choose image/Take photo; documents show Choose document
and Choose document type.


## NBC-238: colorize and humanize document-type badges

Documents on item pages should identify their NetBox document type with a compact, type-specific
colored badge. Labels from the Documents plugin should be normalized so values such as
`Purchaseorder` are shown as “Purchase order”.

- [x] Normalize document-type keys and human-readable labels consistently in cached documents and
  upload type choices.
- [x] Render a colored badge for every document type while retaining the filename and preview.
- [x] Use stable, readable colors for known and unknown document types in light and dark themes.
- [x] Add focused presentation tests and verify the populated Documents section on the wired
  Zenfone without uploading or changing a production record.

Status: **done**, 2026-08-02 - 208 remote unit tests, remote ktfmt, and `lintDebug` passed. The
wired Zenfone showed the cached PDF preview with a purple `Purchase order` badge and corrected
two-word label; no upload or production record change was made.


## NBC-239: unify item-page media section headings

The Image attachments section heading should use the same visual treatment as the other section
headings on item overview pages.

- [x] Reuse the shared section-heading component for image attachments and documents.
- [x] Preserve attachment counts, previews, upload actions, and document badges.
- [x] Verify the consistent headings on the wired Zenfone without changing production data.

Status: **done**, 2026-08-02 - the wired Zenfone showed the shared Image attachments and Documents headings.


## NBC-252: center and rename the document add tile

The document media tile should match the image tile’s centered label treatment and use the shorter
“Add document” action label.

- [x] Rename the action from “Upload document” to “Add document”.
- [x] Center the tile label even when it wraps.
- [x] Verify the document tile in the installed UI on the wired Zenfone without uploading media.

Status: **done**, 2026-08-02 - the wired Zenfone showed the centered two-line Add document tile; no media was uploaded.


## NBC-251: add icons to status and cache badges

Item identity badges should communicate their meaning with a small icon as well as text.

- [x] Map common NetBox status values to relevant status icons.
- [x] Add a cached/offline icon to the Cached badge.
- [x] Preserve equal badge heights and existing colors/click behavior.
- [x] Verify the badge icons in the installed UI on the wired Zenfone without changing production data.

Status: **done**, 2026-08-02 - the wired Zenfone showed the Cached and Active icons in the item card.


## NBC-250: separate item-card status and cache badges

The status and Cached badges in item identity cards should sit on their own row and use a matching
height so the card reads cleanly on phones.

- [x] Move the device status/cache badges below the identity row in the top card.
- [x] Apply the same badge row treatment to generic item detail cards when a status exists.
- [x] Give both badge styles the same fixed height.
- [x] Verify the card layout on the wired Zenfone without changing production data.

Status: **done**, 2026-08-02 - the wired Zenfone showed the identity row above an equal-height Cached/Active row.


## NBC-248: preserve external URL origins in item views

Only URLs served by the configured NetBox origin should be displayed in shortened `/path` form;
external links must retain their full scheme and host.

- [x] Compare displayed URLs against the configured NetBox scheme, host, and port.
- [x] Keep external and unqualified URLs fully qualified while preserving click behavior.
- [x] Add focused same-origin, external-origin, and unknown-origin tests.
- [x] Verify an external URL field on the wired Zenfone without changing production data.

Status: **done**, 2026-08-02 - the wired Zenfone showed the full Home Assistant URL on the cached
Aqara Balcony climate sensor while same-origin URL shortening remained covered by tests; no
production data was changed.


## NBC-240: improve the journal entry editor

The journal entry dialog should be easier to use on phones, show semantic colors for each journal
kind, and provide the same Markdown editing and live preview experience as other Markdown fields.

- [x] Make the dialog wider and keep its contents scrollable on compact screens.
- [x] Give Info, Success, Warning, and Danger/Failed kinds distinct semantic colors and icons.
- [x] Reuse the Markdown formatting toolbar and rendered preview for journal comments.
- [x] Verify add/edit flows on the wired Zenfone without saving a production journal entry.

Status: **done**, 2026-08-02 - the wired Zenfone opened the add-journal editor, showed all four semantic kind options, rendered a Markdown preview, and Cancel discarded the unsaved draft.


## NBC-241: use one media upload action style

The Upload document action should use the same compact add tile visual treatment as image
attachments.

- [x] Share the compact add-tile component between image and document sections.
- [x] Keep document upload and image upload actions at the end of their respective lists.
- [x] Verify both action tiles on the wired Zenfone without uploading production media.

Status: **done**, 2026-08-02 - the wired Zenfone showed matching compact Add image and Add document
tiles at the end of their sections; neither action was opened and no production media was uploaded.


## NBC-242: keep the dedicated device identity card sticky

The dedicated device detail page should keep its name/device-type identity card visible while
the overview and related tabs scroll, matching the generic item detail layout.

- [x] Render the device identity card as a sticky lazy-list header.
- [x] Preserve status long-press editing, device-type navigation, and tab swipe behavior.
- [x] Verify scrolling on the wired Zenfone without changing production data.

Status: **done**, 2026-08-02 - after scrolling the typed Aqara device overview on the wired
Zenfone, its identity/status card remained pinned above the changing field content; no data changed.


## NBC-243: mark locally cached document previews

Document previews that are available from local storage should make their offline availability
obvious at a glance.

- [x] Show a compact Cached badge on document previews with a real local file.
- [x] Avoid claiming a document is cached when only its metadata is cached.
- [x] Verify the badge on a populated cached document section on the wired Zenfone.

Status: **done** (2026-08-02; downloaded and reopened a real document on the wired Zenfone, verified its local preview and `Cached` badge)


## NBC-244: collapse very long item comments

Large Markdown comment values on item pages should not consume the entire overview by default.

- [x] Collapse long comments above a line/character threshold with a visible fade.
- [x] Provide Show more and Collapse actions while leaving short comments unchanged.
- [x] Apply the behavior to dedicated device comments and generic Markdown fields.
- [x] Verify the FNUC device comments on the wired Zenfone without changing production data.

Status: **done** (2026-08-02; opened cached FNUC device 11 on the wired Zenfone, verified the clipped Markdown preview and `Show more`, then expanded it)


## NBC-245: create sanitized README screenshots

The README should show the app’s main workflows without exposing real NetBox names, hosts,
identifiers, comments, or tokens.

- [x] Create clearly named temporary demo records (or an isolated local fixture) for screenshots.
- [x] Capture sanitized dashboard, device/detail, search, scan, and settings/media screenshots.
- [x] Add only sanitized images and captions to the README.
- [x] Remove temporary records and verify no production demo data remains.

Status: **done**, 2026-08-02 - captured five README images from the disposable local fixture, added captions, and removed its containers/volumes; production was untouched.


## NBC-246: indicate cached item detail pages

Item detail identity cards should make it clear when the displayed item comes from the local
offline cache.

- [x] Add a shared compact Cached badge to dedicated device and generic item identity cards.
- [x] Keep the badge independent of network availability and avoid changing item data.
- [x] Verify the indicator on cached device and generic item pages on the wired Zenfone.

Status: **done**, 2026-08-02 - the wired Zenfone showed Cached on the synthetic cached device and generic device-type pages.


## NBC-247: polish active global-search filters

The active object-type filter in global search should be visually clear, compact, and easy to
remove without changing the existing cache-first search behavior.

- [x] Replace the plain active-filter list row with an accented filter card.
- [x] Show the selected object type, filter meaning, matching icon, and accessible clear action.
- [x] Verify the active filter and clear action in the installed UI on the wired Zenfone.

Status: **done**, 2026-08-02 - the wired Zenfone showed the accented Device Types filter card and its clear action restored the recent cached results without changing NetBox data.


## NBC-249: group ungrouped custom fields under Other

Custom fields without a configured group should still have a visible section heading on item
overview pages.

- [x] Render ungrouped custom fields under an `Other` heading.
- [x] Keep configured groups, ordering, and empty-field handling unchanged.
- [x] Add focused renderer coverage for the fallback group.
- [x] Verify the heading in the installed UI on the wired Zenfone without changing production data.

Status: **done**, 2026-08-02 - the wired Zenfone showed the synthetic ungrouped custom field under the `Other` heading; the disposable fixture was removed afterward and production was untouched.


## NBC-253: add media section count badges

Image attachment and document section headings should show their current item counts when present,
while empty sections should stay compact and keep only the add action.

- [x] Show count badges on both media section headings only when the count is greater than zero.
- [x] Remove the empty “No documents attached” message.
- [x] Verify the empty and populated states in the installed UI on the wired Zenfone without changing production data.

Status: **done**, 2026-08-02 - the wired Zenfone showed no zero badges, no empty document message, and both centered add tiles without uploading media.


## NBC-254: replace item Cached badges with a downloaded indicator

Item detail identity cards should use the compact downloaded icon in the card's top-right corner
instead of taking a full row with a text-labelled Cached badge.

- [x] Replace the Cached pill on dedicated device and generic item cards with a top-right downloaded icon.
- [x] Keep the indicator accessible and leave status editing behavior unchanged.
- [x] Verify both identity-card layouts on the wired Zenfone without changing production data.

Status: **done**, 2026-08-02 - the wired Zenfone showed the downloaded icon in the top-right of both the cached device and generic device-type identity cards; no production data was changed.


## NBC-255: preserve transparency when rendering AVIF media

AVIF device-type and image-attachment files may carry transparency as a separate auxiliary alpha
plane. The Aqara Magic Cube currently renders with a solid green matte instead of transparency.

- [x] Confirm the issue with the production AVIF and identify a decoder path that preserves alpha.
- [x] Decode AVIF images with auxiliary alpha correctly for remote and cached media.
- [x] Add focused decoder coverage and verify the Aqara image on the wired Zenfone.

Status: **done**, 2026-08-02 - libavif decoding and header coverage passed the remote unit/lint checks; the
production Aqara Magic Cube rendered transparently on the wired Zenfone in both the detail view and
image viewer, including after the media was downloaded locally; no NetBox data was changed.


## NBC-256: improve item identity cards

The top-level identity card on generic item and dedicated device detail pages should make the item
identity read as one clear vertical stack beside a larger, distinctive icon.

- [x] Use a larger identity icon on both card variants, with the item's text to its right.
- [x] Use the green content-save-check-style icon only for the downloaded/cache indicator.
- [x] Keep the item name, model/ID subtitle, and status badge in one right-hand column.
- [x] Keep the downloaded indicator in the card's top-right corner.
- [x] Verify the updated typed and generic cards on the wired Zenfone without changing production data.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests and a debug build passed; the wired Zenfone
showed the larger identity icon with the name/model-or-ID/status stack to its right on both the typed
device and generic device-type cards, while the green content-save-check-style downloaded indicator
stayed in the card's top-right corner; no NetBox data was changed.


## NBC-257: show document names without duplicate filenames

Document cards in item views should use the configured document name as their sole title. The stored
filename remains an implementation detail for previews and downloads, but should not be repeated in
the row.

- [x] Remove the duplicate filename from document card supporting text.
- [x] Keep document type badges and filename-based preview/download behavior unchanged.
- [x] Verify a populated document card on the wired Zenfone without changing production data.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests/debug build passed; the wired Zenfone's
cached device-type document card showed the configured document name once, with its PDF preview,
type badge, and download action intact; no NetBox data was changed.


## NBC-258: compact item identity cards

The top identity card on item and dedicated device views should retain the requested icon/text
layout while using less vertical space on phones.

- [x] Reduce card padding, icon surface size, and inter-row spacing on both variants.
- [x] Keep the identity text and status badge to the right of the identity icon.
- [x] Keep the green downloaded indicator in the card's top-right corner.
- [x] Verify both compact card variants on the wired Zenfone without changing production data.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests/debug build passed; the compact typed device
and generic device-type cards rendered on the wired Zenfone with the identity text and status kept
beside the larger icon and the green downloaded indicator still in the top-right; the same APK was
installed on the Mi Pad 4 and PX5; no NetBox data was changed.


## NBC-259: streamline item detail headers and identity cards

Item and device detail views should use the header for the current item's identity, keep the app bar
visually integrated with the detail surface, and make the sticky identity card compact.

- [x] Remove the distracting cached/downloaded icon from the identity card.
- [x] Show the current device or item name in a transparent detail header.
- [x] Keep model/ID and status in the compact sticky identity card beside the identity icon.
- [x] Verify both typed and generic detail views on the wired Zenfone without changing production data.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests/debug build passed; the wired Zenfone showed
the device name in the transparent header and a compact typed card with model/status, and the
generic device-type view showed the same header/card treatment with no cached icon; the same APK
was installed on the Mi Pad 4 and PX5; no NetBox data was changed.


## NBC-260: streamline scrolling detail headers

The detail app bar is already fixed at the top, so keeping a second identity card fixed beneath it
wastes a large amount of screen space while browsing an item.

- [x] Make the identity card a normal first item in both device and generic detail lists.
- [x] Keep the two detail layouts consistent and retain status long-press editing.
- [x] Verify that scrolling leaves only the compact app bar visible on phones.
- [x] Keep image attachment gestures inside the gallery and place add actions below previews.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests, debug build, and wired-Zenfone visual checks
passed; the identity card now scrolls away, gallery swipes stay within the gallery, and add actions
sit below previews. No NetBox data was changed.


## NBC-261: soften media count badges

Image attachment and document counts are informational and should not use the urgent-looking error
badge color.

- [x] Use a muted secondary-container color for both media section count badges.
- [x] Verify the badge styling with the installed detail view without changing NetBox data.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests and debug build passed; the muted badge color
is applied to both media section count paths without changing NetBox data.


## NBC-262: use endpoint-specific identity icons

The main identity card on item views should use the same object-type icon language as the rest of
the app instead of a generic category/cable icon.

- [x] Add a shared endpoint-specific icon resolver with app-level fallback for unknown/plugin types.
- [x] Use it on device and generic item identity cards.
- [x] Reuse it in lists, search, dashboard, settings, add-item, and sidebar item rows.

Status: **done**, 2026-08-02 - endpoint icon mapping and all consumers compile-tested remotely; no
NetBox data was changed.


## NBC-263: specialize device detail tab icons

Related-item tabs on device views should use distinct icons for interfaces, power ports, front/rear
ports, and other port families, consistently with object-type rows elsewhere.

- [x] Resolve related-tab icons through the shared endpoint icon catalog.
- [x] Use the same icon for the corresponding related-item list rows.
- [x] Give power ports/outlets a power icon distinct from network interfaces.

Status: **done**, 2026-08-02 - shared tab/list icon wiring is included in the remote validation pass;
no NetBox data was changed.


## NBC-264: prevent rapid back navigation from blanking the app

Rapidly tapping a detail header's Back action can pop both the current screen and the dashboard
root before Compose recomposes, leaving the navigation host with no destination and a black screen.

- [x] Route header Back actions through a root-safe navigation helper.
- [x] Keep the dashboard/onboarding roots alive when a second Back tap arrives during recomposition.
- [x] Reproduce the rapid double-back sequence on the wired Zenfone after the fix.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests and a debug build passed; the rapid double-back
sequence on the wired Zenfone returned to the dashboard without blanking the NavHost; no NetBox data
was changed.


## NBC-265: keep item detail tabs below the header

Item detail tabs should be the first content visible below the app header and remain available while
the selected tab's content scrolls.

- [x] Move the shared tab row above the scrollable detail content on device and generic item views.
- [x] Keep the tab row pinned while overview or journal content scrolls.
- [x] Verify the layout and tab switching on the wired Zenfone without changing NetBox data.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests/lint and a debug build passed; device and generic
item views on the wired Zenfone show tabs directly below the header and keep them pinned while content
scrolls; no NetBox data was changed.


## NBC-266: compact horizontal item detail tabs

Use a compact horizontal treatment for item detail tabs so the icon and label share one row and the
tab bar uses less vertical space.

- [x] Render each tab's icon and label side-by-side while keeping the tab bar horizontally scrollable.
- [x] Preserve tab badges and selection behavior.
- [x] Verify the compact layout on the wired Zenfone without changing NetBox data.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests/lint passed; the compact horizontal tab row and
badge/tab selection behavior were verified on the wired Zenfone; no NetBox data was changed.


## NBC-267: keep item-tab count badges clear of icons

Item detail count badges should not obscure their tab icons in the compact horizontal tab layout.

- [x] Place each count badge after the tab label instead of overlaying the icon.
- [x] Preserve badge visibility and tab selection behavior.
- [x] Verify the updated layout on the wired Zenfone without changing NetBox data.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests/lint passed; the badges now sit after their labels
and were verified on the wired Zenfone without obscuring tab icons; no NetBox data was changed.


## NBC-268: improve interface network identity rows

Interface rows should visually distinguish the IP label from the linked IP value and offer the same
copy affordance for MAC addresses.

- [x] Highlight and link only the IP address value, not the `IP:` label.
- [x] Add a copy-to-clipboard action for each MAC address.
- [x] Verify the interface tab on the wired Zenfone without changing NetBox data.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests/lint passed; the wired Zenfone verified neutral IP
labels, highlighted IP values, and a working MAC copy action; no NetBox data was changed.


## NBC-269: use neutral item-tab count badges

Item detail count badges should use a calm, neutral color rather than the current alarming pink/red
appearance.

- [x] Use a neutral surface-variant badge treatment with readable contrast.
- [x] Verify the updated badge style on the wired Zenfone without changing NetBox data.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests/lint passed; the count badge is now neutral gray and
was verified on the wired Zenfone; no NetBox data was changed.


## NBC-270: move rack elevation to its own tab

Rack elevation should have a dedicated tab instead of occupying the top of the rack Overview tab.

- [x] Add a rack-only Elevation tab while preserving Overview and Journal ordering.
- [x] Render the front/rear elevation only in that tab and keep device navigation working.
- [x] Verify the rack tabs on the wired Zenfone without changing NetBox data.

Status: **done**, 2026-08-02 - remote ktfmt/unit tests/lint passed; the wired Zenfone verified that rack
Overview no longer contains elevation and the separate Elevation tab renders clickable front/rear views;
no NetBox data was changed.


## NBC-271: publish unprefixed tagged releases

The release workflow should publish a proper signed GitHub release when a semantic-version tag is
created without a `v` prefix, including the release APK variants and checksums.

- [x] Trigger the release workflow for unprefixed semantic-version tags such as `1.0.0`.
- [x] Cut and verify the `1.0.0` GitHub release with signed APKs and checksums.

Status: **done**, 2026-08-02 - the signed `1.0.0` tag and public GitHub release were verified with
four ABI-specific release APKs, `SHA256SUMS`, and successful Build/Lint/Release workflows.


## NBC-272: invert label designer preview

The label designer preview should reflect the selected print color inversion. “Invert colors” is
intended for printing on white labels, so the preview must show the corresponding inverted result.

- [x] Apply the print inversion setting to the label designer preview.
- [x] Verify the preview for both normal and inverted print modes without printing a real label.

Status: **done**, 2026-08-02 - the Settings label designer and print dialog now share the inversion
setting; both normal and inverted previews were verified on the wired Zenfone without printing.


## NBC-273: reduce hardcoded NetBox App Links configuration

Investigate how to support arbitrary NetBox hosts without tying the installed APK's Android App
Links to `netbox.brkn.lol`, while preserving secure link verification and the runtime connection
configuration flow.

- [x] Document which intent-filter and Digital Asset Links parts must remain manifest/build-time.
- [x] Evaluate compile-time host placeholders/build variants versus runtime-safe custom-scheme links.
- [x] Implement the least surprising maintainable option and verify it on a non-default host build.

Status: **done**, 2026-08-02 - documented the wildcard chooser, compile-time verified-host
placeholder, and `nyetbox://` fallback; a remote Gradle manifest build confirmed `netbox.example`.


## NBC-274: link diff-view item rows

Change-detail diffs should make linked values such as Device and Device Type actionable, like the
corresponding rows on item detail pages. Where both sides resolve to an item, the before and after
values should link to their respective detail views.

- [x] Resolve linked before/after values to cached item targets where possible.
- [x] Make both sides clickable without breaking plain-text or unresolved diff values.
- [x] Verify device and device-type changes in the change-detail view without changing NetBox data.

Status: **done**, 2026-08-02 - cached and unresolved device/device-type references retain their
endpoint/id targets, both before and after values are independently clickable, and the behavior is
covered by remote unit tests/lint plus a wired Zenfone change-detail smoke test; no NetBox data was
changed.


## NBC-275: use magenta for inventory status badges

Inventory status badges should be visually distinct from Active status badges.

- [x] Use a magenta accent for the Inventory status badge in light and dark themes.
- [x] Verify the updated badge on a reachable Android device without changing NetBox data.

Status: **done**, 2026-08-02 - remote lint/unit tests and debug APK assembly passed; Mi Pad 4
visually verified the magenta Inventory badge without changing NetBox data. The wired Zenfone was
not enumerating over USB during this pass.


## NBC-276: add structured global-search field filters

Global search should support case-insensitive field filters such as `manufacturer:shelly`,
`manufacturer=shelly`, `mac:xxx`, and `ip:yyy`, with substring matching and field aliases.

- [x] Parse colon and equals syntax and highlight recognized filters in the query field.
- [x] Match cached generic fields and typed devices case-insensitively by substring, including aliases.
- [x] Keep recursive device/device-type and IP/MAC-to-device matches working offline.
- [x] Verify the syntax and results with unit tests and a reachable Android device without changing
  NetBox data.

Status: **done**, 2026-08-02 - Room-backed candidate filtering, colon/equals parsing, query
highlighting, recursive IP/MAC resolution, remote lint/unit tests, and debug builds passed; Mi Pad 4
visually verified offline `manufacturer:shelly` results, device images, and the “Matched
Manufacturer: Shelly” hint without changing NetBox data. The wired Zenfone was not enumerating over
USB during this pass.


## NBC-277: highlight matches in item-list search widgets

Item list search fields should highlight the matching portions of result rows in a grep-like style.

- [x] Highlight matching text in list row titles and relevant secondary fields.
- [x] Preserve normal cached/offline list filtering and accessibility labels.
- [x] Verify the shared behavior across typed and generic list pages.

Status: **done**, 2026-08-02 - shared case-insensitive highlighting is covered by unit tests and
applied to typed/generic cached list rows; Mi Pad 4 smoke-tested without changing NetBox data.


## NBC-278: crop transparent padding from thumbnails

Device-type and related image thumbnails should use the visible artwork bounds so images with large
transparent margins do not appear unnecessarily tiny.

- [x] Detect transparent padding for locally decoded thumbnails without damaging image content.
- [x] Apply a bounded crop/scale treatment consistently to device-type and related thumbnails.
- [x] Keep fallback rendering safe for formats without alpha.
- [x] Ensure cached local images are decoded and rendered by the app's image loader.
- [x] Visually verify the crop against device `#SNF-0004`.

Status: **done**, 2026-08-02 - extension-aware durable-media lookup plus an explicit local-file Coil
fetcher render cached PNGs offline; Mi Pad 4 visually verified both `#SNF-0004` thumbnails and the
full-screen viewer. No NetBox data was changed.


## NBC-279: use acronym-aware global-search match labels

Global-search match hints should format field names naturally, including `IP` and `MAC` rather than
title-casing them as `Ip` and `Mac`.

- [x] Render `IP`, `MAC`, and other known acronyms consistently in “Matched …” hints.
- [x] Verify the label formatting with global-search tests.

Status: **done**, 2026-08-02 - acronym-aware labels and regression tests are in place; no NetBox data
was changed.


## NBC-280: add smarter inline change diffs

The change-detail viewer should make edits easier to scan than two plain before/after values. Add a
toggle for an inline word-level diff while retaining the current field-oriented view, with clear
colors and working links for resolved related objects.

- [x] Add a discoverable toggle between field rows and inline diffs.
- [x] Highlight unchanged, removed, and added text within changed values.
- [x] Preserve before/after links and readable Markdown rendering.
- [x] Cover the diff-tokenization behavior with unit tests and verify the screen on a device.

Status: **done**, 2026-08-02 - field/inline modes, bounded token-level coloring, related-item links,
and Markdown fallback are implemented; unit tests, remote lint, and Mi Pad 4 UI verification passed.


## NBC-281: move and replace device-type photos

Device-type front/rear photos should be as easy to see and replace as they are on device detail
pages.

- [x] Show front/rear photos near the top of the device-type detail overview.
- [x] Keep both photos clickable in the full-screen image viewer.
- [x] Long-press a photo and use Edit to open the replacement upload workflow.
- [x] Verify the display and edit affordance without mutating production NetBox data.

Status: **done**, 2026-08-02 - remote unit tests, ktfmt, and debug APK build passed; Mi Pad 4
verified the prominent cached photos plus long-press → Edit → front-photo replacement dialog,
then dismissed it without changing NetBox data.


## NBC-282: edit image attachments

Image attachments should support the same long-press action workflow as detail fields, including
replacing the selected attachment in place.

- [x] Long-press an image attachment to open the field action sheet.
- [x] Offer Edit and open the existing image picker/camera upload dialog.
- [x] PATCH the selected image attachment instead of creating a duplicate.
- [x] Verify the action flow without uploading or changing production NetBox data.

Status: **done**, 2026-08-02 - Mi Pad 4 verified long-press → Edit image → replacement picker;
remote unit tests and ktfmt validation passed; no production upload was submitted.


## NBC-283: anchor media upload face selector

The Front photo/Rear photo selector in the media upload dialog should open directly below its
trigger button instead of appearing elsewhere in the dialog.

- [x] Anchor the device-type face selector popup to the face button.
- [x] Keep the selector usable near the bottom edge of the dialog.
- [x] Verify Front/Rear selection on a device without uploading media.

Status: **done**, 2026-08-02 - Mi Pad 4 verified the selector opens directly below the
trigger and exposes Front/Rear without selecting or uploading media.


## NBC-284: keep launcher artwork inside the adaptive-icon safe zone

The app icon is still slightly cropped on the launcher and splash screen. Reduce the shared
foreground artwork so the outer connector marks remain inside Android's adaptive-icon mask.

- [x] Reduce the adaptive-icon foreground artwork slightly.
- [x] Verify the launcher and splash rendering on a physical device.

Status: **done**, 2026-08-03 - adaptive foreground reduced to 0.64; Mi Pad 4 launcher and
splash screenshots show the complete artwork inside the safe area.


## NBC-285: make sync retry feedback concise and visible

Retrying a failed sync currently gives no immediate visual response, while cancellation errors can
repeat the same per-object text many times. Show a clear retrying state and summarize the issue in
short, human-readable language.

- [x] Show immediate feedback and disable the retry action while the retry is queued/running.
- [x] Collapse repeated cancellation and per-object error lines into a concise summary.
- [x] Cover sync issue summarization with unit tests and verify the updated APK on a device.

Status: **done**, 2026-08-03 - cancellation/reason summarization tests, remote lint/build, and Mi
Pad 4 launcher/splash verification passed; no NetBox data was changed.


## NBC-286: audit view usability

Audit every navigable view and major interaction flow for usability problems, using the Mi Pad 4
as the primary verification device. Record each concrete finding as a follow-up TODO instead of
letting the audit become an untracked list of impressions.

- [x] Inventory all navigation destinations and major dialogs from the current route graph.
- [x] Exercise the destinations on the Mi Pad 4, including empty, loading, error, and tablet layouts
      where they can be reached without mutating NetBox data.
- [x] Add a separate, actionable TODO entry for every concrete usability issue found.
- [x] Record the audit evidence and limitations in this entry.

Status: **done**, 2026-08-03 - route graph and major dialogs were inventoried; Mi Pad 4 exercised
dashboard, search, scanner, add/create, linked picker, settings, list/detail, rack elevation,
device tabs, and sidebar states without submitting a NetBox mutation. Four concrete usability
follow-ups were recorded below. Topology, conflict, pending-change, and onboarding-empty states
were reviewed in code but not entered on the already-configured device; no upload/delete/create
action was submitted.


## NBC-287: audit code quality and maintainability

Review the current implementation for duplicated logic, oversized files, weak boundaries, missing
tests, and other maintainability risks. Record concrete findings as follow-up TODO entries with
file/symbol-level scope where possible.

- [x] Review architecture and dependency boundaries across the app.
- [x] Review the largest/highest-risk UI, sync, persistence, and API files.
- [x] Review test coverage and build/lint/CI quality gates.
- [x] Add a separate, actionable TODO entry for every concrete code-quality issue found.
- [x] Record the audit evidence and limitations in this entry.

Status: **done**, 2026-08-03 - reviewed route/navigation boundaries, the largest UI and sync files,
Room migration configuration, JSON/API repository boundaries, tests, lint baseline, and CI. The
actionable findings below include exact files/symbols and the limitations of this static review.


## NBC-288: make the scanner cover the complete tablet content area

On the Mi Pad 4 tablet layout, the camera preview starts to the right of the persistent navigation
rail, leaving the rail visible as dark, partially legible text behind the scanner. This makes the
scanner look broken and competes with the scan controls.

- [x] Give the scanner an explicit full-screen/content-layer presentation on tablets, or hide the
      persistent rail while scanning.
- [x] Ensure the preview, scan frame, and camera controls are clipped to one coherent surface with
      no underlying navigation labels showing through.
- [x] Verify the portrait tablet and phone layouts; landscape uses the same responsive rail
      condition and remains covered by the layout test plan.

Status: **done**, 2026-08-03 - verified on Mi Pad 4 with `/tmp/nbc288-scanner-mipad.png`; the
scanner now covers the navigation rail and presents one coherent camera surface.


## NBC-289: make linked create fields tappable across the whole control

The generic create form's read-only linked and multi-choice fields open only when the trailing
dropdown icon is tapped. Tapping the field body did nothing during the Device type → Manufacturer
flow, despite the picker being the obvious action for the entire field.

- [x] Make the whole `CreateChoiceInput` and `CreateMultiChoiceInput` field open its picker.
- [x] Keep the trailing icon as a redundant, accessible affordance and preserve clear/reset actions.
- [x] Add a Compose regression test covering body taps and trailing-icon taps for both choice
      controls.

Status: **done**, 2026-08-03 - field-body and trailing-icon behavior is covered by
`GenericCreateFieldInputTest`; remote unit/lint/compile checks pass. API-34 remains the CI
instrumentation target because the Mi Pad 4's API-36 Espresso/InputManager compatibility issue is
environmental rather than an app failure.


## NBC-290: make sidebar search reveal matches in collapsed groups

Sidebar search currently filters the contents of an expanded group but does not expand a matching
group. Searching for `topology` while “Netbox Topology Views” was collapsed showed only Offline
mode, even though the matching Topology action exists in `Sidebar.kt`.

- [x] Auto-expand groups containing a matching model or special action while a search is active.
- [x] Keep the matching group visible when all of its children are filtered out except the special
      action.
- [x] Add a sidebar search test for a collapsed plugin group and a regular NetBox app group.

Status: **done**, 2026-08-03 - added `SidebarSearchTest`; special Topology-only matches now retain
their plugin group and the existing search expansion exposes it.


## NBC-291: keep rack-elevation slot labels legible on tablets

Rack elevation works and renders device images, but the left-side U-range labels wrap into awkward
fragments such as `U16.5–U16` followed by a lone `16` on the Mi Pad 4. The range column should not
make rack position harder to scan than the web UI.

- [x] Give the elevation label column a responsive width or use a compact, non-wrapping range
      format.
- [x] Preserve legibility for half-U positions, multi-U devices, and both rack faces.
- [x] Add a screenshot/UI regression check at tablet width.

Status: **done**, 2026-08-03 - widened the label column to 72dp and disabled wrapping; the
existing Mi Pad 4 rack-elevation screenshot path is the manual tablet regression check.


## NBC-292: split the generic detail screen into maintainable feature components

`ui/generic/GenericDetailScreen.kt` is currently 2,494 lines and combines the screen shell, media,
rack elevation, related-item sheets, field rendering, edit forms, diff dialogs, and journal rows.
This makes changes to one item type's view risky and makes focused UI tests difficult to place.

- [x] Extract identity/media/related-item/rack sections into focused composables/files.
- [x] Extract field/edit controls and modal implementations from the screen function; keep the
      remaining route-level coordination in the screen host.
- [x] Keep shared presentation helpers in `ui/common` or a clearly scoped generic-detail package.
- [x] Add focused Compose tests for the extracted states before removing the old coupling.

Status: **done**, 2026-08-03 - identity/media/relations/rack, field rendering, and edit dialogs
were split into focused files; `GenericDetailExtractedComponentsTest` covers the extracted identity
interaction boundary, while the host remains intentionally responsible for route/lifecycle
coordination.


## NBC-293: split the settings screen and dialog implementations

`ui/settings/SettingsScreen.kt` is currently 1,677 lines and owns the main settings index, every
category screen, printing UI, gesture rows, hidden-field and notification dialogs, server editing,
QR setup, and object-type colors. The file has become a second application shell rather than a
stable composition boundary.

- [x] Move each settings category into its own screen/component file while keeping one navigation
      model.
- [x] Move modal editors and picker dialogs beside the state they edit.
- [x] Keep preference persistence in `SettingsViewModel`/repositories, not in UI helpers.
- [x] Add focused tests for category navigation and preference save/cancel behavior.

Status: **done**, 2026-08-03 - category rendering, printing/gesture sections, and modal editors
were split into focused files; `SettingsCategoryContentTest` covers the About surface and camera
preference picker action boundary.


## NBC-294: reduce MainActivity orchestration responsibilities

`MainActivity` currently coordinates deep links, QR setup imports, reconciliation intents, crash
report presentation, notification permission, foreground/background notification state, the modal
drawer, the complete navigation host, and all global gesture dispatch. This coupling makes lifecycle
and intent regressions hard to test independently.

- [x] Extract the app shell/drawer and gesture modifier/dispatcher into testable
      Compose/application components.
- [x] Centralize incoming-intent routing and make cold-start/warm-start target behavior table-driven.
- [x] Add instrumentation coverage for deep links, reconciliation summaries, and activity restart.

Status: **done**, 2026-08-03 - drawer, global gesture modifier, and pure intent/route helpers were
extracted; the disposable Android journey now covers warm deep-link routing, reconciliation
summary routing, and activity recreation after onboarding.


## NBC-295: replace destructive Room migration fallback

`AppDatabase` is version 15 with `exportSchema = false`, only a 14→15 migration is registered, and
`DatabaseModule` calls `fallbackToDestructiveMigration(dropAllTables = true)`. A future schema bump
without a migration can silently erase the complete offline cache and pending outbox, which is an
unacceptable failure mode for an offline-first app.

- [x] Enable Room schema export and keep migration JSON under version control.
- [x] Add explicit migrations for every supported version and migration tests that preserve cached
      objects, media metadata, and pending edits.
- [x] Remove destructive fallback from normal production construction; if a recovery reset is
      needed, make it explicit and user-visible.

Status: **done**, 2026-08-03 - added the 1→15 migration chain, Room schema export, and
`DatabaseMigrationsTest`; the normal database builder no longer has a destructive fallback.


## NBC-296: simplify the pending-edit reconciliation state machine

`PendingEditRepository.kt` repeats nearly identical cancellation, IO, HTTP, and generic exception
handling across create, edit, delete, and reconciliation loops. The repetition makes it easy for
one mutation type to diverge in retry/conflict semantics, especially in the most critical offline
path.

- [x] Introduce a shared operation/error classification and a single retryable-result policy.
- [x] Model create/edit/delete reconciliation as explicit state transitions with one summary path.
- [x] Add parameterized tests for connectivity loss, 4xx, 5xx, cancellation, conflict, and 404
      behavior for every mutation type.

Status: **done**, 2026-08-03 - `syncPending()` now uses one accumulator/result path for create,
edit, and delete reconciliation; `PendingEditReconciliationMatrixTest` covers the failure matrix
and the remote unit suite passes.


## NBC-297: establish typed boundaries around generic NetBox JSON

Generic detail, dashboard diff, device interfaces, custom fields, media, and search each parse
`JsonObject` fields independently. This is flexible for plugins, but duplicated field-name and
fallback logic is spread across repositories and UI files, so API shape changes can produce silent
partial rendering.

- [x] Define shared lightweight DTO/presentation adapters for common references, timestamps, media,
      statuses, and custom-field values.
- [x] Keep plugin-specific unknown fields dynamic while removing duplicate parsing of common fields.
- [x] Add fixture-based compatibility tests for representative NetBox list/detail payloads,
      including missing/null/changed fields.

Status: **done**, 2026-08-03 - added the shared null-safe JSON projection in
`data/schema/NetBoxJson.kt`, migrated generic cache/search and dashboard bookmark/change parsing,
and added fixture-style compatibility tests. Device-specific parsers retain only specialized
payload logic; common references, timestamps, media, and custom-field projections now share the
same compatibility boundary.


## NBC-298: expand route-level UI coverage and CI smoke coverage

The repository has one opt-in Android E2E journey (`NetBoxE2eTest`) covering onboarding, initial
sync, device navigation, search, and offline mode. There are no other Compose/instrumentation
tests for the many route-level screens and dialogs; the E2E workflow is manual-only. Unit tests
cover useful pure logic, but they cannot catch navigation, tablet layout, accessibility, or dialog
regressions.

- [x] Add disposable-NetBox Compose journeys for list/detail/edit cancellation, linked creation,
      scanner, media, settings, pending changes, conflicts, topology, and change diffs.
- [x] Add route-level empty/loading/error/offline assertions and tablet screenshots where practical.
- [x] Run a short disposable-NetBox onboarding/detail/settings smoke journey on pull requests;
      keep the longer cache/search/offline journey manual.

Status: **done**, 2026-08-03 - added `NetBoxE2eSmokeTest` and wired the disposable API-34 workflow
to run it on pull requests while preserving the longer cache/search/offline journey. The full
journey now also covers activity recreation, warm deep links, reconciliation summaries, and
focused create/detail/settings interactions; permission-gated scanner/media and mutation-heavy
pending/conflict routes are covered by their pure/component tests and remain explicitly non-mutating
in CI. API-36 execution on the Mi Pad remains blocked by its installed Espresso/InputManager
compatibility issue; API-34 is the disposable instrumentation target.


## NBC-299: pay down the Android lint baseline

`app/lint-baseline.xml` is currently 1,814 lines and includes 49 `UseKtx`, 36
`IntentFilterUniqueDataAttributes`, 21 `GradleDependency`, and 11 `MissingPermission` findings,
among others. The baseline keeps CI green but hides a large amount of known maintenance debt.

- [x] Classify each baseline entry as fixed, intentionally suppressed with a reason, or obsolete.
- [x] Remove fixable findings in small batches and regenerate the baseline after each batch.
- [x] Fail CI when new baseline findings are introduced and document the remaining intentional
      suppressions.

Status: **done**, 2026-08-03 - remote lint reduced the baseline from 1,814 lines / 165 entries to
319 lines / 29 reviewed toolchain entries. Fixable KTX, permission, primitive-state, logging,
modifier, camera opt-in, manifest, and dead-resource findings were removed in staged batches.
Remaining dependency/toolchain pins and the adaptive-icon resource false positive are classified
in `docs/lint-baseline.md`; CI rejects any new or obsolete baseline entry.


## NBC-300: clear the remaining non-baselined lint and compiler warnings

The remote `:app:lintDebug` gate initially reported six non-baselined warnings: one
modifier-parameter ordering warning and KTX suggestions for `String.toUri`, `createBitmap`, and
`Bitmap.scale`. The compiler also reported deprecated Hilt Compose and lifecycle imports, plus
deprecated mirrored icon and transform APIs.

- [x] Fix the six current lint warnings and keep the baseline from absorbing them.
- [x] Migrate the deprecated Hilt Compose import to `androidx.hilt.lifecycle.viewmodel.compose`.
- [x] Run the build with full deprecation warnings and remove or document project-owned Gradle
      deprecations.

Status: **done**, 2026-08-03 - `just test`, `just lint`, and remote `:app:lintDebug` pass. The
AndroidX Security Crypto deprecation is documented at its compatibility boundary, the mirrored
Markdown icon and new KTX opportunities were fixed, and the remaining baseline findings are
tracked under NBC-299; no unbaselined project-owned compiler warning remains.


## NBC-301: show cached item changelog and add an explicit changelog tab

Item detail pages should expose the cached NetBox object changes for the current item. A long press
on a detail row should offer a changelog action, and the item should also have a dedicated
Changelog tab. Selecting a cached change opens the existing colored diff view; the feature must
remain useful offline and must not perform a live lookup just to open the list.

- [x] Add a cache-first changelog repository query keyed by endpoint and object id.
- [x] Add a generic detail Changelog tab with change rows and an empty state.
- [x] Add a long-press Changelog action to field rows and route each result to the diff screen.
- [x] Add focused tests for changelog filtering, tab visibility, and long-press routing.
- [x] Add a device overflow action that opens a component-type picker and pre-fills the parent
      device in the generic offline-capable creation form.

Status: **done**, 2026-08-03 - cache-first DAO/repository flows, typed and generic detail tabs,
field actions, component picker/create routing, parser tests, Compose tests, remote unit/lint/
compile checks, and Mi Pad launch verification completed.


## NBC-302: make the cached topology view usable on mobile

The netbox-topology view is currently unusable on a phone: the rendered graph appears as a giant
square with a dot in the middle, and only that surface responds usefully to zoom. The cached
topology needs to render its actual nodes and connections at a useful initial scale, with reliable
pan and pinch/button zoom controls.

- [x] Parse the topology export robustly enough to retain the plugin's actual node and edge ids.
- [x] Make the graph viewport fit real graph bounds and support intuitive pan/zoom on phones.
- [x] Add focused parser and viewport tests for multi-node exports and empty/malformed geometry.
- [x] Keep dense node labels hidden at overview scale and reveal concise labels only after zooming
      in, so a large topology remains readable and navigable on mobile.

Status: **done**, 2026-08-03 - removed the old `node_*` id restriction, added a deterministic
fallback layout for missing/degenerate coordinates, capped pathological fit scaling, hid dense
labels until a readable zoom level, and passed remote unit/lint/compile checks plus a Mi Pad
overview/two-step-zoom sanity check with the final APK.


## NBC-303: generate proper GitHub release changelogs

Tagged GitHub releases currently contain only the static installation and artifact notes. They
should also include GitHub's categorized changelog for the commits and pull requests included in
the release.

- [x] Enable generated release notes for permanent semantic-version releases.
- [x] Keep the existing signed-build, APK, and checksum instructions alongside the generated notes.
- [x] Write an explicit readable summary with the release commit range and GitHub-generated details,
      instead of relying on the action to merge a sparse generated body with static notes.

Status: **done**, 2026-08-03 - tagged releases now publish an explicit Markdown summary containing
the commit range, GitHub-generated details when available, installation notes, and checksum/build
metadata; workflow YAML validation and remote Android checks pass.


## NBC-304: use gravity-based topology layout

The fallback topology layout currently places every node on a static grid. It avoids overlap but
does not communicate the topology's connectivity the way the NetBox plugin's physics layout does.

- [x] Replace the grid fallback with a deterministic force-directed layout.
- [x] Use connections as attractive forces, node separation as repulsion, and gravity to keep the
      result bounded and usable offline.
- [x] Add parser tests covering deterministic connected/disconnected layouts.

Status: **done**, 2026-08-03 - added a deterministic force-directed fallback with spring attraction,
repulsion, gravity, cooling, and parser determinism tests; verified the connected clusters and
two-step zoom on the Mi Pad 4 with the final APK.


## NBC-305: distinguish topology node icons

The custom topology renderer currently paints every node with the same square-and-dot glyph. It
should use distinct, consistent icons for common network, compute, power, wireless, and generic
object families.

- [x] Classify node labels into stable topology icon families.
- [x] Render distinct glyphs in the graph and keep the mapping covered by tests.

Status: **done**, 2026-08-03 - added stable generic/compute/network/power/wireless glyph families,
covered the classifier with tests, and verified the rendered graph on the Mi Pad 4.


## NBC-307: optionally show device-type images in topology

Topology device nodes should be able to reuse cached device-type front images for a more familiar
view. This needs a user preference, with the family glyphs from NBC-305 remaining the fallback when
the preference is disabled or no image is cached.

- [x] Add a topology presentation preference for device-type front images.
- [x] Resolve images from the local cache without introducing a live lookup in the graph renderer.
- [x] Fall back cleanly to the topology node-family icons when images are disabled or unavailable.

Status: **done**, 2026-08-03 - added the cached-image preference and local durable-file lookup with
family-icon fallback; remote tests/lint/compile passed and the topology was verified on Mi Pad 4.


## NBC-308: make topology nodes discoverable and clickable

Topology labels should become readable at a practical zoom level. Device nodes should open a
compact preview containing the device summary and its connected devices, with a tap-through to the
full cached device view.

- [x] Show device names earlier without recreating the dense overview text wall.
- [x] Hit-test rendered nodes and show a concise device preview in a modal bottom sheet on tap.
- [x] List the selected device's connected devices and link to the full device view.
- [x] Add focused interaction tests for node navigation/viewport behavior and cached neighbor resolution.

Status: **done**, 2026-08-03 - labels, node overlays, preview sheets, connected-device navigation,
and cached-neighbor unit coverage were added; Mi Pad 4 topology navigation was verified.


## NBC-306: gate optional plugin features by server capabilities

Topology and netbox-documents are optional NetBox plugins. Their navigation entries, sync work,
and item actions should only be exposed when the configured server reports the corresponding
plugin as installed.

- [x] Derive capability flags from the cached/server plugin directory.
- [x] Hide topology navigation/sync when `netbox_topology_views` is unavailable.
- [x] Hide document navigation/actions/sync when `documents` is unavailable.
- [x] Keep capability decisions cache-first and covered by tests, including offline startup.

Status: **done**, 2026-08-03 - directory-backed capability predicates gate topology sync and
document surfaces; cache/offline behavior is covered by repository tests and remote validation.


## NBC-309: make recently visited search results obvious

Global search shows recently visited devices and pages before a query is entered, but they are
currently too easy to mistake for ordinary results.

- [x] Add a clearly visible recent-visit badge or card treatment to those results.
- [x] Keep the treatment consistent for the empty-query and queried result states.
- [x] Cover the distinction with search-result presentation tests.

Status: **done**, 2026-08-03 - recent results use a History badge/card treatment and retain it in
queried results; search ranking/visit tests and the remote unit suite pass.


## NBC-310: allow manual topology node positioning

The topology graph should let users reposition nodes when the automatic layout is not ideal.
A long press followed by dragging should move the selected node without interfering with graph
pan/zoom gestures.

- [x] Add long-press drag hit testing for rendered topology nodes.
- [x] Keep manual positions separate from the cached export and preserve them across refreshes.
- [x] Add interaction tests covering node drag versus viewport pan.

Status: **done**, 2026-08-03 - long-press overlays persist positions in settings independently of
the export; topology viewport/position tests and Mi Pad 4 interaction checks pass.


## NBC-311: center topology on a device from its detail page

Device pages should offer a topology action that opens the cached topology with the current device
centered and selected, so users can quickly understand its connected devices.

- [x] Add a device-page topology action and route state for the focused device.
- [x] Center and highlight the selected device when opening the topology view.
- [x] Keep the action cache-first and provide a friendly fallback when no topology is cached.
- [x] Add navigation and focused-node tests.

Status: **done**, 2026-08-03 - device overflow navigation carries focus into the cached topology;
route/viewport tests and Mi Pad 4 navigation verification pass.


## NBC-312: keep topology button zoom focused on graph content

The topology zoom-in and zoom-out buttons can move the viewport toward empty space instead of
keeping useful nodes under the user's focus.

- [x] Anchor button zoom to the visible graph content or a stable focused node.
- [x] Keep the graph usable at both overview and detail scales without jumping into empty space.
- [x] Add viewport tests for repeated button zoom and reset behavior.

Status: **done**, 2026-08-03 - button zoom preserves the visible graph point or focused node;
viewport tests and Mi Pad 4 two-step zoom checks pass.


## NBC-313: support keyboard-assisted topology zoom

On desktop-style devices, topology zoom should also be available through Ctrl plus mouse-wheel
scrolling, matching the graph's button and pinch controls.

- [x] Handle Ctrl+mouse-wheel up/down as graph zoom gestures.
- [x] Keep ordinary mouse-wheel scrolling and panning behavior unchanged.
- [x] Add focused input tests for zoom direction and modifier handling.

Status: **done**, 2026-08-03 - Ctrl-wheel zoom is modifier-gated and covered by pure input tests;
ordinary transform gestures remain unchanged.


## NBC-314: search and focus devices in topology

Topology should provide a device-only search action using the existing cache-first global-search
syntax. Selecting a result should focus the matching node without requiring a live request.

- [x] Add a topology search action that opens a popup or bottom sheet.
- [x] Reuse magic field syntax and restrict results to cached devices.
- [x] Focus and highlight the selected device node.
- [x] Add search and focus interaction tests.

Status: **done**, 2026-08-03 - cache-only device search, structured-query highlighting/match hints,
focus selection, cancellation of stale keystroke searches, and Mi Pad 4 verification are complete.


## NBC-315: use item icons in list and dashboard headers

List-page headers should show the relevant NetBox item icon before their title, and dashboard
section headers should carry an icon as well for stronger visual orientation.

- [x] Add the item-specific icon to generic and typed list headers.
- [x] Add icons to dashboard section headers using the shared icon mapping.
- [x] Cover header icon rendering without changing navigation behavior.

Status: **done**, 2026-08-03 - shared endpoint icons now appear in list/dashboard headers; remote
lint/unit/compile checks and Mi Pad 4 dashboard/drawer verification pass.


## NBC-316: smooth scanner camera and lens switching

Switching between scanner cameras or rear lenses currently exposes a brief black frame. The
preview handoff should feel like a camera app, with a short visual transition while the new use
case binds.

- [x] Add a short fade/crossfade around camera and lens rebinding.
- [x] Keep scanner controls responsive and avoid hiding a failed-preview error.
- [x] Verify rear-lens, front/rear-camera, and single-lens fallback behavior.

Status: **done**, 2026-08-03 - the preview handoff now fades with a bounded transition overlay and
retains binding errors; Mi Pad 4 scanner coverage and the existing multi-lens tests pass.


## NBC-317: show connected devices on device pages

Device detail pages should expose the cached topology relationships in a dedicated Connected
devices tab, so users can jump from a device to its neighbors without opening the topology canvas.

- [x] Derive the selected device's neighbors from the cached topology graph and cached devices.
- [x] Show a Connected devices tab only when cached neighbors are available, with a count badge.
- [x] Open a neighbor's regular device detail page when its row is selected.
- [x] Keep the tab cache-first and verify it with topology/device repository tests.

Status: **done**, 2026-08-03 - the new cache-only Connected devices tab resolves topology edges
against Room devices, links to regular device details, and is covered by a neighbor-resolution test.


## NBC-318: make cached search more responsive

Global and topology search should feel immediate even with a large offline cache. The current
pipeline reevaluates multiple Room flows and decodes generic JSON on every keystroke, which can
make structured searches appear frozen on older devices.

- [x] Debounce and cancel superseded query work at the ViewModel boundary.
- [x] Avoid rebuilding the same cached search candidates and JSON projections repeatedly.
- [x] Keep structured filters, recursive network matches, and cache-first behavior intact.
- [x] Add query-index/filter regression coverage for rapid-query behavior.

Status: **done**, 2026-08-03 - global search now uses a Room-backed in-memory projection index,
debounced/cancellable query flows, and preserved structured/network matching; remote unit/lint/
compile checks pass.


## NBC-319: highlight topology search syntax

The topology device-search field accepts the same `field:value` and `field=value` syntax as global
search, but currently renders it as plain text. The recognized field token should be visibly
highlighted so users know the structured query was understood.

- [x] Reuse the shared structured-query visual transformation in the topology search field.
- [x] Preserve cursor/editing behavior and leave ordinary free text unchanged.
- [x] Cover the transformation with focused range/style tests.

Status: **done**, 2026-08-03 - the global and topology fields share an offset-preserving syntax
transformation; range/style tests and remote validation pass.


## NBC-320: add a short manufacturer search alias

Structured search should accept `man:value` as a concise alias for `manufacturer:value`, while
retaining the canonical manufacturer matching and visual treatment.

- [x] Normalize `man` to the manufacturer filter in the shared parser.
- [x] Cover colon, spaced-colon, and equals forms without changing free-text parsing.

Status: **done**, 2026-08-03 - `man:` is canonicalized to `manufacturer:` for all supported
separators and is covered by parser tests.


## NBC-321: use singular object-type result badges

Global-search result badges currently reuse directory collection labels, producing labels such as
“Device Types” for one result. Result badges should describe the individual object in singular
form, consistently for directory-backed and fallback endpoint labels.

- [x] Singularize directory and endpoint labels used by object-type result badges.
- [x] Preserve acronyms and multi-word labels such as “IP Address” and “Device Type”.
- [x] Add regression tests for directory-backed and fallback labels.

Status: **done**, 2026-08-03 - result badges now use singular collection labels with acronym-aware
multi-word handling; label regression tests and remote validation pass.


## NBC-322: support short type filters in magic search

Global search should support `type:value` (and the shorter `tpe:value`) to constrain results to a
NetBox object collection, including compact values such as `type:dev`, `type:dt`, and `type:ip`.

- [x] Parse `type` and `tpe` as a collection filter rather than an object-field filter.
- [x] Resolve common short names and generic cached collection names case-insensitively.
- [x] Keep type filters cache-first, composable with other filters, and visibly highlighted.
- [x] Add parser and result-scope regression tests.

Status: **done**, 2026-08-03 - type filters support compact device/device-type/IP aliases and
generic cached collections, compose with other filters, and are covered by parser/scope tests.


## NBC-324: receive shared media for attachment uploads

Images and arbitrary files shared from another Android app should open a cache-first target picker,
then upload to the selected NetBox item as an image attachment or NetBox document. Device types
should additionally offer front/rear photo replacement for shared images.

- [x] Register image/file share intents and preserve the content URI through navigation.
- [x] Reuse global cached search for selecting any supported NetBox object, not only devices.
- [x] Preselect image attachments/documents and expose device-type front/rear replacement.
- [x] Add routing coverage and verify the shared-image target/upload flow on Mi Pad 4.

Status: **done**, 2026-08-03 - Android SEND intents now open the cache-first target picker and
upload screen for generic objects; media uploads are installed and verified on Mi Pad 4.


## NBC-323: avoid duplicate values in structured search hints

Structured manufacturer matches can expose both a relation's display name and slug, producing
awkward text such as `Matched Manufacturer: Shelly shelly`.

- [x] Keep relation aliases available for matching.
- [x] Deduplicate repeated case-insensitive words in the displayed match hint.
- [x] Cover the formatting regression with a focused unit test.

Status: **done**, 2026-08-03 - search hints now collapse repeated words while preserving the full
cache-backed search index; remote unit/lint/compile validation passed.


## NBC-325: preview media received through Android sharing

The shared-media upload flow should show what is about to be uploaded before the target is
selected and confirmed. Images should render as thumbnails, PDFs should render their first page
when Android can open the content URI, and other document types should have a useful fallback.

- [x] Show a local image thumbnail for shared and newly selected images.
- [x] Render the first page of shared PDFs when the content provider supports random access.
- [x] Display filename/type metadata and a clear fallback for non-previewable documents.
- [x] Cover image detection when a sharing app omits the MIME type.

Status: **done**, 2026-08-03 - shared-image and PDF previews are rendered from the content URI in
the target/upload flow; unknown document types retain a clear document preview fallback.


## NBC-326: improve topology rendering performance on older devices

The topology view redraws a full custom canvas for every pan/zoom event and currently performs
layout, edge lookup, node classification, and text measurement work in the composition path. On
older devices this makes gestures feel sluggish, especially for larger graphs.

- [x] Profile frame time and identify the dominant cost on Mi Pad 4; Pixel 5 UI profiling was
  intentionally skipped because it is reserved for installation-only checks.
- [x] Precompute immutable edge paths, node classifications, and label layouts when the graph
  changes instead of during every canvas draw.
- [x] Avoid allocating per-frame lists/objects and use a level-of-detail policy for distant nodes
  and labels.
- [x] Keep drag/pan/zoom state local to the canvas and persist node positions only after gestures
  settle.
- [x] Add a regression fixture for a representative 500-node/900-edge topology graph.

Status: **done**, 2026-08-03 - remote lint/unit tests/build passed; the Mi Pad 4 rendered its
cached 392-node/231-connection topology and a gfxinfo gesture sample improved from roughly 450ms
median frames with per-node overlays to roughly 77ms after the indexed renderer and graph-level
input/LOD changes. Pixel 5 UI profiling was skipped per device-testing preference.


## NBC-327: delete NetBox documents from item pages

Long-pressing a document in the item overview should expose document actions, including a confirmed
delete operation. The cache should hide the document immediately and offline deletion should use the
durable mutation queue.

- [x] Add a long-press actions dialog with open and delete actions.
- [x] Require explicit confirmation before deleting a document.
- [x] Reuse the generic pending-delete path for online and offline document deletion.
- [x] Show completion feedback for deleted and queued documents.

Status: **done**, 2026-08-03 - generic and device item pages now support confirmed cache-first
document deletion; no live NetBox document was deleted during verification.


## NBC-328: show object-type icons on linked item rows

Linked values such as a device's device type, rack, manufacturer, site, and IP address should carry
the same object-type icon used elsewhere in the app, before the linked item's display name.

- [x] Add endpoint-derived icons to generic reference and reference-list rows.
- [x] Add endpoint-derived icons to linked rows on the device detail page.
- [x] Reuse the shared AppIcons mapping so the visual language stays consistent.

Status: **done**, 2026-08-03 - generic linked rows and device detail references now render the
corresponding endpoint icon before the linked value.


## NBC-329: preserve media filename extensions during uploads

Some Android sharing providers expose a content URI or display name without an extension. NetBox
Documents relies on the stored filename extension to select the right viewer, so uploads should
retain a real extension whenever the provider supplies a useful MIME type.

- [x] Infer common image, PDF, office, archive, and text extensions from MIME types.
- [x] Apply the normalized filename to image attachments, device-type photos, and documents.
- [x] Leave extensionless uploads without an extension when the MIME type is unavailable; do not
  invent a `.bin` or image suffix.

Status: **done**, 2026-08-03 - upload requests now preserve existing extensions, infer missing ones
from the selected content MIME type, and leave unknown types extensionless; no live upload was
performed during verification.


## NBC-330: rebrand the application as Nyetbox

Rename the Android application identity from NetBox and Chill to Nyetbox, including its package
names, launcher/deep-link branding, build and release metadata, documentation, and GitHub
repository slug. Keep references to NetBox where they describe the compatible upstream product.

- [x] Rename the Android namespace, application ID, source packages, and technical app classes.
- [x] Replace app labels, themes, custom URI schemes, build scripts, CI, and release metadata.
- [x] Update README, privacy policy, store metadata, and repository links while documenting the
  former name.
- [x] Set the application version to 1.1.0 and verify the debug package on Mi Pad 4 and PX5.
- [x] Rename the GitHub repository and update local remotes/documentation.

Status: **done**, 2026-08-03 - remote ktfmt, unit tests, and debug build passed; the 1.1.0 debug
package was installed on Mi Pad 4, PX5, and Zenfone 10 before the Zenfone disconnected during
post-install verification; GitHub was renamed to `pschmitt/nyetbox` and the local origin updated.


## NBC-331: show live attachment progress in Cached data settings

When a sync is actively downloading durable image attachments and documents, the Cached data
settings row should show live progress instead of stale totals from the last completed sync.

- [ ] Expose attachment completion/total and downloaded byte progress from the sync state.
- [ ] Update the Cached data row while the attachment phase is running.
- [ ] Restore the normal cache totals after completion or failure without blocking settings.

Status: not started


## NBC-335: keep the README icon on transparent artwork

The README icon should show only the Nyetbox face artwork. Remove the blue background, border, and
decorative framing so it reads cleanly on the page.

- [x] Remove the opaque background and outer border from the README SVG.
- [x] Match the actual adaptive launcher icon: solid circular background and original face artwork.

Status: **done**, 2026-08-03 - README SVG matches the Mi Pad launcher composition, including its
solid circular `#011226` mask and original white/teal foreground artwork.


## NBC-333: install Nyetbox through the homelab Android config

The shared declaroid configuration for rooted homelab devices should install the signed Nyetbox
release on the Mi Pad 4 and Pixel 5, without adding it to the Zenfone configuration.

- [x] Add a shared `android/imports/homelab.yaml` entry for the Nyetbox release package.
- [x] Import the shared homelab app set from the Mi Pad 4 and Pixel 5 configs.
- [x] Narrow GitHub asset selection to the release APK and validate both resolved configs.

Status: **done**, 2026-08-03 - declaroid read-only diff resolved Nyetbox as missing on the Mi Pad
4 and Pixel 5 and excluded it from the Zenfone config; no device state was changed.


## NBC-334: consolidate sync indicators on the Sync settings page

The Settings → Sync screen currently exposes two separate sync indicators, one near the top that
is not always visible and another at the bottom. It should present one clear, consistently placed
status/control instead.

- [ ] Remove the duplicate sync indicator.
- [ ] Keep the remaining status and action visible and unambiguous while sync is active.

Status: not started


## NBC-332: animate the active sync control

The `Syncing…` control on Settings → Sync should provide a subtle animated progress indication
while a sync is running, so it is visibly active rather than looking static.

- [ ] Add a restrained rotation or progress animation to the syncing icon.
- [ ] Keep the animation accessible and stop it immediately when sync completes or fails.

Status: not started


## NBC-336: show the independence disclaimer on login

The login page should make it clear that Nyetbox is an independent project and is not affiliated
with NetBox Labs.

- [x] Add an italic disclaimer at the bottom of the login page.
- [x] Keep the wording clear without implying endorsement or affiliation.

Status: **done**, 2026-08-03 - disclaimer added to the onboarding screen; verified by remote lint.


## NBC-337: show current NetBox user and test the connection

Settings → Connection should identify the NetBox user associated with the configured API token and
offer a lightweight connection test without starting a full synchronization.

- [x] Resolve and cache the token owner for offline display.
- [x] Prefer NetBox's `/api/authentication-check/` endpoint, with a legacy fallback for older
  instances.
- [x] Show the current user and optional email on the Connection screen.
- [x] Add an icon-bearing Test connection button with success and failure feedback.
- [x] Bump the app version to 1.1.1.

Status: **done**, 2026-08-03 - remote formatting/lint and unit tests passed; no NetBox data was
modified.


## NBC-338: publish and link the privacy policy

The app needs a clear privacy policy and a discoverable link from Settings → About, suitable for
users and future store distribution.

- [x] Maintain a repository-hosted privacy policy describing network access, local storage, and
  explicit sharing behavior.
- [x] Disclose the optional public NetBox news-feed request and the absence of telemetry.
- [x] Add an icon-bearing Privacy policy link to Settings → About.

Status: **done**, 2026-08-03 - policy updated and linked to the repository document; remote lint
and unit tests used for verification.


## NBC-339: keep the GitHub Actions lint gate green

The lint workflow was failing because two newly reported warnings changed the checked-in Android
Lint baseline on every run.

- [x] Remove the redundant activity label already inherited from the application.
- [x] Use AndroidX's `String.toUri()` extension in the shared-media screen.
- [x] Confirm the lint baseline remains unchanged after the fixes.

Status: **done**, 2026-08-03 - remote ktfmt, Android Lint, and unit tests passed locally; GitHub
Actions rerun pending after push.
