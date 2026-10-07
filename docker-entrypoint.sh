#!/bin/sh
set -eu
# Railway mounts volumes owned by root: fix ownership once, then drop privileges.
if [ "$(id -u)" = "0" ]; then
  [ "$(stat -c %u /data)" = "10001" ] || chown -R 10001:10001 /data
  exec su-exec app java -jar /app/app.jar "$@"
fi
exec java -jar /app/app.jar "$@"
