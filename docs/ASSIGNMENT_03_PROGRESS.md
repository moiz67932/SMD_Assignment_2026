# Assignment 03 implementation evidence

18 November 2026. This file records observed results, not planned completion.

## Implemented foundation

- PHP PDO bootstrap, structured error responses, opaque hashed server sessions.
- Signup/login/me/logout endpoints. Signup name validation does not require mbstring.
- MySQL account/session/device/action receipt and initial social/media/chat/message schema.
- Migration runner with MySQL advisory lock and recovery of partial CREATE TABLE migrations. MySQL DDL is not wrapped in a misleading transaction.
- Direct-chat creation; text send/list/edit/delete APIs. Mutation receipt replay and conflicting UUID detection are implemented with an actor lock and a transaction.
- SQLite atomic optimistic entity/action insertion, ordered action claims with leases, acknowledgment protection for newer pending changes, failure states and account cleanup.
- Keystore AES-GCM session storage; token/cache backup exclusions.

## Executed checks

- All PHP files passed `php -l`.
- Three migrations applied to local MySQL 8.4 container `kinnect-mysql-a3`, port 3307.
- `python3 backend/tests/integration.py` passed authentication, credential rejection, token revocation, membership denial, message replay, UUID payload conflict, sender authorization, edit and delete checks.
- `python3 backend/tests/expiry.py` passed server rejection of edits/deletes to messages older than five minutes.
- `./gradlew :app:compileDebugKotlin --offline` passed (BUILD SUCCESSFUL, 35 seconds). This verifies compilation, not device behavior.

## Still incomplete

The Android screens still use Firebase; the new local components are not wired into production flows. There is no working reconnect scheduler, API client, screen observer integration or attachment pipeline yet. Full friends/posts/comments/stories/profile/search/media/push/call APIs, change feeds, rate limiting, logout presence/device cleanup, two-device checks, migrated Espresso tests, private repository creation and submission export remain. Existing Firebase code cannot be removed until its callers have migrated. The current backend text messaging is a foundation, not complete messaging: media, notifications, unread/read previews and delta pagination remain.

## Local services

The PHP development server was launched on 127.0.0.1:8088 with document root backend/public. The MySQL fixture uses disposable local credentials; it is not a production deployment. Do not expose either service publicly. Test scripts assume this isolated fixture and create test accounts/messages.

## Subsequent API work (18 November)

Implemented SQL profile setup/get with unique usernames; friend send/accept/decline/cancel/remove/list; required relationship search filters; text post create/feed; six reactions; text comments/replies and comment likes. Added a synchronous Android HTTP client for repository workers with encrypted session persistence. These APIs are not yet connected to production Activities. Multipart posts, image comments, photo/cover profile changes and full pagination are still missing from these routes.

Executed `social.py`, `feed.py`, and `comments.py` against the PHP/MySQL fixture: PASS for profile setup/username uniqueness, friend send/accept/remove and filters, post replay and feed authorization, six reaction changes/removal, comment replay/replies/likes and outsider denial. Migration 004 applied. Latest Kotlin compilation: BUILD SUCCESSFUL. Firebase Auth/RTDB removal is **not complete**; FCM must remain under the assignment specification.

## Media, delivery and durable scheduling implementation

- Implemented protected filesystem uploads/downloads (including range responses), multipart multi-image posts, story create/feed/views and close-friend controls. PHP/MySQL tests passed upload replay, script rejection, audience enforcement, server expiry and expired-media denial, and multipart post replay.
- Added message attachments, logical notification records, durable per-device push jobs, screenshot system-message/notification transaction, device registration and session heartbeat presence. Delivery tests passed presence/logout, notification deduplication and screenshot replay.
- Added PHP HTTP v1 FCM worker with OAuth, leases, retry backoff and invalid-device cleanup. Syntax checks pass. Startup correctly refuses missing credentials; actual FCM delivery has NOT been tested.
- Added a network-constrained persisted Android JobScheduler service for SQLite queue draining, plus instrumentation tests for queue persistence/order/retry/account separation. Main Kotlin compilation passes; instrumented execution requires a device. Scheduler and API client are not yet wired into existing Firebase Activities.
- Created private GitHub repository `https://github.com/moiz67932/i230529_i230723_Assignment03`. Source has not been pushed as a completed submission.
- No Android devices were connected at the ADB check. No project FCM service-account key was found. Remaining code work is not blocked by these prerequisites, but actual push/device/demo validation requires them.

### Recheck of current revision

All backend fixture suites (expiry, social, feed, comments, media/stories, delivery) passed after delivery schema changes. Main Kotlin and instrumentation Kotlin compilation passed; `assembleDebugAndroidTest` passed after fetching an uncached test dependency online. The Small_Phone emulator crashed with exit 139; a Medium_Phone retry with cameras disabled is being used for actual queue instrumentation. Test compilation/package success must not be reported as device test success. The worker also cancels stale notification jobs when a device registration changes account.

Queue instrumentation attempt: FAILED at device discovery/property retrieval (ADB PropertyFetcher timeout, no compatible ready device). No queue test body was confirmed executed. Medium_Phone is still starting; Gradle daemon was stopped to free resources. Treat queue instrumentation as NOT VERIFIED until a successful rerun. This is distinct from the passing compilation and packaging checks.
