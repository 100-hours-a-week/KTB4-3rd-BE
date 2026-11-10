package com.ktb.moyeota.domain.chat.success;

import com.ktb.moyeota.global.common.SuccessCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChatSuccessCode implements SuccessCode {

    PARTICIPANT_CHECKED("채팅방 참여 여부를 확인했습니다"),

    MESSAGE_SENT("채팅 메시지를 저장했습니다");

    private final String message;
}
