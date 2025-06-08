const functions = require("firebase-functions");
const admin = require("firebase-admin");
const { google } = require("googleapis");
const cors = require("cors")({ origin: true });

admin.initializeApp();
const db = admin.firestore();

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
