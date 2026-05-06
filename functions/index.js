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
const scoreFixturePredictions = async (fixtureDoc, predictionsRef, usersRef, scoredGameweeks) => {
  const fixture = fixtureDoc.data();
  const fixtureId = fixtureDoc.id;

  const fixtureHome = Number(fixture.homeTeamGoals);
  const fixtureAway = Number(fixture.awayTeamGoals);

  if (isNaN(fixtureHome) || isNaN(fixtureAway) || fixtureHome < 0 || fixtureAway < 0) return;

  const actualOutcome =
    fixtureHome === fixtureAway ? "draw" : fixtureHome > fixtureAway ? "home" : "away";

  console.log(`Scoring fixture ${fixtureId}: ${fixtureHome}-${fixtureAway} (${actualOutcome})`);

  const predictionsSnap = await predictionsRef
    .where("fixtureId", "==", fixtureId)
    .where("scoredPoints", "==", false)
    .get();

  for (const predictionDoc of predictionsSnap.docs) {
    const prediction = predictionDoc.data();
    const userRef = usersRef.doc(prediction.userId);
    const userDoc = await userRef.get();

    const predHome = Number(prediction.homeTeamGoals);
    const predAway = Number(prediction.awayTeamGoals);
    const predictedOutcome =
      predHome === predAway ? "draw" : predHome > predAway ? "home" : "away";

    let points = 0;
    if (predHome === fixtureHome && predAway === fixtureAway) {
      points = 3;
    } else if (predictedOutcome === actualOutcome) {
      points = 1;
    }

    if (prediction.captainUsed && points > 0) points *= 2;

    console.log(`  User ${prediction.userId} predicted ${predHome}-${predAway} → ${points} pts`);

    await predictionDoc.ref.update({ scoredPoints: true, isCorrect: points > 0, awardedPoints: points });
    await userRef.update({
      score: admin.firestore.FieldValue.increment(points),
      weeklyScore: admin.firestore.FieldValue.increment(points),
      monthlyScore: admin.firestore.FieldValue.increment(points),
    });

    if (scoredGameweeks) scoredGameweeks.add(fixture.gameweek);

    const token = userDoc.data()?.fcmToken;
    if (token) {
      try {
        await admin.messaging().send({
          notification: {
            title: `You earned ${points} point${points !== 1 ? "s" : ""}!`,
            body: "Your new total is updating...",
            sound: "default",
          },
          token,
        });
      } catch (e) {
        console.error(`FCM error for ${prediction.userId}:`, e.message);
      }
    }
  }
};

const calculatePoints = async () => {
  const fixturesSnap = await db.collection("fixtures").get();
  const predictionsRef = db.collection("predictions");
  const usersRef = db.collection("users");
  const scoredGameweeks = new Set();

  for (const fixtureDoc of fixturesSnap.docs) {
    await scoreFixturePredictions(fixtureDoc, predictionsRef, usersRef, scoredGameweeks);
  }

  for (const gw of scoredGameweeks) {
    await updateGameweekWinner(gw);
  }
};

