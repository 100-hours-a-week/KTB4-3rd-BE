package com.ktb.moyeota.global.common;

import tools.jackson.databind.PropertyNamingStrategies;

public final class SnakeCaseConverter extends PropertyNamingStrategies.SnakeCaseStrategy {

    private static final SnakeCaseConverter INSTANCE = new SnakeCaseConverter();

    private SnakeCaseConverter() {
    }

    public static String convert(String name) {
        return (name == null || name.isBlank()) ? name : INSTANCE.translate(name);
    }

    @Override
    public String translate(String input) {
        return super.translate(input);
    }
}
