package ie.intellidesk.service;

import ie.intellidesk.domain.Incident;
import ie.intellidesk.domain.Priority;
import ie.intellidesk.ml.MlClient;
import ie.intellidesk.repo.IncidentRepository;
import ie.intellidesk.web.dto.Dtos;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final int TREND_DAYS = 14;

    private final IncidentRepository incidents;
    private final MlClient ml;

    public DashboardService(IncidentRepository incidents, MlClient ml) {
        this.incidents = incidents;
        this.ml = ml;
    }

    @Transactional(readOnly = true)
    public Dtos.Dashboard build() {
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant trendStart = startOfToday.minus(TREND_DAYS - 1L, ChronoUnit.DAYS);

        List<Dtos.CategoryCount> byCategory = incidents.countByCategory().stream()
                .map(row -> new Dtos.CategoryCount((String) row[0], ((Number) row[1]).longValue()))
                .toList();

        List<Dtos.PriorityCount> byPriority = incidents.countByPriority().stream()
                .map(row -> new Dtos.PriorityCount((Priority) row[0], ((Number) row[1]).longValue()))
                .toList();

        return new Dtos.Dashboard(
                incidents.countOpen(),
                incidents.countOpenCritical(),
                incidents.countResolvedSince(startOfToday),
                incidents.countNeedingTriage(),
                averageResolutionHours(),
                byCategory,
                byPriority,
                trend(trendStart),
                ml.isEnabled());
    }

    /**
     * Computed in Java rather than SQL so the same code runs on Postgres and on the
     * in-memory database the tests use. At service-desk volumes this is nothing; if it
     * ever mattered it would become a materialised daily rollup, not a cleverer query.
     */
    private Double averageResolutionHours() {
        List<Incident> resolved = incidents.findByResolvedAtAfter(Instant.EPOCH);
        if (resolved.isEmpty()) return null;
        double total = 0;
        int counted = 0;
        for (Incident i : resolved) {
            Double hours = i.actualResolutionHours();
            if (hours != null) {
                total += hours;
                counted++;
            }
        }
        return counted == 0 ? null : Math.round((total / counted) * 10.0) / 10.0;
    }

    private List<Dtos.TrendPoint> trend(Instant from) {
        Function<Instant, LocalDate> toDay = instant -> instant.atZone(ZoneOffset.UTC).toLocalDate();

        Map<LocalDate, Long> created = incidents.findByCreatedAtAfter(from).stream()
                .collect(Collectors.groupingBy(i -> toDay.apply(i.getCreatedAt()), Collectors.counting()));

        Map<LocalDate, Long> resolved = incidents.findByResolvedAtAfter(from).stream()
                .collect(Collectors.groupingBy(i -> toDay.apply(i.getResolvedAt()), Collectors.counting()));

        List<Dtos.TrendPoint> points = new ArrayList<>();
        LocalDate day = toDay.apply(from);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        while (!day.isAfter(today)) {
            points.add(new Dtos.TrendPoint(
                    day.toString(),
                    created.getOrDefault(day, 0L),
                    resolved.getOrDefault(day, 0L)));
            day = day.plusDays(1);
        }
        return points;
    }
}
