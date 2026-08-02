# Android E2E tests

The opt-in `Android E2E (manual)` GitHub Actions workflow starts a disposable NetBox instance,
seeds deterministic records, and runs the app's real onboarding/cache/search/offline journey in a
Pixel 7 Pro API 35 emulator. It does not use the production NetBox URL and it is separate from the normal
build, release, and Play Store workflows.

The NetBox image is pinned to `v4.6-5.0.2`, with matching PostgreSQL and Valkey image major
versions in [the compose fixture](../ci/netbox/docker-compose.yml). The fixture's API token and
records are CI-only values; the stack is removed with its volumes after every run.

The workflow is manual because emulator plus NetBox startup is expensive. When the test fails it
uploads the emulator logcat, a screenshot, NetBox logs, and the connected Android test reports.
