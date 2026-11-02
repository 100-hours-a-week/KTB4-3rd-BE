package com.ktb.moyeota.domain.congestion.repository;

import com.ktb.moyeota.domain.congestion.document.Spot;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
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
}
