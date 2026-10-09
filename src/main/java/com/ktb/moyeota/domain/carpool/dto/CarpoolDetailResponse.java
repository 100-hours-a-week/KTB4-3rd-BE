package com.ktb.moyeota.domain.carpool.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ktb.moyeota.domain.carpool.model.CarpoolDetail;
import com.ktb.moyeota.domain.carpool.model.CarpoolDetailForViewer;
import com.ktb.moyeota.domain.carpool.model.MyCarpoolRequest;
import java.time.LocalDateTime;
import java.util.List;

public record CarpoolDetailResponse(
        Long id,
        String status,
        Member host,
        String originName,
        String destName,
        LocalDateTime departureAt,
        String carModel,
        int currentCount,
        int capacity,
        Boolean isFull,
        List<Member> participants,
        @JsonInclude(JsonInclude.Include.NON_NULL) MyRequest myRequest) {

    public static CarpoolDetailResponse from(CarpoolDetail detail) {
        return of(detail, null);
    }

    public static CarpoolDetailResponse from(CarpoolDetailForViewer viewed) {
        return of(viewed.detail(), viewed.myRequest().map(MyRequest::from).orElse(null));
    }

    private static CarpoolDetailResponse of(CarpoolDetail detail, MyRequest myRequest) {
        return new CarpoolDetailResponse(
                detail.id(),
                detail.status().name(),
                Member.from(detail.host()),
                detail.originName(),
                detail.destName(),
                detail.departureAt(),
                detail.carModel(),
                detail.currentCount(),
                detail.capacity(),
                detail.full(),
                detail.participants().stream().map(Member::from).toList(),
                myRequest);
    }

    public record Member(Long id, String name, String profileImageUrl) {

        static Member from(CarpoolDetail.Member member) {
            return new Member(member.id(), member.name(), member.profileImageUrl());
        }
    }

    public record MyRequest(Long id, String status) {

        static MyRequest from(MyCarpoolRequest myRequest) {
            return new MyRequest(myRequest.id(), myRequest.status().name());
        }
    }
}
