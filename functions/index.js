require("dotenv").config();
const functions  = require("firebase-functions");
const fetch      = require("node-fetch");
const cors       = require("cors")({ origin: true });
const admin      = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();

// ── Discord config (set via Firebase env / .env) ──────────────────────────────
const DISCORD_CLIENT_ID     = process.env.DISCORD_CLIENT_ID;
const DISCORD_CLIENT_SECRET = process.env.DISCORD_CLIENT_SECRET;
const DISCORD_REDIRECT_URI  = process.env.DISCORD_REDIRECT_URI;
const DISCORD_BOT_TOKEN     = process.env.DISCORD_BOT_TOKEN;
const GUILD_ID              = "1204439893036630026";
const ALLOWED_ROLE_IDS      = [
  "1402342200515231765",  // original role
  "1379468933114892449",  // member role
];

// ── Discord OAuth — verify guild role and return Firebase custom token ────────
exports.verifyDiscordRole = functions
  .runWith({ secrets: ["DISCORD_CLIENT_ID", "DISCORD_CLIENT_SECRET", "DISCORD_REDIRECT_URI", "DISCORD_BOT_TOKEN"] })
  .https.onRequest((req, res) => {
  cors(req, res, async () => {
    try {
      const code = req.query.code;
      if (!code) {
        console.error("❌ Missing `code` in query");
        return res.status(400).json({ success: false, error: "Missing code" });
      }

      console.log("🔁 Starting token exchange…");
      console.log("ℹ️ Using redirectUri:", DISCORD_REDIRECT_URI);

      // 1) Exchange code -> access token
      const tokenResponse = await fetch("https://discord.com/api/oauth2/token", {
        method:  "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
          client_id:     DISCORD_CLIENT_ID,
          client_secret: DISCORD_CLIENT_SECRET,
          grant_type:    "authorization_code",
          code,
          redirect_uri:  DISCORD_REDIRECT_URI,
        }).toString(),
      });

      const rawTokenText = await tokenResponse.text();
      console.log("🪪 Raw token response:", rawTokenText);

      if (!tokenResponse.ok) {
        let parsed;
        try { parsed = JSON.parse(rawTokenText); } catch (_) {}
        const errCode = parsed?.error || "token_exchange_failed";
        const errDesc = parsed?.error_description || "Token exchange failed";
        console.error("❌ Token exchange error:", errCode, errDesc);
        return res.status(400).json({ success: false, error: errDesc, error_code: errCode, used_redirect_uri: DISCORD_REDIRECT_URI });
      }

      const tokenData      = JSON.parse(rawTokenText);
      const userAccessToken = tokenData.access_token;
      if (!userAccessToken) {
        console.error("❌ No access_token in tokenData");
        return res.status(401).json({ success: false, error: "Failed to get access token." });
      }

      // 2) Fetch Discord user
      const userRes  = await fetch("https://discord.com/api/users/@me", {
        headers: { Authorization: `Bearer ${userAccessToken}` },
      });
      const userData = await userRes.json();
      console.log("👤 Discord user info:", userData);

      const userId = userData.id;
      if (!userId) {
        console.error("❌ No user id in userData");
        return res.status(403).json({ success: false, error: "Unable to get user ID." });
      }

      // 3) Check guild membership + role
      console.log("🔍 Checking member roles for user:", userId);
      const memberRes = await fetch(`https://discord.com/api/guilds/${GUILD_ID}/members/${userId}`, {
        headers: { Authorization: `Bot ${DISCORD_BOT_TOKEN}` },
      });
      const memberRaw = await memberRes.text();
      console.log("📥 memberRes.ok:", memberRes.ok);
      console.log("🧾 Raw member JSON:", memberRaw);

      if (!memberRes.ok) {
        console.error("❌ Guild fetch failed:", memberRaw);
        return res.status(403).json({ success: false, error: "You must be a member of The Big 6ix Discord server to sign in." });
      }

      const memberData = JSON.parse(memberRaw);
      const rolesArray = Array.isArray(memberData.roles) ? memberData.roles.map(String) : [];
      console.log("📜 Roles returned (as strings):", rolesArray);

      const hasRole = ALLOWED_ROLE_IDS.some(id => rolesArray.includes(String(id)));
      console.log("🔎 Looking for roles:", ALLOWED_ROLE_IDS, "→ user roles:", rolesArray, "→ match:", hasRole);

      if (!hasRole) {
        return res.status(200).json({
          success: false,
          error: "You don't have the required role to access The Big 6ix Predictions League. Make sure you have the correct role in the Discord server."
        });
      }

      // 4) Ensure Firebase Auth user exists
      try {
        await admin.auth().getUser(userId);
        console.log("🔄 Existing Firebase Auth user found");
      } catch (authErr) {
        if (authErr.code === "auth/user-not-found") {
          await admin.auth().createUser({ uid: userId });
          console.log("✨ Created new Firebase Auth user for:", userId);
        } else {
          throw authErr;
        }
      }

      // 5) Upsert Firestore user
      await db.collection("users").doc(userId).set(
        {
          fullName:                  userData.username || "",
          score:                     admin.firestore.FieldValue.increment(0),
          completedOnboarding:       false,
          generalEmailsEnabled:      true,
          personalizedEmailsEnabled: true,
          createdAt:                 admin.firestore.FieldValue.serverTimestamp(),
        },
        { merge: true }
      );
      console.log("📄 Firestore user document created/merged");

      // 6) Firebase custom token
      const customToken = await admin.auth().createCustomToken(userId, {
        username:      userData.username,
        discriminator: userData.discriminator,
        avatar:        userData.avatar,
      });
      console.log("🔑 Custom token generated");

      // 7) Return JSON — discord-callback.html handles the deep-link redirect
      console.log("✅ Sign-in successful for:", userData.username);
      return res.status(200).json({
        success: true,
        token: customToken,
        name:  userData.username || userData.global_name || ""
      });
    } catch (err) {
      console.error("Role verification error:", err);
      return res.status(500).json({ success: false, error: "Internal error", details: err.message });
    }
  });
});
const SCORING_FUNCTION_VERSION = "gw35-rescore-debug-v2";

const getNumberValue = (...values) => {
  for (const value of values) {
    if (typeof value === "number" && !Number.isNaN(value)) return value;

    if (typeof value === "string") {
      const trimmed = value.trim();
      if (/^-?\d+$/.test(trimmed)) return Number(trimmed);
    }

    if (value && typeof value === "object") {
      const nested = getNumberValue(
        value.goals,
        value.score,
        value.value,
        value.prediction,
        value.predictedGoals,
        value.predictedScore
      );
      if (nested !== null) return nested;
    }
  }

  return null;
};

const getFixtureGameweek = (fixture) =>
  getNumberValue(
    fixture.gameweek,
    fixture.gameWeek,
    fixture.gameweekNumber,
    fixture.gameWeekNumber,
    fixture.week
  );

const getFixtureHomeGoals = (fixture) =>
  getNumberValue(
    fixture.homeTeamGoals,
    fixture.homeScore,
    fixture.homeGoals,
    fixture.home_team_goals
  );

const getFixtureAwayGoals = (fixture) =>
  getNumberValue(
    fixture.awayTeamGoals,
    fixture.awayScore,
    fixture.awayGoals,
    fixture.away_team_goals
  );

const getPredictionHomeGoals = (prediction) =>
  getNumberValue(
    prediction.homeTeamGoals,
    prediction.homeScore,
    prediction.homeGoals,
    prediction.predictedHomeGoals,
    prediction.predictedHomeScore,
    prediction.homeTeamScore,
    prediction.home_team_goals,
    prediction.home_team_score,
    prediction.home,
    prediction.homeTeam,
    prediction.homePrediction,
    prediction.predictionHome,
    prediction.prediction?.homeTeamGoals,
    prediction.prediction?.homeScore,
    prediction.prediction?.homeGoals,
    prediction.prediction?.predictedHomeGoals,
    prediction.score?.home,
    prediction.score?.homeScore,
    prediction.score?.homeTeamGoals,
    prediction.scores?.home,
    prediction.scores?.homeScore,
    prediction.scores?.homeTeamGoals
  );

