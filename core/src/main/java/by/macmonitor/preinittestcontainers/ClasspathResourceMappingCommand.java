package by.macmonitor.preinittestcontainers;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder(toBuilder = true, setterPrefix = "with")
public class ClasspathResourceMappingCommand {

    private final String classpathResourcePath;

    private final String containerPath;

    @Builder.Default
    private final int fileMode = 0644;
}
