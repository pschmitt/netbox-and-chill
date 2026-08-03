# Privacy policy

This privacy policy pertains to the Nyetbox app.

Nyetbox does not collect or transmit any personal information to the developer or any third
party. The app's inventory, authentication, media, and document traffic goes directly between
your device and the NetBox instance you configure in Settings, using the API token you provide.
The dashboard may also fetch the public NetBox news feed from `netboxlabs.com`; this request does
not include your NetBox URL, API token, inventory, or other account data. Links to GitHub,
sponsorship, and other external sites are opened only when you explicitly tap them. Your API
token and NetBox URL are stored on-device only, encrypted via the Android Keystore
(`EncryptedSharedPreferences`), and are never backed up (`android:allowBackup="false"`). The API
token is sent only to your configured NetBox instance.

The app caches the device inventory it fetches from your NetBox instance in a local database
(Room/SQLite) for offline use. This cache never leaves your device and is cleared when you
disconnect in Settings or uninstall the app.

No analytics, automatic crash reporting, or advertising SDKs are included. If the app crashes, a
diagnostic report is kept locally and can only be shared if you explicitly copy or send it.
Photos and other files are read only when you explicitly choose them for an upload or share action,
and are sent only to the configured NetBox instance when you confirm that action.

This Privacy Policy is effective as of 2026-08-03 and may be updated in this repository from time
to time; changes take effect once committed here.

Nyetbox is published by Philipp Schmitt. Inquiries can be submitted via
[GitHub Issues](https://github.com/pschmitt/nyetbox/issues).
