exports.resetScores = async (db, scoreField) => {
  const usersSnap = await db.collection("users").get();

  for (const doc of usersSnap.docs) {
    await doc.ref.update({
      [scoreField]: 0
    });
  }

  await db.collection("adminLogs").add({
    event: `Reset ${scoreField}`,
    timestamp: admin.firestore.FieldValue.serverTimestamp()
  });
};
