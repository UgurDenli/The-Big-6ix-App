const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { google } = require("googleapis");
const cors = require("cors")({ origin: true });

admin.initializeApp();
const db = admin.firestore();

const DISCORD_GUILD_ID   = "1204439893036630026"; // The Community
const DISCORD_REDIRECT   = "https://the-big-6ix.web.app/discord-callback.html";

const YOUTUBE_MEMBER_ROLES = [
  "1402342200515231764", // YouTube Member
  "1402342200515231765", // YouTube Member: The Community 🫵
];
const PRIVILEGED_ROLES = [
  "1216816077783302226", // Admin
  "1204451389665845299", // Moderator
  "1216899081301917726", // Owner
  "1379468933114892449", // Dev Man
];

exports.verifyDiscordRole = functions.https.onRequest(async (req, res) => {
  res.set("Access-Control-Allow-Origin", "*");
  res.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  res.set("Access-Control-Allow-Headers", "Content-Type");
  if (req.method === "OPTIONS") { res.status(204).send(""); return; }

  const code = req.query.code || (req.body && req.body.code);
  if (!code) {
    return res.status(200).json({ success: false, error: "Missing code parameter" });
  }

  try {
    // 1. Exchange code for Discord access token
    const tokenRes = await fetch("https://discord.com/api/oauth2/token", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded" },
      body: new URLSearchParams({
        client_id:     functions.config().discord.client_id,
        client_secret: functions.config().discord.client_secret,
        grant_type:    "authorization_code",
        code:          code,
        redirect_uri:  DISCORD_REDIRECT,
      }),
    });
    const tokenData = await tokenRes.json();
    if (tokenData.error) {
      return res.status(200).json({
        success: false,
        error: tokenData.error_description || tokenData.error,
        error_code: tokenData.error,
        used_redirect_uri: DISCORD_REDIRECT,
      });
    }

    const accessToken = tokenData.access_token;

    // 2. Get Discord user info
    const userRes = await fetch("https://discord.com/api/users/@me", {
      headers: { Authorization: `Bearer ${accessToken}` },
    });
    const user = await userRes.json();
    if (!user.id) {
      return res.status(200).json({ success: false, error: "Failed to fetch Discord user info" });
    }
    const discordId   = user.id;
    const displayName = user.global_name || user.username;

    // 3. Check guild membership with the user's own token (guilds.members.read scope)
    const memberRes = await fetch(
      `https://discord.com/api/users/@me/guilds/${DISCORD_GUILD_ID}/member`,
      { headers: { Authorization: `Bearer ${accessToken}` } }
    );

    if (!memberRes.ok) {
      return res.status(200).json({
        success: false,
        error: "You must be a member of The Big 6ix Discord server to log in.",
      });
    }

    // 4. Check YouTube Member role (or privileged role bypass)
    const memberData = await memberRes.json();
    const userRoles = memberData.roles || [];
    const hasAccess =
      userRoles.some(r => YOUTUBE_MEMBER_ROLES.includes(r)) ||
      userRoles.some(r => PRIVILEGED_ROLES.includes(r));

    if (!hasAccess) {
      return res.status(200).json({
        success: false,
        error: "You need to be a YouTube channel member to access The Big 6ix app. Join at youtube.com/@TheBig6ix",
      });
    }

    // 5. Mint Firebase custom token using Discord user ID
    const firebaseToken = await admin.auth().createCustomToken(discordId);

    return res.status(200).json({ success: true, token: firebaseToken, name: displayName });

  } catch (e) {
    console.error("verifyDiscordRole error:", e);
    return res.status(200).json({ success: false, error: `Server error: ${e.message}` });
  }
});

// === YouTube Membership Check ===
const oAuth2Client = new google.auth.OAuth2(
functions.config().google.client_id,
functions.config().google.client_secret,
"https://us-central1-the-big-6ix.cloudfunctions.net/exchangeAuthCodeForTokenAndCheckMembership"
);

