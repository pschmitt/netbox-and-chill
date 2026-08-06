# App links and custom NetBox hosts

Android intent filters are part of the installed APK's manifest. Nyetbox deliberately does not
bind its installed APK to one NetBox host: its `http`/`https` filters use a host wildcard and the
known NetBox web namespaces. A URL from any configured NetBox instance can therefore be offered in
Android's normal “Open with” chooser without rebuilding the app.

This is normal, non-verified deep linking. It does not automatically bypass the chooser for any
host. Verified Android App Links require both an exact host in a static `android:autoVerify="true"`
manifest filter and a matching Digital Asset Links file served by that host. A runtime preference
or a server-side `assetlinks.json` file cannot add an arbitrary new host to an already-installed
APK. Android 15 Dynamic App Links can only refine paths within the statically declared scope.

`nyetbox://` links remain app-owned and host-independent. They are appropriate for links generated
by Nyetbox itself when a custom-scheme link is preferable to a web URL.

## User setup

No server-side JSON file is needed for the current chooser-based behavior. Users only need to
install Nyetbox, open a matching NetBox URL, and select Nyetbox in Android's “Open with” dialog.
They can choose “Always” (or set Nyetbox as the preferred app in Android's link settings) if they
want future matching URLs to open there by default. This works for each configured NetBox host,
including hosts that were not known when the APK was built.

The `assetlinks.json` file is only needed for a different setup: verified App Links that open
directly in Nyetbox without a chooser. That setup also requires the APK to contain a matching,
exact-host `android:autoVerify="true"` filter; placing the JSON file on a server is not sufficient
by itself, and the current generic APK intentionally does not use that setup.

## Verifying a device

The wildcard filters intentionally use the normal chooser path. To test one, open a matching URL
for a configured or non-verified host:

```shell
adb shell am start -W \
  -a android.intent.action.VIEW \
  -c android.intent.category.BROWSABLE \
  -d 'https://netbox.example/dcim/devices/123/'
```

Android should offer Nyetbox alongside the browser. The selected choice can become the device's
preferred handler, so reset the browser's preferred activities when repeating the chooser test:

```shell
adb shell cmd package clear-package-preferred-activities org.mozilla.firefox
```

Useful references:

- [About App Links](https://developer.android.com/training/app-links/about)
- [Add intent filters for App Links](https://developer.android.com/training/app-links/add-applinks)
- [The manifest `data` element](https://developer.android.com/guide/topics/manifest/data-element)
