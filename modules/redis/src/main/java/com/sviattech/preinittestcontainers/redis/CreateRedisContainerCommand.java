package com.sviattech.preinittestcontainers.redis;

import com.sviattech.preinittestcontainers.CreateGenericContainerCommand;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * {@link com.sviattech.preinittestcontainers.CreateContainerCommand} for Redis containers with
 * optional password authentication.
 */
@Getter
@SuperBuilder(toBuilder = true, setterPrefix = "with")
public class CreateRedisContainerCommand extends CreateGenericContainerCommand {

    private final String password;
}
