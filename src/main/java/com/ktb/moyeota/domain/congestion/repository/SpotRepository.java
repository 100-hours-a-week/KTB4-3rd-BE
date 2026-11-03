package com.ktb.moyeota.domain.congestion.repository;

import com.ktb.moyeota.domain.congestion.document.Spot;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SpotRepository {

    private final MongoTemplate mongoTemplate;

    public Optional<Spot> findNearest(double lat, double lng, int radiusMeters) {
        Query query = new Query(Criteria.where("location")
                .near(new GeoJsonPoint(lng, lat))
                .maxDistance(radiusMeters));
        return Optional.ofNullable(mongoTemplate.findOne(query, Spot.class));
    }

    public List<Long> findIdsSignaledSince(Instant since) {
        Query query = new Query(Criteria.where("last_signal_at").gte(since)).with(Sort.by("_id"));
        query.fields().include("_id");
        return mongoTemplate.find(query, Spot.class).stream()
                .map(Spot::getId)
                .toList();
    }

    public void raiseLastSignalAt(Long spotId, Instant signaledAt) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(spotId)),
                new Update().max("last_signal_at", Date.from(signaledAt)), Spot.class);
    }
}
