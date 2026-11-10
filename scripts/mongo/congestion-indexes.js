db.spots.createIndex({ location: "2dsphere" }, { name: "spots_location_2dsphere" });
db.spots.createIndex({ last_signal_at: 1 }, { name: "spots_last_signal_at" });
db.signals.createIndex({ post_id: 1, comment_id: 1 }, { name: "signals_post_comment_unique", unique: true });
db.signals.createIndex({ spot_id: 1, created_at: 1 }, { name: "signals_spot_created_at" });
if (!db.getCollectionNames().includes("congestion_history")) {
  db.createCollection("congestion_history", {
    timeseries: { timeField: "calculated_at", metaField: "spot_id", granularity: "minutes" },
    expireAfterSeconds: 604800
  });
}
print("congestion indexes ready");