const getPredictionAwayGoals = (prediction) =>
  getNumberValue(
    prediction.awayTeamGoals,
    prediction.awayScore,
    prediction.awayGoals,
    prediction.predictedAwayGoals,
    prediction.predictedAwayScore,
    prediction.awayTeamScore,
    prediction.away_team_goals,
    prediction.away_team_score,
    prediction.away,
    prediction.awayTeam,
    prediction.awayPrediction,
    prediction.predictionAway,
    prediction.prediction?.awayTeamGoals,
    prediction.prediction?.awayScore,
    prediction.prediction?.awayGoals,
    prediction.prediction?.predictedAwayGoals,
    prediction.score?.away,
    prediction.score?.awayScore,
    prediction.score?.awayTeamGoals,
    prediction.scores?.away,
    prediction.scores?.awayScore,
    prediction.scores?.awayTeamGoals
  );

const getPredictionGameweek = (prediction) =>
  getNumberValue(
    prediction.gameweek,
    prediction.gameWeek,
    prediction.gameweekNumber,
    prediction.gameWeekNumber,
    prediction.week
  );

const resetGameweekPredictionState = async (targetGameweek) => {
  if (!Number.isInteger(targetGameweek)) {
    return {
      resetPredictionDocs: 0,
      resetUserScoreAdjustments: 0,
    };
  }

  const predictionsSnap = await db.collection("predictions").get();
  const usersRef = db.collection("users");

  const batchSize = 450;
  let batch = db.batch();
  let opCount = 0;
  let resetPredictionDocs = 0;
  const userScoreAdjustments = {};

  const commitIfNeeded = async () => {
    if (opCount >= batchSize) {
      await batch.commit();
      batch = db.batch();
      opCount = 0;
    }
  };

  for (const predictionDoc of predictionsSnap.docs) {
    const prediction = predictionDoc.data();
    const predictionGameweek = getPredictionGameweek(prediction);

    if (predictionGameweek !== targetGameweek) continue;

    const previousPoints = Number(prediction.awardedPoints || 0);

    if (prediction.userId && previousPoints !== 0) {
      userScoreAdjustments[prediction.userId] =
        (userScoreAdjustments[prediction.userId] || 0) - previousPoints;
    }

    batch.update(predictionDoc.ref, {
      scoredPoints: false,
      awardedPoints: 0,
      isCorrect: false,
      ignoredDuplicate: false,
      scoringReason: "reset_pending_recalculation",
    });

    opCount++;
    resetPredictionDocs++;
    await commitIfNeeded();
  }

  for (const [userId, scoreDelta] of Object.entries(userScoreAdjustments)) {
    if (scoreDelta === 0) continue;

    batch.update(usersRef.doc(userId), {
      score: admin.firestore.FieldValue.increment(scoreDelta),
    });

    opCount++;
    await commitIfNeeded();
  }

  if (opCount > 0) {
    await batch.commit();
  }

  return {
    resetPredictionDocs,
    resetUserScoreAdjustments: Object.keys(userScoreAdjustments).length,
  };
};

const getPredictionScorePair = (prediction) => ({
  home: getNumberValue(
    prediction.homeTeamGoals,
    prediction.homeScore,
    prediction.homeGoals,
    prediction.predictedHomeGoals,
    prediction.predictedHomeScore,
    prediction.homeTeamScore,
    prediction.home_team_goals,
    prediction.home_team_score,
    prediction.prediction?.homeTeamGoals,
    prediction.prediction?.homeScore,
    prediction.prediction?.homeGoals,
    prediction.prediction?.predictedHomeGoals,
    prediction.score?.home,
    prediction.score?.homeScore,
    prediction.score?.homeGoals,
    prediction.score?.homeTeamGoals,
    prediction.scores?.home,
    prediction.scores?.homeScore,
    prediction.scores?.homeGoals,
    prediction.scores?.homeTeamGoals
  ),
  away: getNumberValue(
    prediction.awayTeamGoals,
    prediction.awayScore,
    prediction.awayGoals,
    prediction.predictedAwayGoals,
    prediction.predictedAwayScore,
    prediction.awayTeamScore,
    prediction.away_team_goals,
    prediction.away_team_score,
    prediction.prediction?.awayTeamGoals,
    prediction.prediction?.awayScore,
    prediction.prediction?.awayGoals,
    prediction.prediction?.predictedAwayGoals,
    prediction.score?.away,
    prediction.score?.awayScore,
    prediction.score?.awayGoals,
    prediction.score?.awayTeamGoals,
    prediction.scores?.away,
    prediction.scores?.awayScore,
    prediction.scores?.awayGoals,
    prediction.scores?.awayTeamGoals
  ),
});

const getFixtureScorePair = (fixture) => ({
  home: getNumberValue(
    fixture.homeTeamGoals,
    fixture.homeScore,
    fixture.homeGoals,
    fixture.home_team_goals,
    fixture.score?.home,
    fixture.score?.homeScore,
    fixture.score?.homeGoals,
    fixture.scores?.home,
    fixture.scores?.homeScore,
    fixture.scores?.homeGoals
  ),
  away: getNumberValue(
    fixture.awayTeamGoals,
    fixture.awayScore,
    fixture.awayGoals,
    fixture.away_team_goals,
    fixture.score?.away,
    fixture.score?.awayScore,
    fixture.score?.awayGoals,
    fixture.scores?.away,
    fixture.scores?.awayScore,
    fixture.scores?.awayGoals
  ),
});

const getOutcomeFromScorePair = ({ home, away }) => {
  if (home === null || away === null) return null;
  if (home === away) return "draw";
  return home > away ? "home" : "away";
};

