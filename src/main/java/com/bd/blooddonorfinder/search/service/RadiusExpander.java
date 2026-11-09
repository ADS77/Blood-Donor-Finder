package com.bd.blooddonorfinder.search.service;

import com.bd.blooddonorfinder.search.util.BloodGroupUtil;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;

@Component
@Slf4j
public class RadiusExpander {
    private final BloodGroupUtil bloodGroupUtil;
    private  final MeterRegistry meterRegistry;

    public RadiusExpander(BloodGroupUtil bloodGroupUtil1, MeterRegistry meterRegistry){
        this.bloodGroupUtil = bloodGroupUtil1;
        this.meterRegistry = meterRegistry;
    }

    private static final int[] COMMON_RADII = {10, 25, 50};
    private static final int[] RARE_RADII   = {25, 50,70};

    public record ExpansionResult<T>(
            List<T> results,
            int radiusUsed,
            boolean expanded,
            boolean noDonorsFound
    ){}


    public <T> ExpansionResult<T> expand(String bloodGroup,
                                         int initialRadius,
                                         Function<Integer, List<T>> searcher) {
        int[] radii = buildRadiiSequence(bloodGroup, initialRadius);

        for (int i = 0; i < radii.length; i++) {
            int radius = radii[i];
            List<T> results = searcher.apply(radius);

            if (!results.isEmpty()) {
                boolean didExpand = i > 0 || radius > initialRadius;
                log.debug("RadiusExpander: found {} results at {}km for group={}", results.size(), radius, bloodGroup);
                return new ExpansionResult<>(results, radius, didExpand, false);
            }

            // Emit expansion metric before trying next radius
            if (i < radii.length - 1) {
                int nextRadius = radii[i + 1];
                log.info("RadiusExpander: 0 results at {}km for group={}, expanding to {}km",
                        radius, bloodGroup, nextRadius);
                meterRegistry.counter("geo.search.radius.expanded",
                        "blood_group", bloodGroup,
                        "from_km", String.valueOf(radius),
                        "to_km", String.valueOf(nextRadius)
                ).increment();
            }
        }

        int lastRadius = radii[radii.length - 1];
        log.warn("RadiusExpander: no donors found at any radius up to {}km for group={}",
                lastRadius, bloodGroup);

        return new ExpansionResult<>(List.of(), lastRadius, radii.length > 1, true);
    }

    private int[] buildRadiiSequence(String bloodGroup, int initialRadius) {
        if (bloodGroupUtil.isRare(bloodGroup)) {
            return RARE_RADII;
        }

        // Common: start at requested radius, add expansion steps above it
        if (initialRadius >= 50) return new int[]{50};
        if (initialRadius >= 25) return new int[]{initialRadius, 50};
        return new int[]{initialRadius, 25, 50};
    }


}
