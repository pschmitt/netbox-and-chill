# App Links and custom NetBox hosts

Android intent filters are part of the installed APK's manifest. The app cannot register a new
`http`/`https` host with Android after the user enters a NetBox URL, so a runtime preference cannot
create a verified App Link for an arbitrary instance.

Nyetbox uses two complementary paths:

- The non-verified `http`/`https` filters use a host wildcard and the known NetBox web namespaces.
  This makes a device URL from any configured NetBox instance eligible for the normal Android
  “Open with” chooser. It does not claim ownership of arbitrary domains.
- The separate `android:autoVerify="true"` filter uses the `appLinkHost` manifest placeholder.
  It defaults to `netbox.brkn.lol`, but another build can set
  `-PnetboxAppLinkHost=netbox.example` or `NETBOX_APP_LINK_HOST=netbox.example`. That host must
  publish a Digital Asset Links file containing this app's package and release certificate before
  Android can open matching links automatically.
- `nyetbox://` links are app-owned and host-independent. They are the appropriate format for links
  shared by the app itself when verified web links are not available.

## Verifying a device

The production asset-links file must name the current package, `dev.pschmitt.nyetbox`, and the
release certificate fingerprint. On a connected device, inspect the result with:

```shell
adb shell cmd package get-app-links dev.pschmitt.nyetbox
```

The expected state for `netbox.brkn.lol` is `verified`. Android should then route this external
view intent directly to Nyetbox:

The personal NetBox host also advertises `dev.pschmitt.nyetbox.debug` with the shared development
certificate, so the debug build can be verified when Android accepts that certificate. This is
specific to the development signing key; debug APKs signed elsewhere still need explicit opening or
local App Link approval.

```shell
adb shell am start -W \
  -a android.intent.action.VIEW \
  -c android.intent.category.BROWSABLE \
  -d 'https://netbox.example/dcim/devices/123/'
```

`am start` does not itself force the chooser. It uses the device's preferred handler; a verified
Nyetbox domain intentionally bypasses the chooser. To test the generic chooser path, clear the
browser's preferred activities temporarily (or use Android Settings → Apps → Default apps), then
open a matching URL for a non-verified host:

```shell
adb shell cmd package clear-package-preferred-activities org.mozilla.firefox
```

The wildcard filter should then make Nyetbox eligible alongside the browser. Re-select the normal
browser afterward if this is a personal test device.

Android 15's Dynamic App Links can refine paths for a domain that is already declared and verified;
they do not turn runtime preferences into new manifest hosts. The manifest therefore intentionally
keeps the portable chooser path and makes only the verified host a compile-time setting.

Useful references:

- [About App Links](https://developer.android.com/training/app-links/about)
- [Add intent filters for App Links](https://developer.android.com/training/app-links/add-applinks)
- [The manifest `data` element](https://developer.android.com/guide/topics/manifest/data-element)
