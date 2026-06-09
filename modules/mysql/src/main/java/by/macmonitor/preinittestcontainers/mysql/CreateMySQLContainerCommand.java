package by.macmonitor.preinittestcontainers.mysql;

import by.macmonitor.preinittestcontainers.CreateJdbcContainerCommand;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true, setterPrefix = "with")
public class CreateMySQLContainerCommand extends CreateJdbcContainerCommand {}
