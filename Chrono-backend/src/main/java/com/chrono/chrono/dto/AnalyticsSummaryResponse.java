package com.chrono.chrono.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AnalyticsSummaryResponse(
        int days,
        LocalDateTime generatedAt,
        long totalPageViews,
        long totalClicks,
        long uniqueVisitors,
        long todayPageViews,
        long todayClicks,
        List<DailyPoint> daily,
        List<PageMetric> topPages,
        List<ClickMetric> topClicks,
        List<ReferrerMetric> referrers
) {
    public record DailyPoint(LocalDate date, long pageViews, long clicks, long uniqueVisitors) {}

    public record PageMetric(String path, String pageTitle, long pageViews, long uniqueVisitors) {}

    public record ClickMetric(String path, String label, long clicks) {}

    public record ReferrerMetric(String referrer, long pageViews) {}
}