const recalculateGameweekDirect = async (targetGameweek) => {
  const predictionsSnap = await db.collection("predictions").get();
  const usersRef = db.collection("users");

  let batch = db.batch();
  let opCount = 0;
  const batchSize = 450;

  let matchedPredictions = 0;
  let scoredPredictions = 0;
  let skippedNoFixtureId = 0;
  let skippedFixtureMissing = 0;
  let skippedFixtureNotComplete = 0;
  let skippedPredictionScoreMissing = 0;
  const userScoreDeltas = {};
  const fixtureCache = new Map();

  const commitIfNeeded = async () => {
    if (opCount >= batchSize) {
      await batch.commit();
      batch = db.batch();
      opCount = 0;
    }
  };

  const getFixtureById = async (fixtureId) => {
    if (fixtureCache.has(fixtureId)) return fixtureCache.get(fixtureId);

    const fixtureDoc = await db.collection("fixtures").doc(fixtureId).get();
    const value = fixtureDoc.exists
      ? {
          id: fixtureDoc.id,
          ref: fixtureDoc.ref,
          ...fixtureDoc.data(),
        }
      : null;

    fixtureCache.set(fixtureId, value);
    return value;
  };

  for (const predictionDoc of predictionsSnap.docs) {
    const prediction = predictionDoc.data();
    const predictionGameweek = getPredictionGameweek(prediction);

    if (predictionGameweek !== targetGameweek) continue;

    matchedPredictions++;

    const previousPoints = Number(prediction.awardedPoints || 0);
    if (prediction.userId && previousPoints !== 0) {
      userScoreDeltas[prediction.userId] =
        (userScoreDeltas[prediction.userId] || 0) - previousPoints;
    }

    const fixtureId = prediction.fixtureId;

    if (!fixtureId) {
      skippedNoFixtureId++;
      batch.update(predictionDoc.ref, {
        scoredPoints: true,
        awardedPoints: 0,
        isCorrect: false,
        ignoredDuplicate: false,
        scoringReason: "missing_fixtureId",
      });
      opCount++;
      await commitIfNeeded();
      continue;
    }

    const fixture = await getFixtureById(fixtureId);

    if (!fixture) {
      skippedFixtureMissing++;
      batch.update(predictionDoc.ref, {
        scoredPoints: true,
        awardedPoints: 0,
        isCorrect: false,
        ignoredDuplicate: false,
        scoringReason: "fixture_missing_for_prediction_fixtureId",
        debugFixtureId: fixtureId,
      });
      opCount++;
      await commitIfNeeded();
      continue;
    }

    const fixtureScores = getFixtureScorePair(fixture);
    const predictionScores = getPredictionScorePair(prediction);
    const actualOutcome = getOutcomeFromScorePair(fixtureScores);
    const predictedOutcome = getOutcomeFromScorePair(predictionScores);

    if (
      fixtureScores.home === null ||
      fixtureScores.away === null ||
      fixtureScores.home < 0 ||
      fixtureScores.away < 0
    ) {
      skippedFixtureNotComplete++;
      batch.update(predictionDoc.ref, {
        scoredPoints: true,
        awardedPoints: 0,
        isCorrect: false,
        ignoredDuplicate: false,
        scoringReason: "fixture_not_complete_or_missing_score",
        debugFixtureId: fixtureId,
        debugActualHomeGoals: fixtureScores.home,
        debugActualAwayGoals: fixtureScores.away,
      });
      opCount++;
      await commitIfNeeded();
      continue;
    }

    if (predictionScores.home === null || predictionScores.away === null) {
      skippedPredictionScoreMissing++;
      batch.update(predictionDoc.ref, {
        scoredPoints: true,
        awardedPoints: 0,
        isCorrect: false,
        ignoredDuplicate: false,
        scoringReason: "prediction_score_missing",
        debugFixtureId: fixtureId,
        debugPredictionKeys: Object.keys(prediction),
        debugActualHomeGoals: fixtureScores.home,
        debugActualAwayGoals: fixtureScores.away,
        debugPredictionHomeGoals: predictionScores.home,
        debugPredictionAwayGoals: predictionScores.away,
      });
      opCount++;
      await commitIfNeeded();
      continue;
    }

    let points = 0;
    let scoringReason = "wrong_prediction";

    if (
      predictionScores.home === fixtureScores.home &&
      predictionScores.away === fixtureScores.away
    ) {
      points = 3;
      scoringReason = "exact_score";
    } else if (predictedOutcome === actualOutcome) {
      points = 1;
      scoringReason = "correct_outcome";
    }

    batch.update(predictionDoc.ref, {
      scoredPoints: true,
      awardedPoints: points,
      isCorrect: points > 0,
      ignoredDuplicate: false,
      scoringReason,
      debugFixtureId: fixtureId,
      debugActualHomeGoals: fixtureScores.home,
      debugActualAwayGoals: fixtureScores.away,
      debugPredictionHomeGoals: predictionScores.home,
      debugPredictionAwayGoals: predictionScores.away,
      debugActualOutcome: actualOutcome,
      debugPredictedOutcome: predictedOutcome,
      debugDirectRecalc: true,
    });

    opCount++;
    scoredPredictions++;
    await commitIfNeeded();

    if (prediction.userId && points !== 0) {
      userScoreDeltas[prediction.userId] =
        (userScoreDeltas[prediction.userId] || 0) + points;
    }
  }

  for (const [userId, scoreDelta] of Object.entries(userScoreDeltas)) {
    if (scoreDelta === 0) continue;

    batch.update(usersRef.doc(userId), {
      score: admin.firestore.FieldValue.increment(scoreDelta),
    });

    opCount++;
    await commitIfNeeded();
  }

  if (opCount > 0) {
    await batch.commit();
  }

  return {
    targetGameweek,
    matchedPredictions,
    scoredPredictions,
    changedUsers: Object.keys(userScoreDeltas).filter(
      (userId) => userScoreDeltas[userId] !== 0
    ).length,
    skippedNoFixtureId,
    skippedFixtureMissing,
    skippedFixtureNotComplete,
    skippedPredictionScoreMissing,
  };
};

