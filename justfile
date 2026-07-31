# NetBox and Chill task runner.
#
# Gradle must never run on this machine directly - every build/test/lint recipe here shells out to
# a remote host (rofl-13.brkn.lol or rofl-14.brkn.lol) over SSH instead. See AGENTS.md.

set shell := ["bash", "-euo", "pipefail", "-c"]

application_id := "dev.pschmitt.netboxandchill"

remote_host := env_var_or_default("NBC_REMOTE_HOST", "rofl-13.brkn.lol")

# Empty for the main checkout; "-<worktree-dirname>" when run from a linked git worktree (e.g. one
# of Claude's isolated agent worktrees under .claude/worktrees/). Keeps parallel worktree agents
# from clobbering each other's remote sync directory mid-build.
worktree_suffix := `gd=$(git rev-parse --git-dir); gcd=$(git rev-parse --git-common-dir); if [ "$gd" != "$gcd" ]; then basename "$(git rev-parse --show-toplevel)" | sed 's/^/-/'; fi`

remote_path := env_var_or_default("NBC_REMOTE_PATH", "~/devel/private/pschmitt/netbox-and-chill-verify" + worktree_suffix)
local_dist := env_var_or_default("NBC_DIST_DIR", "./dist")

default_abi := env_var_or_default("NBC_ABI", "arm64-v8a")

zenfone_serial := env_var_or_default("ZENFONE_SERIAL", "R6AIB700W850L7G")

mipad_host := env_var_or_default("MIPAD_HOST", "mi-pad-4.lan")
mipad_ssh_port := env_var_or_default("MIPAD_SSH_PORT", "8022")
mipad_adb_port := env_var_or_default("MIPAD_ADB_PORT", "5555")

# List all available recipes
default:
    @just --list

# --- Remote build (rofl-13 / rofl-14) -------------------------------------

# Sync the working tree to the remote build host (excludes .git/build/.gradle). The .git exclude
# has no trailing slash so it matches both a real .git/ directory (the main checkout) and a plain
# .git file (a linked worktree's gitlink, which points at a local-only .git/worktrees/... path
# that doesn't exist on the remote host and breaks `nix develop` there if it gets copied over).
sync host=remote_host:
    rsync -az --delete \
        --exclude='.git' --exclude='**/build/' \
        --exclude='.gradle/' --exclude='**/.gradle/' \
        ./ {{host}}:{{remote_path}}/

# Run one or more Gradle tasks on the remote host (syncs first)
gradle host=remote_host *tasks: (sync host)
    ssh {{host}} 'cd {{remote_path}} && nix develop --command ./gradlew {{tasks}}'

