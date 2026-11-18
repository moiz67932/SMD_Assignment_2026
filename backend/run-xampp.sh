#!/usr/bin/env bash
# Starts the Kinnect PHP API with XAMPP's PHP, reachable by phones on the same Wi-Fi.
#   1. sudo /opt/lampp/lampp startmysql        (once per boot; XAMPP's MariaDB)
#   2. ./backend/run-xampp.sh setup            (first time only: database, user, tables)
#   3. ./backend/run-xampp.sh                  (serves http://<laptop-ip>:8088)
# Settings come from backend/.env (git-ignored); created on first run with a random password.
set -euo pipefail
cd "$(dirname "$0")"
PHP=/opt/lampp/bin/php
MYSQL=/opt/lampp/bin/mysql
PORT="${PORT:-8088}"

if [ ! -f .env ]; then
  password=$(head -c 18 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | head -c 20)
  cat > .env <<CONF
DB_HOST=127.0.0.1
DB_PORT=3306
DB_NAME=kinnect
DB_USER=kinnect
DB_PASSWORD=$password
MEDIA_ROOT=$(pwd)/var/media
FCM_SERVICE_ACCOUNT=$(pwd)/var/service-account.json
CONF
  chmod 600 .env
  echo "Created backend/.env (private, git-ignored)."
fi
set -a; . ./.env; set +a
mkdir -p "$MEDIA_ROOT"

if [ "${1:-}" = "setup" ]; then
  # XAMPP's MariaDB root user has no password by default (local only).
  "$MYSQL" -uroot <<SQL
CREATE DATABASE IF NOT EXISTS \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
CREATE USER IF NOT EXISTS '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USER'@'localhost' IDENTIFIED BY '$DB_PASSWORD';
ALTER USER '$DB_USER'@'127.0.0.1' IDENTIFIED BY '$DB_PASSWORD';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'localhost';
GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL
  "$PHP" bin/migrate.php
  echo "Database ready."
  exit 0
fi

if [ "${1:-}" = "push" ]; then
  exec "$PHP" bin/push-worker.php
fi

ip=$(ip -4 route get 1.1.1.1 2>/dev/null | awk '{for(i=1;i<=NF;i++) if($i=="src") print $(i+1)}')
echo "Kinnect API: http://${ip:-127.0.0.1}:$PORT   (put this in api.properties on both phones' build)"
exec "$PHP" -d upload_max_filesize=30M -d post_max_size=110M -d memory_limit=256M -S 0.0.0.0:"$PORT" -t public
