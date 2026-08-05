#!/bin/sh
# Runs as a postgres docker-entrypoint-initdb.d script, i.e. only on a genuinely fresh/empty data
# volume - exactly the disposable-fixture case this repo always starts from (see docker-compose.yml
# and justfile's netbox-up/screenshots-netbox-up). Restoring a pre-migrated, pre-seeded dump here
# skips NetBox's own multi-hundred-migration chain and the seed script's sequential API calls,
# which is most of what made spinning up this fixture slow. NETBOX_CI_FIXTURE selects which dump to
# restore, since e2e and the plugin-enabled screenshots fixture use different NetBox versions/plugin
# sets and therefore genuinely different schemas - see docker-compose.screenshots.yml.
set -eu

fixture="${NETBOX_CI_FIXTURE:-e2e}"
dump="/fixtures/${fixture}.dump"

if [ ! -f "$dump" ]; then
    echo "restore-fixture: no dump at $dump, leaving NetBox to run its normal migrate+seed path" >&2
    exit 0
fi

echo "restore-fixture: restoring $dump into database '$POSTGRES_DB'" >&2
pg_restore --no-owner --role="$POSTGRES_USER" --username="$POSTGRES_USER" --dbname="$POSTGRES_DB" "$dump"
