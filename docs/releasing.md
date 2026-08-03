# Releasing Nyetbox

The normal [`Release`](../.github/workflows/release.yaml) workflow publishes APKs to GitHub
Releases. The Play Store workflow is separate and is intentionally manual:
`.github/workflows/play-store.yaml`.

## Before a Play Console account exists

The workflow can still be run with `publish` left disabled. It runs the unit tests, builds a
signed Android App Bundle, verifies that the artifact exists, and uploads the AAB as a workflow
artifact. It does not contact Google Play.

The bundle build reuses the existing CI signing setup. These GitHub Actions secrets are required
for the bundle job:

- `CI_KEYSTORE_BASE64`
- `CI_KEYSTORE_PASSWORD`
- `CI_KEY_ALIAS`
- `CI_KEY_PASSWORD`

The keystore must remain outside the repository. The version code and version name are supplied as
workflow inputs and are passed to Gradle as `-PversionCode` and `-PversionName`.

## Enabling publication later

After creating the Play application and granting a Google Play Developer API service account
access to it:

1. Add the service-account JSON as the `PLAY_SERVICE_ACCOUNT_JSON` repository secret.
2. Set the repository variable `PLAY_PUBLISH_ENABLED` to the exact value `true`.
3. Run the workflow with a new, never-used version code and `publish` explicitly enabled.

Both the workflow input and repository variable are required. Publication targets the internal
testing track and uses `changesNotSentForReview`, so a future release remains reviewable in the
Play Console until it is deliberately sent for review. Keep the variable unset while Play
publishing is not wanted.
