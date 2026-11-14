const spots = [
  { _id: 1001, type: "STATION", name: "금토천교", lat: 37.401232, lng: 127.105813 },
  { _id: 1002, type: "STATION", name: "메디포스트", lat: 37.39995, lng: 127.108717 },
  { _id: 1003, type: "STATION", name: "엔씨", lat: 37.399733, lng: 127.108617 },
  { _id: 1004, type: "STATION", name: "H스퀘어.삼환하이펙스", lat: 37.401817, lng: 127.10965 },
  { _id: 1005, type: "STATION", name: "유라코퍼레이션.SK케미칼", lat: 37.403067, lng: 127.107683 },
  { _id: 1006, type: "STATION", name: "엔씨.안랩", lat: 37.399525, lng: 127.109658 },
  { _id: 1007, type: "STATION", name: "봇들육교", lat: 37.403367, lng: 127.107667 },
  { _id: 1008, type: "STATION", name: "네오위즈.NHN", lat: 37.39995, lng: 127.104267 },
  { _id: 1009, type: "STATION", name: "판교환경생태학습원", lat: 37.39975, lng: 127.104083 },
  { _id: 1010, type: "STATION", name: "한국무역정보통신", lat: 37.403083, lng: 127.110983 },
  { _id: 1011, type: "STATION", name: "삼평교", lat: 37.404009, lng: 127.104501 },
  { _id: 1012, type: "STATION", name: "넥슨네트웍스.NHN.네오위즈", lat: 37.400367, lng: 127.10265 },
  { _id: 1013, type: "STATION", name: "넥슨", lat: 37.401683, lng: 127.102617 },
  { _id: 1014, type: "STATION", name: "차그룹컨소시엄", lat: 37.40335, lng: 127.111225 },
  { _id: 1015, type: "STATION", name: "화랑공원동편", lat: 37.397157, lng: 127.10704 },
  { _id: 1016, type: "STATION", name: "SK플래닛.판교디지털센터", lat: 37.403017, lng: 127.10315 },
  { _id: 1017, type: "STATION", name: "이노밸리.포스코DX", lat: 37.403367, lng: 127.103067 },
  { _id: 1018, type: "STATION", name: "동안교", lat: 37.397233, lng: 127.109675 },
  { _id: 1019, type: "STATION", name: "봇들마을5단지", lat: 37.4014, lng: 127.11275 },
  { _id: 1020, type: "STATION", name: "봇들마을5단지.테크노육교", lat: 37.401433, lng: 127.11315 },
  { _id: 1021, type: "STATION", name: "웰츠타워", lat: 37.3977, lng: 127.11275 },
  { _id: 1022, type: "STATION", name: "판교PDCC.NS홈쇼핑", lat: 37.402583, lng: 127.100783 },
  { _id: 1023, type: "STATION", name: "판교호반써밋플레이스", lat: 37.397983, lng: 127.113133 },
  { _id: 1024, type: "STATION", name: "우주공원.코스맥스", lat: 37.40435, lng: 127.10185 },
  { _id: 1025, type: "STATION", name: "판교세븐벤처밸리", lat: 37.401475, lng: 127.100358 },
  { _id: 1026, type: "STATION", name: "화랑공원남편", lat: 37.395386, lng: 127.106552 },
  { _id: 1027, type: "STATION", name: "판교역북편", lat: 37.396208, lng: 127.111275 },
  { _id: 1028, type: "STATION", name: "이노밸리A동.오콘", lat: 37.40295, lng: 127.100517 },
  { _id: 1029, type: "STATION", name: "판교역서편", lat: 37.394972, lng: 127.109733 },
  { _id: 1030, type: "STATION", name: "실리콘파크.삼성중공업", lat: 37.40475, lng: 127.0998 },
  { _id: 1031, type: "STATION", name: "봇들3단지.판교신미주아파트", lat: 37.403883, lng: 127.115617 },
  { _id: 1032, type: "STATION", name: "판교신미주아파트.봇들3단지", lat: 37.404133, lng: 127.115483 },
  { _id: 1033, type: "STATION", name: "판교역동편", lat: 37.395083, lng: 127.112942 },
  { _id: 1034, type: "STATION", name: "봇들마을7.8단지.롯데마트판교점", lat: 37.3962, lng: 127.11445 },
  { _id: 1035, type: "STATION", name: "벌말육교", lat: 37.4021, lng: 127.116833 },
  { _id: 1036, type: "STATION", name: "판교고.송현초.삼평동행정복지센터", lat: 37.405317, lng: 127.115517 },
  { _id: 1037, type: "STATION", name: "지역난방공사", lat: 37.393418, lng: 127.104772 },
  { _id: 1038, type: "STATION", name: "판교역남편", lat: 37.39385, lng: 127.1115 },
  { _id: 1039, type: "STATION", name: "봇들마을7단지", lat: 37.398083, lng: 127.116667 },
  { _id: 1040, type: "STATION", name: "봇들마을9단지", lat: 37.398, lng: 127.11695 },
  { _id: 1041, type: "STATION", name: "낙생지하차도", lat: 37.39295, lng: 127.109658 },
  { _id: 1042, type: "STATION", name: "불꽃교회", lat: 37.396867, lng: 127.097733 },
  { _id: 1043, type: "STATION", name: "보평초.중고(마을)", lat: 37.396583, lng: 127.11695 },
  { _id: 1044, type: "STATION", name: "이지더원.삼평동행정복지센터", lat: 37.406967, lng: 127.115617 }
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
