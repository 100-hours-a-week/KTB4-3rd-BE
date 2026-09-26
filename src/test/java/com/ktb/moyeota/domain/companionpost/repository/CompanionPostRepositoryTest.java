package com.ktb.moyeota.domain.companionpost.repository;

import static com.ktb.moyeota.domain.companion.entity.CompanionStatus.RECRUITING;
import static com.ktb.moyeota.fixture.CompanionFixture.companionPost;
import static com.ktb.moyeota.fixture.CompanionFixture.taxiPot;
import static com.ktb.moyeota.fixture.UserFixture.user;
import static org.assertj.core.api.Assertions.assertThat;

import com.ktb.moyeota.domain.companion.entity.Companion;
import com.ktb.moyeota.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

@DataJpaTest
class CompanionPostRepositoryTest {

    @Autowired
    private CompanionPostRepository companionPostRepository;

    @Autowired
    private TestEntityManager entityManager;

    private User host;

    @BeforeEach
    void setUp() {
        host = entityManager.persist(user("작성자"));
    }

    @Test
    @DisplayName("동행모집 게시글은 id로 찾는다")
    void findsCompanionPost() {
        Companion post = entityManager.persistAndFlush(companionPost(host));

        assertThat(companionPostRepository.findCompanionPostById(post.getId())).isPresent();
    }

    @Test
    @DisplayName("택시팟 id로는 찾지 않는다")
    void ignoresTaxiPot() {
        Companion taxiPot = entityManager.persistAndFlush(taxiPot(host, RECRUITING));

        assertThat(companionPostRepository.findCompanionPostById(taxiPot.getId())).isEmpty();
    }
}
