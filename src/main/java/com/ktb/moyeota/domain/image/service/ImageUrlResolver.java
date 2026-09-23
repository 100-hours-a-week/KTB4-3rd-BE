package com.ktb.moyeota.domain.image.service;

import com.ktb.moyeota.global.external.s3.S3Properties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ImageUrlResolver {

    private final S3Properties s3Properties;

    public String toUrl(String key) {
        if (key == null) {
            return null;
        }
        return s3Properties.publicBaseUrl() + "/" + key;
    }
}
