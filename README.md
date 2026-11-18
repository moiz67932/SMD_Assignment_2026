# Kinnect (i230529_i230723), Assignment 3

CS 4039 Software for Mobile Devices, Assignment 3: "Kinnect with web services & SQLite".
Kotlin Android app (one Activity per screen) on its own **PHP + MySQL web services**, with an **SQLite** cache and an
offline action queue. Firebase is used **only for push notifications (FCM)**; calls use **Agora**.

- Project name: `i230529_i230723` · package `com.shadowdevelopers.i230529_i230723`
- Submission ZIP: `i230529_i230723_Assignment03` (Android Studio > File > Export > Export to ZIP File)

## Architecture

```
Screens (Activities) ──> Repositories (PostRepo, ChatRepo, StoryRepo, Friends, Inbox, ProfileCache)
        ▲                     │ read: SQLite first, then the server        │ write: SQLite + pending_actions (one transaction)
        │                     ▼                                             ▼
        └──────────── SQLite (OfflineStore: entities + pending_actions) <── SyncJobService (drains the queue in order)
                                                                             │ HTTPS/HTTP + Bearer token, action UUID
                                                                             ▼
                                         PHP web services (backend/public/*.php) ──> MySQL (MariaDB in XAMPP)
                                                     │                          └──> media files on the server filesystem
                                                     └── push_jobs ──> bin/push-worker.php ──> FCM ──> phones
```

- **Auth**: `auth/signup.php`, `auth/login.php`, `auth/logout.php`. Passwords are stored with `password_hash`. Login
  returns a random session token (only its SHA-256 is stored in MySQL); the phone keeps it encrypted with an
  Android Keystore AES-GCM key (`data/SecureSession.kt`) and sends `Authorization: Bearer <token>` on every request.
- **Offline**: every write (message, post, reaction, comment, friend request, edit/delete, story, ...) is saved locally
  and added to `pending_actions` in **one SQLite transaction**, shown at once with a **Pending** label, and sent in order
  by `SyncJobService` (persisted JobScheduler job, also run immediately when online and when the network returns).
  Each action carries a **client UUID**; the server's `processed_actions` table makes a retried action return its
  first result instead of running twice (**no duplicates, no data loss**).
- **Live updates**: open screens poll their endpoint (chat every 3 s, chats 4 s, notifications 8 s, feed 15 s) and
  refresh as soon as queued actions reach the server. Presence = heartbeat every 20 s (online until 45 s after the last).
- **Media**: `media/upload.php` (multipart) stores files outside the web root; `media/get.php` serves them only to
  users allowed to see them. The phone keeps a copy so cached content shows offline.

## Run it (laptop = server, two phones on the same Wi-Fi)

1. **Database (XAMPP)**: `sudo /opt/lampp/lampp startmysql` (once per boot).
2. **First time only**: `./backend/run-xampp.sh setup` creates the `kinnect` database, a private DB user (random password
   in `backend/.env`, git-ignored) and runs all migrations in `backend/migrations`.
3. **API server**: `./backend/run-xampp.sh` prints `Kinnect API: http://<laptop-ip>:8088`. Open the firewall once:
   `sudo firewall-cmd --add-port=8088/tcp --permanent && sudo firewall-cmd --reload`.
4. **Push worker** (FCM from PHP): put the Firebase service-account key at `backend/var/service-account.json`
   (git-ignored) and run `./backend/run-xampp.sh push` in a second terminal.
5. **App config** (all git-ignored, copy the `.example` files):
   - `api.properties`: `baseUrl=http://<laptop-ip>:8088/` (the address printed in step 3)
   - `agora.properties`: `appId=` and `appCertificate=` of the Agora project (tokens are built on the phone)
   - `app/google-services.json`: Firebase project file (used only for FCM)
6. Build and install the debug build on both phones from Android Studio. (Only the debug build allows plain HTTP to
   the laptop; release builds are HTTPS-only.)

Never commit `backend/.env`, `backend/var/`, `api.properties`, `agora.properties`, `google-services.json` or the
service-account key.

## Tests

- Backend (against the running API): `python3 backend/tests/<name>.py` for `integration`, `social`, `feed`, `comments`,
  `delivery`, `device_notifications`, `expiry`, `media_stories`, `extended_media`.
- Espresso (two critical workflows, `app/src/androidTest/.../BackendWorkflowTest.kt`): seed one account with a finished
  profile and one friend, then
  ```
  ./gradlew connectedDebugAndroidTest \
    -Pandroid.testInstrumentationRunnerArguments.testEmail=you@example.com \
    -Pandroid.testInstrumentationRunnerArguments.testPassword=yourPassword \
    "-Pandroid.testInstrumentationRunnerArguments.friendName=Friend Display Name"
  ```
  `OfflineQueueTest` checks queue persistence, order, retry and account separation on the device.

## Live demo checklist (two phones)

1. Laptop: MySQL running, `./backend/run-xampp.sh` and `./backend/run-xampp.sh push` running; both phones on the same Wi-Fi.
2. Splash (5 s) → Home without logging in again (stored token). Log out on one phone → Log in → Recent login card.
3. Phone A posts photos (grid with "+N"), phone B reacts and comments; A gets the notification.
4. Chat: send text, photo, video, voice and a file; edit and delete within 5 minutes ("Edited"); B sees it live.
5. Presence: green dot / "Active now"; close the app on A and B shows "Active … ago".
6. Screenshot in a chat → system message "… took a screenshot" + notification on the other phone.
7. Voice and video call from the chat icons; the call summary appears in the chat (call log stored on the server).
8. **Offline**: turn Wi-Fi off on A ("You're offline" appears). Send messages, react, comment, post, send a friend request:
   each shows **Pending**. Kill and reopen the app (still there, Splash goes to Home). Turn Wi-Fi on
   ("Back online · syncing N pending changes"): everything appears **once** on phone B.

More detail: `docs/CODE_GUIDE.md` (how each feature works, viva notes) and `docs/ASSIGNMENT_03_PROGRESS.md`.
