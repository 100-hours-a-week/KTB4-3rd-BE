package com.ktb.moyeota.global.external.ai;

public sealed interface AnalysisOutcome<T> {

    record Read<T>(T result) implements AnalysisOutcome<T> {
    }

    record Unreadable<T>(String code) implements AnalysisOutcome<T> {
    }

    record SystemFailure<T>(String code) implements AnalysisOutcome<T> {
    }
}
