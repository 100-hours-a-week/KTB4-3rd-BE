package com.ktb.moyeota.domain.carpool.service;

import static com.ktb.moyeota.fixture.CompanionFixture.DEPARTURE_AT;
import static com.ktb.moyeota.fixture.CompanionFixture.carpool;
import static com.ktb.moyeota.fixture.ParticipantFixture.participant;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.chat.entity.Message;
import com.ktb.moyeota.domain.chat.entity.MessageType;
import com.ktb.moyeota.domain.chat.service.ChatSystemMessageService;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.companion.entity.CompanionStatus;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.Gender;
import com.ktb.moyeota.domain.user.entity.User;
import com.ktb.moyeota.global.exception.BusinessException;
import com.ktb.moyeota.global.exception.ErrorCode;
import com.ktb.moyeota.global.external.s3.S3Properties;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@DataJpaTest
@RecordApplicationEvents
@Import({CarpoolRideService.class, CarpoolDetailReader.class, ChatSystemMessageService.class,
        CarpoolRideTestSupport.Config.class})
abstract class CarpoolRideTestSupport {

    @Autowired
    protected CarpoolRideService carpoolRideService;

    @Autowired
    protected TestEntityManager entityManager;

    @Autowired
    protected ApplicationEvents events;

    protected Companion persistCarpool(CompanionStatus status, int currentCount) {
        User host = entityManager.persist(User.register("김방장", "방장닉", Gender.MALE, null));
        Companion carpool = entityManager.persist(carpool(host, status, currentCount));
        entityManager.persist(ChatRoom.create(carpool));
        entityManager.persist(participant(carpool, host, DEPARTURE_AT.minusMinutes(30)));
        if (currentCount > 1) {
            User member = entityManager.persist(User.register("이동승", "동승닉", Gender.FEMALE, null));
            entityManager.persist(participant(carpool, member, DEPARTURE_AT.minusMinutes(20)));
        }
        entityManager.flush();
        return carpool;
    }

    protected Companion reload(Companion carpool) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.find(Companion.class, carpool.getId());
    }

    protected ChatRoom reloadChatRoom(Companion carpool) {
        entityManager.flush();
        entityManager.clear();
        return entityManager.getEntityManager()
                .createQuery("SELECT r FROM ChatRoom r WHERE r.companion.id = :id", ChatRoom.class)
                .setParameter("id", carpool.getId())
                .getSingleResult();
    }

    protected List<CompanionParticipant> participantsOf(Companion carpool) {
        return entityManager.getEntityManager()
                .createQuery("SELECT p FROM CompanionParticipant p WHERE p.companion.id = :id", CompanionParticipant.class)
                .setParameter("id", carpool.getId())
                .getResultList();
    }

    protected List<MessageType> messageTypes(Companion carpool) {
        entityManager.flush();
        return entityManager.getEntityManager()
                .createQuery("SELECT m FROM Message m WHERE m.chatRoom.companion.id = :id ORDER BY m.id", Message.class)
                .setParameter("id", carpool.getId())
                .getResultList()
                .stream()
                .map(Message::getMessageType)
                .toList();
    }

    protected static void assertErrorCode(ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(expected);
    }

    @TestConfiguration
    static class Config {

        @Bean
        Clock clock() {
            ZoneId kst = ZoneId.of("Asia/Seoul");
            return Clock.fixed(DEPARTURE_AT.atZone(kst).toInstant(), kst);
        }

        @Bean
        ImageUrlResolver imageUrlResolver() {
            return new ImageUrlResolver(new S3Properties(
                    "moyeota-test-images", "ap-northeast-2", Duration.ofMinutes(5), "https://cdn.moyeota.test"));
        }
    }
}
