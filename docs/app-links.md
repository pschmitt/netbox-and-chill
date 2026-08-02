# App Links and custom NetBox hosts

Android intent filters are part of the installed APK's manifest. The app cannot register a new
`http`/`https` host with Android after the user enters a NetBox URL, so a runtime preference cannot
create a verified App Link for an arbitrary instance.

NetBox and Chill uses two complementary paths:

- The non-verified `http`/`https` filters use a host wildcard and the known NetBox web namespaces.
  This makes a device URL from any configured NetBox instance eligible for the normal Android
  “Open with” chooser. It does not claim ownership of arbitrary domains.
- The separate `android:autoVerify="true"` filter uses the `appLinkHost` manifest placeholder.
  It defaults to `netbox.brkn.lol`, but another build can set
  `-PnetboxAppLinkHost=netbox.example` or `NETBOX_APP_LINK_HOST=netbox.example`. That host must
  publish a Digital Asset Links file containing this app's package and release certificate before
  Android can open matching links automatically.
- `nbxc://` links are app-owned and host-independent. They are the appropriate format for links
  shared by the app itself when verified web links are not available.

Android 15's Dynamic App Links can refine paths for a domain that is already declared and verified;
they do not turn runtime preferences into new manifest hosts. The manifest therefore intentionally
keeps the portable chooser path and makes only the verified host a compile-time setting.

Useful references:

- [About App Links](https://developer.android.com/training/app-links/about)
- [Add intent filters for App Links](https://developer.android.com/training/app-links/add-applinks)
- [The manifest `data` element](https://developer.android.com/guide/topics/manifest/data-element)
