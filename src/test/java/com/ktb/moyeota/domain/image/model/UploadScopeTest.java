package com.ktb.moyeota.domain.image.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UploadScopeTest {

    @Test
    @DisplayName("회원가입 세션 scope는 토큰 해시 앞 16자를 쓴다")
    void signupScopeUsesHashPrefix() {
        UploadScope scope = UploadScope.signup("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        assertThat(scope.kind()).isEqualTo(UploadScope.Kind.SIGNUP);
        assertThat(scope.value()).isEqualTo("s-0123456789abcdef");
    }

    @Test
    @DisplayName("회원 scope는 사용자 ID를 쓴다")
    void userScopeUsesUserId() {
        UploadScope scope = UploadScope.user(42L);

        assertThat(scope.kind()).isEqualTo(UploadScope.Kind.USER);
        assertThat(scope.value()).isEqualTo("u-42");
    }

    @Test
    @DisplayName("회원가입 세션은 프로필 용도만, 회원은 모든 용도를 올릴 수 있다")
    void signupAllowsOnlyProfile() {
        assertThat(UploadScope.signup("0123456789abcdef").allows(ImagePurpose.PROFILE)).isTrue();
        assertThat(UploadScope.user(42L).allows(ImagePurpose.PROFILE)).isTrue();
    }
}
