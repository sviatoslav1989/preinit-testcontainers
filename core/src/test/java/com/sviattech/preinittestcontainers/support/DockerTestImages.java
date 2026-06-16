package com.sviattech.preinittestcontainers.support;

import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.DockerImageName;

/** Builds ephemeral test images with a stable, per-test repository tag. */
final class DockerTestImages {

    private DockerTestImages() {}

    static DockerImageName build(String repositoryTag, String dockerfile) {
        return DockerImageName.parse(new ImageFromDockerfile(repositoryTag, false)
                .withFileFromString("Dockerfile", dockerfile)
                .get());
    }
}