# Build an APK remotely. variant: debug (default) or release. Release builds are signed with the
# persistent CI keystore (fetched from the rbw entry "NetBox and Chill CI Signing Keystore" and
# staged on the build host only for the duration of the build). Without CI_KEYSTORE_*, Gradle
# silently signs with the host's throwaway ~/.android/debug.keystore and devices carrying
# CI-signed installs (GitHub releases / Obtainium) reject the APK with
# INSTALL_FAILED_UPDATE_INCOMPATIBLE.
build variant="debug" host=remote_host:
    #!/usr/bin/env bash
    set -euo pipefail
    if [[ "{{variant}}" != "release" ]]; then
      just gradle "{{host}}" ":app:assembleDebug"
      exit 0
    fi
    if ! rbw unlocked >/dev/null 2>&1; then
      printf 'rbw is locked - run "rbw unlock" first (needed for the CI signing keystore)\n' >&2
      exit 2
    fi
    tmpdir=$(mktemp -d)
    trap 'rm -rf "$tmpdir"' EXIT
    git_revision=$(git describe --always --abbrev=12 --dirty)
    rbw attachment get "NetBox and Chill CI Signing Keystore" --attachment netboxandchill-ci.jks --output "$tmpdir/netboxandchill-ci.jks"
    rbw attachment get "NetBox and Chill CI Signing Keystore" --attachment netboxandchill-ci-keystore.env --output "$tmpdir/netboxandchill-ci-keystore.env"
    just sync "{{host}}"
    ssh "{{host}}" 'mkdir -p ~/.netboxandchill-ci-tmp && chmod 700 ~/.netboxandchill-ci-tmp'
    scp -q "$tmpdir/netboxandchill-ci.jks" "$tmpdir/netboxandchill-ci-keystore.env" "{{host}}:.netboxandchill-ci-tmp/"
    # The keystore is shredded on the host whether or not the build succeeds.
    ssh "{{host}}" "
      artifact={{remote_path}}/app/build/outputs/apk/release/app-{{default_abi}}-release.apk
      previous_mtime=0
      [[ -f \"\$artifact\" ]] && previous_mtime=\$(stat -c %Y \"\$artifact\")
      set -a
      . ~/.netboxandchill-ci-tmp/netboxandchill-ci-keystore.env
      set +a
      export CI_KEYSTORE_PATH=\$HOME/.netboxandchill-ci-tmp/netboxandchill-ci.jks
      export GIT_REVISION='$git_revision'
      cd {{remote_path}} && nix develop --command ./gradlew ':app:assembleRelease' --rerun-tasks 2>&1 | tee ~/netboxandchill-release-build.log
      rc=\$?
      if [[ \$rc -eq 0 && (! -f \"\$artifact\" || \$(stat -c %Y \"\$artifact\") -le \$previous_mtime) ]]; then
        echo 'release build did not refresh its APK artifact' >&2
        rc=1
      fi
      shred -u ~/.netboxandchill-ci-tmp/* 2>/dev/null || true
      rmdir ~/.netboxandchill-ci-tmp 2>/dev/null || true
      exit \$rc
    "

# Copy a built APK split back to ./dist locally. variant/host same as `build`, plus abi=<abi>
fetch variant="debug" host=remote_host abi=default_abi:
    #!/usr/bin/env bash
    set -euo pipefail
    mkdir -p {{local_dist}}
    scp "{{host}}:{{remote_path}}/app/build/outputs/apk/{{variant}}/app-{{abi}}-{{variant}}.apk" {{local_dist}}/

# Build an APK remotely and copy it back to ./dist. Same args as `build`.
build-fetch variant="debug" host=remote_host:
    just build {{variant}} {{host}}
    just fetch {{variant}} {{host}}

# ktfmt check via Gradle, remotely (mirrors .github/workflows/lint.yaml)
lint host=remote_host: (gradle host "ktfmtCheck")

# Run the unit test suite remotely
test host=remote_host: (gradle host ":app:testDebugUnitTest")

# Remote `./gradlew clean`
clean host=remote_host: (gradle host "clean")

# --- Zenfone 10 (USB, directly attached to this machine) -------------------

# Install an APK on the Zenfone 10 over adb (USB)
zenfone-install apk:
    adb -s {{zenfone_serial}} install -r {{apk}}

# Uninstall a package from the Zenfone 10. WARNING: wipes that app's local data (Room DB, saved
# credentials).
zenfone-uninstall pkg=application_id:
    adb -s {{zenfone_serial}} uninstall {{pkg}}

# Tail logcat from the Zenfone 10, optionally filtered by a grep pattern
zenfone-logcat filter="":
    #!/usr/bin/env bash
    set -euo pipefail
    if [ -n "{{filter}}" ]; then
        adb -s {{zenfone_serial}} logcat | grep -i --line-buffered "{{filter}}"
    else
        adb -s {{zenfone_serial}} logcat
    fi

# Build an APK remotely, fetch it, and install it on the Zenfone 10. variant: debug (default) or release.
deploy-zenfone variant="debug":
    just build-fetch {{variant}}
    just zenfone-install "{{local_dist}}/app-{{default_abi}}-{{variant}}.apk"

# --- Mi Pad 4 (rooted, Termux SSH on port 8022) -----------------------------

# Run an arbitrary command on the Mi Pad 4 over SSH
mipad-ssh +cmd:
    ssh -p {{mipad_ssh_port}} {{mipad_host}} "{{cmd}}"

# Interactive shell on the Mi Pad 4
mipad-shell:
    ssh -p {{mipad_ssh_port}} {{mipad_host}}

# Find the port adbd is actually listening on (via `ss -ltnp` over root SSH), starting it as a
# fallback if it isn't running at all, then `adb connect` to it. Prints the resulting "host:port"
# adb target on stdout so other recipes can capture it - status/progress goes to stderr.
mipad-connect:
    #!/usr/bin/env bash
    set -euo pipefail
    port=$(ssh -p {{mipad_ssh_port}} {{mipad_host}} "su -c 'ss -ltnp'" 2>/dev/null \
        | awk '/adbd/ { n = split($4, a, ":"); print a[n]; exit }')
    if [ -z "$port" ]; then
        echo "adbd not listening - starting it via root shell" >&2
        ssh -p {{mipad_ssh_port}} {{mipad_host}} \
            "su -c 'setprop service.adb.tcp.port {{mipad_adb_port}} && stop adbd && start adbd'" >&2
        sleep 1
        port={{mipad_adb_port}}
    fi
    target="{{mipad_host}}:$port"
    adb connect "$target" >&2
    echo "$target"

# Install an APK on the Mi Pad 4 over adb (network, via mipad-connect). Simpler and more reliable
# than scp + `pm install`: adb push/install runs as adbd, which doesn't hit the SELinux/FUSE
# permission issues a plain scp into /sdcard runs into when system_server tries to read the file
# back.
mipad-install apk:
    #!/usr/bin/env bash
    set -euo pipefail
    target=$(just mipad-connect)
    adb -s "$target" install -r {{apk}}

# Uninstall a package from the Mi Pad 4. WARNING: wipes that app's local data.
mipad-uninstall pkg=application_id:
    #!/usr/bin/env bash
    set -euo pipefail
    target=$(just mipad-connect)
    adb -s "$target" uninstall {{pkg}}

# Tail logcat from the Mi Pad 4, optionally filtered by a grep pattern
mipad-logcat filter="":
    #!/usr/bin/env bash
    set -euo pipefail
    target=$(just mipad-connect)
    if [ -n "{{filter}}" ]; then
        adb -s "$target" logcat | grep -i --line-buffered "{{filter}}"
    else
        adb -s "$target" logcat
    fi

# Build an APK remotely, fetch it, and install it on the Mi Pad 4. variant: debug (default) or release.
deploy-mipad variant="debug":
    just build-fetch {{variant}}
    just mipad-install "{{local_dist}}/app-{{default_abi}}-{{variant}}.apk"

# --- Formatting / hooks ----------------------------------------------------

# Format Kotlin sources locally with ktfmt (lightweight - not a Gradle build, safe to run on this
# machine). CAUTION: this is nixpkgs' standalone ktfmt, which may be a newer version than the one
# CI actually uses (see gradle/libs.versions.toml) - treat this as an advisory quick pass, not a
# substitute for `just lint`.
format:
    ktfmt --kotlinlang-style $(git ls-files '*.kt' '*.kts')

# Nix formatting/lint for this repo's flake.nix (per global AI context rules)
nix-fmt:
    nixfmt flake.nix

nix-lint:
    nix develop --command statix check
