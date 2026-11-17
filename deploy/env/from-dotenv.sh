#!/usr/bin/env bash
# Builds deploy/env/<stage>.env from its .example and the .env you already use for development.
#
#   deploy/env/from-dotenv.sh staging
#   deploy/env/from-dotenv.sh production
#   deploy/env/from-dotenv.sh staging /some/other/.env
#
# For every key that exists in both files, the value from your .env is copied. SPRING_PROFILES_ACTIVE is never
# copied, it stays what the example says. Placeholder secrets that nothing could fill in are generated. The
# file is git-ignored. Nothing is printed except key names, never values.
set -euo pipefail

STAGE="${1:-}"
case "$STAGE" in staging|production) ;; *) echo "Usage: $0 staging|production [path-to-.env]" >&2; exit 2;; esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SRC="${2:-$ROOT/.env}"
EX="$ROOT/deploy/env/$STAGE.env.example"
F="$ROOT/deploy/env/$STAGE.env"
[ -f "$EX" ] || { echo "Missing $EX" >&2; exit 1; }
[ -f "$SRC" ] || echo "No $SRC found: nothing to copy, only placeholders will be generated."
[ -f "$F" ] && echo "Updating the existing $F" || cp "$EX" "$F"

getv() { grep -m1 "^$1=" "$2" 2>/dev/null | cut -d= -f2- || true; }
setv() { K="$1" V="$2" awk 'BEGIN{FS=OFS="="} $1==ENVIRON["K"]{print $1,ENVIRON["V"];next}1' "$F" > "$F.tmp" && mv "$F.tmp" "$F"; }
rand() { openssl rand "$@"; }

# The messenger shares its JWT secret and internal key with the auth-service. If the messenger .env does not have them,
# look in the auth-service .env next to it.
SIBLING="${AUTH_ENV:-$ROOT/../premisave_auth_service/.env}"

for K in $(grep -oE '^[A-Z][A-Z0-9_]*' "$F" | grep -v '^SPRING_PROFILES_ACTIVE$'); do
  V="$(getv "$K" "$SRC")"
  if [ -z "$V" ]; then
    case "$K" in
      JWT_SECRET)       V="$(getv JWT_SECRET "$SIBLING")" ;;
      INTERNAL_API_KEY) V="$(getv API_KEY "$SIBLING")" ;;
    esac
  fi
  if [ -n "$V" ]; then setv "$K" "$V"; echo "copied   $K"; fi
done

# Placeholders that can be generated
if grep -q '^ACTUATOR_PASSWORD=replace' "$F"; then setv ACTUATOR_PASSWORD "$(rand -hex 24)"; echo "made     ACTUATOR_PASSWORD"; fi
if grep -q '^JWT_SECRET=replace' "$F" && grep -q '^API_KEY=' "$F"; then
  setv JWT_SECRET "$(rand -base64 32)"; echo "made     JWT_SECRET (give the same value to the messenger)"
fi
if grep -q '^API_KEY=replace' "$F"; then setv API_KEY "$(rand -hex 32)"; echo "made     API_KEY (give the same value to the messenger as INTERNAL_API_KEY)"; fi

echo
echo "Still to check in $F (example values, or addresses of this computer):"
grep -nE 'replace-with|your-|example\.com|localhost|127\.0\.0\.1|^[A-Z_]+=$' "$F" | cut -d= -f1 || true
echo
echo "Next: docker compose --env-file deploy/env/$STAGE.env -f deploy/docker-compose.$STAGE.yml up -d --build"
