# Kinnect PHP/MySQL backend

Requires PHP 8.2+ (`pdo_mysql`, `fileinfo`, `curl`, `openssl`) and MySQL 8.4. This backend is under active migration; Android screens still depend on the old Firebase data layer. Do not describe this revision as submission ready.

Serve **only** `public/`. Keep filesystem media and service-account credentials outside that directory. Copy `.env.example` into private deployment configuration and export its values into the PHP process environment; PHP does not automatically source that file.

```bash
php backend/bin/migrate.php
php -S 127.0.0.1:8088 -t backend/public
```

Migrations use an advisory lock; MySQL DDL is not transactional. Existing table creation can resume after an interrupted initial migration. Back up real data before schema changes. The built-in PHP server is for local development only.

Implemented routes: `auth/{signup,login,me,logout}.php`; `profiles/{get,update}.php`; `friends/{action,list}.php`; `search/users.php`; `posts/{create,feed,react}.php`; `comments/{create,list,like}.php`; `media/{upload,get}.php`; `stories/{create,feed,view}.php`; `close-friends/set.php`; `chats/{ensure,screenshot}.php`; `messages/{send,list,edit,delete}.php`; `devices/register.php`; `notifications/list.php`; `presence/{heartbeat,get}.php`.

POST business mutations include an `action_uuid`. Content creation also includes a client entity `id`; use distinct UUIDs for actions and entities. Authentication uses `Authorization: Bearer <session token>`. Tokens are hashed on the server. All dates are UTC. The action receipt transaction serializes an actor's writes, commits logical changes and notification intents together, and rejects reuse of the UUID for different input.

Media upload is multipart with `id`, `action_uuid`, and `file`. Posts support JSON text or multipart `id`, `action_uuid`, `body`, and `images[]`. Bytes live in `MEDIA_ROOT`, not MySQL or a public directory. Downloads require authentication and resource visibility; byte ranges support playback. The PHP/web-server upload limits must accommodate the documented 25 MB per file and multipart aggregate. Clean up unreferenced files after interrupted transactions with an operational cleanup policy (not yet automated).

Messages accept `type` text/image/video/audio/file and `media_id` for uploaded attachments. Server creation time controls the five-minute edit/delete deadline. Deleted messages remain tombstones. Message list currently returns the latest 100 records; full history/delta pagination remains unfinished.

Run the PHP FCM worker with a private service-account file path in `FCM_SERVICE_ACCOUNT`:

```bash
php backend/bin/push-worker.php
```

It obtains OAuth tokens and sends FCM HTTP v1 data messages, leasing jobs and retrying transient failures. `--once` drains at most one due job. Real delivery has **not** been verified without service-account credentials. Run under a supervisor for deployment.

## Executed local integration checks

Tests expect the isolated PHP endpoint at `127.0.0.1:8088`. `expiry.py` and `media_stories.py` also expect the disposable Docker MySQL fixture `kinnect-mysql-a3` with the local-only credentials in those test scripts. Never run fixture mutation tests on production.

```bash
python3 backend/tests/integration.py
python3 backend/tests/expiry.py
python3 backend/tests/social.py
python3 backend/tests/feed.py
python3 backend/tests/comments.py
python3 backend/tests/media_stories.py
python3 backend/tests/delivery.py
```

These scripts create unique accounts/content and assert actual PHP/MySQL behavior. They do not certify Android integration, device delivery, call quality, UI accuracy, or the live demo. Login throttling, remaining APIs, cursor synchronization, operational hardening, and the full Android migration remain tracked in `docs/ASSIGNMENT_03_PROGRESS.md`.
