package com.sviattech.preinittestcontainers.metadata;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Dot-separated Docker image tag version for inclusive range matching. Numeric segments compare
 * numerically; trailing non-numeric suffix segments compare lexicographically.
 */
public final class ImageVersion implements Comparable<ImageVersion> {

    private static final ImageVersion LATEST_SENTINEL = new ImageVersion(Collections.emptyList());

    private final List<Segment> segments;

    private ImageVersion(List<Segment> segments) {
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
    }

    @Override
    public int compareTo(ImageVersion other) {
        Objects.requireNonNull(other, "other");
        if (this.isLatestOrEmpty() || other.isLatestOrEmpty()) {
            if (this.isLatestOrEmpty() && other.isLatestOrEmpty()) {
                return 0;
            }
            return this.isLatestOrEmpty() ? 1 : -1;
        }
        int max = Math.max(segments.size(), other.segments.size());
        for (int i = 0; i < max; i++) {
            Segment left = i < segments.size() ? segments.get(i) : null;
            Segment right = i < other.segments.size() ? other.segments.get(i) : null;
            int cmp = Segment.compare(left, right);
            if (cmp != 0) {
                return cmp;
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof ImageVersion)) {
            return false;
        }
        ImageVersion other = (ImageVersion) obj;
        return segments.equals(other.segments);
    }

    @Override
    public int hashCode() {
        return segments.hashCode();
    }

    public boolean isLatestOrEmpty() {
        return this == LATEST_SENTINEL;
    }

    @Override
    public String toString() {
        if (isLatestOrEmpty()) {
            return "latest";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(segments.get(i));
        }
        return sb.toString();
    }

    public static ImageVersion parse(String versionPart) {
        if (versionPart == null
                || versionPart.trim().isEmpty()
                || "latest".equalsIgnoreCase(versionPart.trim())) {
            return LATEST_SENTINEL;
        }
        String[] parts = versionPart.trim().split("\\.");
        List<Segment> parsed = new ArrayList<>(parts.length);
        for (String part : parts) {
            if (part.isEmpty()) {
                throw new IllegalArgumentException(
                        "Invalid version: empty segment in '" + versionPart + "'");
            }
            parsed.add(Segment.parse(part));
        }
        return new ImageVersion(parsed);
    }

    private static final class Segment implements Comparable<Segment> {

        private final Long numericPart;

        private final String suffix;

        private Segment(Long numericPart, String suffix) {
            this.numericPart = numericPart;
            this.suffix = suffix == null ? "" : suffix;
        }

        @Override
        public int compareTo(Segment other) {
            if (numericPart != null && other.numericPart != null) {
                int cmp = Long.compare(numericPart, other.numericPart);
                if (cmp != 0) {
                    return cmp;
                }
            } else if (numericPart != null) {
                return -1;
            } else if (other.numericPart != null) {
                return 1;
            }
            return suffix.compareTo(other.suffix);
        }

        @Override
        public String toString() {
            if (numericPart != null) {
                return numericPart + suffix;
            }
            return suffix;
        }

        static int compare(Segment left, Segment right) {
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return -1;
            }
            if (right == null) {
                return 1;
            }
            return left.compareTo(right);
        }

        static Segment parse(String part) {
            int i = 0;
            while (i < part.length() && Character.isDigit(part.charAt(i))) {
                i++;
            }
            if (i == 0) {
                return new Segment(null, part);
            }
            Long numeric = Long.parseLong(part.substring(0, i));
            String suffix = part.substring(i);
            return new Segment(numeric, suffix);
        }
    }
}
