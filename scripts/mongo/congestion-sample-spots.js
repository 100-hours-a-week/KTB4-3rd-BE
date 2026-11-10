const spots = [
  { _id: 101, type: "STATION", name: "판교역", lat: 37.3948, lng: 127.1112 },
  { _id: 102, type: "STATION", name: "정자역", lat: 37.3670, lng: 127.1083 },
  { _id: 103, type: "STATION", name: "서현역", lat: 37.3851, lng: 127.1232 },
  { _id: 104, type: "STATION", name: "이매역", lat: 37.3954, lng: 127.1279 },
  { _id: 201, type: "STATION", name: "강남역", lat: 37.4979, lng: 127.0276 },
  { _id: 202, type: "STATION", name: "신논현역", lat: 37.5046, lng: 127.0251 },
  { _id: 203, type: "STATION", name: "역삼역", lat: 37.5006, lng: 127.0364 },
  { _id: 204, type: "STATION", name: "양재역", lat: 37.4841, lng: 127.0346 },
  { _id: 205, type: "STATION", name: "교대역", lat: 37.4934, lng: 127.0141 },
  { _id: 301, type: "STATION", name: "가산디지털단지역", lat: 37.4816, lng: 126.8827 },
  { _id: 302, type: "STATION", name: "구로디지털단지역", lat: 37.4852, lng: 126.9015 },
  { _id: 303, type: "STATION", name: "남구로역", lat: 37.4861, lng: 126.8873 },
  { _id: 304, type: "STATION", name: "독산역", lat: 37.4661, lng: 126.8893 },
  { _id: 401, type: "STATION", name: "마곡나루역", lat: 37.5667, lng: 126.8272 },
  { _id: 402, type: "STATION", name: "마곡역", lat: 37.5602, lng: 126.8254 },
  { _id: 403, type: "STATION", name: "발산역", lat: 37.5585, lng: 126.8376 },
  { _id: 404, type: "STATION", name: "양천향교역", lat: 37.5682, lng: 126.8418 }
];

const result = db.spots.bulkWrite(spots.map((spot) => ({
  updateOne: {
    filter: { _id: spot._id },
    update: {
      $set: {
        type: spot.type,
        name: spot.name,
        location: { type: "Point", coordinates: [spot.lng, spot.lat] }
      }
    },
    upsert: true
  }
})));

print(`spots upserted=${result.upsertedCount} updated=${result.modifiedCount} total=${db.spots.countDocuments()}`);