const calculatePoints = async (targetGameweek = null, options = {}) => {
  const { rescore = false, sendPush = true, enforceDeadline = true } = options;

  const fixturesSnap = await db.collection("fixtures").get();
  const predictionsRef = db.collection("predictions");
  const usersRef = db.collection("users");

  const batchSize = 450;
  let batch = db.batch();
  let opCount = 0;

  const commitIfNeeded = async () => {
    if (opCount >= batchSize) {
      await batch.commit();
      batch = db.batch();
      opCount = 0;
    }
  };

  const userScoreAdjustments = {};
  let processedFixtures = 0;
  let processedPredictions = 0;
  let resetPredictions = 0;
  let skippedWrongGameweek = 0;
  let skippedNoResult = 0;
  let skippedNoDeadline = 0;

  for (const fixtureDoc of fixturesSnap.docs) {
    const fixture = fixtureDoc.data();

    const fixtureGameweek = getFixtureGameweek(fixture);

    if (targetGameweek !== null && fixtureGameweek !== targetGameweek) {
      skippedWrongGameweek++;
      continue;
    }

    const fixtureId = fixtureDoc.id;
    const fixtureHomeGoals = getFixtureHomeGoals(fixture);
    const fixtureAwayGoals = getFixtureAwayGoals(fixture);

    if (
      fixtureHomeGoals === null ||
      fixtureAwayGoals === null ||
      fixtureHomeGoals < 0 ||
      fixtureAwayGoals < 0
    ) {
      skippedNoResult++;
      continue;
    }

    if (!fixture.deadline || typeof fixture.deadline.toMillis !== "function") {
      skippedNoDeadline++;
      continue;
    }

    processedFixtures++;

    const actualOutcome =
      fixtureHomeGoals === fixtureAwayGoals
        ? "draw"
        : fixtureHomeGoals > fixtureAwayGoals
        ? "home"
        : "away";

    const snapshot = await predictionsRef
      .where("fixtureId", "==", fixtureId)
      .get();

    const predictionsByUser = new Map();

    snapshot.forEach((doc) => {
      const data = doc.data();
      if (!data.userId) return;

      if (!predictionsByUser.has(data.userId)) {
        predictionsByUser.set(data.userId, []);
      }

      predictionsByUser.get(data.userId).push({
        id: doc.id,
        ref: doc.ref,
        ...data,
      });
    });

    for (const [userId, predictions] of predictionsByUser.entries()) {
      if (rescore) {
        for (const p of predictions) {
          batch.update(p.ref, {
            scoredPoints: false,
            awardedPoints: 0,
            isCorrect: false,
            ignoredDuplicate: false,
            scoringReason: "fixture_scoped_reset_pending_recalculation",
          });

          opCount++;
          resetPredictions++;
          await commitIfNeeded();
        }
      }

      const validPrediction = predictions.reduce((latest, current) => {
        const latestTime = latest?.submittedAt?.toMillis?.() ?? 0;
        const currentTime = current?.submittedAt?.toMillis?.() ?? 0;
        return currentTime > latestTime ? current : latest;
      }, null);

      if (!validPrediction) continue;

      const duplicates = predictions.filter((p) => p.id !== validPrediction.id);

      for (const dup of duplicates) {
        batch.update(dup.ref, {
          scoredPoints: true,
          awardedPoints: 0,
          isCorrect: false,
          ignoredDuplicate: true,
          scoringReason: "ignored_duplicate_prediction",
        });

        opCount++;
        processedPredictions++;
        await commitIfNeeded();
      }

      if (!rescore && validPrediction.scoredPoints === true) continue;

      if (
        enforceDeadline &&
        (!validPrediction.submittedAt ||
          typeof validPrediction.submittedAt.toMillis !== "function" ||
          validPrediction.submittedAt.toMillis() > fixture.deadline.toMillis())
      ) {
        batch.update(validPrediction.ref, {
          scoredPoints: true,
          isCorrect: false,
          awardedPoints: 0,
          ignoredDuplicate: false,
          scoringReason: !validPrediction.submittedAt || typeof validPrediction.submittedAt.toMillis !== "function"
            ? "missing_or_invalid_submittedAt"
            : "submitted_after_deadline",
          debugDeadlineEnforced: enforceDeadline,
        });

        opCount++;
        processedPredictions++;
        await commitIfNeeded();
        continue;
      }

      const predictionHomeGoals = getPredictionHomeGoals(validPrediction);
      const predictionAwayGoals = getPredictionAwayGoals(validPrediction);
      const debugPredictionKeys = Object.keys(validPrediction).filter((key) => key !== "ref");

      if (predictionHomeGoals === null || predictionAwayGoals === null) {
        batch.update(validPrediction.ref, {
          scoredPoints: true,
          isCorrect: false,
          awardedPoints: 0,
          ignoredDuplicate: false,
          scoringReason: "could_not_parse_prediction_score",
          debugPredictionKeys,
          debugRawHomeTeamGoals: validPrediction.homeTeamGoals ?? null,
          debugRawAwayTeamGoals: validPrediction.awayTeamGoals ?? null,
          debugRawHomeScore: validPrediction.homeScore ?? null,
          debugRawAwayScore: validPrediction.awayScore ?? null,
        });

        opCount++;
        processedPredictions++;
        await commitIfNeeded();
        continue;
      }

      const predictedOutcome =
        predictionHomeGoals === predictionAwayGoals
          ? "draw"
          : predictionHomeGoals > predictionAwayGoals
          ? "home"
          : "away";

      let points = 0;

      if (
        predictionHomeGoals === fixtureHomeGoals &&
        predictionAwayGoals === fixtureAwayGoals
      ) {
        points = 3;
      } else if (predictedOutcome === actualOutcome) {
        points = 1;
      }

      batch.update(validPrediction.ref, {
        scoredPoints: true,
        isCorrect: points > 0,
        awardedPoints: points,
        ignoredDuplicate: false,
        scoringReason: points === 3
          ? "exact_score"
          : points === 1
          ? "correct_outcome"
          : "wrong_prediction",
        debugActualHomeGoals: fixtureHomeGoals,
        debugActualAwayGoals: fixtureAwayGoals,
        debugPredictionHomeGoals: predictionHomeGoals,
        debugPredictionAwayGoals: predictionAwayGoals,
        debugPredictionKeys,
        debugActualOutcome: actualOutcome,
        debugPredictedOutcome: predictedOutcome,
      });

      opCount++;
      processedPredictions++;
      await commitIfNeeded();

      if (points !== 0) {
        userScoreAdjustments[userId] =
          (userScoreAdjustments[userId] || 0) + points;
      }

      if (sendPush && points > 0) {
        try {
          const userRef = usersRef.doc(userId);
          const userDoc = await userRef.get();
          const token = userDoc.data()?.fcmToken;

          if (token) {
            await admin.messaging().send({
              notification: {
                title: `You earned ${points} point${points !== 1 ? "s" : ""}!`,
                body: `Your score has been updated.`,
              },
              token,
            });
          }
        } catch (e) {
          console.log("Push failed:", e.message);
        }
      }
    }
  }

  for (const [userId, scoreDelta] of Object.entries(userScoreAdjustments)) {
    if (scoreDelta === 0) continue;

    batch.update(usersRef.doc(userId), {
      score: admin.firestore.FieldValue.increment(scoreDelta),
    });

    opCount++;
    await commitIfNeeded();
  }

  if (opCount > 0) {
    await batch.commit();
  }

  console.log(
    `✅ Scoring complete. fixtures=${processedFixtures}, predictions=${processedPredictions}, reset=${resetPredictions}, rescore=${rescore}, targetGameweek=${targetGameweek ?? "all"}, skippedWrongGameweek=${skippedWrongGameweek}, skippedNoResult=${skippedNoResult}, skippedNoDeadline=${skippedNoDeadline}`
  );

  return {
    processedFixtures,
    processedPredictions,
    resetPredictions,
    changedUsers: Object.keys(userScoreAdjustments).length,
    targetGameweek,
    rescore,
    skippedWrongGameweek,
    skippedNoResult,
    skippedNoDeadline,
  };
};

// Scheduled scorer — runs every 5 minutes as a safety net.
// syncFootballFixtures also triggers scoring when new results come in.
exports.calculatePoints = functions.pubsub.schedule("every 5 minutes").onRun(async () => {
  await calculatePoints();
});

