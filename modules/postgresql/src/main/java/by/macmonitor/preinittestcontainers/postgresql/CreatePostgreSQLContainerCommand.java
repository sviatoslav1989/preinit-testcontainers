package by.macmonitor.preinittestcontainers.postgresql;

import by.macmonitor.preinittestcontainers.CreateJdbcContainerCommand;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

import org.testcontainers.containers.wait.strategy.WaitStrategy;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

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
