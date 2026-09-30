package com.skinproof.api.verdict;

import java.util.List;

public record VerdictRecomputeRequest(List<ConfoundCode> confounds) {
    public VerdictRecomputeRequest {
        confounds = confounds == null ? List.of() : List.copyOf(confounds);
        if (confounds.size() > 12) throw new IllegalArgumentException("At most 12 confound flags may be supplied.");
    }
}