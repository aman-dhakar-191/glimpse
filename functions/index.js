const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");

admin.initializeApp();
const db = admin.database();

// The client only registers each other's tokens under
// users/{uid}/fcmTokens/{token}, so "everyone except the actor" is the
// allowlist minus whoever just wrote the change. Keeps {uid, token} pairs
// (not a flat token list) so a dead token can be pruned from the right
// user's node afterward.
async function tokensForUsersExcept(excludeUid) {
  const allowedSnap = await db.ref("shared/settings/allowedUsers").get();
  const allowedUids = Object.keys(allowedSnap.val() || {});
  const recipientUids = allowedUids.filter((uid) => uid !== excludeUid);

  const tokensByUser = await Promise.all(
    recipientUids.map(async (uid) => {
      const tokensSnap = await db.ref(`users/${uid}/fcmTokens`).get();
      return Object.keys(tokensSnap.val() || {}).map((token) => ({ uid, token }));
    })
  );
  return tokensByUser.flat();
}

// Sent as a data-only message (no top-level "notification" key) so
// FCMService.onMessageReceived is always invoked and builds the visible
// notification itself — a "notification" payload would instead be
// auto-displayed by the system tray whenever the app is backgrounded,
// bypassing our own styling/tap-target/channel setup.
async function sendToTokens(recipients, data) {
  if (recipients.length === 0) return null;
  const response = await admin.messaging().sendEachForMulticast({
    tokens: recipients.map((r) => r.token),
    data,
    android: { priority: "high" },
  });
  // Registered-but-dead tokens (uninstalled app, etc.) fail permanently —
  // prune them so the recipient list doesn't grow stale forever.
  const staleUpdates = {};
  response.responses.forEach((result, index) => {
    if (
      !result.success &&
      (result.error?.code === "messaging/registration-token-not-registered" ||
        result.error?.code === "messaging/invalid-registration-token")
    ) {
      const { uid, token } = recipients[index];
      staleUpdates[`users/${uid}/fcmTokens/${token}`] = null;
    }
  });
  if (Object.keys(staleUpdates).length > 0) {
    await db.ref().update(staleUpdates);
  }
  return response;
}

// Each message now gets its own push key under shared/messages/{messageId}
// (see MessageRepository.sendMessage) instead of overwriting one shared
// node, so History can show a scrollback instead of just the latest message.
// onCreate (not onWrite) so this fires exactly once, when the message is
// first written — reactions live under a more specific child path and are
// handled by onNewReaction below instead, per Firebase's closest-ancestor
// trigger matching.
exports.onNewMessage = functions.database
  .ref("/shared/messages/{messageId}")
  .onCreate(async (snapshot) => {
    const after = snapshot.val();
    if (!after) return null;

    const tokens = await tokensForUsersExcept(after.authorUid);
    const preview =
      after.type === "photo"
        ? "📷 Sent a photo"
        : after.content && after.content.length > 0
        ? after.content
        : "Sent a new message";

    return sendToTokens(tokens, {
      type: "message",
      title: after.authorName || "Glimpse",
      body: preview,
    });
  });

// 👀 is written automatically (FirebaseSync.markSeenIfNeeded) the moment the
// other person's widget/app loads your message — it's meant to be a quiet
// visual "seen" cue on the widget, not a push notification, so it's excluded
// here rather than firing "X reacted 👀" every time a message is opened.
const AUTO_SEEN_EMOJI = "👀";

exports.onNewReaction = functions.database
  .ref("/shared/messages/{messageId}/reactions/{emoji}")
  .onWrite(async (change, context) => {
    if (context.params.emoji === AUTO_SEEN_EMOJI) return null;

    const before = change.before.val() || [];
    const after = change.after.val() || [];
    if (after.length <= before.length) return null; // only notify on additions

    const messageSnap = await db.ref(`shared/messages/${context.params.messageId}`).get();
    const message = messageSnap.val();
    if (!message) return null;

    const newUid = after.find((uid) => !before.includes(uid));
    if (!newUid || newUid === message.authorUid) return null; // no self-notify

    const reactorSnap = await db.ref(`users/${newUid}/displayName`).get();
    const reactorName = reactorSnap.val() || "Someone";
    const emoji = context.params.emoji;

    const tokens = await tokensForUsersExcept(newUid);
    return sendToTokens(tokens, {
      type: "reaction",
      title: "New reaction",
      body: `${reactorName} reacted ${emoji}`,
    });
  });
