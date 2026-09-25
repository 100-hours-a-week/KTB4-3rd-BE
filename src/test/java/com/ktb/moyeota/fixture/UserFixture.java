package com.ktb.moyeota.fixture;

import com.ktb.moyeota.domain.user.entity.Gender;
import com.ktb.moyeota.domain.user.entity.User;

public final class UserFixture {

    private UserFixture() {
    }

    public static User user(String nickname) {
        return User.register("홍길동", nickname, Gender.MALE, null);
    }
}
