package com.ktb.moyeota.domain.congestion.repository;

import com.ktb.moyeota.domain.congestion.document.CongestionLatest;
import com.ktb.moyeota.domain.congestion.document.Spot;
import java.time.Instant;
import java.util.Collection;
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

    public void markAnalyzing(Long spotId, Instant windowEnd) {
        mongoTemplate.updateFirst(new Query(Criteria.where("_id").is(spotId)),
                new Update().set("pending_window_end", Date.from(windowEnd)), Spot.class);
    }

    public Optional<Spot> findById(Long spotId) {
        return Optional.ofNullable(mongoTemplate.findById(spotId, Spot.class));
    }

    public List<Spot> findAllById(Collection<Long> spotIds) {
        return mongoTemplate.find(new Query(Criteria.where("_id").in(spotIds)), Spot.class);
    }

    public boolean applyLatest(Long spotId, CongestionLatest latest) {
        Update update = new Update()
                .set("status", latest.status())
                .set("congestion_level", latest.congestionLevel())
                .set("causes", latest.causes())
                .set("summary", latest.summary())
                .set("report_count", latest.reportCount())
                .set("calculated_at", Date.from(latest.calculatedAt()))
                .set("valid_until", Date.from(latest.validUntil()));
        return updateIfNewer(spotId, latest.calculatedAt(), update);
    }

    public boolean advanceCalculatedAt(Long spotId, Instant calculatedAt) {
        return updateIfNewer(spotId, calculatedAt, new Update().set("calculated_at", Date.from(calculatedAt)));
    }

    private boolean updateIfNewer(Long spotId, Instant calculatedAt, Update update) {
        Query query = new Query(Criteria.where("_id").is(spotId)
                .and("calculated_at").not().gte(Date.from(calculatedAt)));
        return mongoTemplate.updateFirst(query, update, Spot.class).getModifiedCount() == 1;
    }
}
