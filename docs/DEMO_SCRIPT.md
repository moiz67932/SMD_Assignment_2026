# Live demo script (two phones)

Before the demo: both phones run the same debug build (with `google-services.json`, `agora.properties`, `media.properties`).
`fcm-server` is running on the laptop (`npm start`), and the rules are deployed. Phone **A** = Abdul Sami, phone **B** = Muhammad Moiz.
Grant notification, microphone, camera and photo permissions when asked.

| # | Step | Phone | Expected |
|---|---|---|---|
| 1 | Open the app | A | Splash for 5 s, then Log in |
| 2 | Create account (watch the password meter), birthday, gender | A | Meter goes red → orange → teal; lands on Edit profile |
| 3 | Pick a username + bio + profile photo + cover, Save | A | Upload %, then Home; Recent login card shows A on next logout |
| 4 | Log in with an existing account | B | Home |
| 5 | Search "<A's last name>" and tap Add friend | B | Button turns "Requested" |
| 6 | Open Notifications, tap Confirm | A | Request disappears; both profiles show "1 friend" |
| 7 | Create a post with 5 photos | A | Feed card: big + 2 tiles with "+2" |
| 8 | Long-press Like → Love; then add a comment, reply to it, like it, add a photo comment | B | A sees "B and …" summary, nested reply with "Replying to" bar and Cancel, like badge, photo |
| 9 | Camera → take a photo → label → Your story; then a 5 s video → Close friends (add B first) | A | B sees A's tile first after "Create story" with a teal ring |
| 10 | Open A's story, send ❤️ | B | Ring turns grey; A's Your story shows "Seen by 1" and a heart; A gets a chat message |
| 11 | Chat: send text, photo, share a post (Share on a feed post) | A | Appears on B live; Chats preview "Photo" / "Shared a post"; unread dot + badge on B |
| 12 | Long-press A's last message: Edit, then Delete another within 5 min | A | "Edited" on both phones; Chats preview updates |
| 13 | Wait 5 minutes, long-press an old message | A | Only Reply / Heart |
| 14 | Double-tap a message, reply to a message | B | Heart badge on A's phone; "B replied to you" with quote |
| 15 | Presence: put A in the background ~6 s | A | B: green dot gone, Chat header "Active just now" |
| 16 | Take a screenshot in the chat (Android 14+) | A | "A took a screenshot" in the chat on both phones; B gets a notification |
| 17 | Push: B presses Home; A sends a message | A | B gets a "Messages" notification; tapping opens the chat |
| 18 | Voice call: tap the phone icon | A | B rings (full screen if in background); Accept → timer on both; B hangs up → both close; chat shows "Voice call · m:ss" |
| 19 | Video call | A | Both previews, remote video, switch camera, camera off |
| 20 | Decline a call / let one ring out | B | "Voice call declined" / "Missed voice call" in the chat |
| 21 | Menu → Log out, press Back | A | Login screen; Back leaves the app (no way back into Home) |
