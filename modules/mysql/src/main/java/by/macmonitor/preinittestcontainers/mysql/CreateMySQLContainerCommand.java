package by.macmonitor.preinittestcontainers.mysql;

import by.macmonitor.preinittestcontainers.CreateJdbcContainerCommand;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/** {@link by.macmonitor.preinittestcontainers.CreateContainerCommand} for MySQL containers. */
@Getter
@SuperBuilder(toBuilder = true, setterPrefix = "with")
public class CreateMySQLContainerCommand extends CreateJdbcContainerCommand {}
