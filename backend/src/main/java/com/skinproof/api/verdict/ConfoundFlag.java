package com.skinproof.api.verdict;

public record ConfoundFlag(ConfoundCode code, ConfoundSeverity severity) {
    public ConfoundFlag {
        if (code == null) throw new IllegalArgumentException("Confound code is required.");
        if (severity != code.severity()) throw new IllegalArgumentException("Confound severity is determined by its code.");
    }

    public ConfoundFlag(ConfoundCode code) {
        this(code, code.severity());
    }
}