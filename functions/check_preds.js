process.env.GCLOUD_PROJECT = 'the-big-6ix';
const admin = require('firebase-admin');
if (!admin.apps.length) admin.initializeApp({ projectId: 'the-big-6ix' });
const db = admin.firestore();

async function main() {
  const scoredFixtures = ['wc26_537339', 'wc26_537357', 'wc26_537369'];
  for (const fid of scoredFixtures) {
    const snap = await db.collection('predictions').where('fixtureId', '==', fid).get();
    console.log(`\n=== ${fid} (${snap.size} predictions) ===`);
    snap.forEach(doc => {
      const d = doc.data();
      console.log(`  scored=${d.scoredPoints}  pts=${d.awardedPoints ?? '?'}  reason=${d.scoringReason || 'NONE'}  submittedAt=${d.submittedAt ? 'YES' : 'MISSING'}  goals=${d.homeTeamGoals}-${d.awayTeamGoals}`);
    });
  }
  process.exit(0);
}
main().catch(e => { console.error(e.message); process.exit(1); });
