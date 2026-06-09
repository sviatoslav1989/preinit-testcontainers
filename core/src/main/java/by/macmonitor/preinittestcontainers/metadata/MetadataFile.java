package by.macmonitor.preinittestcontainers.metadata;

import by.macmonitor.preinittestcontainers.ContainerMetadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Version-ranged metadata records loaded from a classpath {@code .metadata} properties file. */
public final class MetadataFile {

    private final List<VersionedContainerMetadata> records;

    MetadataFile(List<VersionedContainerMetadata> records) {
        if (records.isEmpty()) {
            throw new IllegalStateException("Metadata file must contain at least one record");
        }
        this.records = Collections.unmodifiableList(new ArrayList<>(records));
    }

    public Optional<ContainerMetadata> resolve(ImageVersion tag) {
        if (tag.isLatestOrEmpty()) {
            return Optional.of(recordWithMaxEndVersion().getMetadata());
        }

        List<VersionedContainerMetadata> exactMatches = new ArrayList<>();
        for (VersionedContainerMetadata record : records) {
            if (record.getStartVersion().compareTo(tag) <= 0
                    && tag.compareTo(record.getEndVersion()) <= 0) {
                exactMatches.add(record);
            }
        }
        if (!exactMatches.isEmpty()) {
            return Optional.of(exactMatches.get(0).getMetadata());
        }

        VersionedContainerMetadata maxRecord = recordWithMaxEndVersion();
        if (tag.compareTo(maxRecord.getEndVersion()) > 0) {
            return Optional.of(maxRecord.getMetadata());
        }

        ImageVersion minStart = records.stream()
                .map(VersionedContainerMetadata::getStartVersion)
                .min(Comparator.<ImageVersion>naturalOrder())
                .get();
        if (tag.compareTo(minStart) < 0) {
            return Optional.empty();
        }

        return Optional.empty();
    }

    private VersionedContainerMetadata recordWithMaxEndVersion() {
        return records.stream()
                .max(Comparator.comparing(VersionedContainerMetadata::getEndVersion))
                .get();
    }
}
