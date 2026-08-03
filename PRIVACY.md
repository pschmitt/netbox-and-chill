# Privacy policy

This privacy policy pertains to the Nyetbox app.

Nyetbox does not collect or transmit any personal information to the developer or any
third party. The only network communication the app performs is directly between your device and
the NetBox instance you configure in Settings, using the API token you provide. That token and
your NetBox URL are stored on-device only, encrypted via the Android Keystore
(`EncryptedSharedPreferences`), and are never backed up (`android:allowBackup="false"`) or sent
anywhere but your own NetBox instance.

The app caches the device inventory it fetches from your NetBox instance in a local database
(Room/SQLite) for offline use. This cache never leaves your device and is cleared when you
disconnect in Settings or uninstall the app.

No analytics, crash reporting, or advertising SDKs are included.

This Privacy Policy is effective as of 2026-07-31 and may be updated in this repository from time
to time; changes take effect once committed here.

Nyetbox is published by Philipp Schmitt. Inquiries can be submitted via
[GitHub Issues](https://github.com/pschmitt/nyetbox/issues).
