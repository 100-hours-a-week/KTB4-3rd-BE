package com.ktb.moyeota.domain.congestion.repository;

import com.ktb.moyeota.domain.congestion.document.Signal;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.MongoTemplate;
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
}