const updateGameweekWinner = async (gameweek) => {
  const gwPreds = await db.collection("predictions")
    .where("gameweek", "==", gameweek)
    .where("scoredPoints", "==", true)
    .get();

  const pointsByUser = {};
  for (const doc of gwPreds.docs) {
    const { userId, awardedPoints = 0 } = doc.data();
    pointsByUser[userId] = (pointsByUser[userId] || 0) + awardedPoints;
  }

  const sorted = Object.entries(pointsByUser).sort(([, a], [, b]) => b - a);
  if (sorted.length === 0) return;

  const [topUserId, topPoints] = sorted[0];
  const userDoc = await db.collection("users").doc(topUserId).get();
  const name = userDoc.data()?.fullName || "Unknown";

  await db.collection("gameweekWinners").doc(String(gameweek)).set({
    userId: topUserId,
    name,
    points: topPoints,
    gameweek,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  console.log(`GW ${gameweek} winner: ${name} with ${topPoints} pts`);
};

// === Scheduled: every 5 minutes
exports.calculatePoints = functions.pubsub.schedule("every 5 minutes").onRun(async () => {
await calculatePoints();
});

// === Manual trigger — also handles rescore via ?rescore=true&gameweek=35
exports.manualCalculatePoints = functions.https.onRequest(async (req, res) => {
  try {
    const rescore = req.query.rescore === "true";
    const gameweek = Number(req.query.gameweek);

    if (rescore && gameweek) {
      const predictionsRef = db.collection("predictions");
      const fixturesRef = db.collection("fixtures");
      const usersRef = db.collection("users");

      // Query predictions by gameweek (try both number and string)
      const [predSnapNum, predSnapStr] = await Promise.all([
        predictionsRef.where("gameweek", "==", gameweek).get(),
        predictionsRef.where("gameweek", "==", String(gameweek)).get(),
      ]);
      const seenPredIds = new Set();
      const predDocs = [...predSnapNum.docs, ...predSnapStr.docs].filter(d => {
        if (seenPredIds.has(d.id)) return false;
        seenPredIds.add(d.id);
        return true;
      });

      // Cache fixture lookups
      const fixtureCache = {};
      let rescored = 0;

      for (const predDoc of predDocs) {
        const pred = predDoc.data();
        const fixtureId = pred.fixtureId;
        if (!fixtureId) continue;

        if (!fixtureCache[fixtureId]) {
          const fDoc = await fixturesRef.doc(fixtureId).get();
          fixtureCache[fixtureId] = fDoc.exists ? fDoc.data() : null;
        }
        const fixture = fixtureCache[fixtureId];
        if (!fixture) continue;

        const fixtureHome = Number(fixture.homeTeamGoals);
        const fixtureAway = Number(fixture.awayTeamGoals);
        if (isNaN(fixtureHome) || fixtureHome < 0 || isNaN(fixtureAway) || fixtureAway < 0) continue;

        const actualOutcome = fixtureHome === fixtureAway ? "draw" : fixtureHome > fixtureAway ? "home" : "away";
        const prevPoints = Number(pred.awardedPoints ?? 0);
        const predHome = Number(pred.homeTeamGoals);
        const predAway = Number(pred.awayTeamGoals);
        const predictedOutcome = predHome === predAway ? "draw" : predHome > predAway ? "home" : "away";

        let newPoints = 0;
        if (predHome === fixtureHome && predAway === fixtureAway) newPoints = 3;
        else if (predictedOutcome === actualOutcome) newPoints = 1;
        if (pred.captainUsed && newPoints > 0) newPoints *= 2;

        const diff = newPoints - prevPoints;
        console.log(`Rescore GW${gameweek} fixture ${fixtureId}: user ${pred.userId} predicted ${predHome}-${predAway} vs actual ${fixtureHome}-${fixtureAway} → ${prevPoints}→${newPoints}`);

        await predDoc.ref.update({ scoredPoints: true, isCorrect: newPoints > 0, awardedPoints: newPoints });
        if (diff !== 0) {
          await usersRef.doc(pred.userId).update({
            score: admin.firestore.FieldValue.increment(diff),
            weeklyScore: admin.firestore.FieldValue.increment(diff),
            monthlyScore: admin.firestore.FieldValue.increment(diff),
          });
        }
        rescored++;
      }

      await updateGameweekWinner(gameweek);
      return res.status(200).send(`Rescored ${rescored} predictions for GW${gameweek}.`);
    }

    await calculatePoints();
    res.status(200).send("Manual point calculation completed.");
  } catch (e) {
    console.error("Manual calculation error:", e);
    res.status(500).send("Error during manual scoring.");
  }
});

// === Rescore a gameweek (fixes predictions already marked scoredPoints:true with wrong points)
// POST body: { "gameweek": 5 }
exports.rescoreGameweek = functions.https.onRequest(async (req, res) => {
  try {
    const gameweek = Number(req.body?.gameweek ?? req.query?.gameweek);
    if (!gameweek || isNaN(gameweek)) {
      return res.status(400).send("Missing or invalid gameweek parameter.");
    }

    const db2 = admin.firestore();
    const predictionsRef = db2.collection("predictions");
    const fixturesRef = db2.collection("fixtures");
    const usersRef = db2.collection("users");

    // Get all fixtures for this gameweek that have actual goals set
    const fixtureSnap = await fixturesRef.where("gameweek", "==", gameweek).get();
    let rescored = 0;

    for (const fixtureDoc of fixtureSnap.docs) {
      const fixture = fixtureDoc.data();
      const fixtureId = fixtureDoc.id;
      const fixtureHome = Number(fixture.homeTeamGoals);
      const fixtureAway = Number(fixture.awayTeamGoals);
      if (isNaN(fixtureHome) || fixtureHome < 0 || isNaN(fixtureAway) || fixtureAway < 0) continue;

      const actualOutcome = fixtureHome === fixtureAway ? "draw" : fixtureHome > fixtureAway ? "home" : "away";

      // Rescore ALL predictions for this fixture (both scored and unscored)
      const predsSnap = await predictionsRef.where("fixtureId", "==", fixtureId).get();
      for (const predDoc of predsSnap.docs) {
        const pred = predDoc.data();
        const prevPoints = Number(pred.awardedPoints ?? 0);
        const predHome = Number(pred.homeTeamGoals);
        const predAway = Number(pred.awayTeamGoals);
        const predictedOutcome = predHome === predAway ? "draw" : predHome > predAway ? "home" : "away";

        let newPoints = 0;
        if (predHome === fixtureHome && predAway === fixtureAway) newPoints = 3;
        else if (predictedOutcome === actualOutcome) newPoints = 1;
        if (pred.captainUsed && newPoints > 0) newPoints *= 2;

        const diff = newPoints - prevPoints;
        console.log(`Rescore GW${gameweek} fixture ${fixtureId}: user ${pred.userId} ${prevPoints}→${newPoints} (diff ${diff})`);

        await predDoc.ref.update({ scoredPoints: true, isCorrect: newPoints > 0, awardedPoints: newPoints });
        if (diff !== 0) {
          await usersRef.doc(pred.userId).update({
            score: admin.firestore.FieldValue.increment(diff),
            weeklyScore: admin.firestore.FieldValue.increment(diff),
            monthlyScore: admin.firestore.FieldValue.increment(diff),
          });
        }
        rescored++;
      }
    }

    await updateGameweekWinner(gameweek);
    res.status(200).send(`Rescored ${rescored} predictions for GW${gameweek}.`);
  } catch (e) {
    console.error("rescoreGameweek error:", e);
    res.status(500).send(`Error: ${e.message}`);
  }
});
