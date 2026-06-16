package com.sviattech.preinittestcontainers.postgresql;

import com.sviattech.preinittestcontainers.CreateJdbcContainerCommand;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import org.testcontainers.containers.wait.strategy.WaitStrategy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

/**
 * {@link com.sviattech.preinittestcontainers.CreateContainerCommand} for PostgreSQL containers with
 * a module-specific default wait strategy.
 */
@Getter
@SuperBuilder(toBuilder = true, setterPrefix = "with")
public class CreatePostgreSQLContainerCommand extends CreateJdbcContainerCommand {

    public static final WaitStrategy DEFAULT_WAIT_STRATEGY = new PostgreSQLContainerWaitStrategy()
            .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS));

    @Override
    protected WaitStrategy defaultWaitStrategy() {
        return DEFAULT_WAIT_STRATEGY;
    }
}
