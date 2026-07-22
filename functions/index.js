const functions = require("firebase-functions/v1");
const admin = require("firebase-admin");
const { randomBytes } = require("crypto");

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
        : after.type === "drawing"
        ? "🎨 Sent a drawing"
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

// shared/nudge is a single overwritten node (see MessageRepository.sendNudge),
// not a growing list — onWrite (not onCreate) so every nudge after the first
// still triggers this. Firebase only fires onWrite when the value actually
// changes, which is why the client always includes a fresh ServerValue.TIMESTAMP.
exports.onNudge = functions.database
  .ref("/shared/nudge")
  .onWrite(async (change) => {
    const after = change.after.val();
    if (!after || !after.senderUid) return null;

    const senderSnap = await db.ref(`users/${after.senderUid}/displayName`).get();
    const senderName = senderSnap.val() || "Someone";

    const tokens = await tokensForUsersExcept(after.senderUid);
    return sendToTokens(tokens, {
      type: "nudge",
      title: "💓 Thinking of you",
      body: `${senderName} sent you a nudge`,
    });
  });

// shared/moods/{uid} is set by MoodViewModel/FirebaseSync.setMood — no
// title/body here on purpose: FCMService.onMessageReceived triggers a
// widget refresh (WidgetSyncTrigger.requestSync) for *any* received push
// before it even looks at title/body, and only shows a visible
// notification if a title is present. So this just gets the partner's
// widget to pick up the new mood promptly, with no notification popup for
// what's meant to be a quiet status change, not an alert.
exports.onMoodChanged = functions.database
  .ref("/shared/moods/{uid}")
  .onWrite(async (change, context) => {
    const tokens = await tokensForUsersExcept(context.params.uid);
    return sendToTokens(tokens, { type: "mood" });
  });

// shared/settings is deliberately locked to ".write": false in the database
// rules (see database.rules.json) — allowedUsers is the authorization
// boundary for the whole app, so no client can grant itself access there
// directly. These two callable functions are the only path in: the Admin
// SDK here bypasses the rules entirely, same as the notification functions
// above already do for reading tokens.

// Callable by an already-allowed user only — generates a short-lived
// numeric code a second person can redeem to get their own allowedUsers
// entry. Doesn't touch allowedUsers itself; redeemPairingCode does that.
exports.createPairingCode = functions.https.onCall(async (data, context) => {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in first.");
  }
  const allowedSnap = await db.ref(`shared/settings/allowedUsers/${uid}`).get();
  if (!allowedSnap.val()) {
    throw new functions.https.HttpsError(
      "permission-denied",
      "Only an existing Glimpse user can invite someone."
    );
  }

  // 6 digits — short enough to read aloud or type by hand, and this is an
  // invite-only surface (not a public signup form), so the ~1-in-a-million
  // guess space plus the 15-minute window is a reasonable tradeoff against
  // usability rather than a hardened secret.
  const code = (randomBytes(4).readUInt32BE(0) % 1000000).toString().padStart(6, "0");
  const expiresAt = Date.now() + 15 * 60 * 1000;
  await db.ref(`shared/pairing_codes/${code}`).set({ createdBy: uid, expiresAt });
  return { code, expiresAt };
});

// Callable by anyone signed in, including someone with zero allowedUsers
// access yet — that's the whole point, since a not-yet-paired account has
// no other way to get its first entry. Capped at two people total, matching
// every other part of this app's 2-person assumption.
exports.redeemPairingCode = functions.https.onCall(async (data, context) => {
  const uid = context.auth?.uid;
  if (!uid) {
    throw new functions.https.HttpsError("unauthenticated", "Sign in first.");
  }
  const code = (data?.code || "").trim();
  if (!/^\d{6}$/.test(code)) {
    throw new functions.https.HttpsError("invalid-argument", "Enter the 6-digit code.");
  }

  const codeRef = db.ref(`shared/pairing_codes/${code}`);
  const snap = await codeRef.get();
  const entry = snap.val();
  if (!entry) {
    throw new functions.https.HttpsError("not-found", "That code isn't valid.");
  }
  if (entry.expiresAt < Date.now()) {
    await codeRef.remove();
    throw new functions.https.HttpsError("deadline-exceeded", "That code has expired — ask for a new one.");
  }
  if (entry.createdBy === uid) {
    throw new functions.https.HttpsError("failed-precondition", "You can't redeem your own invite code.");
  }

  const allowedSnap = await db.ref("shared/settings/allowedUsers").get();
  const allowedUids = Object.keys(allowedSnap.val() || {});
  if (allowedUids.length >= 2 && !allowedUids.includes(uid)) {
    throw new functions.https.HttpsError("resource-exhausted", "Glimpse is already paired with two people.");
  }

  await db.ref(`shared/settings/allowedUsers/${uid}`).set(true);
  await codeRef.remove();
  return { success: true };
});
