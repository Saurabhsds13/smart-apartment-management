package com.societix.common.internal;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the application-wide {@link Clock} bean (UTC).
 *
 * <p>Services that stamp or compare times (e.g. account lockout windows, token expiry) inject this
 * {@link Clock} rather than calling {@code now()} directly, so time-dependent logic is
 * deterministically testable by substituting a fixed clock. The bean is UTC to match the platform's
 * UTC storage convention (Req 23.1). {@link ConditionalOnMissingBean} lets a test context supply a
 * fixed {@link Clock} instead.
 */
@Configuration
public class ClockConfig {

    /**
     * @return a {@link Clock} in UTC backed by the system time source
     */
    @Bean
    @ConditionalOnMissingBean
    public Clock systemClock() {
        return Clock.systemUTC();
    }
}
