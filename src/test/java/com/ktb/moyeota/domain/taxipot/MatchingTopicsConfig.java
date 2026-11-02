package com.ktb.moyeota.domain.taxipot;

import com.ktb.moyeota.domain.taxipot.message.MatchingMessages;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;

@TestConfiguration(proxyBeanMethods = false)
class MatchingTopicsConfig {

    @Bean
    NewTopic matchingRequestTopic() {
        return TopicBuilder.name(MatchingMessages.REQUEST_TOPIC).partitions(1).build();
    }

    @Bean
    NewTopic matchingDecisionTopic() {
        return TopicBuilder.name(MatchingMessages.DECISION_TOPIC).partitions(1).build();
    }
}
