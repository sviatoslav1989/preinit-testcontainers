package by.macmonitor.preinittestcontainers;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Getter
@SuperBuilder(toBuilder = true, setterPrefix = "with")
public abstract class CreateJdbcContainerCommand extends CreateGenericContainerCommand {

    /** Nullable: when null, Testcontainers default (120s) is used. */
    private final Integer connectTimeoutSeconds;

    @Builder.Default
    private final String dbName = "test";

    @Builder.Default
    private final List<String> initScripts = Collections.emptyList();

    @Builder.Default
    private final String password = "test";

    /** Nullable: when null, Testcontainers default (120s) is used. */
    private final Integer startupTimeoutSeconds;

    @Builder.Default
    private final Map<String, String> urlParameters = Collections.emptyMap();

    @Builder.Default
    private final String username = "test";
}