exports.manualCalculatePoints = functions.https.onRequest(async (req, res) => {
  try {
    if (req.query.debug === "true") {
      const fixturesSnap = await db.collection("fixtures").limit(100).get();
      const predictionsSnap = await db.collection("predictions").limit(100).get();

      const fixtures = fixturesSnap.docs.map((doc) => {
        const fixture = doc.data();
        return {
          id: doc.id,
          gameweek: fixture.gameweek,
          gameWeek: fixture.gameWeek,
          gameweekNumber: fixture.gameweekNumber,
          week: fixture.week,
          parsedGameweek: getFixtureGameweek(fixture),
          homeTeam: fixture.homeTeam,
          awayTeam: fixture.awayTeam,
          homeTeamGoals: fixture.homeTeamGoals,
          awayTeamGoals: fixture.awayTeamGoals,
          homeScore: fixture.homeScore,
          awayScore: fixture.awayScore,
          parsedHomeGoals: getFixtureHomeGoals(fixture),
          parsedAwayGoals: getFixtureAwayGoals(fixture),
          hasDeadline: !!fixture.deadline,
        };
      });

      const predictions = predictionsSnap.docs.map((doc) => {
        const prediction = doc.data();
        return {
          id: doc.id,
          fixtureId: prediction.fixtureId,
          userId: prediction.userId,
          gameweek: prediction.gameweek,
          gameWeek: prediction.gameWeek,
          parsedGameweek: getPredictionGameweek(prediction),
          homeTeamGoals: prediction.homeTeamGoals,
          awayTeamGoals: prediction.awayTeamGoals,
          parsedHomeGoals: getPredictionHomeGoals(prediction),
          parsedAwayGoals: getPredictionAwayGoals(prediction),
          scoredPoints: prediction.scoredPoints,
          awardedPoints: prediction.awardedPoints,
          scoringReason: prediction.scoringReason,
          submittedAt: prediction.submittedAt,
        };
      });

      return res.status(200).json({
        version: SCORING_FUNCTION_VERSION,
        exportedFunction: "manualCalculatePoints",
        fixtureCount: fixtures.length,
        predictionCount: predictions.length,
        fixtures,
        predictions,
      });
    }
    if (req.query.rebuildScores === "true") {
      const rawGameweekForRebuild = req.query.gameweek;
      const targetGameweekForRebuild = rawGameweekForRebuild !== undefined
        ? Number(rawGameweekForRebuild)
        : null;

      if (
        rawGameweekForRebuild !== undefined &&
        !Number.isInteger(targetGameweekForRebuild)
      ) {
        return res.status(400).send("Invalid gameweek. Example: ?rebuildScores=true&gameweek=36");
      }

      const predictionsSnap = await db.collection("predictions").get();
      const usersRef = db.collection("users");

      const totalsByUser = {};
      let countedPredictions = 0;
      let skippedNoUserId = 0;
      let skippedWrongGameweek = 0;

      predictionsSnap.forEach((doc) => {
        const prediction = doc.data();
        const userId = prediction.userId;
        const predictionGameweek = getPredictionGameweek(prediction);

        if (!userId) {
          skippedNoUserId++;
          return;
        }

        if (
          targetGameweekForRebuild !== null &&
          predictionGameweek !== targetGameweekForRebuild
        ) {
          skippedWrongGameweek++;
          return;
        }

        const awardedPoints = Number(prediction.awardedPoints || 0);
        totalsByUser[userId] = (totalsByUser[userId] || 0) + awardedPoints;
        countedPredictions++;
      });

      const batchSize = 450;
      let batch = db.batch();
      let opCount = 0;
      let updatedUsers = 0;

      const commitIfNeeded = async () => {
        if (opCount >= batchSize) {
          await batch.commit();
          batch = db.batch();
          opCount = 0;
        }
      };

      if (targetGameweekForRebuild === null) {
        for (const [userId, totalScore] of Object.entries(totalsByUser)) {
          batch.set(
            usersRef.doc(userId),
            { score: totalScore },
            { merge: true }
          );

          opCount++;
          updatedUsers++;
          await commitIfNeeded();
        }
      } else {
        for (const [userId, gameweekPoints] of Object.entries(totalsByUser)) {
          if (gameweekPoints === 0) continue;

          batch.update(usersRef.doc(userId), {
            score: admin.firestore.FieldValue.increment(gameweekPoints),
          });

          opCount++;
          updatedUsers++;
          await commitIfNeeded();
        }
      }

      if (opCount > 0) {
        await batch.commit();
      }

      return res.status(200).json({
        message: targetGameweekForRebuild === null
          ? "Rebuilt all user total scores from predictions.awardedPoints"
          : `Added GW${targetGameweekForRebuild} awardedPoints to user totals`,
        targetGameweek: targetGameweekForRebuild,
        countedPredictions,
        updatedUsers,
        skippedNoUserId,
        skippedWrongGameweek,
      });
    }
    const rawGameweek = req.query.gameweek;
    const targetGameweek = rawGameweek !== undefined ? Number(rawGameweek) : null;
    const rescore = req.query.rescore === "true" || req.query.reset === "true";

    if (rawGameweek !== undefined && !Number.isInteger(targetGameweek)) {
      return res.status(400).send("Invalid gameweek. Example: ?gameweek=35");
    }

    const resetResult = rescore && targetGameweek !== null
      ? await resetGameweekPredictionState(targetGameweek)
      : {
          resetPredictionDocs: 0,
          resetUserScoreAdjustments: 0,
        };

    const result = rescore && targetGameweek !== null
      ? await recalculateGameweekDirect(targetGameweek)
      : await calculatePoints(targetGameweek, {
          rescore,
          sendPush: !rescore,
          enforceDeadline: true,
        });

    result.resetPredictionDocs = resetResult.resetPredictionDocs;
    result.resetUserScoreAdjustments = resetResult.resetUserScoreAdjustments;

    if (rescore && targetGameweek !== null) {
      return res
        .status(200)
        .send(
          `version=${SCORING_FUNCTION_VERSION}. Direct reset + rescored GW${targetGameweek}. resetPredictionDocs=${result.resetPredictionDocs}, resetUserScoreAdjustments=${result.resetUserScoreAdjustments}, matchedPredictions=${result.matchedPredictions}, scoredPredictions=${result.scoredPredictions}, usersChangedFromRecalc=${result.changedUsers}, skippedNoFixtureId=${result.skippedNoFixtureId}, skippedFixtureMissing=${result.skippedFixtureMissing}, skippedFixtureNotComplete=${result.skippedFixtureNotComplete}, skippedPredictionScoreMissing=${result.skippedPredictionScoreMissing}`
        );
    }

    if (targetGameweek !== null) {
      return res
        .status(200)
        .send(
          `version=${SCORING_FUNCTION_VERSION}. Manual point calculation completed for GW${targetGameweek}. fixtures=${result.processedFixtures}, predictions=${result.processedPredictions}, usersChanged=${result.changedUsers}, skippedWrongGameweek=${result.skippedWrongGameweek}, skippedNoResult=${result.skippedNoResult}, skippedNoDeadline=${result.skippedNoDeadline}`
        );
    }

    return res
      .status(200)
      .send(
        `version=${SCORING_FUNCTION_VERSION}. Manual point calculation completed. fixtures=${result.processedFixtures}, predictions=${result.processedPredictions}, usersChanged=${result.changedUsers}, skippedWrongGameweek=${result.skippedWrongGameweek}, skippedNoResult=${result.skippedNoResult}, skippedNoDeadline=${result.skippedNoDeadline}`
      );
  } catch (e) {
    console.error("manualCalculatePoints error:", e);
    return res.status(500).send(e.message);
  }
});

exports.debugGameweeks = functions.https.onRequest(async (req, res) => {
  try {
    const fixturesSnap = await db.collection("fixtures").limit(50).get();

    const fixtures = fixturesSnap.docs.map((doc) => {
      const fixture = doc.data();
      return {
        id: doc.id,
        gameweek: fixture.gameweek,
        gameWeek: fixture.gameWeek,
        gameweekNumber: fixture.gameweekNumber,
        week: fixture.week,
        parsedGameweek: getFixtureGameweek(fixture),
        homeTeam: fixture.homeTeam,
        awayTeam: fixture.awayTeam,
        homeTeamGoals: fixture.homeTeamGoals,
        awayTeamGoals: fixture.awayTeamGoals,
        homeScore: fixture.homeScore,
        awayScore: fixture.awayScore,
        parsedHomeGoals: getFixtureHomeGoals(fixture),
        parsedAwayGoals: getFixtureAwayGoals(fixture),
        hasDeadline: !!fixture.deadline,
      };
    });

    return res.status(200).json({
      version: SCORING_FUNCTION_VERSION,
      count: fixtures.length,
      fixtures,
    });
  } catch (e) {
    console.error("debugGameweeks error:", e);
    return res.status(500).send(e.message);
  }
});

exports.recalculateGameweek = functions.https.onRequest(async (req, res) => {
  try {
    const rawGameweek = req.query.gameweek;
    const targetGameweek = rawGameweek !== undefined ? Number(rawGameweek) : 35;

    if (!Number.isInteger(targetGameweek)) {
      return res.status(400).send("Invalid gameweek. Example: ?gameweek=35");
    }

    const result = await calculatePoints(targetGameweek, {
      rescore: true,
      sendPush: false,
      enforceDeadline: false,
    });

    return res.status(200).json({
      version: SCORING_FUNCTION_VERSION,
      message: `Rescored GW${targetGameweek}`,
      ...result,
    });
  } catch (e) {
    console.error("recalculateGameweek error:", e);
    return res.status(500).send(e.message);
  }
});

