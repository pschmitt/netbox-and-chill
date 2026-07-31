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

Not done (still needs its own pass, see above): downloading/caching image *bytes* to disk for
true offline browsing (Coil's disk cache is best-effort, not a durable offline store) - same
"binary asset synced for offline use" shape as NBC-7's document-viewing gap.

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

Status: mostly done (network-backed image display) - code complete, build/lint/test green,
installed and launches cleanly on all three devices, 2026-07-31. Live visual verification against
real device-type photos/image-attachments still pending due to an unrelated netbox.brkn.lol
outage during this session. Offline asset sync intentionally out of scope for this pass.

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

## NBC-5: Editable objects (generic PATCH-based editing)

Allow editing object fields from the app (not just read-only browsing), via NetBox's REST PATCH.

**Why:** user request - the app should be a two-way tool, not just a lookup/scan viewer.
**How it landed:** built on top of NBC-6's generic engine rather than as a Device-specific
feature - `buildEditableFields` (`GenericFieldRenderer.kt`) picks out primitive (string/number/
boolean) top-level fields from the raw JSON, skipping a blocklist of server-managed/computed ones
(`id`, `url`, `display`, `display_url`, `created`, `last_updated`, `custom_fields`). Edit mode on
`GenericDetailScreen` swaps the read-only field list for text inputs (a `Switch` for booleans),
Save PATCHes only via `GenericNetBoxApi.patchObject`/`GenericObjectRepository.updateObject`, which
re-caches the server's response. **Verified against the user's real NetBox instance** (via the
Mi Pad 4, which is already logged in): edited and saved a live Provider Account, confirmed the
`last_updated` timestamp actually changed server-side - full round trip works, not just
simulated/unit-tested.

Explicitly out of scope for this pass (noted, not forgotten):
- [ ] Editing reference fields (site, rack, tenant, ...) or choice fields (status, ...) - both
  need a picker UI, not a text field. Only plain primitives are editable right now.
- [ ] `custom_fields` editing - each custom field has its own type (text/select/object/multi-object/
  boolean/...) that would need its own per-type handling, not a blanket text field.
- [ ] The *old* Device detail screen (`DeviceDetailScreen`/`DeviceEntity`) still isn't editable -
  only objects routed through NBC-6's generic engine are. Same unification note as NBC-6's
  "Linked items" follow-up: migrating Devices onto the generic engine would fix both at once.

Status: **done** (generic objects), 2026-07-31. `just test`/`just lint` green; live-verified
end-to-end on the Mi Pad 4 against the real NetBox instance, not just simulated.

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

Follow-ups noted during/after this landed (not done yet):
- [ ] "Linked items" on the *Device* detail screen (e.g. tapping its Rack/Site) don't navigate
  anywhere yet - `DeviceEntity` only stores display strings (`rackName`, `siteName`), not the
  id/url needed to link out, because the typed Device pipeline predates this generic one. The
  clean fix is migrating Device detail rendering onto the same generic JSON-based renderer used
  for every other type (`GenericFieldRenderer`) instead of bolting related-object ids onto
  `DeviceEntity` - would also finally unify the two parallel list/detail code paths this section
  above deliberately left split. Not done - flagging as the natural next step for whoever picks
  device-detail work back up.
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

Still missing for *full* support:
- [ ] Opening/downloading/previewing the actual file content - the generic detail screen shows
  the document's metadata fields, but there's no in-app file viewer or download/cache step yet.
  This is the same "binary asset synced for offline use" work NBC-3 already flagged wanting a
  joint design pass for.
- [ ] Nothing plugin-specific has been verified beyond "list + basic metadata detail" - e.g.
  whether netbox-documents exposes anything (custom actions, nested structure) that doesn't fit
  the generic list/detail shape.

Status: partially done (list/detail browsing works via NBC-6, confirmed live), 2026-07-31 - file
content viewing still open, see NBC-3.

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
- [ ] Domain-verified App Links (`assetlinks.json` on the NetBox host) - still open, needs
  infrastructure work outside this repo.

Status: partially done, 2026-07-31 - see checklist above.

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

Status: not started, 2026-07-31.

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
**How to apply:** need to look at printlabel's actual interface (CLI? library? network service?)
to figure out the integration shape - could be a shared Kotlin/native lib, a shelled-out call, or
a network call to a printlabel server instance. Not yet investigated.

Status: not started, 2026-07-31.

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

- [x] `GlobalSearchRepository` (`data/repository/GlobalSearchRepository.kt`) - fans out
  `listObjects(endpointPath, mapOf("q" to term, "limit" to "15"))` across a baseline curated list
  (devices, device-types, sites, racks, ip-addresses, prefixes, circuits, virtual-machines,
  tenants - covers the TODO's own suggested set plus a few equally common ones) in parallel via
  `coroutineScope`/`async`/`awaitAll`, one model's failure logged and skipped rather than failing
  the whole search (mirrors `DirectoryRepository.refresh`'s per-app `runCatching`). Results aren't
  written into the `NetBoxObjectEntity` cache - transient search-only, since the point is a live
  merge across many models, not another sync path; tapping a result still funnels through the
  normal `GenericDetailViewModel.refreshObject` cache-first flow once you land on its detail
  screen.
- [x] `GlobalSearchViewModel` unions the baseline set with the user's *pinned* model paths
  (`SettingsRepository.pinnedModelPaths`) so anything a user has explicitly starred in the sidebar
  is searchable too, not just the fixed baseline - reuses `DirectoryRepository.observePinned(...)`
  (despite the "pinned" name, it's just a generic `WHERE endpointPath IN (...)` lookup) to resolve
  each hit's endpoint path back to a humanized model label + `appKey` for the icon.
- [x] Input is debounced 300ms (`Flow.debounce` + `collectLatest`, so a fast typist's earlier
  in-flight fan-out is dropped, not raced) before firing; empty query shows a hint, in-flight shows
  "Searching…", zero results shows an explicit "No results" state.
- [x] New `GlobalSearchScreen` (`ui/search/`) - a dedicated full-screen search (not a dropdown),
  reachable via a new search `IconButton` added to the top bar `actions` of both `DeviceListScreen`
  and `GenericListScreen` (the two screens users land on most, per the bottom nav / sidebar model
  clicks) - deliberately separate from NBC-6/14's existing sidebar search field, which still only
  filters section/category *names* and is untouched. Result rows show the object's display name,
  its model label + optional secondary line (status/description), and `AppIcons.forAppKey(...)` for
  the icon - tapping navigates to `Route.Generic(endpointPath, id)`, the same generic detail route
  scanning/deep-links already use.
- [x] Factored `appKeyFromEndpointPath` (endpointPath -> appKey for `AppIcons.forAppKey`) out of
  `GenericListScreen` into `AppIcons.kt` itself, since `GlobalSearchScreen` needed the identical
  logic - avoids two copies drifting apart.

Not done / explicitly out of scope for this pass:
- [ ] No debounce-level request cancellation of already-in-flight HTTP calls (only new user input
  cancels the *collector*, via `collectLatest` - the underlying OkHttp calls from an outdated
  keystroke may still complete and get discarded rather than being aborted mid-flight). Not a
  correctness bug, just not maximally efficient.
- [ ] Result ranking is naive (alphabetical by display name across the merged set, no relevance
  scoring/highlighting of the matched substring).

Status: **mostly done**, 2026-07-31. `just build`/`just lint`/`just test` all green on rofl-13
(lint re-verified with `--rerun-tasks` to rule out a stale up-to-date cache hit). **Not
independently verified**: no physical device was available this session to install onto and
interact with live (Zenfone/Mi Pad/Pixel 5 all out of reach from this worktree) - so the actual
search UX (typing, debounce feel, tapping into a result) has not been visually confirmed
end-to-end on-device, only confirmed to build/compile/pass unit tests. Live API verification of
the underlying approach (no global-search endpoint exists; `?q=` works on per-model endpoints)
*was* done directly against the real netbox.brkn.lol instance via `curl`, see above.

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

Status: **done**, 2026-07-31. `just test`/`just lint` green on rofl-14 (compiles clean, only
pre-existing deprecation warnings unrelated to this change). Not yet live-verified against the real
instance or installed on-device this session - do that before considering this fully closed out.

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
time it's available. Actual offline caching/pre-sync of attachments (vs. on-demand download when
tapped) is still open, tracked under NBC-17.

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

**Slice 2 (not started):** the actual attachment-to-disk download sweep (extend `SyncWorker` to
scan all cached `NetBoxObjectEntity` rows when the new setting is on, downloading each detected
attachment via a new durable - not cache-dir - `FileDownloadRepository` method, with
`GenericDetailScreen` preferring an already-synced local copy over re-downloading); extending the
existing `SyncWorker`/`SyncScheduler` to also sync the NBC-6 generic-object cache, not just the
legacy device list; surfacing background (not just manual) sync failures to the user somehow
(a background `WorkManager` failure has no `Activity` to show a `Snackbar` in - probably wants a
`Notification`, unlike the manual-sync case slice 1 covers).

Status: **in progress**, 2026-07-31 - slice 1 done (`just test`/`just lint` green on rofl-14,
installed on the Mi Pad 4, smoke-tested crash-free - full UI verification blocked by the same
netbox.brkn.lol outage as NBC-15/18, since the device is currently logged out and can't reconnect
until the instance is reachable again). Slice 2 not started.

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

Status: not started, 2026-07-31 - needs verification against a populated cache once
netbox.brkn.lol is reachable again; unable to test today due to a live network outage encountered
mid-session (see above).

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
match the intended layout with no crash. Not yet installed on Pixel 5/Zenfone 10 this session.
Side effect of testing: logged the Mi Pad 4 out to see the onboarding screen, and couldn't log it
back in before netbox.brkn.lol's outage (see NBC-18) resolved - needs re-connecting once the
instance is reachable again.

## NBC-20: tap an image to view it full-size with pinch/swipe zoom

Device-type stock photos and image attachments (NBC-3) currently just sit inline at a fixed
thumbnail size - tapping one should open a full-screen viewer with pinch-to-zoom/pan, not require
falling back to "open in browser" the way a document attachment does.

**Why:** user request - "images need to be clickable -> show in full size + swipe to zoom".
**How to apply:** needs a full-screen image viewer composable (Coil3 `AsyncImage` + a zoom/pan
gesture modifier - either hand-rolled via `detectTransformGestures`/`graphicsLayer` scale-translate,
or a small dependency like Telephoto/Zoomable if one's already idiomatic for Coil3 - check what
findroidplus uses for any full-screen image viewing before picking). Applies to both
`RemoteThumbnail` usages from NBC-3 (device list row thumbnail probably shouldn't open this - the
detail screen's front/rear photos and image-attachment thumbnails should) - needs its own look at
how NBC-3 wired those up before implementing.

Status: not started, 2026-07-31.

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
two pre-existing unrelated deprecations); installed on Mi Pad 4 and Pixel 5, app launches
crash-free. Not visually verified interacting with the actual camera view this session - the
Scanner tab lives behind login, and the Mi Pad is logged out pending the netbox.brkn.lol outage
(see NBC-18) resolving - needs a live tap-to-focus/flashlight check once reconnected.

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

Status: not started, 2026-07-31.
