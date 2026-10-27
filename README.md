# Kinnect (i230529_i230723)

CS 4039 Software for Mobile Devices, Assignment 2: "Kinnect Backend with Firebase".
Kotlin, one Activity per screen, Firebase Authentication + Realtime Database + Cloud Messaging, Agora for calls,
Cloudinary (unsigned upload preset) for photos and videos.

- Project name: `i230529_i230723`
- Package / applicationId: `com.shadowdevelopers.i230529_i230723`
- Submission ZIP: `i230529_i230723_Assignment02` (Android Studio > File > Export > Export to ZIP File)

## Setup

Nothing secret is in this repository. Each of these files is git-ignored and must be created on your machine.

| File | Where it comes from |
|---|---|
| `app/google-services.json` | Firebase console > Project settings > Your apps > Android app with package **com.shadowdevelopers.i230529_i230723** > download |
| `agora.properties` (repo root) | copy `agora.properties.example`; `appId=` from an Agora project in **Testing mode (App ID only)** |
| `media.properties` (repo root) | copy `media.properties.example`; Cloudinary cloud name + an **unsigned** upload preset |
| `fcm-server/serviceAccountKey.json` and `fcm-server/.env` | see `fcm-server/README.md` |

Firebase console, once:
1. Authentication > Sign-in method > enable **Email/Password**.
2. Realtime Database > create the database, then deploy the rules from this repo:
   `npm install -g firebase-tools && firebase login && firebase use --add && firebase deploy --only database`
   (or paste `database.rules.json` into Realtime Database > Rules > Publish).
3. Cloud Messaging works with the default project settings; pushes are sent by `fcm-server` (below).

Build: open the folder in Android Studio, or run `./gradlew :app:assembleDebug` (needs `app/google-services.json`).

## Push notifications (run during the demo)

```
cd fcm-server
cp .env.example .env   # set DATABASE_URL
npm install
npm start
```
The app writes small requests to `pushQueue/`; this script delivers them with FCM (new messages, screenshot alerts,
incoming calls). Without it, everything else works but no push arrives.

## Espresso tests

Two backend-connected tests in `app/src/androidTest/.../BackendWorkflowTest.kt`:
1. `loginThenCreatedPostAppearsInHomeFeed`
2. `sendingChatMessageShowsInConversationAndChatsList`

Seed one account (finished Edit profile) and make it friends with a second account. Then, with a phone or emulator connected:
```
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.testEmail=you@example.com \
  -Pandroid.testInstrumentationRunnerArguments.testPassword=yourPassword \
  "-Pandroid.testInstrumentationRunnerArguments.friendName=Friend Display Name"
```
Never commit real credentials.

## Features

Status uses one vocabulary: **compiles** means the code builds; **needs device test** means it has not been run on phones yet.
Every feature below compiles and still needs a device test.

| Feature (brief section) | Screens |
|---|---|
| Splash 5 s, auto routing | 01 |
| Sign up (live password strength), log in, recent login, forgot password, log out | 02, 03, 19 |
| First-time profile setup (unique username + bio) | 16 |
| Profile picture, cover, name, username, bio | 15, 16, 17 |
| Friend requests: send / confirm / delete / unfriend, Add friend → Requested → Friends, counts, mutual friends, suggestions | 13, 14, 17, 18 |
| Posts with text and photos, multi-image grid with "+N" | 04, 07, 08 |
| Six reactions, one per user, live summary | 04, 05, 06 |
| Comments with replies, likes and photos | 06 |
| Feed: me + friends, newest first | 04 |
| Stories: photo/video, label, Your story / Close friends, 24 h expiry, viewer, Your story with viewers | 04, 09, 10, 11, 12 |
| Chats list with last message, time, unread, online friends | 20 |
| Chat: text, photos, shared posts, edit/delete within 5 minutes, "Edited", replies, hearts, typing, seen | 21 |
| Voice and video calls (Agora), incoming call screen, call history in chat | 21, 22, new |
| Push notifications for new messages (FCM via fcm-server) | (system) |
| Search by username or name as you type | 13 |
| Online / offline, "Active now", "Active 5m ago" | 20, 21 |
| Screenshot alert (system message + notification) | 21 |
| Two Espresso tests | (tests) |

More detail: `docs/CODE_GUIDE.md` (how each feature works and viva questions), `docs/DEMO_SCRIPT.md`,
`docs/TEST_MATRIX.md`, `docs/UI_CHECKLIST.md`.

## Known limitations

- The Camera screen uses the phone's camera app (there is no live viewfinder inside Kinnect). TEXT / LIVE / BOOMERANG modes, flash and effects are design-only.
- Calls have no foreground service: audio may stop if the app stays in the background for a long time during a call.
  On Android 14+ the full-screen ringing screen needs the "full-screen notifications" permission; otherwise a heads-up notification is shown.
- Screenshot detection is reliable on Android 14+ (system API). On older versions it is best effort (it watches new images named "screenshot") and needs gallery permission.
- Pushes only arrive while `fcm-server` is running.
- Posts are readable by every signed-in user at the database level (the feed shows only friends); see `docs/CODE_GUIDE.md`.
- Searching posts and groups, notes, highlights, voice messages and the call keypad are not part of this version (the app says so when tapped).
