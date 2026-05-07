const functions = require("firebase-functions");
const admin = require("firebase-admin");

if (!admin.apps.length) {
  admin.initializeApp();
}

const db = admin.firestore();
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