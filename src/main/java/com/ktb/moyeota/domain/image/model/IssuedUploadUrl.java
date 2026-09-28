package com.ktb.moyeota.domain.image.model;

import java.net.URL;

public record IssuedUploadUrl(URL uploadUrl, String imageKey, long expiresIn) {
}