exports.exchangeAuthCodeForTokenAndCheckMembership = functions.https.onRequest((req, res) => {
cors(req, res, async () => {
try {
  const authCode = req.body.authCode;
  if (!authCode) return res.status(400).send({ error: "Authorization code is required" });

  const { tokens } = await oAuth2Client.getToken(authCode);
  oAuth2Client.setCredentials(tokens);

  const youtube = google.youtube({ version: "v3", auth: oAuth2Client });
  const response = await youtube.subscriptions.list({ part: "snippet", mine: true, maxResults: 50 });

  const isMember = (response.data.items || []).some((sub) =>
    sub.snippet?.resourceId?.channelId === "UCUP5RcljxXkKm3agi9WNrDA"
  );

  res.status(isMember ? 200 : 403).send({
    isMember,
    message: isMember ? undefined : "Not a member of the channel"
  });
} catch (err) {
  console.error("Membership check error:", err.message);
  res.status(500).send({ error: "Verification failed", details: err.message });
}
});
});

// === Shared Scoring Logic ===
const calculatePoints = async () => {
const fixturesSnap = await db.collection("fixtures").get();
const predictionsRef = db.collection("predictions");
const usersRef = db.collection("users");

for (const fixtureDoc of fixturesSnap.docs) {
const fixture = fixtureDoc.data();
const fixtureId = fixtureDoc.id;

if (fixture.homeTeamGoals < 0 || fixture.awayTeamGoals < 0) continue;

const actualOutcome =
  fixture.homeTeamGoals === fixture.awayTeamGoals
    ? "draw"
    : fixture.homeTeamGoals > fixture.awayTeamGoals
    ? "home"
    : "away";

const predictionsSnap = await predictionsRef
  .where("fixtureId", "==", fixtureId)
  .where("scoredPoints", "==", false)
  .get();

for (const predictionDoc of predictionsSnap.docs) {
  const prediction = predictionDoc.data();
  const userRef = usersRef.doc(prediction.userId);
  const userDoc = await userRef.get();

  const predictedOutcome =
    prediction.homeTeamGoals === prediction.awayTeamGoals
      ? "draw"
      : prediction.homeTeamGoals > prediction.awayTeamGoals
      ? "home"
      : "away";

  let points = 0;
  if (
    prediction.homeTeamGoals === fixture.homeTeamGoals &&
    prediction.awayTeamGoals === fixture.awayTeamGoals
  ) {
    points = 3;
  } else if (predictedOutcome === actualOutcome) {
    points = 1;
  }

  console.log(`User ${prediction.userId} earned ${points} pts for fixture ${fixtureId}`);

  await predictionDoc.ref.update({
    scoredPoints: true,
    isCorrect: points > 0,
    awardedPoints: points,
  });

  await userRef.update({
    score: admin.firestore.FieldValue.increment(points),
    weeklyScore: admin.firestore.FieldValue.increment(points),
    monthlyScore: admin.firestore.FieldValue.increment(points),
  });

  // 🔔 Push Notification
  const userData = userDoc.data();
  const token = userData?.fcmToken;

  if (token) {
    const payload = {
      notification: {
        title: `You earned ${points} point${points !== 1 ? "s" : ""}!`,
        body: `Your new total is updating...`,
        sound: "default"
      },
      token: token,
    };

    try {
      await admin.messaging().send(payload);
      console.log(`Notification sent to ${prediction.userId}`);
    } catch (e) {
      console.error(`Failed to send notification to ${prediction.userId}`, e.message);
    }
  }
}
}
};

// === Scheduled: every 5 minutes
exports.calculatePoints = functions.pubsub.schedule("every 5 minutes").onRun(async () => {
await calculatePoints();
});

// === Manual trigger (Postman or browser)
exports.manualCalculatePoints = functions.https.onRequest(async (req, res) => {
try {
await calculatePoints();
res.status(200).send("Manual point calculation completed.");
} catch (e) {
console.error("Manual calculation error:", e);
res.status(500).send("Error during manual scoring.");
}
});
