package com.ktb.moyeota.domain.chat.service;

import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.chat.dto.ChatMessageSendRequest;
import com.ktb.moyeota.domain.chat.dto.MessageItem;
import com.ktb.moyeota.domain.chat.entity.ChatRoom;
import com.ktb.moyeota.domain.chat.entity.CompanionParticipant;
import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.image.service.ImageUrlResolver;
import com.ktb.moyeota.domain.user.entity.User;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import(ChatMessageSendService.class)
class ChatMessageSendDuplicateTest {

    @MockitoBean
    private ImageUrlResolver imageUrlResolver;

    @Autowired
    private ChatMessageSendService service;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;
    private Long roomId;
    private Long userId;

    @BeforeEach
    void setUp() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.executeWithoutResult(status -> {
            User sender = persist(user("재전송"));
            Companion companion = persist(companionPost(sender));
            roomId = persist(ChatRoom.create(companion)).getId();
            persist(CompanionParticipant.join(companion, sender));
            userId = sender.getId();
        });
    }

    @AfterEach
    void cleanUp() {
        transactionTemplate.executeWithoutResult(status -> {
            for (String entity : new String[]{"CompanionParticipant", "Message", "ChatRoom", "Companion", "User"}) {
                entityManager.createQuery("delete from " + entity).executeUpdate();
            }
        });
    }

    @Test
    @DisplayName("같은 client_message_id로 다시 보내면 실제 DB에서도 처음 저장한 메시지를 돌려주고 한 건만 남는다")
    void resendReturnsFirstMessage() {
        MessageItem first = service.send(userId, roomId, new ChatMessageSendRequest("dup-1", "처음 보낸 내용")).orElseThrow();

        Optional<MessageItem> resent = service.send(userId, roomId, new ChatMessageSendRequest("dup-1", "다시 보낸 내용"));

        assertThat(resent).get().satisfies(item -> {
            assertThat(item.id()).isEqualTo(first.id());
            assertThat(item.content()).isEqualTo("처음 보낸 내용");
        });
        Long count = transactionTemplate.execute(status -> entityManager
                .createQuery("select count(m) from Message m where m.chatRoom.id = :roomId", Long.class)
                .setParameter("roomId", roomId)
                .getSingleResult());
        assertThat(count).isOne();
    }

    private <T> T persist(T entity) {
        entityManager.persist(entity);
        return entity;
    }
}
