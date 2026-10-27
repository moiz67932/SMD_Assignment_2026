// Kinnect push sender.
// The Android app never sends pushes itself (that would need a secret key inside the APK).
// Instead it writes a small item to pushQueue/{pushId}; this script, running on a laptop with the
// project's service account, picks each item up, sends it with Firebase Cloud Messaging and deletes it.
const fs = require('fs');
const path = require('path');
const admin = require('firebase-admin');

// --- tiny .env reader (no extra dependency) ---
const envFile = path.join(__dirname, '.env');
if (fs.existsSync(envFile)) {
  for (const line of fs.readFileSync(envFile, 'utf8').split(/\r?\n/)) {
    const match = line.match(/^\s*([A-Z_]+)\s*=\s*(.*)\s*$/);
    if (match && !process.env[match[1]]) process.env[match[1]] = match[2];
  }
}

const keyPath = path.resolve(__dirname, process.env.SERVICE_ACCOUNT_PATH || './serviceAccountKey.json');
if (!fs.existsSync(keyPath) || !process.env.DATABASE_URL) {
  console.error('Missing setup: put the service account key at', keyPath, 'and DATABASE_URL in .env (see .env.example).');
  process.exit(1);
}

admin.initializeApp({
  credential: admin.credential.cert(require(keyPath)),
  databaseURL: process.env.DATABASE_URL,
});
const db = admin.database();
const MAX_AGE_MS = 2 * 60 * 1000; // skip items older than 2 minutes (e.g. queued while this script was off)

async function deliver(snapshot) {
  const item = snapshot.val() || {};
  const id = snapshot.key;
  try {
    if (!item.toUid || (item.createdAt && Date.now() - item.createdAt > MAX_AGE_MS)) {
      console.log(`[skip] ${id} (${item.type || 'unknown'}) is too old or has no receiver`);
      return;
    }
    const tokensSnap = await db.ref(`fcmTokens/${item.toUid}`).once('value');
    const tokens = Object.entries(tokensSnap.val() || {}); // [tokenId, token]
    if (tokens.length === 0) {
      console.log(`[skip] ${id}: ${item.toUid} has no registered device`);
      return;
    }
    // Data-only, high priority: the app builds the notification itself (and can skip it if that chat is open).
    const data = {};
    for (const key of ['type', 'title', 'body', 'chatId', 'fromUid', 'callId', 'video']) {
      if (item[key] !== undefined && item[key] !== null) data[key] = String(item[key]);
    }
    const response = await admin.messaging().sendEach(tokens.map(([, token]) => ({
      token,
      data,
      android: { priority: 'high', ttl: item.type === 'call' ? 30 * 1000 : 60 * 60 * 1000 },
    })));
    // Remove tokens of uninstalled apps / logged-out phones so they are not tried again.
    const dead = [];
    response.responses.forEach((result, index) => {
      const code = result.error && result.error.code;
      if (code === 'messaging/registration-token-not-registered' || code === 'messaging/invalid-registration-token') {
        dead.push(db.ref(`fcmTokens/${item.toUid}/${tokens[index][0]}`).remove());
      }
    });
    await Promise.all(dead);
    console.log(`[sent] ${id} ${item.type} -> ${item.toUid}: ${response.successCount}/${tokens.length} delivered, ${dead.length} dead token(s) removed`);
  } catch (error) {
    console.error(`[error] ${id}:`, error.message);
  } finally {
    await snapshot.ref.remove().catch(() => {});
  }
}

db.ref('pushQueue').on('child_added', (snapshot) => { deliver(snapshot); });
console.log('Kinnect FCM server is running. Waiting for pushQueue items… (Ctrl+C to stop)');
