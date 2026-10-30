package com.ktb.moyeota.domain.taxipot.stream;

import com.ktb.moyeota.domain.taxipot.config.TaxiPotMatchingProperties;
import com.ktb.moyeota.domain.taxipot.matching.MatchingRules;
import com.ktb.moyeota.domain.taxipot.message.MatchDecidedData;
import com.ktb.moyeota.global.config.ConditionalOnServerRole;
import com.ktb.moyeota.global.config.ServerRole;
import com.ktb.moyeota.global.event.EventEnvelope;
import com.ktb.moyeota.global.event.EventEnvelopeFactory;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.config.StreamsBuilderFactoryBeanConfigurer;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableKafkaStreams
@EnableConfigurationProperties(TaxiPotMatchingProperties.class)
@ConditionalOnServerRole({ServerRole.WORKER, ServerRole.ALL})
@ConditionalOnProperty(prefix = "moyeota.taxi-pot.matching", name = "mode", havingValue = "kafka")
public class MatchingStreamsConfig {

    private static final int TAXI_POT_CAPACITY = 4;

    @Bean
    public KStream<String, EventEnvelope<MatchDecidedData>> matchingDecisions(
            StreamsBuilder builder, JsonMapper jsonMapper, TaxiPotMatchingProperties properties,
            EventEnvelopeFactory envelopeFactory, Clock clock, MeterRegistry meterRegistry) {
        MatchingRules rules = new MatchingRules(properties.radiusMeters(), TAXI_POT_CAPACITY);
        return MatchingTopology.define(builder, jsonMapper, () -> new RegionMatchingProcessor(rules,
                properties.neighborLimit(), properties.sweepDelay(), clock.getZone(), envelopeFactory, jsonMapper,
                meterRegistry));
    }

    @Bean
    public StreamsBuilderFactoryBeanConfigurer matchingStreamsAvailability(ApplicationEventPublisher publisher) {
        return factoryBean -> factoryBean.setStateListener(new MatchingStreamsAvailability(publisher));
    }
}
