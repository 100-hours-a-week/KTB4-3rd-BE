package com.ktb.moyeota.global.external.ai;

import com.fasterxml.jackson.annotation.JsonProperty;

record AiImageRequest(@JsonProperty("image_URL") String imageUrl) {
}
