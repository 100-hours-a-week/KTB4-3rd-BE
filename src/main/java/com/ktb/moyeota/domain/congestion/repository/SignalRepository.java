package com.ktb.moyeota.domain.congestion.repository;

import com.ktb.moyeota.domain.congestion.document.Signal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SignalRepository {

    private final MongoTemplate mongoTemplate;

    public boolean insertIfAbsent(Signal signal) {
        try {
            mongoTemplate.insert(signal);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public List<Signal> findInWindow(Long spotId, Instant start, Instant end) {
        Query query = new Query(Criteria.where("spot_id").is(spotId).and("created_at").gte(start).lt(end));
        return mongoTemplate.find(query, Signal.class);
    }

    public List<Signal> findPosts(Collection<Long> postIds) {
        Query query = new Query(Criteria.where("post_id").in(postIds).and("comment_id").is(null));
        return mongoTemplate.find(query, Signal.class);
    }
}
