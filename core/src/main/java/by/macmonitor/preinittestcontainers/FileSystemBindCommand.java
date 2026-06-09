package by.macmonitor.preinittestcontainers;

import lombok.Builder;
import lombok.Getter;

import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.SelinuxContext;

@Getter
@Builder(toBuilder = true, setterPrefix = "with")
public class FileSystemBindCommand {

    @Builder.Default
    private final BindMode bindMode = BindMode.READ_WRITE;

    private final String containerPath;

    private final String hostPath;

    @Builder.Default
    private final SelinuxContext selinuxContext = SelinuxContext.SHARED;
}
