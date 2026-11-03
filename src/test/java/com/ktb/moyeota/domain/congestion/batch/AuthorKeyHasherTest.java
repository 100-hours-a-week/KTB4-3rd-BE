package com.ktb.moyeota.domain.congestion.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthorKeyHasherTest {

    private static final String SECRET = "dGVzdC1vbmx5LWNvbmdlc3Rpb24tYXV0aG9yLWtleSE=";
    private static final String OTHER_SECRET = "b3RoZXItb25seS1jb25nZXN0aW9uLWF1dGhvci1rZXk=";

    private final AuthorKeyHasher hasher = new AuthorKeyHasher(SECRET);

    @Test
    @DisplayName("같은 작성자는 늘 같은 가명 키가 되고 실제 ID 는 드러나지 않는다")
    void sameAuthorSameKey() {
        String key = hasher.hash("10");

        assertThat(hasher.hash("10")).isEqualTo(key);
        assertThat(key).hasSize(64).doesNotContain("10");
    }

    @Test
    @DisplayName("다른 작성자는 다른 가명 키가 된다")
    void differentAuthorsDifferentKeys() {
        assertThat(hasher.hash("10")).isNotEqualTo(hasher.hash("11"));
    }

    @Test
    @DisplayName("비밀 키가 다르면 같은 작성자도 다른 가명 키가 된다")
    void secretChangesKey() {
        assertThat(new AuthorKeyHasher(OTHER_SECRET).hash("10")).isNotEqualTo(hasher.hash("10"));
    }

    @Test
    @DisplayName("32바이트가 아닌 비밀 키는 받지 않는다")
    void rejectsShortSecret() {
        assertThatThrownBy(() -> new AuthorKeyHasher("c2hvcnQ=")).isInstanceOf(IllegalStateException.class);
    }
}
