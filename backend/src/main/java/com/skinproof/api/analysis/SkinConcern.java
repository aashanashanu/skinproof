package com.skinproof.api.analysis;

public enum SkinConcern {
    WRINKLE("wrinkle"),
    DROOPY_UPPER_EYELID("droopy_upper_eyelid"),
    DROOPY_LOWER_EYELID("droopy_lower_eyelid"),
    FIRMNESS("firmness"),
    ACNE("acne"),
    MOISTURE("moisture"),
    EYE_BAG("eye_bag"),
    DARK_CIRCLE_V2("dark_circle_v2"),
    AGE_SPOT("age_spot"),
    RADIANCE("radiance"),
    REDNESS("redness"),
    OILINESS("oiliness"),
    PORE("pore"),
    TEXTURE("texture"),
    TEAR_TROUGH("tear_trough"),
    SKIN_TYPE("skin_type");

    private final String action;

    SkinConcern(String action) {
        this.action = action;
    }

    public String action() {
        return action;
    }
}