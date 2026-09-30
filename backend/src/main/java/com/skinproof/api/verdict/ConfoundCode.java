package com.skinproof.api.verdict;

public enum ConfoundCode {
    CAPTURE_GAP(ConfoundSeverity.MINOR),
    QUALITY_DRIFT(ConfoundSeverity.MINOR),
    SUN_EXPOSURE(ConfoundSeverity.MINOR),
    TRAVEL(ConfoundSeverity.MINOR),
    STRESS(ConfoundSeverity.MINOR),
    ILLNESS(ConfoundSeverity.MAJOR),
    NEW_PRODUCT(ConfoundSeverity.MAJOR),
    PROTOCOL_CHANGE(ConfoundSeverity.MAJOR);

    private final ConfoundSeverity severity;

    ConfoundCode(ConfoundSeverity severity) {
        this.severity = severity;
    }

    public ConfoundSeverity severity() {
        return severity;
    }
}