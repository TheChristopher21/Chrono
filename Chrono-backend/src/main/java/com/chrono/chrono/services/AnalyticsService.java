package com.chrono.chrono.services;

import com.chrono.chrono.dto.AnalyticsExcludedIpRequest;
import com.chrono.chrono.dto.AnalyticsExcludedIpResponse;
import com.chrono.chrono.dto.AnalyticsEventRequest;
import com.chrono.chrono.dto.AnalyticsSummaryResponse;
import com.chrono.chrono.entities.AnalyticsExcludedIp;
import com.chrono.chrono.entities.AnalyticsEvent;
import com.chrono.chrono.repositories.AnalyticsExcludedIpRepository;
import com.chrono.chrono.repositories.AnalyticsEventRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class AnalyticsService {

    private static final String EVENT_PAGEVIEW = "pageview";
    private static final String EVENT_CLICK = "click";
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 90;
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$"
    );

    private final AnalyticsEventRepository analyticsEventRepository;
    private final AnalyticsExcludedIpRepository analyticsExcludedIpRepository;
    private final Set<String> excludedIpAddresses;

    public AnalyticsService(
            AnalyticsEventRepository analyticsEventRepository,
            AnalyticsExcludedIpRepository analyticsExcludedIpRepository,
            @Value("${app.analytics.excluded-ips:}") String excludedIps
    ) {
        this.analyticsEventRepository = analyticsEventRepository;
        this.analyticsExcludedIpRepository = analyticsExcludedIpRepository;
        this.excludedIpAddresses = parseExcludedIps(excludedIps);
    }

    public void recordEvent(AnalyticsEventRequest request, HttpServletRequest servletRequest) {
        if (request == null
                || isExcludedIp(servletRequest)
                || isBot(servletRequest.getHeader("User-Agent"))) {
            return;
        }

        String eventType = normalizeEventType(request.getEventType());
        String visitorId = clean(request.getVisitorId(), 64);
        String path = cleanPath(request.getPath());

        if (visitorId == null || path == null) {
            return;
        }

        AnalyticsEvent event = new AnalyticsEvent();
        event.setEventType(eventType);
        event.setVisitorId(visitorId);
        event.setSessionId(clean(request.getSessionId(), 64));
        event.setPath(path);
        event.setPageTitle(clean(request.getPageTitle(), 256));
        event.setReferrer(clean(request.getReferrer(), 512));
        event.setReferrerHost(extractReferrerHost(request.getReferrer()));
        event.setElementLabel(EVENT_CLICK.equals(eventType) ? clean(request.getElementLabel(), 160) : null);
        event.setElementTarget(EVENT_CLICK.equals(eventType) ? clean(request.getElementTarget(), 512) : null);
        event.setLanguage(clean(request.getLanguage(), 32));
        event.setViewportWidth(safeDimension(request.getViewportWidth()));
        event.setViewportHeight(safeDimension(request.getViewportHeight()));
        event.setCreatedAt(LocalDateTime.now());

        analyticsEventRepository.save(event);
    }

    public AnalyticsSummaryResponse getSummary(int requestedDays) {
        int days = Math.max(MIN_DAYS, Math.min(MAX_DAYS, requestedDays));
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(days - 1L);
        LocalDateTime start = startDate.atStartOfDay();
        List<AnalyticsEvent> events = analyticsEventRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(start);

        Map<LocalDate, DailyAccumulator> daily = new LinkedHashMap<>();
        for (int offset = 0; offset < days; offset++) {
            daily.put(startDate.plusDays(offset), new DailyAccumulator());
        }

        Map<String, PageAccumulator> pages = new HashMap<>();
        Map<String, ClickAccumulator> clicks = new HashMap<>();
        Map<String, Long> referrers = new HashMap<>();
        Set<String> uniqueVisitors = new HashSet<>();
        long totalPageViews = 0;
        long totalClicks = 0;
        long todayPageViews = 0;
        long todayClicks = 0;

        for (AnalyticsEvent event : events) {
            LocalDate eventDate = event.getCreatedAt().toLocalDate();
            DailyAccumulator dailyPoint = daily.get(eventDate);
            boolean isPageView = EVENT_PAGEVIEW.equals(event.getEventType());
            boolean isClick = EVENT_CLICK.equals(event.getEventType());

            if (event.getVisitorId() != null && isPageView) {
                uniqueVisitors.add(event.getVisitorId());
                if (dailyPoint != null) {
                    dailyPoint.visitors.add(event.getVisitorId());
                }
            }

            if (isPageView) {
                totalPageViews++;
                if (today.equals(eventDate)) {
                    todayPageViews++;
                }
                if (dailyPoint != null) {
                    dailyPoint.pageViews++;
                }
                PageAccumulator page = pages.computeIfAbsent(event.getPath(), PageAccumulator::new);
                page.pageViews++;
                page.visitors.add(event.getVisitorId());
                if (event.getPageTitle() != null) {
                    page.pageTitle = event.getPageTitle();
                }
                referrers.merge(normalizeReferrer(event.getReferrerHost()), 1L, Long::sum);
            } else if (isClick) {
                totalClicks++;
                if (today.equals(eventDate)) {
                    todayClicks++;
                }
                if (dailyPoint != null) {
                    dailyPoint.clicks++;
                }
                String label = event.getElementLabel() != null ? event.getElementLabel() : "Unbenannter Klick";
                String key = event.getPath() + "\u0000" + label;
                ClickAccumulator click = clicks.computeIfAbsent(key, ignored -> new ClickAccumulator(event.getPath(), label));
                click.clicks++;
            }
        }

        List<AnalyticsSummaryResponse.DailyPoint> dailyPoints = daily.entrySet().stream()
                .map(entry -> new AnalyticsSummaryResponse.DailyPoint(
                        entry.getKey(),
                        entry.getValue().pageViews,
                        entry.getValue().clicks,
                        entry.getValue().visitors.size()
                ))
                .toList();

        List<AnalyticsSummaryResponse.PageMetric> topPages = pages.values().stream()
                .sorted(Comparator.comparingLong(PageAccumulator::pageViews).reversed())
                .limit(8)
                .map(page -> new AnalyticsSummaryResponse.PageMetric(
                        page.path,
                        page.pageTitle,
                        page.pageViews,
                        page.visitors.size()
                ))
                .toList();

        List<AnalyticsSummaryResponse.ClickMetric> topClicks = clicks.values().stream()
                .sorted(Comparator.comparingLong(ClickAccumulator::clicks).reversed())
                .limit(8)
                .map(click -> new AnalyticsSummaryResponse.ClickMetric(click.path, click.label, click.clicks))
                .toList();

        List<AnalyticsSummaryResponse.ReferrerMetric> referrerMetrics = referrers.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(6)
                .map(entry -> new AnalyticsSummaryResponse.ReferrerMetric(entry.getKey(), entry.getValue()))
                .toList();

        return new AnalyticsSummaryResponse(
                days,
                LocalDateTime.now(),
                totalPageViews,
                totalClicks,
                uniqueVisitors.size(),
                todayPageViews,
                todayClicks,
                dailyPoints,
                topPages,
                topClicks,
                referrerMetrics
        );
    }

    public List<AnalyticsExcludedIpResponse> getExcludedIps() {
        List<AnalyticsExcludedIpResponse> configured = excludedIpAddresses.stream()
                .sorted()
                .map(ip -> new AnalyticsExcludedIpResponse(null, ip, "Konfiguration", true, null))
                .toList();

        List<AnalyticsExcludedIpResponse> stored = analyticsExcludedIpRepository.findAllByOrderByCreatedAtAsc().stream()
                .filter(entry -> !excludedIpAddresses.contains(entry.getIpAddress()))
                .map(this::toExcludedIpResponse)
                .toList();

        List<AnalyticsExcludedIpResponse> result = new ArrayList<>(configured.size() + stored.size());
        result.addAll(configured);
        result.addAll(stored);
        return result;
    }

    public AnalyticsExcludedIpResponse addExcludedIp(AnalyticsExcludedIpRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "IP-Adresse ist erforderlich");
        }

        String ipAddress = clean(request.getIpAddress(), 45);
        if (!isValidIpAddress(ipAddress)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ungueltige IP-Adresse");
        }

        if (excludedIpAddresses.contains(ipAddress)) {
            return new AnalyticsExcludedIpResponse(null, ipAddress, "Konfiguration", true, null);
        }

        AnalyticsExcludedIp entry = analyticsExcludedIpRepository.findByIpAddress(ipAddress)
                .orElseGet(AnalyticsExcludedIp::new);
        entry.setIpAddress(ipAddress);
        entry.setLabel(clean(request.getLabel(), 120));
        if (entry.getCreatedAt() == null) {
            entry.setCreatedAt(LocalDateTime.now());
        }
        return toExcludedIpResponse(analyticsExcludedIpRepository.save(entry));
    }

    public void removeExcludedIp(Long id) {
        AnalyticsExcludedIp entry = analyticsExcludedIpRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "IP-Filter nicht gefunden"));
        analyticsExcludedIpRepository.delete(entry);
    }

    private String normalizeEventType(String eventType) {
        String cleanType = clean(eventType, 32);
        if (EVENT_CLICK.equals(cleanType)) {
            return EVENT_CLICK;
        }
        return EVENT_PAGEVIEW;
    }

    private String cleanPath(String value) {
        String cleanValue = clean(value, 512);
        if (cleanValue == null) {
            return null;
        }

        if (cleanValue.startsWith("http://") || cleanValue.startsWith("https://")) {
            try {
                URI uri = URI.create(cleanValue);
                String path = uri.getPath();
                return path == null || path.isBlank() ? "/" : clean(path, 512);
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }

        return cleanValue.startsWith("/") ? cleanValue : "/" + cleanValue;
    }

    private String clean(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replaceAll("\\p{Cntrl}", "").trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private Integer safeDimension(Integer value) {
        if (value == null || value <= 0 || value > 10000) {
            return null;
        }
        return value;
    }

    private String extractReferrerHost(String referrer) {
        String cleanReferrer = clean(referrer, 512);
        if (cleanReferrer == null) {
            return null;
        }
        try {
            URI uri = URI.create(cleanReferrer);
            return clean(uri.getHost(), 255);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private String normalizeReferrer(String referrerHost) {
        return referrerHost == null ? "Direkt" : referrerHost;
    }

    private boolean isBot(String userAgent) {
        if (userAgent == null) {
            return false;
        }
        String lower = userAgent.toLowerCase(Locale.ROOT);
        return lower.contains("bot")
                || lower.contains("crawl")
                || lower.contains("spider")
                || lower.contains("slurp")
                || lower.contains("preview")
                || lower.contains("facebookexternalhit")
                || lower.contains("pingdom")
                || lower.contains("uptime")
                || lower.contains("curl")
                || lower.contains("wget");
    }

    private Set<String> parseExcludedIps(String excludedIps) {
        if (excludedIps == null || excludedIps.isBlank()) {
            return Set.of();
        }
        Set<String> parsed = new HashSet<>();
        for (String ip : excludedIps.split(",")) {
            String cleanIp = ip.trim();
            if (!cleanIp.isEmpty()) {
                parsed.add(cleanIp);
            }
        }
        return Collections.unmodifiableSet(parsed);
    }

    private boolean isExcludedIp(HttpServletRequest request) {
        if (excludedIpAddresses.isEmpty()) {
            String clientIp = resolveClientIp(request);
            return clientIp != null && analyticsExcludedIpRepository.existsByIpAddress(clientIp);
        }

        String clientIp = resolveClientIp(request);
        return clientIp != null
                && (excludedIpAddresses.contains(clientIp) || analyticsExcludedIpRepository.existsByIpAddress(clientIp));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    private boolean isValidIpAddress(String ipAddress) {
        if (ipAddress == null) {
            return false;
        }

        if (IPV4_PATTERN.matcher(ipAddress).matches()) {
            return true;
        }

        try {
            return URI.create("http://[" + ipAddress + "]").getHost() != null;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private AnalyticsExcludedIpResponse toExcludedIpResponse(AnalyticsExcludedIp entry) {
        return new AnalyticsExcludedIpResponse(
                entry.getId(),
                entry.getIpAddress(),
                entry.getLabel(),
                false,
                entry.getCreatedAt()
        );
    }

    private static class DailyAccumulator {
        private long pageViews;
        private long clicks;
        private final Set<String> visitors = new HashSet<>();
    }

    private static class PageAccumulator {
        private final String path;
        private String pageTitle;
        private long pageViews;
        private final Set<String> visitors = new HashSet<>();

        private PageAccumulator(String path) {
            this.path = path;
        }

        private long pageViews() {
            return pageViews;
        }
    }

    private static class ClickAccumulator {
        private final String path;
        private final String label;
        private long clicks;

        private ClickAccumulator(String path, String label) {
            this.path = path;
            this.label = label;
        }

        private long clicks() {
            return clicks;
        }
    }
}
