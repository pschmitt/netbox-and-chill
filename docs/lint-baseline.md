# Android lint baseline

The lint baseline is intentionally small and checked in CI. The lint workflow runs
`updateLintBaselineDebug` and fails if that task changes `app/lint-baseline.xml`, so a new
finding cannot be added silently.

As of 2026-08-03, the remaining 29 entries are all reviewed policy/toolchain findings:

- `GradleDependency` (21): newer compile SDK 37, CameraX 1.6.1, Compose 1.11.4, AndroidX Core
  1.19.0/ Splashscreen 1.2.0, Hilt 1.4.0, and Lifecycle 2.11.0 are available. These are grouped
  upgrades that need a compatibility pass rather than an opportunistic version bump, especially
  because the scanner and Compose UI are hardware-tested against the current versions.
- `NewerVersionAvailable` (5): Kotlin 2.4.10, coroutines-test 1.11.0, and markdown renderer 0.43.0
  are available. They are tracked with the dependency batch above for the same reason.
- `AndroidGradlePluginVersion` (1): AGP 9.3.1 is available while the project is pinned to 9.2.1;
  upgrade it together with the Gradle/Nix toolchain, not in an isolated app change.
- `OldTargetApi` (1): compile/target SDK 36 is the currently tested platform; lint knows about SDK
  37 but the project does not yet have a tested SDK-37 compatibility pass.
- `ObsoleteSdkInt` (1): adaptive launcher icons remain in `mipmap-anydpi-v26`. Moving them to
  unqualified `mipmap-anydpi` removes the generated `R.mipmap` resource with the current Android
  resource toolchain, so this is a toolchain false positive for a minSdk-26 app.

The larger groups removed during NBC-299 were fixable: 48 KTX suggestions, 36 manifest data
attribute warnings, 11 permission warnings, 18 camera opt-in warnings, four primitive-state
warnings, two modifier warnings, two logging warnings, and the obsolete/dead resource entries.
Intent-filter suppressions carry their reason in `AndroidManifest.xml`; permission and camera
suppressions sit beside the runtime guard or interop boundary that justifies them.
