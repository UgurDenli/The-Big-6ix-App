exports.notifyTopScorers = async (db) => {
  const topSnap = await db.collection("users")
    .orderBy("weeklyScore", "desc")
    .limit(3)
    .get();

  for (const doc of topSnap.docs) {
    const user = doc.data();
    await db.collection("adminLogs").add({
      userId: doc.id,
      name: user.fullName || "Unknown",
      score: user.weeklyScore,
      event: "Top Scorer Notification Logged",
      timestamp: admin.firestore.FieldValue.serverTimestamp()
    });
    // Integrate push/email service here if needed
  }
};
