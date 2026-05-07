const admin = require("firebase-admin");

admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  projectId: "the-big-6ix",
});

const db = admin.firestore();

async function rescoreGW35() {
  const gameweek = 35;
  const predictionsRef = db.collection("predictions");
  const usersRef = db.collection("users");

  const [snapNum, snapStr] = await Promise.all([
    predictionsRef.where("gameweek", "==", gameweek).get(),
    predictionsRef.where("gameweek", "==", String(gameweek)).get(),
  ]);

  const seen = new Set();
  const predDocs = [...snapNum.docs, ...snapStr.docs].filter(d => {
    if (seen.has(d.id)) return false;
    seen.add(d.id);
    return true;
  });

  console.log(`Found ${predDocs.length} predictions for GW35`);

  const fixtureCache = {};
  let rescored = 0;

  for (const predDoc of predDocs) {
    const pred = predDoc.data();
    const fixtureId = pred.fixtureId;
    if (!fixtureId) { console.log("  Skipping - no fixtureId:", predDoc.id); continue; }

    if (!fixtureCache[fixtureId]) {
      const fDoc = await db.collection("fixtures").doc(fixtureId).get();
      fixtureCache[fixtureId] = fDoc.exists ? fDoc.data() : null;
    }
    const fixture = fixtureCache[fixtureId];
    if (!fixture) { console.log(`  Skipping - fixture not found: ${fixtureId}`); continue; }

    const fixtureHome = Number(fixture.homeTeamGoals);
    const fixtureAway = Number(fixture.awayTeamGoals);
    if (isNaN(fixtureHome) || fixtureHome < 0 || isNaN(fixtureAway) || fixtureAway < 0) {
      console.log(`  Skipping - ${fixture.homeTeam} vs ${fixture.awayTeam} goals not set (${fixture.homeTeamGoals}-${fixture.awayTeamGoals})`);
      continue;
    }

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
    console.log(`  ${fixture.homeTeam} vs ${fixture.awayTeam} | actual ${fixtureHome}-${fixtureAway} | user ${pred.userId} predicted ${predHome}-${predAway} → ${newPoints} pts (diff ${diff > 0 ? "+" : ""}${diff})`);

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

  console.log(`\nDone! Rescored ${rescored} predictions.`);
  process.exit(0);
}

rescoreGW35().catch(e => { console.error("Error:", e.message); process.exit(1); });
