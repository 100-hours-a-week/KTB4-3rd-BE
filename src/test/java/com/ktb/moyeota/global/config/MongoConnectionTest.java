package com.ktb.moyeota.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.mongodb.core.MongoTemplate;

@DataMongoTest
@Import(MongoTestContainerConfig.class)
class MongoConnectionTest {

    private static final String COLLECTION = "mongo-connection-test";

    @Autowired
    private MongoTemplate mongoTemplate;

    @Test
    @DisplayName("테스트 컨테이너 MongoDB에 쓰고 읽을 수 있다")
    void writesAndReads() {
        mongoTemplate.insert(new Document("_id", "spot-101").append("name", "판교역"), COLLECTION);

        Document found = mongoTemplate.findById("spot-101", Document.class, COLLECTION);

        assertThat(found).isNotNull();
        assertThat(found.getString("name")).isEqualTo("판교역");
    }
}
