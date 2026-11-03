package com.ktb.moyeota.domain.congestion.repository;

import com.ktb.moyeota.domain.congestion.document.CongestionHistory;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CongestionHistoryRepository {

    private final MongoTemplate mongoTemplate;

    public void append(CongestionHistory history) {
        mongoTemplate.insert(history);
    }

    public List<CongestionHistory> findBySpot(Long spotId, Instant from, Instant to) {
        Query query = new Query(Criteria.where("spot_id").is(spotId)
                .and("calculated_at").gte(from).lt(to))
                .with(Sort.by("calculated_at"));
        Map<Instant, CongestionHistory> byWindow = new LinkedHashMap<>();
        for (CongestionHistory history : mongoTemplate.find(query, CongestionHistory.class)) {
            byWindow.putIfAbsent(history.getCalculatedAt(), history);
        }
        return List.copyOf(byWindow.values());
    }
}
