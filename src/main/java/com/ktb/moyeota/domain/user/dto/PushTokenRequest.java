package com.ktb.moyeota.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PushTokenRequest(
        @NotBlank
        @Size(max = PushTokenRequest.MAX_LENGTH)
        @Pattern(regexp = "^\\p{ASCII}*$")
        String token
) {

    public static final int MAX_LENGTH = 500;
}
