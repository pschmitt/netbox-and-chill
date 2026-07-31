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
**How to apply:** NetBox has a built-in global search endpoint (`GET /api/extras/search/` in
recent NetBox versions, called `object-types`-driven search under the hood) that queries across
registered searchable models server-side - almost certainly a better fit than trying to fan out
client-side queries across every cached `NetBoxObjectEntity` endpoint. Needs checking exact
endpoint/response shape against a live instance. Result rows would route through NBC-6's generic
detail screen the same way scanning/deep-links already do, since results span arbitrary object
types.

Status: not started, 2026-07-31.

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
context/history on an object, not currently visible anywhere in the app.
**How to apply:** `GET /api/extras/journal-entries/?assigned_object_type=<app.model>&assigned_object_id=<id>`
(the `assigned_object_type` filter takes a `"app_label.model"` string, e.g. `"dcim.device"" - need
to derive that from the generic screen's `endpointPath`, which is close but not identical:
`api/dcim/devices/` -> `dcim.device` needs de-pluralizing the model segment, not just a string
slice). Each entry has `created`, `kind` (info/success/warning/danger), and a Markdown `comments`
body - should reuse `CommentCard`/the Markdown renderer from NBC-12/NBC-14 rather than plain text.
Not investigated yet: whether posting new journal entries (not just reading) is wanted too.

Status: not started, 2026-07-31.

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
tapped) is still open, tracked under the broader NBC-3/NBC-7 offline-assets scope.
