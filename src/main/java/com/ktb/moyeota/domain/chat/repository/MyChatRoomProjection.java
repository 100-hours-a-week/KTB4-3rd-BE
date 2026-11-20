package com.ktb.moyeota.domain.chat.repository;

public interface MyChatRoomProjection extends ChatRoomSummary {

    Long getLastReadSeq();
}
