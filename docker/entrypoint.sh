#!/bin/sh
# Starts the auth-service inside the container.
#
# 1. Render, Heroku, Railway, Cloud Run and similar platforms choose the port and pass it in PORT.
#    The application reads SERVER_PORT, so PORT is copied across.
# 2. A volume or disk mounted at /data is usually owned by root. When the container starts as root,
#    the folder is handed to the service user first, and the service then runs without root rights.
#    Platforms that start the container as an ordinary user (Heroku) skip that step.
set -eu

if [ -n "${PORT:-}" ]; then
  export SERVER_PORT="$PORT"
fi

fix_owner() {
  dir="$1"
  case "$dir" in
    /*) ;;
    *) return 0 ;;
  esac
  mkdir -p "$dir" 2>/dev/null || return 0
  if [ "$(stat -c %u "$dir" 2>/dev/null || echo 0)" != "10001" ]; then
    chown -R 10001:10001 "$dir" 2>/dev/null || echo "Could not change the owner of $dir. Documents may not be saved." >&2
  fi
}

if [ "$(id -u)" = "0" ]; then
  fix_owner /data
  fix_owner "${STORAGE_LOCAL_PATH:-/data/homeowner-onboarding}"
  # shellcheck disable=SC2086
  exec setpriv --reuid=10001 --regid=10001 --init-groups java $JAVA_OPTS -jar /app/app.jar
fi

# shellcheck disable=SC2086
exec java $JAVA_OPTS -jar /app/app.jar
