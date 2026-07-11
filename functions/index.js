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
const SCORING_FUNCTION_VERSION = "token-multipliers-v1";

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

const generateNotificationBody = (basePoints, captainApplied, doubleDownApplied, predScore, finalScore, points) => {
  const pick = (arr) => arr[Math.floor(Math.random() * arr.length)];

  if (basePoints === 3) {
    const exactOptions = [
      `⭐ ${finalScore}. You called it like Pep's got a secret data centre. Precision. +${points} pts`,
      `🎯 ${finalScore} on the dot. More accurate than Arsenal's title predictions. Exact score. +${points} pts`,
      `📟 ${finalScore}. You didn't bottle it like Spurs in March. Dead on. +${points} pts`,
      `🧠 ${finalScore} exactly. Man United couldn't aim that straight. +${points} pts`,
      // 2025/26 season
      `🎯 ${finalScore}. Nailed it like Arsenal finally nailed the league. Bang on. +${points} pts`,
      `🔮 ${finalScore} exact. More composed than Liverpool's back four all season. +${points} pts`,
      `📐 ${finalScore} to the inch. You actually defended your call, unlike Liverpool. +${points} pts`,
    ];

    if (captainApplied && doubleDownApplied) {
      const both = [
        `👑🔥 BOTH TOKENS. EXACT SCORE. ${finalScore}. You are not normal. Even Pep's jealous. +${points} pts`,
        `👑🔥 Captain. Double Down. Exact on ${finalScore}. More commitment than Tottenham's entire season. +${points} pts`,
        `👑🔥 ${finalScore} exact, armband on, doubled down. The confidence of a Big 6 team that actually won something. +${points} pts`,
        // 2025/26 season
        `👑🔥 Captain + double down, ${finalScore} exact. More trophies than Chelsea, Liverpool and Spurs combined. +${points} pts`,
        `👑🔥 Both tokens, ${finalScore} spot on. A cup double of your own — eat your heart out, City. +${points} pts`,
      ];
      return pick(both);
    } else if (captainApplied) {
      const captain = [
        `👑 CAPTAIN'S ARMBAND. ${finalScore} EXACT. You've got Pep's attention to detail. +${points} pts`,
        `👑 Captained it AND called it. ${finalScore}. Arsenal could learn commitment from you. +${points} pts`,
        `👑 The armband came through. ${finalScore} on the button. Not a Tottenham bottle job. +${points} pts`,
        // 2025/26 season
        `👑 Captain nailed ${finalScore} exactly. The title-winning composure Arteta finally found. +${points} pts`,
        `👑 Armband delivered ${finalScore} on the nose. City could've used you in the title race. +${points} pts`,
      ];
      return pick(captain);
    } else if (doubleDownApplied) {
      const ddOptions = [
        `🔥 DOUBLED DOWN ON ${finalScore} EXACT. That's conviction Arsenal could use. Absolute filth. +${points} pts`,
        `🔥 You backed yourself on the exact score and it came in. More guts than Spurs. +${points} pts`,
        `🔥 Double Down on ${finalScore}. You committed harder than Chelsea commits to a formation. +${points} pts`,
        // 2025/26 season
        `🔥 Doubled down on ${finalScore}, exact. More clutch than City in the cup finals. +${points} pts`,
        `🔥 All-in on ${finalScore} and it landed. The nerve Chelsea never showed all year. +${points} pts`,
      ];
      return pick(ddOptions);
    }
    return pick(exactOptions);
  } else if (basePoints === 1) {
    const outcomeOptions = [
      `✅ Right result, wrong score. Still better than Liverpool's defending last season. +${points} pts`,
      `👀 Outcome nailed, score chaotic. You're 50% there — which is better than Chelsea's system. +${points} pts`,
      `🎲 Result-right, number-wrong. More consistent than Tottenham's form. Take the point. +${points} pts`,
      // 2025/26 season
      `✅ Right result, wrong score. More end product than Chelsea's whole season. +${points} pts`,
      `🧭 Right outcome, off on the digits. Closer than City got to the title. +${points} pts`,
      `👍 Got the winner, missed the score. Tighter than anything Spurs did near the drop. +${points} pts`,
    ];

    if (captainApplied && doubleDownApplied) {
      return `👑🔥 Result correct, captain + double down locked in. ${finalScore} wasn't exact but you made it count. +${points} pts`;
    } else if (captainApplied) {
      const capOutcome = [
        `👑 Captain got the result. Score off but who cares — you're still more decisive than Man United's planning. +${points} pts`,
        `👑 Captain got the result. Held firmer than Liverpool's defence ever did. +${points} pts`,
      ];
      return pick(capOutcome);
    } else if (doubleDownApplied) {
      const ddOutcome = [
        `🔥 Doubled Down on the result. Score was off but the call landed. Better odds than Man United's Europa chances. +${points} pts`,
        `🔥 Double down, right result. Smarter business than Chelsea's transfer splurge. +${points} pts`,
      ];
      return pick(ddOutcome);
    }
    return pick(outcomeOptions);
  } else {
    const wrongOptions = [
      `😬 ${predScore}? You predicted like Arsenal picking strikers. Final: ${finalScore}. Better luck next week.`,
      `🚮 You said ${predScore}, it was ${finalScore}. Even Man United didn't miss by that much on transfers. 💀`,
      `🙈 Bold prediction: ${predScore}. Reality: ${finalScore}. You've got Spurs-level accuracy today.`,
      `📺 ${predScore} vs ${finalScore}. Your prediction had the gap of Man United's ambition and results.`,
      // 2025/26 season
      `💀 ${predScore} vs ${finalScore}. Bottled it like City bottled the league to Arsenal.`,
      `🪦 ${predScore}? Finished ${finalScore}. Defended worse than Liverpool this season.`,
      `🤡 You said ${predScore}, it was ${finalScore}. A Chelsea-finishing-10th level of wrong.`,
      `📉 ${predScore}? Final ${finalScore}. Scrapping at the bottom with Spurs.`,
      `😬 ${predScore} nowhere near ${finalScore}. Amorim got sacked for a start like that.`,
      `🦐 ${predScore}? It finished ${finalScore}. That prediction was worse than Man United losing to Grimsby.`,
      `🥅 ${predScore} vs ${finalScore}. You skied that one like Gabriel's penalty in the UCL final.`,
    ];
    return pick(wrongOptions);
  }
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
  const userDataCache = new Map();

  const getUserData = async (userId) => {
    if (userDataCache.has(userId)) return userDataCache.get(userId);
    const doc = await usersRef.doc(userId).get();
    const data = doc.data() || {};
    userDataCache.set(userId, data);
    return data;
  };

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

    let basePoints = 0;
    let baseReason = "wrong_prediction";

    if (
      predictionScores.home === fixtureScores.home &&
      predictionScores.away === fixtureScores.away
    ) {
      basePoints = 3;
      baseReason = "exact_score";
    } else if (predictedOutcome === actualOutcome) {
      basePoints = 1;
      baseReason = "correct_outcome";
    }

    let captainApplied = false;
    let doubleDownApplied = false;

    if (basePoints > 0) {
      if (prediction.captainUsed === true) {
        captainApplied = true;
      }
      if (prediction.userId) {
        const userData = await getUserData(prediction.userId);
        const fixtureGw = Number(fixture.gameweek ?? -1);
        const ddGw = Number(userData.doubleDownUsedGameweek ?? -1);
        if (fixtureGw >= 0 && ddGw === fixtureGw) {
          doubleDownApplied = true;
        }
      }
    }

    const multiplier = (captainApplied ? 2 : 1) * (doubleDownApplied ? 2 : 1);
    const points = basePoints * multiplier;

    const scoringReason = baseReason
      + (captainApplied    ? "_captain"     : "")
      + (doubleDownApplied ? "_double_down" : "");

    batch.update(predictionDoc.ref, {
      scoredPoints: true,
      awardedPoints: points,
      isCorrect: points > 0,
      ignoredDuplicate: false,
      scoringReason,
      captainApplied,
      doubleDownApplied,
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

  // Per-run cache so each user doc is fetched at most once.
  const userDataCache = new Map();
  const getUserData = async (userId) => {
    if (userDataCache.has(userId)) return userDataCache.get(userId);
    const doc = await usersRef.doc(userId).get();
    const data = doc.data() || {};
    userDataCache.set(userId, data);
    return data;
  };
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

      // Pick the prediction with the latest submittedAt.
      // IMPORTANT: do NOT use `null` as the reduce seed — when a user has no
      // submittedAt (old Android clients), every comparison is 0 > 0 = false and
      // `latest` would stay null, crashing the CF on `null.scoredPoints` later.
      // Using predictions[0] as the seed ensures we always have a valid object.
      const validPrediction = predictions.reduce((latest, current) => {
        const latestTime = latest.submittedAt?.toMillis?.() ?? 0;
        const currentTime = current.submittedAt?.toMillis?.() ?? 0;
        return currentTime > latestTime ? current : latest;
      });  // no initial value → uses predictions[0] as seed, iterates from index 1

      if (!validPrediction) continue; // safety guard (predictions is always non-empty)

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

      // Scoring reasons that indicate the prediction was zero-scored due to a bug
      // or an incomplete fixture state rather than a genuine wrong/late prediction.
      // These must be re-evaluated on every normal scoring run so the underlying
      // issue (fixed deadline logic, fixture now having a result) is reflected.
      //
      // "submitted_after_gw_deadline"           — old CF used earliest-in-GW deadline;
      //                                           now uses per-fixture deadline, so many of
      //                                           these should now be valid.
      // "fixture_not_complete_or_missing_score" — set by recalculateGameweekDirect when
      //                                           the fixture had no result; needs re-check
      //                                           now that the fixture may be complete.
      // "fixture_scoped_reset_pending_recalculation" / "reset_pending_recalculation"
      //                                         — transient states left if a rescore batch
      //                                           aborted; always re-score.
      const RESCORE_LEGACY_REASONS = new Set([
        "submitted_after_gw_deadline",          // old CF used GW-wide deadline; now per-fixture
        "missing_or_invalid_submittedAt",       // original Android bug: no submittedAt field
        "fixture_not_complete_or_missing_score",// recalculate ran before fixture was complete
        "fixture_scoped_reset_pending_recalculation", // transient reset state
        "reset_pending_recalculation",          // transient reset state
      ]);

      const alreadyFinallyScored =
        validPrediction.scoredPoints === true &&
        !RESCORE_LEGACY_REASONS.has(validPrediction.scoringReason);

      if (!rescore && alreadyFinallyScored) continue;

      // Only enforce deadline when submittedAt is actually present and parseable.
      // Predictions missing submittedAt were submitted by older Android clients that
      // didn't include the field — give them benefit of the doubt rather than zero-scoring.
      const hasSubmittedAt =
        validPrediction.submittedAt &&
        typeof validPrediction.submittedAt.toMillis === "function";

      // Enforce per-fixture deadline: a prediction is valid as long as it was
      // submitted before THAT fixture's own kickoff.  The UI shows a single GW
      // deadline (earliest kickoff) to prevent confusion, but scoring must remain
      // per-fixture so that legacy predictions submitted between Game 1's kickoff
      // and Game N's kickoff (under the old per-fixture display) are not rejected.
      if (
        enforceDeadline &&
        hasSubmittedAt &&
        validPrediction.submittedAt.toMillis() > fixture.deadline.toMillis()
      ) {
        batch.update(validPrediction.ref, {
          scoredPoints: true,
          isCorrect: false,
          awardedPoints: 0,
          ignoredDuplicate: false,
          scoringReason: "submitted_after_deadline",
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

      let basePoints = 0;
      let baseReason = "wrong_prediction";

      if (
        predictionHomeGoals === fixtureHomeGoals &&
        predictionAwayGoals === fixtureAwayGoals
      ) {
        basePoints = 3;
        baseReason = "exact_score";
      } else if (predictedOutcome === actualOutcome) {
        basePoints = 1;
        baseReason = "correct_outcome";
      }

      // Apply token multipliers when the prediction earned points.
      let captainApplied = false;
      let doubleDownApplied = false;

      if (basePoints > 0) {
        // Captain: 2× on this specific fixture
        if (validPrediction.captainUsed === true) {
          captainApplied = true;
        }

        // Double Down: 2× on ALL correct predictions in the GW it was used
        const userData = await getUserData(userId);
        const ddGw = Number(userData.doubleDownUsedGameweek ?? -1);
        if (ddGw === fixtureGameweek) {
          doubleDownApplied = true;
        }
      }

      const multiplier = (captainApplied ? 2 : 1) * (doubleDownApplied ? 2 : 1);
      const points = basePoints * multiplier;

      const scoringReason = baseReason
        + (captainApplied    ? "_captain"     : "")
        + (doubleDownApplied ? "_double_down" : "");

      // Guard against duplicate result pushes: a prediction is notified exactly
      // once, ever. The scheduled scorer runs every 5 min and the manual endpoint
      // can also push, so without this flag the same result fires repeatedly.
      const alreadyNotified = validPrediction.resultNotificationSent === true;
      const willNotify = sendPush && !alreadyNotified;

      batch.update(validPrediction.ref, {
        scoredPoints: true,
        isCorrect: points > 0,
        awardedPoints: points,
        ignoredDuplicate: false,
        scoringReason,
        captainApplied,
        doubleDownApplied,
        // Mark as notified as soon as we decide to push, so any concurrent/next
        // run sees the flag and skips. Persists even if the FCM send below fails
        // (better to miss one than spam — the score itself is always correct).
        ...(willNotify ? { resultNotificationSent: true } : {}),
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

      if (willNotify) {
        try {
          const userData = await getUserData(userId);
          const token    = userData.fcmToken;

          if (token && userData.pushNotificationsEnabled !== false) {
            const homeTeam = fixture.homeTeam || "Home";
            const awayTeam = fixture.awayTeam || "Away";

            // Title is always the final scoreline — instantly readable on the lock screen
            const title = `${homeTeam} ${fixtureHomeGoals}–${fixtureAwayGoals} ${awayTeam}`;
            const finalScore = `${fixtureHomeGoals}–${fixtureAwayGoals}`;
            const predScore = `${predictionHomeGoals}–${predictionAwayGoals}`;
            const body = generateNotificationBody(basePoints, captainApplied, doubleDownApplied, predScore, finalScore, points);

            // Stable per-result key so a duplicate delivery collapses instead of
            // stacking — in the background tray (android tag) and via FCM's queue
            // (collapseKey) when the device was offline.
            const resultKey = `result_${fixtureId}`;

            await admin.messaging().send({
              token,
              notification: { title, body },
              // data.type routes the notification to the Results channel in Big6ixMessagingService (Android foreground)
              data: { type: "result" },
              android: {
                priority: "high",
                collapseKey: resultKey,
                // channelId ensures the correct channel is used even when the app is in the background.
                // tag makes a repeat of the SAME result replace the existing tray entry, not duplicate it.
                notification: { channelId: "big6ix_results", tag: resultKey },
              },
              apns: {
                // apns-collapse-id dedupes the same result on iOS too.
                headers: { "apns-collapse-id": resultKey },
                payload: { aps: { sound: "default" } },
              },
            });
          }
        } catch (e) {
          // Clean up stale / revoked FCM tokens automatically
          if (e.code === "messaging/registration-token-not-registered") {
            await usersRef.doc(userId).update({ fcmToken: admin.firestore.FieldValue.delete() });
            console.log(`Cleared stale FCM token for user ${userId}`);
          } else {
            console.warn(`FCM result push failed for ${userId}:`, e.message);
          }
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
    // ?debugFixture=wc26_537357  → show all predictions for one fixture
    if (req.query.debugFixture) {
      const fid = req.query.debugFixture;
      const fixtureDoc = await db.collection("fixtures").doc(fid).get();
      const predictionsSnap = await db.collection("predictions").where("fixtureId", "==", fid).get();
      const fixture = fixtureDoc.exists ? {
        id: fixtureDoc.id,
        homeTeam: fixtureDoc.data().homeTeam,
        awayTeam: fixtureDoc.data().awayTeam,
        homeTeamGoals: fixtureDoc.data().homeTeamGoals,
        awayTeamGoals: fixtureDoc.data().awayTeamGoals,
        hasDeadline: !!fixtureDoc.data().deadline,
        deadline: fixtureDoc.data().deadline?.toDate?.()?.toISOString() ?? null,
      } : null;
      const predictions = predictionsSnap.docs.map(doc => {
        const d = doc.data();
        return {
          id: doc.id,
          userId: d.userId,
          fixtureId: d.fixtureId,
          homeTeamGoals: d.homeTeamGoals,
          awayTeamGoals: d.awayTeamGoals,
          parsedHome: getPredictionHomeGoals(d),
          parsedAway: getPredictionAwayGoals(d),
          scoredPoints: d.scoredPoints,
          awardedPoints: d.awardedPoints,
          scoringReason: d.scoringReason,
          submittedAt: d.submittedAt?.toDate?.()?.toISOString() ?? "MISSING",
          wildcardUsed: d.wildcardUsed,
          captainUsed: d.captainUsed,
        };
      });
      return res.status(200).json({ fixture, predictionCount: predictions.length, predictions });
    }

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

exports.rollbackTokenMultipliers = functions.https.onRequest(async (req, res) => {
  try {
    const gameweek = Number(req.query.gameweek);
    if (!Number.isInteger(gameweek) || gameweek < 1) {
      return res.status(400).send("Invalid gameweek. Example: ?gameweek=1");
    }

    const predictionsSnap = await db.collection("predictions").get();
    const usersRef = db.collection("users");

    let userScoreDeltas = {};
    let processedPredictions = 0;
    let skippedNoMultipliers = 0;

    for (const predictionDoc of predictionsSnap.docs) {
      const prediction = predictionDoc.data();
      const predGameweek = getPredictionGameweek(prediction);

      if (predGameweek !== gameweek) continue;

      const captainApplied = prediction.captainApplied === true;
      const doubleDownApplied = prediction.doubleDownApplied === true;

      // Only rollback predictions that had multipliers applied
      if (!captainApplied && !doubleDownApplied) {
        skippedNoMultipliers++;
        continue;
      }

      const multiplier = (captainApplied ? 2 : 1) * (doubleDownApplied ? 2 : 1);
      const currentPoints = Number(prediction.awardedPoints || 0);
      const oldPoints = Math.floor(currentPoints / multiplier);
      const delta = currentPoints - oldPoints;

      if (prediction.userId && delta !== 0) {
        userScoreDeltas[prediction.userId] = (userScoreDeltas[prediction.userId] || 0) - delta;
      }

      processedPredictions++;
    }

    // Apply score deltas to users
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

    const usersAffected = Object.keys(userScoreDeltas).filter(uid => userScoreDeltas[uid] !== 0).length;

    res.status(200).send(
      `Rollback complete for GW${gameweek}. Processed: ${processedPredictions} predictions, rolled back multipliers from ${usersAffected} users, skipped ${skippedNoMultipliers} (no multipliers applied).`
    );
  } catch (e) {
    console.error("rollbackTokenMultipliers error:", e);
    res.status(500).send(e.message);
  }
});

exports.calculateCorrectScores = functions.https.onRequest(async (req, res) => {
  try {
    const gameweek = Number(req.query.gameweek) || 1;

    const predictionsSnap = await db.collection("predictions").get();
    const usersSnap = await db.collection("users").get();

    // Build current user scores
    const currentUserScores = {};
    usersSnap.docs.forEach(doc => {
      currentUserScores[doc.id] = Number(doc.data().score || 0);
    });

    // Calculate correct scores (without multipliers)
    const userCorrectScores = {};
    let processedPredictions = 0;

    predictionsSnap.docs.forEach(predDoc => {
      const pred = predDoc.data();
      const predGw = getPredictionGameweek(pred);

      if (predGw !== gameweek || !pred.userId) return;

      const awardedPoints = Number(pred.awardedPoints || 0);
      const captainApplied = pred.captainApplied === true;
      const doubleDownApplied = pred.doubleDownApplied === true;

      // Calculate what it should have been without multipliers
      let correctPoints = awardedPoints;
      if (captainApplied && doubleDownApplied) {
        correctPoints = Math.floor(awardedPoints / 4);
      } else if (captainApplied || doubleDownApplied) {
        correctPoints = Math.floor(awardedPoints / 2);
      }

      if (!userCorrectScores[pred.userId]) {
        userCorrectScores[pred.userId] = 0;
      }
      userCorrectScores[pred.userId] += correctPoints;
      processedPredictions++;
    });

    // Build final scores: current score - (points with multipliers) + (correct points)
    const restorationList = [];
    for (const [userId, correctGwPoints] of Object.entries(userCorrectScores)) {
      const currentScore = currentUserScores[userId] || 0;
      // We need to know how much was added with multipliers to subtract it
      // This is tricky — let me calculate differently:
      // Find all predictions for this user in GW1, sum their correct points
      let userCurrentGwPoints = 0;
      predictionsSnap.docs.forEach(predDoc => {
        const pred = predDoc.data();
        if (pred.userId === userId && getPredictionGameweek(pred) === gameweek) {
          userCurrentGwPoints += Number(pred.awardedPoints || 0);
        }
      });

      const delta = userCurrentGwPoints - correctGwPoints;
      const priorScore = currentScore - delta;

      restorationList.push({
        userId,
        currentScore,
        correctGwPoints,
        userCurrentGwPoints,
        delta,
        priorScore,
      });
    }

    res.status(200).json({
      gameweek,
      processedPredictions,
      usersAffected: restorationList.length,
      restorationList,
    });
  } catch (e) {
    console.error("calculateCorrectScores error:", e);
    res.status(500).send(e.message);
  }
});

exports.backupUserScores = functions.https.onRequest(async (req, res) => {
  try {
    const label = req.query.label || `backup_${Date.now()}`;

    const usersSnap = await db.collection("users").get();
    const scores = {};
    usersSnap.forEach((doc) => {
      scores[doc.id] = Number(doc.data().score || 0);
    });

    // Firestore doc has a 1MB limit; ~150 users of {id: number} is tiny, fits easily.
    await db.collection("scoreBackups").doc(label).set({
      label,
      createdAt: admin.firestore.FieldValue.serverTimestamp(),
      userCount: Object.keys(scores).length,
      scores,
    });

    return res.status(200).json({
      message: `Backed up ${Object.keys(scores).length} user scores.`,
      label,
      restoreWith: `/restoreScoreBackup?label=${label}&commit=true`,
    });
  } catch (e) {
    console.error("backupUserScores error:", e);
    return res.status(500).send(e.message);
  }
});

exports.restoreScoreBackup = functions.https.onRequest(async (req, res) => {
  try {
    const label = req.query.label;
    const dryRun = req.query.commit !== "true";
    if (!label) {
      return res.status(400).send("Provide ?label=<backup label>. List with /listScoreBackups");
    }

    const backupDoc = await db.collection("scoreBackups").doc(label).get();
    if (!backupDoc.exists) {
      return res.status(404).send(`No backup found with label "${label}".`);
    }

    const scores = backupDoc.data().scores || {};
    const usersRef = db.collection("users");

    const usersSnap = await usersRef.get();
    const changes = [];
    usersSnap.forEach((doc) => {
      if (!(doc.id in scores)) return;
      const oldScore = Number(doc.data().score || 0);
      const restored = Number(scores[doc.id]);
      if (oldScore !== restored) {
        changes.push({ userId: doc.id, currentScore: oldScore, restoreTo: restored });
      }
    });

    if (dryRun) {
      return res.status(200).json({
        dryRun: true,
        note: "No writes performed. Add &commit=true to apply.",
        label,
        usersToRestore: changes.length,
        changes,
      });
    }

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

    for (const c of changes) {
      batch.update(usersRef.doc(c.userId), { score: c.restoreTo });
      opCount++;
      await commitIfNeeded();
    }
    if (opCount > 0) await batch.commit();

    return res.status(200).json({
      dryRun: false,
      label,
      usersRestored: changes.length,
    });
  } catch (e) {
    console.error("restoreScoreBackup error:", e);
    return res.status(500).send(e.message);
  }
});

exports.fixUserTokenDocs = functions.https.onRequest(async (req, res) => {
  try {
    const userId = req.query.userId;
    const dryRun = req.query.commit !== "true";
    if (!userId) {
      return res.status(400).send("Provide ?userId=<id>. Add &commit=true to apply.");
    }

    // Current fixtures only — never touch stale predictions.
    const fixturesSnap = await db.collection("fixtures").get();
    const validFixtureIds = new Set(fixturesSnap.docs.map((d) => d.id));

    // This user's double-down gameweek (if any).
    const userDoc = await db.collection("users").doc(userId).get();
    const ddGw = userDoc.exists
      ? Number(userDoc.data().doubleDownUsedGameweek ?? -1)
      : -1;

    const predsSnap = await db.collection("predictions").where("userId", "==", userId).get();

    const batch = db.batch();
    const changes = [];

    predsSnap.forEach((doc) => {
      const p = doc.data();
      if (!p.fixtureId || !validFixtureIds.has(p.fixtureId)) return;

      const awarded = Number(p.awardedPoints || 0);
      if (awarded <= 0) return; // only predictions that earned points can be doubled

      const captainApplied = p.captainApplied === true;
      const doubleDownApplied = p.doubleDownApplied === true;
      const currentMult = (captainApplied ? 2 : 1) * (doubleDownApplied ? 2 : 1);

      // What SHOULD be applied based on the token flags.
      const predGw = getPredictionGameweek(p);
      const captainShould = p.captainUsed === true;
      const ddShould = ddGw >= 0 && predGw === ddGw;
      const desiredMult = (captainShould ? 2 : 1) * (ddShould ? 2 : 1);

      if (desiredMult === currentMult) return; // already correct

      const base = Math.round(awarded / currentMult); // strip current multiplier
      const newAwarded = base * desiredMult;

      // Rebuild scoring reason from the base outcome + applied tokens.
      const baseReason = base === 3 ? "exact_score" : base === 1 ? "correct_outcome" : "wrong_prediction";
      const newReason = baseReason
        + (captainShould ? "_captain" : "")
        + (ddShould ? "_double_down" : "");

      changes.push({
        predId: doc.id,
        fixtureId: p.fixtureId,
        gameweek: predGw,
        oldAwarded: awarded,
        newAwarded,
        captainApplied: captainShould,
        doubleDownApplied: ddShould,
        newReason,
      });

      if (!dryRun) {
        batch.update(doc.ref, {
          awardedPoints: newAwarded,
          captainApplied: captainShould,
          doubleDownApplied: ddShould,
          scoringReason: newReason,
        });
      }
    });

    if (!dryRun && changes.length > 0) {
      await batch.commit();
    }

    const docDelta = changes.reduce((s, c) => s + (c.newAwarded - c.oldAwarded), 0);

    return res.status(200).json({
      dryRun,
      userId,
      note: dryRun
        ? "No writes performed. Add &commit=true to apply. User TOTAL score is never touched."
        : "Prediction docs updated. User total score was NOT modified.",
      docsChanged: changes.length,
      predictionDocPointsDelta: docDelta,
      changes,
    });
  } catch (e) {
    console.error("fixUserTokenDocs error:", e);
    return res.status(500).send(e.message);
  }
});

exports.debugTokens = functions.https.onRequest(async (req, res) => {
  try {
    const fixturesSnap = await db.collection("fixtures").get();
    const validFixtureIds = new Set(fixturesSnap.docs.map((d) => d.id));

    const usersSnap = await db.collection("users").get();
    const ddByUser = {}; // userId -> doubleDownUsedGameweek
    usersSnap.forEach((d) => {
      const v = d.data().doubleDownUsedGameweek;
      if (v !== undefined && v !== null) ddByUser[d.id] = Number(v);
    });

    const predsSnap = await db.collection("predictions").get();
    const captainRows = [];
    const ddRows = [];

    predsSnap.forEach((doc) => {
      const p = doc.data();
      if (!p.fixtureId || !validFixtureIds.has(p.fixtureId)) return; // current fixtures only

      if (p.captainUsed === true) {
        captainRows.push({
          predId: doc.id,
          userId: p.userId,
          fixtureId: p.fixtureId,
          gameweek: getPredictionGameweek(p),
          awardedPoints: Number(p.awardedPoints || 0),
          captainUsed: p.captainUsed === true,
          captainApplied: p.captainApplied === true,
          scoredPoints: p.scoredPoints === true,
          scoringReason: p.scoringReason,
        });
      }

      const ddGw = ddByUser[p.userId];
      if (ddGw !== undefined && getPredictionGameweek(p) === ddGw) {
        ddRows.push({
          predId: doc.id,
          userId: p.userId,
          fixtureId: p.fixtureId,
          gameweek: getPredictionGameweek(p),
          doubleDownGw: ddGw,
          awardedPoints: Number(p.awardedPoints || 0),
          doubleDownApplied: p.doubleDownApplied === true,
          scoredPoints: p.scoredPoints === true,
          scoringReason: p.scoringReason,
        });
      }
    });

    return res.status(200).json({
      captainCount: captainRows.length,
      doubleDownPredCount: ddRows.length,
      usersWithDoubleDown: Object.keys(ddByUser).length,
      ddByUser,
      captainPredictions: captainRows,
      doubleDownPredictions: ddRows,
    });
  } catch (e) {
    console.error("debugTokens error:", e);
    return res.status(500).send(e.message);
  }
});

exports.listScoreBackups = functions.https.onRequest(async (req, res) => {
  try {
    const snap = await db.collection("scoreBackups").orderBy("createdAt", "desc").get();
    const backups = snap.docs.map((d) => ({
      label: d.id,
      userCount: d.data().userCount,
      createdAt: d.data().createdAt?.toDate?.()?.toISOString?.() ?? null,
    }));
    return res.status(200).json({ count: backups.length, backups });
  } catch (e) {
    console.error("listScoreBackups error:", e);
    return res.status(500).send(e.message);
  }
});

exports.rebuildScoresCurrentFixtures = functions.https.onRequest(async (req, res) => {
  try {
    const dryRun = req.query.commit !== "true";

    // 1. Load every fixture that currently exists. Predictions pointing at a
    //    fixture that no longer exists (old Premier League games) must NOT count.
    const fixturesSnap = await db.collection("fixtures").get();
    const validFixtureIds = new Set(fixturesSnap.docs.map((d) => d.id));

    // 2. Sum awardedPoints per user, but ONLY for predictions on a current fixture.
    //    awardedPoints already includes legitimate captain / double-down multipliers.
    const predictionsSnap = await db.collection("predictions").get();
    const usersRef = db.collection("users");

    const totalsByUser = {};
    let countedPredictions = 0;
    let skippedStaleFixture = 0;
    let skippedNoUserId = 0;

    predictionsSnap.forEach((doc) => {
      const p = doc.data();
      if (!p.userId) { skippedNoUserId++; return; }
      if (!p.fixtureId || !validFixtureIds.has(p.fixtureId)) {
        skippedStaleFixture++;
        return;
      }
      const awarded = Number(p.awardedPoints || 0);
      totalsByUser[p.userId] = (totalsByUser[p.userId] || 0) + awarded;
      countedPredictions++;
    });

    // 3. Build the change set (only users whose score is actually wrong).
    const usersSnap = await usersRef.get();
    const changes = [];
    usersSnap.forEach((doc) => {
      const oldScore = Number(doc.data().score || 0);
      const newScore = totalsByUser[doc.id] || 0;
      if (oldScore !== newScore) {
        changes.push({ userId: doc.id, oldScore, newScore, diff: newScore - oldScore });
      }
    });

    if (dryRun) {
      return res.status(200).json({
        dryRun: true,
        note: "No writes performed. Add &commit=true to apply.",
        validFixtures: validFixtureIds.size,
        countedPredictions,
        skippedStaleFixture,
        skippedNoUserId,
        usersToChange: changes.length,
        changes: changes.sort((a, b) => Math.abs(b.diff) - Math.abs(a.diff)),
      });
    }

    // 4. Apply: absolute set (not increment) so drift can never accumulate again.
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

    for (const c of changes) {
      batch.update(usersRef.doc(c.userId), { score: c.newScore });
      opCount++;
      await commitIfNeeded();
    }
    if (opCount > 0) await batch.commit();

    return res.status(200).json({
      dryRun: false,
      validFixtures: validFixtureIds.size,
      countedPredictions,
      skippedStaleFixture,
      usersChanged: changes.length,
    });
  } catch (e) {
    console.error("rebuildScoresCurrentFixtures error:", e);
    return res.status(500).send(e.message);
  }
});

exports.debugUserPredictions = functions.https.onRequest(async (req, res) => {
  try {
    // ?gameweekMismatch=true → find predictions whose stored gameweek no longer
    // matches their fixture's current gameweek (fallout from matchday reassignment).
    // Add &commit=true to rewrite prediction.gameweek to the fixture's value.
    if (req.query.gameweekMismatch === "true") {
      const commit = req.query.commit === "true";
      const fixturesSnap = await db.collection("fixtures").get();
      const fixtureGw = {};
      fixturesSnap.docs.forEach((d) => { fixtureGw[d.id] = getFixtureGameweek(d.data()); });

      const predsSnap = await db.collection("predictions").get();
      const mismatches = [];
      const byBucket = {};
      predsSnap.forEach((doc) => {
        const p = doc.data();
        if (!p.fixtureId || !(p.fixtureId in fixtureGw)) return; // current fixtures only
        const fGw = fixtureGw[p.fixtureId];
        const pGw = getPredictionGameweek(p);
        if (fGw != null && pGw !== fGw) {
          mismatches.push({ predId: doc.id, ref: doc.ref, fixtureId: p.fixtureId, predictionGw: pGw, fixtureGw: fGw });
          const k = `pred_gw${pGw}_should_be_gw${fGw}`;
          byBucket[k] = (byBucket[k] || 0) + 1;
        }
      });

      if (commit && mismatches.length) {
        let batch = db.batch(); let n = 0;
        for (const m of mismatches) {
          batch.update(m.ref, { gameweek: m.fixtureGw });
          if (++n % 450 === 0) { await batch.commit(); batch = db.batch(); }
        }
        if (n % 450 !== 0) await batch.commit();
      }

      return res.status(200).json({
        dryRun: !commit,
        note: commit ? "prediction.gameweek rewritten to match fixture." : "No writes. Add &commit=true to fix.",
        mismatchCount: mismatches.length,
        breakdown: byBucket,
        sample: mismatches.slice(0, 40).map(({ ref, ...m }) => m),
      });
    }

    // ?scanTokens=true → global audit of every captain / double-down prediction
    // on a CURRENT fixture, flagging any where the multiplier wasn't applied.
    if (req.query.scanTokens === "true") {
      const fixturesSnap = await db.collection("fixtures").get();
      const validFixtureIds = new Set(fixturesSnap.docs.map((d) => d.id));

      const usersSnap = await db.collection("users").get();
      const ddByUser = {};
      usersSnap.forEach((d) => {
        const v = d.data().doubleDownUsedGameweek;
        if (v !== undefined && v !== null) ddByUser[d.id] = Number(v);
      });

      const predsSnap = await db.collection("predictions").get();
      const captainRows = [];
      const ddRows = [];
      const problems = [];

      predsSnap.forEach((doc) => {
        const p = doc.data();
        if (!p.fixtureId || !validFixtureIds.has(p.fixtureId)) return;
        const awarded = Number(p.awardedPoints || 0);
        const scored = p.scoredPoints === true;
        const predGw = getPredictionGameweek(p);

        if (p.captainUsed === true) {
          const applied = p.captainApplied === true;
          const problem = scored && awarded > 0 && !applied;
          const row = { predId: doc.id, userId: p.userId, fixtureId: p.fixtureId, gameweek: predGw, awardedPoints: awarded, captainApplied: applied, scored, reason: p.scoringReason };
          captainRows.push(row);
          if (problem) problems.push({ kind: "captain_not_applied", ...row });
        }

        const ddGw = ddByUser[p.userId];
        if (ddGw !== undefined && ddGw >= 0 && predGw === ddGw) {
          const applied = p.doubleDownApplied === true;
          const problem = scored && awarded > 0 && !applied;
          const row = { predId: doc.id, userId: p.userId, fixtureId: p.fixtureId, gameweek: predGw, ddGw, awardedPoints: awarded, doubleDownApplied: applied, scored, reason: p.scoringReason };
          ddRows.push(row);
          if (problem) problems.push({ kind: "double_down_not_applied", ...row });
        }
      });

      return res.status(200).json({
        captainCount: captainRows.length,
        doubleDownPredCount: ddRows.length,
        usersWithDoubleDown: Object.keys(ddByUser).length,
        ddByUser,
        problemCount: problems.length,
        problems,
        captainPredictions: captainRows,
        doubleDownPredictions: ddRows,
      });
    }

    const userId = req.query.userId;
    if (!userId) {
      return res.status(400).send("Provide ?userId=<id>");
    }

    // ?resetTokens=true → give this user all 3 tokens back (for demo/recording).
    // Clears doubleDownUsedGameweek so the Double Down card reads "TAP TO USE".
    // Does NOT touch prediction docs, so existing scores are unaffected.
    if (req.query.resetTokens === "true") {
      const uDoc = await db.collection("users").doc(userId).get();
      if (!uDoc.exists) return res.status(404).json({ error: "user not found" });
      await uDoc.ref.update({
        wildcardAvailable: true,
        captainAvailable: true,
        doubleDownAvailable: true,
        doubleDownUsedGameweek: admin.firestore.FieldValue.delete(),
      });
      return res.status(200).json({ success: true, resetTokensFor: userId });
    }

    // ?pushTitle=&pushBody= → send a single push to this user's device (test/video).
    if (req.query.pushTitle && req.query.pushBody) {
      const uDoc = await db.collection("users").doc(userId).get();
      if (!uDoc.exists) return res.status(404).json({ error: "user not found" });
      const token = uDoc.data().fcmToken;
      if (!token) return res.status(400).json({ error: "user has no fcmToken (not logged in on a device?)" });
      await admin.messaging().send({
        token,
        notification: { title: req.query.pushTitle, body: req.query.pushBody },
        data: { type: "result" },
        android: { priority: "high", notification: { channelId: "big6ix_results" } },
        apns: { payload: { aps: { sound: "default" } } },
      });
      return res.status(200).json({ success: true, sentTo: userId });
    }

    const userDoc = await db.collection("users").doc(userId).get();
    const currentScore = userDoc.exists ? Number(userDoc.data().score || 0) : null;

    const predsSnap = await db.collection("predictions").where("userId", "==", userId).get();

    const rows = [];
    let sumAwarded = 0;
    let sumBase = 0;

    for (const doc of predsSnap.docs) {
      const p = doc.data();
      const fixtureId = p.fixtureId;
      let fixtureScore = null;
      if (fixtureId) {
        const fx = await db.collection("fixtures").doc(fixtureId).get();
        if (fx.exists) {
          const f = fx.data();
          fixtureScore = `${getFixtureHomeGoals(f)}-${getFixtureAwayGoals(f)}`;
        }
      }

      const awarded = Number(p.awardedPoints || 0);
      const captainApplied = p.captainApplied === true;
      const doubleDownApplied = p.doubleDownApplied === true;
      const mult = (captainApplied ? 2 : 1) * (doubleDownApplied ? 2 : 1);
      const base = mult > 1 ? Math.floor(awarded / mult) : awarded;

      sumAwarded += awarded;
      sumBase += base;

      rows.push({
        predId: doc.id,
        gameweek: getPredictionGameweek(p),
        fixtureId,
        predicted: `${getPredictionHomeGoals(p)}-${getPredictionAwayGoals(p)}`,
        actual: fixtureScore,
        awardedPoints: awarded,
        basePoints: base,
        captainUsed: p.captainUsed === true,
        captainApplied,
        doubleDownApplied,
        wildcardUsed: p.wildcardUsed === true,
        ignoredDuplicate: p.ignoredDuplicate === true,
        scoringReason: p.scoringReason,
      });
    }

    rows.sort((a, b) => (a.gameweek || 0) - (b.gameweek || 0));

    res.status(200).json({
      userId,
      currentScore,
      totalPredictions: rows.length,
      sumAwardedPoints: sumAwarded,
      sumBasePoints: sumBase,
      drift: currentScore !== null ? currentScore - sumAwarded : null,
      predictions: rows,
    });
  } catch (e) {
    console.error("debugUserPredictions error:", e);
    res.status(500).send(e.message);
  }
});

exports.restoreUserScores = functions.https.onRequest(async (req, res) => {
  try {
    // POST body: { scores: [{ userId: "uid1", priorScore: 42 }, ...] }
    const { scores } = req.body;

    if (!Array.isArray(scores) || scores.length === 0) {
      return res.status(400).send("POST body must contain: { scores: [{ userId, priorScore }, ...] }");
    }

    const usersRef = db.collection("users");
    const batchSize = 450;
    let batch = db.batch();
    let opCount = 0;
    let updated = 0;

    const commitIfNeeded = async () => {
      if (opCount >= batchSize) {
        await batch.commit();
        batch = db.batch();
        opCount = 0;
      }
    };

    for (const { userId, priorScore } of scores) {
      if (!userId || priorScore === undefined || priorScore === null) {
        console.warn(`Skipped invalid entry: userId=${userId}, priorScore=${priorScore}`);
        continue;
      }

      batch.update(usersRef.doc(userId), {
        score: Number(priorScore),
      });

      opCount++;
      updated++;
      await commitIfNeeded();
    }

    if (opCount > 0) {
      await batch.commit();
    }

    res.status(200).send(`Restored scores for ${updated} users.`);
  } catch (e) {
    console.error("restoreUserScores error:", e);
    res.status(500).send(e.message);
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
    await verifyAdmin(req);

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

    // Build sorted list for top-players snapshot
    const sortedUsers = usersSnap.docs
      .map(d => ({ id: d.id, ...d.data() }))
      .sort((a, b) => (b.score || 0) - (a.score || 0));

    const topPlayers = sortedUsers.slice(0, 3).map(u => ({
      fullName:        u.fullName          || "",
      score:           u.score             || 0,
      profileImageURL: u.profileImageURL   || u.profileImageUrl || "",
    }));

    // Write the parent season doc so SeasonArchiveView (iOS + Android) can display it.
    // • year stored as String so both platforms can read it as a string field.
    // • topPlayers is the array field both iOS and Android read.
    await seasonRef.set({
      season:      `Season ${year}`,   // Android reads "season" first
      name:        `Season ${year}`,   // iOS reads "name"
      year:        year,               // kept as String (new Date().getFullYear().toString())
      topPlayers,
      archivedAt:  admin.firestore.FieldValue.serverTimestamp(),
    }, { merge: true });

    // Archive current scores into subcollection
    for (const userDoc of usersSnap.docs) {
      const data = userDoc.data();
      const archiveRef = seasonRef.collection("userScores").doc(userDoc.id);
      batch.set(archiveRef, {
        userId:       userDoc.id,
        fullName:     data.fullName     || "",
        score:        data.score        || 0,
        weeklyScore:  data.weeklyScore  || 0,
        monthlyScore: data.monthlyScore || 0,
        archivedAt:   admin.firestore.FieldValue.serverTimestamp(),
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

    // ── Enrich userScores with per-user prediction stats ──────────────────────
    // Query all scored predictions once and group by userId — one big read is
    // faster than N individual user queries.
    try {
      const predsSnap = await db.collection("predictions")
          .where("awardedPoints", ">=", 0).get();

      const statMap = {};
      for (const doc of predsSnap.docs) {
        const d   = doc.data();
        const uid = d.userId;
        if (!uid) continue;
        if (!statMap[uid]) statMap[uid] = { correctScores: 0, gamesPlayed: 0, gwPts: {} };
        statMap[uid].gamesPlayed++;
        if (d.awardedPoints === 3) statMap[uid].correctScores++;
        const gw = d.gameweek || 0;
        if (gw > 0) statMap[uid].gwPts[gw] = (statMap[uid].gwPts[gw] || 0) + d.awardedPoints;
      }

      let statBatch  = db.batch();
      let statOps    = 0;
      for (const [uid, stats] of Object.entries(statMap)) {
        const bestGwScore = Object.values(stats.gwPts).length
            ? Math.max(...Object.values(stats.gwPts)) : 0;
        statBatch.update(seasonRef.collection("userScores").doc(uid), {
          correctScores: stats.correctScores,
          gamesPlayed:   stats.gamesPlayed,
          bestGwScore,
        });
        statOps++;
        if (statOps >= 450) {
          await statBatch.commit();
          statBatch = db.batch();
          statOps   = 0;
        }
      }
      if (statOps > 0) await statBatch.commit();
      console.log(`Stats enriched for ${Object.keys(statMap).length} users.`);
    } catch (statErr) {
      // Non-fatal — leaderboard still works, just without accuracy stats
      console.warn("Stats enrichment skipped:", statErr.message);
    }

    // ── Archive per-user predictions into seasons/{year}/userPredictions/{uid} ─
    // Each doc is a self-contained snapshot: prediction + actual scores so the
    // app never needs to cross-reference the live fixtures collection.
    try {
      const allPredsSnap = await db.collection("predictions")
          .where("awardedPoints", ">=", 0).get();

      // Group by userId, collect unique fixtureIds
      const predsByUser = {};
      const fixtureIdSet = new Set();
      for (const doc of allPredsSnap.docs) {
        const d = doc.data();
        if (!d.userId) continue;
        if (!predsByUser[d.userId]) predsByUser[d.userId] = [];
        predsByUser[d.userId].push(d);
        if (d.fixtureId) fixtureIdSet.add(d.fixtureId);
      }

      // Fetch actual scores from fixtures in chunks of 30
      const fixtureMap = {};
      const fixtureIdArr = Array.from(fixtureIdSet);
      for (let i = 0; i < fixtureIdArr.length; i += 30) {
        const chunk = fixtureIdArr.slice(i, i + 30);
        const fSnap = await db.collection("fixtures")
            .where(admin.firestore.FieldPath.documentId(), "in", chunk).get();
        for (const fDoc of fSnap.docs) fixtureMap[fDoc.id] = fDoc.data();
      }

      // Build a userId→fullName lookup from the already-fetched sortedUsers list
      const nameMap = {};
      for (const u of sortedUsers) nameMap[u.id] = u.fullName || "";

      let predBatch = db.batch();
      let predOps   = 0;

      for (const [uid, preds] of Object.entries(predsByUser)) {
        const entries = preds.map(p => {
          const fx = fixtureMap[p.fixtureId] || {};
          return {
            fixtureId:     p.fixtureId    || "",
            homeTeam:      p.homeTeam     || fx.homeTeam  || "",
            awayTeam:      p.awayTeam     || fx.awayTeam  || "",
            predictedHome: p.homeTeamGoals ?? -1,
            predictedAway: p.awayTeamGoals ?? -1,
            actualHome:    fx.homeTeamGoals ?? -1,
            actualAway:    fx.awayTeamGoals ?? -1,
            awardedPoints: p.awardedPoints  ?? 0,
            gameweek:      p.gameweek || fx.gameweek || 0,
          };
        }).sort((a, b) => a.gameweek - b.gameweek || a.homeTeam.localeCompare(b.homeTeam));

        predBatch.set(seasonRef.collection("userPredictions").doc(uid), {
          userId:      uid,
          fullName:    nameMap[uid] || "",
          totalScore:  sortedUsers.find(u => u.id === uid)?.score || 0,
          predictions: entries,
          archivedAt:  admin.firestore.FieldValue.serverTimestamp(),
        });
        predOps++;
        if (predOps >= 450) {
          await predBatch.commit();
          predBatch = db.batch();
          predOps   = 0;
        }
      }
      if (predOps > 0) await predBatch.commit();
      console.log(`Archived predictions for ${Object.keys(predsByUser).length} users.`);
    } catch (predErr) {
      console.warn("Prediction archiving skipped:", predErr.message);
    }

    console.log(`Season ${year} reset: ${archived} users archived and reset.`);
    return res.status(200).json({ message: `Season ${year} archived and reset.`, usersReset: archived });
  } catch (e) {
    console.error("resetSeason error:", e);
    return res.status(500).json({ error: e.message });
  }
});

// Backfill: archives predictions into seasons/{year}/userPredictions/{uid} for a
// season that was reset before prediction-archiving was added. Safe to re-run.
exports.backfillSeasonPredictions = functions
  .runWith({ timeoutSeconds: 540, memory: "512MB" })
  .https.onRequest(async (req, res) => {
  try {
    await verifyAdmin(req);
    const yearParam  = req.query.year || new Date().getFullYear().toString();
    const seasonRef  = db.collection("seasons").doc(yearParam);
    const seasonSnap = await seasonRef.get();
    if (!seasonSnap.exists) return res.status(404).json({ error: `seasons/${yearParam} not found` });

    // Fetch all scored predictions
    const allPredsSnap = await db.collection("predictions")
        .where("awardedPoints", ">=", 0).get();

    const predsByUser  = {};
    const fixtureIdSet = new Set();
    for (const doc of allPredsSnap.docs) {
      const d = doc.data();
      if (!d.userId) continue;
      if (!predsByUser[d.userId]) predsByUser[d.userId] = [];
      predsByUser[d.userId].push(d);
      if (d.fixtureId) fixtureIdSet.add(d.fixtureId);
    }

    // Fetch fixture actual scores in chunks of 30
    const fixtureMap   = {};
    const fixtureIdArr = Array.from(fixtureIdSet);
    for (let i = 0; i < fixtureIdArr.length; i += 30) {
      const chunk = fixtureIdArr.slice(i, i + 30);
      const fSnap = await db.collection("fixtures")
          .where(admin.firestore.FieldPath.documentId(), "in", chunk).get();
      for (const fDoc of fSnap.docs) fixtureMap[fDoc.id] = fDoc.data();
    }

    // Fetch user names from the existing userScores subcollection
    const scoresSnap = await seasonRef.collection("userScores").get();
    const nameMap    = {};
    const scoreMap   = {};
    for (const doc of scoresSnap.docs) {
      nameMap[doc.id]  = doc.data().fullName  || "";
      scoreMap[doc.id] = doc.data().score     || 0;
    }

    let predBatch = db.batch();
    let predOps   = 0;

    for (const [uid, preds] of Object.entries(predsByUser)) {
      const entries = preds.map(p => {
        const fx = fixtureMap[p.fixtureId] || {};
        return {
          fixtureId:     p.fixtureId    || "",
          homeTeam:      p.homeTeam     || fx.homeTeam  || "",
          awayTeam:      p.awayTeam     || fx.awayTeam  || "",
          predictedHome: p.homeTeamGoals ?? -1,
          predictedAway: p.awayTeamGoals ?? -1,
          actualHome:    fx.homeTeamGoals ?? -1,
          actualAway:    fx.awayTeamGoals ?? -1,
          awardedPoints: p.awardedPoints  ?? 0,
          gameweek:      p.gameweek || fx.gameweek || 0,
        };
      }).sort((a, b) => a.gameweek - b.gameweek || a.homeTeam.localeCompare(b.homeTeam));

      predBatch.set(seasonRef.collection("userPredictions").doc(uid), {
        userId:      uid,
        fullName:    nameMap[uid]  || "",
        totalScore:  scoreMap[uid] || 0,
        predictions: entries,
        archivedAt:  admin.firestore.FieldValue.serverTimestamp(),
      });
      predOps++;
      if (predOps >= 450) {
        await predBatch.commit();
        predBatch = db.batch();
        predOps   = 0;
      }
    }
    if (predOps > 0) await predBatch.commit();

    return res.status(200).json({
      ok: true, year: yearParam,
      users: Object.keys(predsByUser).length,
      fixtures: Object.keys(fixtureMap).length,
    });
  } catch (e) {
    console.error("backfillSeasonPredictions error:", e);
    return res.status(e.status || 500).json({ error: e.message });
  }
});

// One-time patch: fixes the seasons/{docId} parent document to use correct field types.
// Converts year Int→String, renames topPlayers[].name→fullName, adds "season" label.
// Safe to call multiple times. Delete this function after use.
exports.patchSeasonDoc = functions.https.onRequest(async (req, res) => {
  try {
    await verifyAdmin(req);
    const docId = req.query.doc || "2026";
    const ref   = db.collection("seasons").doc(docId);
    const snap  = await ref.get();
    if (!snap.exists) return res.status(404).json({ error: `seasons/${docId} not found` });

    const d = snap.data();
    const fixedPlayers = (d.topPlayers || []).map(p => ({
      fullName:        p.fullName        || p.name || "",
      score:           p.score           || 0,
      profileImageURL: p.profileImageURL || p.profileImageUrl || "",
    }));

    await ref.update({
      year:       docId,                               // String, not Int
      season:     d.season || d.name || `Season ${docId}`,  // Android reads "season"
      name:       d.name   || `Season ${docId}`,            // iOS reads "name"
      topPlayers: fixedPlayers,
    });

    return res.status(200).json({ ok: true, doc: docId, players: fixedPlayers.length });
  } catch (e) {
    console.error("patchSeasonDoc error:", e);
    return res.status(e.status || 500).json({ error: e.message });
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
  "Portugal", "Brazil",
]);

// Hard cap: never surface more than 6 fixtures in a single gameweek. With 6
// featured nations a group round yields at most 6 games anyway, but this rule
// guarantees it even if the API ever returns extras (e.g. a featured-nation
// replay). When a gameweek is over the cap, the earliest kickoffs are kept.
const WC_MAX_FIXTURES_PER_GW = 6;

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

  // Assign gameweek by competition stage + matchday so GW1 = all Round 1 group
  // games, GW2 = Round 2, GW3 = Round 3, then GW4–8 for knockout stages.
  const wcGameweek = (match) => {
    if (match.stage === "GROUP_STAGE") return match.matchday ?? null;
    // football-data.org names the knockout rounds LAST_32 / LAST_16 (not
    // ROUND_OF_32 / ROUND_OF_16). Map both spellings so a future API change
    // can't silently drop the knockout fixtures again.
    const stageMap = {
      LAST_32: 4, ROUND_OF_32: 4,
      LAST_16: 5, ROUND_OF_16: 5,
      QUARTER_FINALS: 6, SEMI_FINALS: 7, THIRD_PLACE: 8, FINAL: 8,
    };
    return stageMap[match.stage] ?? null;
  };

  const assignedRaw = matches
    .map(match => ({ match, gameweek: wcGameweek(match) }))
    .filter(({ gameweek }) => gameweek != null);

  // Enforce the per-gameweek cap: within each gameweek keep at most
  // WC_MAX_FIXTURES_PER_GW games, preferring the earliest kickoffs.
  const byGwForCap = {};
  for (const item of assignedRaw) {
    (byGwForCap[item.gameweek] ||= []).push(item);
  }
  const assigned = [];
  for (const gw of Object.keys(byGwForCap)) {
    const kept = byGwForCap[gw]
      .sort((a, b) => new Date(a.match.utcDate) - new Date(b.match.utcDate))
      .slice(0, WC_MAX_FIXTURES_PER_GW);
    assigned.push(...kept);
  }

  const cappedOut = assignedRaw.length - assigned.length;
  console.log(`WC 2026: ${matches.length} featured → ${assigned.length} assignable across ${new Set(assigned.map(a => a.gameweek)).size} gameweeks (capped out ${cappedOut} over the ${WC_MAX_FIXTURES_PER_GW}/GW limit)`);

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
      // ?debugStages=true → inspect raw API: what stages exist, and whether the
      // knockout matches have real team names yet or are still TBD/placeholders.
      if (req.query.debugStages === "true") {
        const apiKey = process.env.FOOTBALLDATA_KEY;
        const apiRes = await fetch(
          "https://api.football-data.org/v4/competitions/WC/matches?season=2026",
          { headers: { "X-Auth-Token": apiKey } }
        );
        const data = await apiRes.json();
        const all = data.matches || [];
        const byStage = {};
        for (const m of all) {
          const st = m.stage || "UNKNOWN";
          if (!byStage[st]) byStage[st] = { total: 0, withNamedTeams: 0, sample: [] };
          byStage[st].total++;
          const named = !!(m.homeTeam?.name && m.awayTeam?.name);
          if (named) byStage[st].withNamedTeams++;
          if (byStage[st].sample.length < 4) {
            byStage[st].sample.push({
              home: m.homeTeam?.name ?? null,
              away: m.awayTeam?.name ?? null,
              matchday: m.matchday ?? null,
              status: m.status,
              utcDate: m.utcDate,
            });
          }
        }
        return res.status(200).json({ totalMatches: all.length, byStage });
      }

      const preview = req.query.preview === "true";
      const result  = await doSyncWorldCup(preview);
      res.status(200).json({ success: true, preview, ...result });
    } catch (e) {
      console.error("manualSyncWorldCup error:", e);
      res.status(500).json({ success: false, error: e.message });
    }
  });

// Scheduled: runs every 10 minutes to pick up full-time results quickly.
// One API call per run (fetches all WC2026 matches in a single request), so
// 144 calls/day — well within football-data.org free tier.
// When finished matches are detected, goals are written to Firestore and
// calculatePoints is triggered automatically (see doSyncWorldCup).
// NOTE: PL sync (doSyncFixtures) is disabled — WC 2026 only mode.
// Re-enable doSyncFixtures() when the 2026/27 PL season begins.
exports.syncFootballFixtures = functions
  .runWith({ secrets: ["FOOTBALLDATA_KEY"] })
  .pubsub.schedule("every 10 minutes").onRun(async () => {
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

// ── Admin helper: verify caller is admin ─────────────────────────────────────
const ADMIN_EMAILS = new Set([
  "ugurdenli30@gmail.com",
  "inveniumm@gmail.com",
]);

const verifyAdmin = async (req) => {
  const authHeader = req.headers.authorization || "";
  const idToken = authHeader.replace("Bearer ", "").trim();
  if (!idToken) throw Object.assign(new Error("Missing auth token"), { status: 401 });
  const decoded = await admin.auth().verifyIdToken(idToken);
  if (!ADMIN_EMAILS.has(decoded.email)) {
    // Also check Firestore isAdmin flag
    const userDoc = await db.collection("users").doc(decoded.uid).get();
    if (!userDoc.data()?.isAdmin) {
      throw Object.assign(new Error("Admin only"), { status: 403 });
    }
  }
  return decoded;
};

// ── Reset ALL user tokens ────────────────────────────────────────────────────
exports.adminResetAllTokens = functions.https.onRequest(async (req, res) => {
  cors(req, res, async () => {
    try {
      await verifyAdmin(req);
      const usersSnap = await db.collection("users").get();
      const tokenFields = { wildcardAvailable: true, captainAvailable: true, doubleDownAvailable: true };
      let batch = db.batch();
      let opCount = 0;
      for (const doc of usersSnap.docs) {
        batch.update(doc.ref, tokenFields);
        opCount++;
        if (opCount % 450 === 0) { await batch.commit(); batch = db.batch(); opCount = 0; }
      }
      if (opCount > 0) await batch.commit();
      return res.status(200).json({ message: `Tokens reset for ${usersSnap.size} users.` });
    } catch (e) {
      return res.status(e.status || 500).json({ error: e.message });
    }
  });
});

// ── Reset tokens for a single user (by fullName) ─────────────────────────────
exports.adminResetUserTokens = functions.https.onRequest(async (req, res) => {
  cors(req, res, async () => {
    try {
      await verifyAdmin(req);
      const name = req.body?.name || req.query?.name;
      if (!name) return res.status(400).json({ error: "Missing name" });
      const snap = await db.collection("users").where("fullName", "==", name.trim()).limit(1).get();
      if (snap.empty) return res.status(404).json({ error: `No user named "${name}" found` });
      await snap.docs[0].ref.update({ wildcardAvailable: true, captainAvailable: true, doubleDownAvailable: true });
      return res.status(200).json({ message: `Tokens reset for ${name}.` });
    } catch (e) {
      return res.status(e.status || 500).json({ error: e.message });
    }
  });
});

// ── Points audit for a gameweek ───────────────────────────────────────────────
exports.adminGetPointsAudit = functions.https.onRequest(async (req, res) => {
  cors(req, res, async () => {
    try {
      await verifyAdmin(req);
      const gw = Number(req.query.gameweek);
      if (!Number.isInteger(gw)) return res.status(400).json({ error: "Invalid gameweek" });

      const [predSnap, fixtureSnap] = await Promise.all([
        db.collection("predictions").where("gameweek", "==", gw).get(),
        db.collection("fixtures").where("gameweek", "==", gw).get(),
      ]);

      const fixtureMap = {};
      fixtureSnap.forEach(doc => {
        const d = doc.data();
        fixtureMap[doc.id] = { homeTeam: d.homeTeam, awayTeam: d.awayTeam,
          actualHome: d.homeTeamGoals ?? -1, actualAway: d.awayTeamGoals ?? -1 };
      });

      const userIds = [...new Set(predSnap.docs.map(d => d.data().userId).filter(Boolean))];
      const userMap = {};
      for (let i = 0; i < userIds.length; i += 30) {
        const chunk = userIds.slice(i, i + 30);
        const uSnap = await db.collection("users").where(admin.firestore.FieldPath.documentId(), "in", chunk).get();
        uSnap.forEach(d => { userMap[d.id] = d.data().fullName || "Unknown"; });
      }

      const entries = [];
      predSnap.forEach(doc => {
        const p = doc.data();
        const f = fixtureMap[p.fixtureId];
        if (!f || f.actualHome < 0) return;
        entries.push({
          userName: userMap[p.userId] || "Unknown",
          homeTeam: f.homeTeam, awayTeam: f.awayTeam,
          predictedHome: p.homeTeamGoals ?? 0, predictedAway: p.awayTeamGoals ?? 0,
          actualHome: f.actualHome, actualAway: f.actualAway,
          points: p.awardedPoints ?? 0,
          wildcardUsed: p.wildcardUsed ?? false, captainUsed: p.captainUsed ?? false,
        });
      });
      entries.sort((a, b) => b.points - a.points || a.userName.localeCompare(b.userName));
      return res.status(200).json({ entries });
    } catch (e) {
      return res.status(e.status || 500).json({ error: e.message });
    }
  });
});

// ── GW Winner Notification ────────────────────────────────────────────────────
exports.sendGwWinnerNotification = functions.https.onRequest(async (req, res) => {
  cors(req, res, async () => {
    try {
      const gw = Number(req.query.gameweek);
      if (!Number.isInteger(gw)) return res.status(400).json({ error: "Invalid gameweek" });

      // Find who scored the most points this GW
      const predSnap = await db.collection("predictions").where("gameweek", "==", gw).get();
      const totals = {};
      predSnap.forEach(doc => {
        const { userId, awardedPoints } = doc.data();
        if (!userId) return;
        totals[userId] = (totals[userId] || 0) + (awardedPoints || 0);
      });

      const [winnerUid] = Object.entries(totals).sort((a, b) => b[1] - a[1])[0] || [];
      if (!winnerUid) return res.status(404).json({ error: "No predictions found" });

      const winnerDoc = await db.collection("users").doc(winnerUid).get();
      const winnerName = winnerDoc.data()?.fullName || "A player";
      const winnerPts  = totals[winnerUid];

      // Save to gameweekWinners collection
      await db.collection("gameweekWinners").doc(`gw${gw}`).set({
        userId: winnerUid, gameweek: gw, points: winnerPts,
        fullName: winnerName, updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });

      // Notify all users
      const usersSnap = await db.collection("users").where("fcmToken", "!=", null).get();
      const messaging = admin.messaging();
      let sent = 0;
      for (const userDoc of usersSnap.docs) {
        const { fcmToken, pushNotificationsEnabled } = userDoc.data();
        if (pushNotificationsEnabled === false || !fcmToken) continue;
        try {
          await messaging.send({
            token: fcmToken,
            notification: {
              title: `⚡ GW${gw} Winner: ${winnerName}!`,
              body: `${winnerName} topped GW${gw} with ${winnerPts} pts. See the leaderboard!`,
            },
            android: { priority: "high" }
          });
          sent++;
        } catch (e) {
          if (e.code === "messaging/registration-token-not-registered") {
            await db.collection("users").doc(userDoc.id).update({ fcmToken: admin.firestore.FieldValue.delete() });
          }
        }
      }
      return res.status(200).json({ winner: winnerName, points: winnerPts, notified: sent });
    } catch (e) {
      console.error("sendGwWinnerNotification error:", e);
      return res.status(e.status || 500).json({ error: e.message });
    }
  });
});

// ── Sharpshooter leaderboard (any authenticated user) ────────────────────────
// GET — returns users ranked by correct-score count (awardedPoints >= 3)
exports.getSharpshooterLeaderboard = functions.https.onRequest(async (req, res) => {
  cors(req, res, async () => {
    try {
      const authHeader = req.headers.authorization || "";
      const idToken = authHeader.replace("Bearer ", "").trim();
      if (!idToken) return res.status(401).json({ error: "Missing auth token" });
      await admin.auth().verifyIdToken(idToken);

      // Count predictions with awardedPoints >= 3 per user
      const predSnap = await db.collection("predictions")
        .where("awardedPoints", ">=", 3).get();

      const counts = {};
      predSnap.forEach(doc => {
        const uid = doc.data().userId;
        if (uid) counts[uid] = (counts[uid] || 0) + 1;
      });

      const userIds = Object.keys(counts);
      if (userIds.length === 0) return res.status(200).json({ entries: [] });

      // Fetch user names + images in chunks of 30
      const userMap = {};
      for (let i = 0; i < userIds.length; i += 30) {
        const chunk = userIds.slice(i, i + 30);
        const uSnap = await db.collection("users")
          .where(admin.firestore.FieldPath.documentId(), "in", chunk).get();
        uSnap.forEach(d => {
          userMap[d.id] = {
            name: d.data().fullName || "Anonymous",
            profileImageUrl: d.data().profileImageUrl || null,
          };
        });
      }

      const entries = userIds
        .map(uid => ({
          userId: uid,
          name: userMap[uid]?.name || "Unknown",
          profileImageUrl: userMap[uid]?.profileImageUrl || null,
          correctScores: counts[uid],
        }))
        .sort((a, b) => b.correctScores - a.correctScores)
        .slice(0, 100);

      return res.status(200).json({ entries });
    } catch (e) {
      console.error("getSharpshooterLeaderboard error:", e);
      return res.status(500).json({ error: e.message });
    }
  });
});

// ── Custom push notification to all users ────────────────────────────────────
exports.sendCustomNotification = functions.https.onRequest(async (req, res) => {
  cors(req, res, async () => {
    try {
      await verifyAdmin(req);
      const { title, body } = req.body || {};
      if (!title || !body) return res.status(400).json({ error: "Missing title or body" });

      const usersSnap = await db.collection("users").where("fcmToken", "!=", null).get();
      const messaging = admin.messaging();
      let sent = 0;
      for (const userDoc of usersSnap.docs) {
        const { fcmToken, pushNotificationsEnabled } = userDoc.data();
        if (pushNotificationsEnabled === false || !fcmToken) continue;
        try {
          await messaging.send({ token: fcmToken, notification: { title, body }, android: { priority: "high" } });
          sent++;
        } catch (e) {
          if (e.code === "messaging/registration-token-not-registered") {
            await db.collection("users").doc(userDoc.id).update({ fcmToken: admin.firestore.FieldValue.delete() });
          }
        }
      }
      return res.status(200).json({ message: `Sent to ${sent} users.` });
    } catch (e) {
      return res.status(e.status || 500).json({ error: e.message });
    }
  });
});

// ── Debug: show all fixtures with homeTeamGoals != -1, or matching team name ─
exports.debugFixtures = functions.https.onRequest(async (req, res) => {
  try {
    await verifyAdmin(req);
    const team   = (req.query.team || "").toLowerCase();
    const gw     = req.query.gw  ? parseInt(req.query.gw) : null;
    const docId  = req.query.id  || null;

    // Single-doc raw dump (for diagnosing field type issues)
    if (docId) {
      const doc = await db.collection("fixtures").doc(docId).get();
      if (!doc.exists) return res.status(404).json({ error: "not found" });
      const raw = doc.data();
      const typed = {};
      for (const [k, v] of Object.entries(raw)) {
        typed[k] = { value: v instanceof admin.firestore.Timestamp ? v.toDate() : v, type: v?.constructor?.name ?? typeof v };
      }
      return res.status(200).json({ id: doc.id, fields: typed });
    }

    const snap = await db.collection("fixtures").get();
    const results = snap.docs
      .map(d => ({ id: d.id, ...d.data() }))
      .filter(d => {
        if (gw !== null) return d.gameweek === gw;
        if (team)        return (d.homeTeam||"").toLowerCase().includes(team) || (d.awayTeam||"").toLowerCase().includes(team);
        return d.homeTeamGoals !== -1 || d.awayTeamGoals !== -1;
      })
      .map(d => ({
        id: d.id, homeTeam: d.homeTeam, awayTeam: d.awayTeam,
        homeTeamGoals: d.homeTeamGoals, awayTeamGoals: d.awayTeamGoals,
        gameweek: d.gameweek, deadline: d.deadline?.toDate?.() || d.deadline,
        hasDeadline: !!d.deadline
      }));
    return res.status(200).json({ count: results.length, fixtures: results });
  } catch (e) {
    return res.status(e.status || 500).json({ error: e.message });
  }
});

// ── One-time: delete fixtures with gameweek 101-104 ──────────────────────────
exports.deleteOldGameweeks = functions
  .runWith({ timeoutSeconds: 300, memory: "256MB" })
  .https.onRequest(async (req, res) => {
  try {
    await verifyAdmin(req);
    const OLD_GWS = [101, 102, 103, 104];
    let totalDeleted = 0;
    for (const gw of OLD_GWS) {
      const snap = await db.collection("fixtures").where("gameweek", "==", gw).get();
      if (snap.empty) { console.log(`GW${gw}: 0 docs`); continue; }
      const batch = db.batch();
      snap.docs.forEach(d => batch.delete(d.ref));
      await batch.commit();
      console.log(`GW${gw}: deleted ${snap.size}`);
      totalDeleted += snap.size;
    }
    return res.status(200).json({ ok: true, deleted: totalDeleted });
  } catch (e) {
    return res.status(e.status || 500).json({ error: e.message });
  }
});