exports.rebuildUserScoresFromPredictions = functions.https.onRequest(async (req, res) => {
  try {
    const rawGameweek = req.query.gameweek;
    const targetGameweek = rawGameweek !== undefined ? Number(rawGameweek) : null;

    if (rawGameweek !== undefined && !Number.isInteger(targetGameweek)) {
      return res.status(400).send("Invalid gameweek. Example: ?gameweek=36");
    }

    const predictionsSnap = await db.collection("predictions").get();
    const usersRef = db.collection("users");

    const totalsByUser = {};
    let countedPredictions = 0;
    let skippedNoUserId = 0;
    let skippedWrongGameweek = 0;

    predictionsSnap.forEach((doc) => {
      const prediction = doc.data();
      const userId = prediction.userId;
      const predictionGameweek = getPredictionGameweek(prediction);

      if (!userId) {
        skippedNoUserId++;
        return;
      }

      if (targetGameweek !== null && predictionGameweek !== targetGameweek) {
        skippedWrongGameweek++;
        return;
      }

      const awardedPoints = Number(prediction.awardedPoints || 0);
      totalsByUser[userId] = (totalsByUser[userId] || 0) + awardedPoints;
      countedPredictions++;
    });

    const batchSize = 450;
    let batch = db.batch();
    let opCount = 0;
    let updatedUsers = 0;

    const commitIfNeeded = async () => {
      if (opCount >= batchSize) {
        await batch.commit();
        batch = db.batch();
        opCount = 0;
      }
    };

    if (targetGameweek === null) {
      for (const [userId, totalScore] of Object.entries(totalsByUser)) {
        batch.set(
          usersRef.doc(userId),
          { score: totalScore },
          { merge: true }
        );

        opCount++;
        updatedUsers++;
        await commitIfNeeded();
      }
    } else {
      for (const [userId, gameweekPoints] of Object.entries(totalsByUser)) {
        if (gameweekPoints === 0) continue;

        batch.update(usersRef.doc(userId), {
          score: admin.firestore.FieldValue.increment(gameweekPoints),
        });

        opCount++;
        updatedUsers++;
        await commitIfNeeded();
      }
    }

    if (opCount > 0) {
      await batch.commit();
    }

    return res.status(200).json({
      message: targetGameweek === null
        ? "Rebuilt all user total scores from predictions.awardedPoints"
        : `Added GW${targetGameweek} awardedPoints to user totals`,
      targetGameweek,
      countedPredictions,
      updatedUsers,
      skippedNoUserId,
      skippedWrongGameweek,
    });
  } catch (e) {
    console.error("rebuildUserScoresFromPredictions error:", e);
    return res.status(500).send(e.message);
  }
});
// === Season Reset ===
// POST with Firebase ID token in Authorization header
// Optional: ?force=true to re-archive even if season already archived
exports.resetSeason = functions.https.onRequest(async (req, res) => {
  try {
    const authHeader = req.headers.authorization || "";
    const idToken = authHeader.replace("Bearer ", "");
    if (!idToken) return res.status(401).json({ error: "Missing auth token" });

    const decoded = await admin.auth().verifyIdToken(idToken);
    if (decoded.email !== "ugurdenli30@gmail.com") {
      return res.status(403).json({ error: "Admin only" });
    }

    const force = req.query.force === "true";
    const year = new Date().getFullYear().toString();
    const seasonRef = db.collection("seasons").doc(year);
    const seasonSnap = await seasonRef.get();

    if (seasonSnap.exists && !force) {
      return res.status(400).json({ error: `Season ${year} already archived. Use ?force=true to override.` });
    }

    const usersSnap = await db.collection("users").get();
    const batchSize = 450;
    let batch = db.batch();
    let opCount = 0;
    let archived = 0;

    const commitIfNeeded = async () => {
      if (opCount >= batchSize) {
        await batch.commit();
        batch = db.batch();
        opCount = 0;
      }
    };

    // Archive current scores
    for (const userDoc of usersSnap.docs) {
      const data = userDoc.data();
      const archiveRef = seasonRef.collection("userScores").doc(userDoc.id);
      batch.set(archiveRef, {
        userId: userDoc.id,
        fullName: data.fullName || "",
        score: data.score || 0,
        weeklyScore: data.weeklyScore || 0,
        monthlyScore: data.monthlyScore || 0,
        archivedAt: admin.firestore.FieldValue.serverTimestamp(),
      });
      opCount++;
      archived++;
      await commitIfNeeded();
    }

    // Reset scores and re-grant tokens
    for (const userDoc of usersSnap.docs) {
      batch.update(userDoc.ref, {
        score: 0,
        weeklyScore: 0,
        monthlyScore: 0,
        wildcardAvailable: true,
        captainAvailable: true,
        doubleDownAvailable: true,
      });
      opCount++;
      await commitIfNeeded();
    }

    if (opCount > 0) await batch.commit();

    console.log(`Season ${year} reset: ${archived} users archived and reset.`);
    return res.status(200).json({ message: `Season ${year} archived and reset.`, usersReset: archived });
  } catch (e) {
    console.error("resetSeason error:", e);
    return res.status(500).json({ error: e.message });
  }
});

// === Sync fixtures from football-data.org (free forever, no credit card) ===
// Register at https://www.football-data.org/client/register to get a free key.
// Set it once with: firebase functions:secrets:set FOOTBALLDATA_KEY
// Runs every 6 hours. One API call per run — well within the free 10 req/min limit.

// Official football-data.org team IDs for the Big 6 — more reliable than string matching.
const BIG_SIX_IDS = new Set([
  57,  // Arsenal FC
  61,  // Chelsea FC
  64,  // Liverpool FC
  65,  // Manchester City FC
  66,  // Manchester United FC
  73,  // Tottenham Hotspur FC
]);

// preview=true → returns what WOULD be written without touching Firestore (for testing).
const doSyncFixtures = async (preview = false) => {
  const apiKey = process.env.FOOTBALLDATA_KEY;
  if (!apiKey) { console.warn("FOOTBALLDATA_KEY not set — skipping sync"); return { error: "FOOTBALLDATA_KEY not set" }; }

  // PL seasons are labelled by the year they START: 2025 = 2025/26 season.
  // Override via FOOTBALL_SEASON secret when a new season begins.
  const season = process.env.FOOTBALL_SEASON || "2025";

  let apiRes;
  try {
    apiRes = await fetch(
      `https://api.football-data.org/v4/competitions/PL/matches?season=${season}`,
      { headers: { "X-Auth-Token": apiKey } }
    );
  } catch (e) {
    console.error("football-data.org fetch error:", e.message);
    throw e;
  }

  if (apiRes.status === 429) { console.warn("football-data.org rate limit hit — will retry next run"); return { error: "rate_limited" }; }
  if (!apiRes.ok) {
    const errText = await apiRes.text().catch(() => "");
    console.error(`football-data.org error ${apiRes.status}:`, errText);
    throw new Error(`API error ${apiRes.status}: ${errText}`);
  }

  const data = await apiRes.json();
  const allMatches = data.matches || [];

  // ── Filter to Big 6 fixtures only ────────────────────────────────────────
  const matches = allMatches.filter(m =>
    BIG_SIX_IDS.has(m.homeTeam?.id) || BIG_SIX_IDS.has(m.awayTeam?.id)
  );

  console.log(`Fetched ${allMatches.length} total matches → ${matches.length} Big 6 fixtures (season ${season})`);

  // ── Preview mode: return fixture list without writing ─────────────────────
  if (preview) {
    return {
      season,
      totalFromApi:   allMatches.length,
      big6Fixtures:   matches.length,
      fixtures: matches.map(m => ({
        id:       `prem_${m.id}`,
        home:     m.homeTeam?.shortName || m.homeTeam?.name,
        away:     m.awayTeam?.shortName || m.awayTeam?.name,
        gameweek: m.matchday,
        status:   m.status,
        kickoff:  m.utcDate,
      })),
    };
  }

  // ── Write to Firestore ────────────────────────────────────────────────────
  let batch    = db.batch();
  let opCount  = 0;
  let anyFinished = false;

  for (const match of matches) {
    const gameweek = match.matchday;
    if (!gameweek) continue;

    const isFinished = match.status === "FINISHED";
    const homeGoals  = isFinished && match.score?.fullTime?.home != null ? match.score.fullTime.home : -1;
    const awayGoals  = isFinished && match.score?.fullTime?.away != null ? match.score.fullTime.away : -1;
    if (isFinished) anyFinished = true;

    const homeTeam    = match.homeTeam?.shortName || match.homeTeam?.name || "";
    const awayTeam    = match.awayTeam?.shortName || match.awayTeam?.name || "";
    const kickoffDate = match.utcDate ? new Date(match.utcDate) : null;

    batch.set(db.collection("fixtures").doc(`prem_${match.id}`), {
      homeTeam,
      awayTeam,
      homeTeamGoals: homeGoals,
      awayTeamGoals: awayGoals,
      gameweek,
      date:     match.utcDate || "",
      deadline: kickoffDate ? admin.firestore.Timestamp.fromDate(kickoffDate) : null,
      apiFixtureId: match.id,
    }, { merge: true });

    opCount++;
    if (opCount % 450 === 0) { await batch.commit(); batch = db.batch(); }
  }

  if (opCount % 450 !== 0) await batch.commit();

  // Fire-and-forget scoring trigger — don't await so we don't cascade-timeout.
  if (anyFinished) {
    console.log("Finished Big 6 matches present — triggering scoring (fire-and-forget)");
    fetch(
      "https://us-central1-the-big-6ix.cloudfunctions.net/manualCalculatePoints",
      { method: "POST" }
    ).catch(e => console.error("Failed to trigger scoring:", e.message));
  }

  const result = { season, totalFromApi: allMatches.length, big6Fixtures: matches.length, synced: opCount, triggeredScoring: anyFinished };
  console.log("Sync complete.", result);
  return result;
};

