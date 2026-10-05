package com.skinproof.api.report;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record SharedReportSnapshot(
        String productName,
        String concern,
        LocalDate startDate,
        int durationWeeks,
        Instant createdAt,
        boolean simulated,
        String disclaimer,
        ReportVerdictSnapshot verdict,
        List<ReportCaptureSnapshot> captures) {
}