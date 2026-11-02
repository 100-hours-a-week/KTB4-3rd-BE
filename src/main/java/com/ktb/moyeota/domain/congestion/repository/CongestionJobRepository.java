package com.ktb.moyeota.domain.congestion.repository;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CongestionJobRepository {

    private static final String COLLECTION = "congestion_jobs";
    private static final String JOB_ID = "window-close";
    private static final String LAST_WINDOW_END = "last_window_end";

    private final MongoTemplate mongoTemplate;

    public Optional<Instant> findLastWindowEnd() {
        Document job = mongoTemplate.findById(JOB_ID, Document.class, COLLECTION);
        return Optional.ofNullable(job)
                .map(document -> document.getDate(LAST_WINDOW_END).toInstant());
    }

    public boolean start(Instant windowEnd) {
        try {
            mongoTemplate.insert(new Document("_id", JOB_ID).append(LAST_WINDOW_END, Date.from(windowEnd)), COLLECTION);
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    public boolean advance(Instant from, Instant to) {
        Query query = new Query(Criteria.where("_id").is(JOB_ID).and(LAST_WINDOW_END).is(Date.from(from)));
        Update update = new Update().set(LAST_WINDOW_END, Date.from(to));
        return mongoTemplate.updateFirst(query, update, COLLECTION).getModifiedCount() == 1;
    }
}