// === World Cup 2026 sync =====================================================
// Chunks all 104 WC matches into gameweeks of 6, sorted by kick-off date.
// WC gameweek numbers start at 101 so they don't collide with PL (1-38).
// preview=true returns what would be written without touching Firestore.

const WC_MATCHES_PER_GW = 6;
const WC_GW_OFFSET      = 0; // GW 1, 2, 3 ...

// Top 8 nations by FIFA ranking (April 2026) — edit to adjust.
const WC_FEATURED_NATIONS = new Set([
  "France", "Spain", "Argentina", "England",
  "Portugal", "Brazil", "Netherlands", "Morocco",
]);

const doSyncWorldCup = async (preview = false) => {
  const apiKey = process.env.FOOTBALLDATA_KEY;
  if (!apiKey) return { error: "FOOTBALLDATA_KEY not set" };

  let apiRes;
  try {
    apiRes = await fetch(
      "https://api.football-data.org/v4/competitions/WC/matches?season=2026",
      { headers: { "X-Auth-Token": apiKey } }
    );
  } catch (e) {
    console.error("football-data.org WC fetch error:", e.message);
    throw e;
  }

  if (apiRes.status === 429) { console.warn("Rate limit hit on WC sync"); return { error: "rate_limited" }; }
  if (!apiRes.ok) {
    const errText = await apiRes.text().catch(() => "");
    throw new Error(`WC API error ${apiRes.status}: ${errText}`);
  }

  const data = await apiRes.json();

  // Filter to featured nations only, then sort chronologically
  const matches = (data.matches || [])
    .filter(m =>
      WC_FEATURED_NATIONS.has(m.homeTeam?.name) ||
      WC_FEATURED_NATIONS.has(m.awayTeam?.name)
    )
    .sort((a, b) => new Date(a.utcDate) - new Date(b.utcDate));

  console.log(`WC 2026: ${data.matches?.length} total → ${matches.length} featured-nation matches`);

  // Assign gameweek: every 6 matches = next GW (101, 102, ...)
  const assigned = matches.map((match, i) => ({
    match,
    gameweek: WC_GW_OFFSET + Math.floor(i / WC_MATCHES_PER_GW) + 1,
  }));

  console.log(`WC 2026: ${matches.length} matches → ${Math.ceil(matches.length / WC_MATCHES_PER_GW)} gameweeks`);

  // ── Preview mode ───────────────────────────────────────────────────────────
  if (preview) {
    const byGw = {};
    for (const { match, gameweek } of assigned) {
      if (!byGw[gameweek]) byGw[gameweek] = [];
      byGw[gameweek].push({
        home:    match.homeTeam?.name || "TBD",
        away:    match.awayTeam?.name || "TBD",
        kickoff: match.utcDate,
        status:  match.status,
        round:   match.stage,
      });
    }
    return {
      total:      matches.length,
      gameweeks:  Object.keys(byGw).length,
      byGameweek: byGw,
    };
  }

  // ── Write to Firestore ─────────────────────────────────────────────────────
  let batch    = db.batch();
  let opCount  = 0;
  let anyFinished = false;

  for (const { match, gameweek } of assigned) {
    const isFinished = match.status === "FINISHED";
    const homeGoals  = isFinished && match.score?.fullTime?.home != null ? match.score.fullTime.home : -1;
    const awayGoals  = isFinished && match.score?.fullTime?.away != null ? match.score.fullTime.away : -1;
    if (isFinished) anyFinished = true;

    const kickoffDate = match.utcDate ? new Date(match.utcDate) : null;

    batch.set(db.collection("fixtures").doc(`wc26_${match.id}`), {
      homeTeam:      match.homeTeam?.name || "TBD",
      awayTeam:      match.awayTeam?.name || "TBD",
      homeTeamGoals: homeGoals,
      awayTeamGoals: awayGoals,
      gameweek,
      competition:   "WC2026",
      date:          match.utcDate || "",
      deadline:      kickoffDate ? admin.firestore.Timestamp.fromDate(kickoffDate) : null,
      apiFixtureId:  match.id,
    }, { merge: true });

    opCount++;
    if (opCount % 450 === 0) { await batch.commit(); batch = db.batch(); }
  }

  if (opCount % 450 !== 0) await batch.commit();

  if (anyFinished) {
    fetch("https://us-central1-the-big-6ix.cloudfunctions.net/manualCalculatePoints", { method: "POST" })
      .catch(e => console.error("WC scoring trigger failed:", e.message));
  }

  const result = { totalFromApi: data.matches?.length, featuredMatches: matches.length, synced: opCount, gameweeks: Math.ceil(matches.length / WC_MATCHES_PER_GW), triggeredScoring: anyFinished };
  console.log("WC sync complete.", result);
  return result;
};

// HTTP: ?preview=true for dry-run
exports.manualSyncWorldCup = functions
  .runWith({ secrets: ["FOOTBALLDATA_KEY"], timeoutSeconds: 120 })
  .https.onRequest(async (req, res) => {
    try {
      const preview = req.query.preview === "true";
      const result  = await doSyncWorldCup(preview);
      res.status(200).json({ success: true, preview, ...result });
    } catch (e) {
      console.error("manualSyncWorldCup error:", e);
      res.status(500).json({ success: false, error: e.message });
    }
  });

// Scheduled: runs every 6 hours automatically
// NOTE: PL sync (doSyncFixtures) is disabled — WC 2026 only mode.
// Re-enable doSyncFixtures() when the 2026/27 PL season begins.
exports.syncFootballFixtures = functions
  .runWith({ secrets: ["FOOTBALLDATA_KEY"] })
  .pubsub.schedule("every 6 hours").onRun(async () => {
    await doSyncWorldCup();
  });

// HTTP: ?preview=true → dry-run (no writes), default → full sync
exports.manualSyncFixtures = functions
  .runWith({ secrets: ["FOOTBALLDATA_KEY"], timeoutSeconds: 300 })
  .https.onRequest(async (req, res) => {
    try {
      const preview = req.query.preview === "true";
      const result  = await doSyncFixtures(preview);
      res.status(200).json({ success: true, preview, ...result });
    } catch (e) {
      console.error("manualSyncFixtures error:", e);
      res.status(500).json({ success: false, error: e.message });
    }
  });

