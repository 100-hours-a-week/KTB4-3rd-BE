package com.ktb.moyeota.domain.notification.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class NotificationListQueryTest {

    @Nested
    @DisplayName("tab")
    class Tab {

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"all", "unread"})
        @DisplayName("tab이 없거나 all, unread면 유효하다")
        void valid(String tab) {
            assertThat(new NotificationListQuery(tab, null).isValidTab()).isTrue();
        }

        @ParameterizedTest
        @ValueSource(strings = {"5", "", " ", "ALL", "Unread", "abc"})
        @DisplayName("all, unread가 아니면 유효하지 않다")
        void invalid(String tab) {
            assertThat(new NotificationListQuery(tab, null).isValidTab()).isFalse();
        }

        @Test
        @DisplayName("read는 읽음 탭으로 판별되고, 유효한 탭이 아니다")
        void read() {
            NotificationListQuery query = new NotificationListQuery("read", null);

            assertThat(query.isReadTab()).isTrue();
            assertThat(query.isValidTab()).isFalse();
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"all", "unread", "5"})
        @DisplayName("read가 아니면 읽음 탭이 아니다")
        void notRead(String tab) {
            assertThat(new NotificationListQuery(tab, null).isReadTab()).isFalse();
        }

        @Test
        @DisplayName("unread일 때만 안읽은 알림만 조회한다")
        void unreadOnly() {
            assertThat(new NotificationListQuery("unread", null).unreadOnly()).isTrue();
            assertThat(new NotificationListQuery("all", null).unreadOnly()).isFalse();
            assertThat(new NotificationListQuery(null, null).unreadOnly()).isFalse();
        }
    }

    @Nested
    @DisplayName("cursor")
    class Cursor {

        @Test
        @DisplayName("cursor가 없으면 유효하고 cursorId는 null이다")
        void nullCursor() {
            NotificationListQuery query = new NotificationListQuery(null, null);

            assertThat(query.isValidCursor()).isTrue();
            assertThat(query.cursorId()).isNull();
        }

        @ParameterizedTest
        @ValueSource(strings = {"1", "881", "9223372036854775807"})
        @DisplayName("1 이상의 Long 범위 숫자면 유효하고 Long으로 변환된다")
        void valid(String cursor) {
            NotificationListQuery query = new NotificationListQuery(null, cursor);

            assertThat(query.isValidCursor()).isTrue();
            assertThat(query.cursorId()).isEqualTo(Long.parseLong(cursor));
        }

        @ParameterizedTest
        @ValueSource(strings = {"abc", "", " ", "0", "-1", "1.5", "99999999999999999999"})
        @DisplayName("숫자가 아니거나 0 이하이거나 Long 범위를 넘으면 유효하지 않다")
        void invalid(String cursor) {
            assertThat(new NotificationListQuery(null, cursor).isValidCursor()).isFalse();
        }
    }
}
