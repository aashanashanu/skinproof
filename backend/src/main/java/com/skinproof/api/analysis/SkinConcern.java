package com.skinproof.api.analysis;

public enum SkinConcern {
    ACNE("acne"),
    AGE_SPOT("age_spot"),
    REDNESS("redness"),
    TEXTURE("texture");

    private final String action;

    SkinConcern(String action) {
        this.action = action;
    }

    public String action() {
        return action;
    }
}