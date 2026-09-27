package com.ktb.moyeota.domain.user.dto;

import com.ktb.moyeota.domain.user.model.MyProfile;

public record MyProfileResponse(Long id, String nickname, String profileImageUrl, boolean hasBankAccount) {

    public static MyProfileResponse from(MyProfile profile) {
        return new MyProfileResponse(
                profile.id(), profile.nickname(), profile.profileImageUrl(), profile.hasBankAccount());
    }
}