// Cleanup: deletes wc26_ fixtures where neither team is a featured nation.
// Run after changing WC_FEATURED_NATIONS or after the initial full sync.
exports.cleanupNonFeaturedWCFixtures = functions
  .runWith({ timeoutSeconds: 120 })
  .https.onRequest(async (req, res) => {
    try {
      const snap = await db.collection("fixtures").get();

      const toDelete = snap.docs.filter(doc => {
        if (!doc.id.startsWith("wc26_")) return false;
        const { homeTeam, awayTeam } = doc.data();
        return !WC_FEATURED_NATIONS.has(homeTeam) && !WC_FEATURED_NATIONS.has(awayTeam);
      });

      console.log(`Deleting ${toDelete.length} non-featured WC fixtures out of ${snap.size} total`);

      let batch = db.batch();
      let count = 0;
      for (const doc of toDelete) {
        batch.delete(doc.ref);
        count++;
        if (count % 450 === 0) { await batch.commit(); batch = db.batch(); }
      }
      if (count % 450 !== 0) await batch.commit();

      res.status(200).json({ success: true, deleted: toDelete.length, remaining: snap.size - toDelete.length });
    } catch (e) {
      console.error("cleanupNonFeaturedWCFixtures error:", e);
      res.status(500).json({ success: false, error: e.message });
    }
  });

// Deletes ALL non-WC fixtures (prem_ docs + any old manually-entered ones).
// Use when clearing out the PL season to leave only WC fixtures.
exports.deleteAllPLFixtures = functions
  .runWith({ timeoutSeconds: 120 })
  .https.onRequest(async (req, res) => {
    try {
      const snap = await db.collection("fixtures").get();

      const toDelete = snap.docs.filter(doc => !doc.id.startsWith("wc26_"));

      console.log(`Deleting ${toDelete.length} non-WC fixtures`);

      let batch = db.batch();
      let count = 0;
      for (const doc of toDelete) {
        batch.delete(doc.ref);
        count++;
        if (count % 450 === 0) { await batch.commit(); batch = db.batch(); }
      }
      if (count % 450 !== 0) await batch.commit();

      res.status(200).json({ success: true, deleted: toDelete.length, remaining: snap.size - toDelete.length });
    } catch (e) {
      console.error("deleteAllPLFixtures error:", e);
      res.status(500).json({ success: false, error: e.message });
    }
  });

// One-time cleanup: deletes any prem_ fixtures NOT involving a Big 6 team.
// Run once after the initial full-season sync to remove non-Big-6 docs.
exports.cleanupNonBig6Fixtures = functions
  .runWith({ timeoutSeconds: 300 })
  .https.onRequest(async (req, res) => {
    try {
      const snap = await db.collection("fixtures").get();
      const BIG_SIX_NAMES = new Set([
        "Arsenal", "Chelsea", "Liverpool",
        "Man City", "Man Utd", "Tottenham", "Spurs",
        // full-name fallbacks
        "Arsenal FC", "Chelsea FC", "Liverpool FC",
        "Manchester City", "Manchester City FC",
        "Manchester United", "Manchester United FC",
        "Tottenham Hotspur", "Tottenham Hotspur FC",
      ]);

      const toDelete = snap.docs.filter(doc => {
        if (!doc.id.startsWith("prem_")) return false; // never touch WC fixtures
        const { homeTeam, awayTeam } = doc.data();
        return !BIG_SIX_NAMES.has(homeTeam) && !BIG_SIX_NAMES.has(awayTeam);
      });

      console.log(`Found ${toDelete.length} non-Big-6 fixtures to delete out of ${snap.size} total`);

      // Delete in batches of 450
      let batch  = db.batch();
      let count  = 0;
      for (const doc of toDelete) {
        batch.delete(doc.ref);
        count++;
        if (count % 450 === 0) { await batch.commit(); batch = db.batch(); }
      }
      if (count % 450 !== 0) await batch.commit();

      res.status(200).json({ success: true, deleted: toDelete.length, remaining: snap.size - toDelete.length });
    } catch (e) {
      console.error("cleanupNonBig6Fixtures error:", e);
      res.status(500).json({ success: false, error: e.message });
    }
  });

// ── Deadline Reminder Notifications ──────────────────────────────────────────
// Runs every hour. Sends an FCM push to users who HAVEN'T predicted yet for
// fixtures whose deadline is within the next 24 h (first reminder) or 1 h (urgent).
// Skips users with pushNotificationsEnabled === false.
exports.sendDeadlineReminders = functions
  .runWith({ timeoutSeconds: 120 })
  .pubsub.schedule("every 1 hours").onRun(async () => {
    const now   = new Date();
    const in1h  = new Date(now.getTime() + 60 * 60 * 1000);
    const in24h = new Date(now.getTime() + 24 * 60 * 60 * 1000);

    // Fixtures whose deadline falls in [now, now+24h]
    const fixtureSnap = await db.collection("fixtures")
      .where("deadline", ">",  admin.firestore.Timestamp.fromDate(now))
      .where("deadline", "<=", admin.firestore.Timestamp.fromDate(in24h))
      .get();

    if (fixtureSnap.empty) return null;

    // Group upcoming fixture IDs by gameweek
    const gwToFixtures = {};
    fixtureSnap.forEach(doc => {
      const gw = doc.data().gameweek;
      if (!gwToFixtures[gw]) gwToFixtures[gw] = [];
      gwToFixtures[gw].push({ id: doc.id, deadline: doc.data().deadline.toDate() });
    });

    // All user docs with a valid FCM token
    const userSnap = await db.collection("users")
      .where("fcmToken", "!=", null)
      .get();

    const messaging = admin.messaging();
    let sent = 0;

    for (const userDoc of userSnap.docs) {
      const data = userDoc.data();
      if (data.pushNotificationsEnabled === false) continue;
      const token = data.fcmToken;
      if (!token || typeof token !== "string") continue;

      // Which fixtures has this user already predicted?
      const predSnap = await db.collection("predictions")
        .where("userId", "==", userDoc.id)
        .get();
      const predictedIds = new Set(predSnap.docs.map(d => d.data().fixtureId));

      for (const [gw, fixtures] of Object.entries(gwToFixtures)) {
        const unpredicted = fixtures.filter(f => !predictedIds.has(f.id));
        if (unpredicted.length === 0) continue;

        // Pick the earliest deadline in this GW to determine urgency
        const earliest = unpredicted.reduce((a, b) => a.deadline < b.deadline ? a : b).deadline;
        const msLeft   = earliest.getTime() - now.getTime();
        const isUrgent = msLeft <= 60 * 60 * 1000;   // ≤ 1 hour left

        // 24 h reminder: deadline between 23 h and 24 h away
        // 1 h  reminder: deadline within 1 h
        const in23h   = new Date(now.getTime() + 23 * 60 * 60 * 1000);
        const send24h = earliest > in23h && earliest <= in24h;
        const send1h  = isUrgent;
        if (!send24h && !send1h) continue;

        const count = unpredicted.length;
        const title = isUrgent
          ? `⏰ GW${gw} closes in under 1 hour!`
          : `🔔 GW${gw} deadline in 24 hours`;
        const body  = isUrgent
          ? `You still have ${count} unpredicted fixture${count > 1 ? "s" : ""}. Predict now before it's too late!`
          : `Don't forget — ${count} fixture${count > 1 ? "s" : ""} still need${count === 1 ? "s" : ""} your prediction.`;

        try {
          await messaging.send({
            token,
            notification: { title, body },
            android: { priority: "high" }
          });
          sent++;
        } catch (e) {
          // Token expired — clear it so we don't retry
          if (e.code === "messaging/registration-token-not-registered") {
            await db.collection("users").doc(userDoc.id).update({ fcmToken: admin.firestore.FieldValue.delete() });
          } else {
            console.warn(`FCM send failed for ${userDoc.id}:`, e.message);
          }
        }
      }
    }

    console.log(`sendDeadlineReminders: sent ${sent} notifications`);
    return null;
  });
