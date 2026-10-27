# Manual test matrix

Result stays **NOT RUN** until it is tried on a phone. Fill in PASS / FAIL + a note.

| Feature | Steps | Expected | Result |
|---|---|---|---|
| Splash timing | Cold start, logged out / logged in | 5 s, then Log in / Home | NOT RUN |
| Sign up | Create account | users + profiles written, goes to Edit profile | NOT RUN |
| Password strength | Type `abc`, `Abcdefgh`, `Abcdefg1!` | Weak / Medium / Strong with 1/2/3 bars | NOT RUN |
| Unique username | Second account tries the same username | "That username is already taken" | NOT RUN |
| First-time routing | New account saves Edit profile | Lands on Home, Back exits the app | NOT RUN |
| Recent login | Log out | Card shows the last account; tap fills the email | NOT RUN |
| Forgot password | Type email, tap Forgot password | Reset email received | NOT RUN |
| Logout | Menu → Log out | Offline on the other phone, Login screen, Back exits | NOT RUN |
| Profile photo / cover | Edit profile → pick both → Save | Shown on Profile, Other profile, Chats, feed | NOT RUN |
| Friend request | Search → Add friend → other phone Confirm | Requested → Friends on both | NOT RUN |
| Delete / unfriend | Delete a request; "…" → Unfriend | Request gone; friend count −1 on both | NOT RUN |
| Mutual friends | A, B, C friends; open C's profile from A | "Friends with B" | NOT RUN |
| Post with images | 1, 2, 3, 4, 6 photos | Layouts as in CODE_GUIDE §4, "+1" / "+3" | NOT RUN |
| Reactions | React, change, remove | One per user, live summary on both phones | NOT RUN |
| Comments | Reply, reply-to-reply, like, photo comment | Nested, "@Name", badge count, photo | NOT RUN |
| Feed scope | Non-friend posts | Not shown; own + friends newest first | NOT RUN |
| Story upload | Photo and video, both audiences | Tile on friends' Home, close friends only for list members | NOT RUN |
| Story expiry | Set `expiresAt` in the past in the console | Disappears; owner's app deletes it | NOT RUN |
| Story viewer / Your story | View, react | Seen by N, hearts | NOT RUN |
| Chats list | New message | Last message, time, bold + dot, badge | NOT RUN |
| Chat media | Photo, shared post | Bubble / card on both phones | NOT RUN |
| Edit / delete window | Before and after 5 min | Options only before; "Edited" label | NOT RUN |
| Replies / hearts | Reply, double-tap | Quote + header, heart badge for both | NOT RUN |
| Presence | Background / foreground | Green dot, "Active now" / "Active Xm ago" | NOT RUN |
| Search | First name, last name, username | Found while typing | NOT RUN |
| Push | Message to a backgrounded phone | Notification, opens chat; none if chat open | NOT RUN |
| Screenshot | Screenshot in chat (Android 14+ and older) | System message + notification | NOT RUN |
| Voice call | Call, accept, hang up | Audio both ways, both end, history line | NOT RUN |
| Video call | Call, accept | Both videos, switch camera | NOT RUN |
| Declined / missed call | Decline; ignore 30 s | "Declined" / "Missed" lines | NOT RUN |
| Espresso | `connectedDebugAndroidTest` with arguments | 2 tests pass | NOT RUN |
