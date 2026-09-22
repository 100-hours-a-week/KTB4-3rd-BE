package com.ktb.moyeota.domain.image.model;

public record UploadScope(Kind kind, String value) {

    private static final int SIGNUP_HASH_PREFIX_LENGTH = 16;

    public enum Kind { SIGNUP, USER }

    public static UploadScope signup(String signupTokenHash) {
        return new UploadScope(Kind.SIGNUP, "s-" + signupTokenHash.substring(0, SIGNUP_HASH_PREFIX_LENGTH));
    }

    public static UploadScope user(Long userId) {
        return new UploadScope(Kind.USER, "u-" + userId);
    }

    public boolean allows(ImagePurpose purpose) {
        return kind == Kind.USER || purpose == ImagePurpose.PROFILE;
    }
}
