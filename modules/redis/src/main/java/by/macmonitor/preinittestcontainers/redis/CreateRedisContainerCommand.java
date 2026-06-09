package by.macmonitor.preinittestcontainers.redis;

import by.macmonitor.preinittestcontainers.CreateGenericContainerCommand;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true, setterPrefix = "with")
public class CreateRedisContainerCommand extends CreateGenericContainerCommand {

    private final String password;
}
