package by.macmonitor.preinittestcontainers.clickhouse;

import by.macmonitor.preinittestcontainers.CreateJdbcContainerCommand;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder(toBuilder = true, setterPrefix = "with")
public class CreateClickHouseContainerCommand extends CreateJdbcContainerCommand {}
