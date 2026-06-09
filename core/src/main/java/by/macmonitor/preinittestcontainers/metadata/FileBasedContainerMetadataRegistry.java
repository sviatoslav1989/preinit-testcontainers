package by.macmonitor.preinittestcontainers.metadata;

import by.macmonitor.preinittestcontainers.ContainerMetadata;

import org.testcontainers.utility.DockerImageName;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Classpath-backed registry that loads {@code preinit-testcontainers/metadata/{segment}.metadata}
 * where {@code segment} is the last path component of the image repository (e.g.
 * {@code library/mysql:8} → {@code mysql.metadata}, {@code clickhouse/clickhouse-server:26} →
 * {@code clickhouse-server.metadata}).
 */
public final class FileBasedContainerMetadataRegistry implements ContainerMetadataRegistry {

    private static final String METADATA_PREFIX = "preinit-testcontainers/metadata/";

    private final ConcurrentMap<String, Optional<MetadataFile>> cache = new ConcurrentHashMap<>();

    @Override
    public Optional<ContainerMetadata> find(String imageName) {
        if (imageName == null || imageName.trim().isEmpty()) {
            return Optional.empty();
        }
        DockerImageName image = DockerImageName.parse(imageName);
        return metadataFile(metadataResourcePath(image))
                .flatMap(file -> file.resolve(ImageVersion.parse(image.getVersionPart())));
    }

    private Optional<MetadataFile> metadataFile(String resourcePath) {
        return cache.computeIfAbsent(
                resourcePath, path -> MetadataFileLoader.tryLoad(path, classLoader()));
    }

    static String metadataResourcePath(DockerImageName image) {
        String repository = image.getRepository();
        int lastSlash = repository.lastIndexOf('/');
        String segment = lastSlash >= 0 ? repository.substring(lastSlash + 1) : repository;
        return METADATA_PREFIX + segment + ".metadata";
    }

    private static ClassLoader classLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null
                ? context
                : FileBasedContainerMetadataRegistry.class.getClassLoader();
    }
}
