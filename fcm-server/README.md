# Kinnect FCM server

The app writes a small item to `pushQueue/{pushId}` whenever it needs to notify someone
(new message, screenshot, incoming call). This script reads those items and sends them with
Firebase Cloud Messaging using the Admin SDK, then deletes each item. It runs on your laptop during
the demo. It needs no paid Firebase plan. Cloud Functions would need the Blaze plan.

## Setup (once)
1. Install Node.js 18 or newer.
2. Firebase console > Project settings > Service accounts > **Generate new private key**.
   Save the file as `fcm-server/serviceAccountKey.json`. It is git-ignored; never commit or share it.
3. `cp .env.example .env` and set `DATABASE_URL` to your Realtime Database URL.
4. `npm install`

## Run
```
cd fcm-server
npm start
```
Leave it running. Every delivery is logged, for example `[sent] -Nx… message -> <uid>: 1/1 delivered`.

## How it works
- `child_added` on `pushQueue` fires for every new item.
- Items older than 2 minutes are skipped, so old messages queued while the server was off are not pushed later.
- Tokens come from `fcmTokens/{toUid}`; the app saves its token after login and removes it on logout.
- Pushes are **data-only, high priority**. The app's `KinnectMessagingService` builds the notification itself,
  so it can skip the notification when that chat is already open on screen.
- Dead tokens (uninstalled app) are deleted.
