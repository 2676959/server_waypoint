package _959.server_waypoint;

import org.jspecify.annotations.NonNull;

import java.util.*;

final class CompatibilityChecker {
    public static final MinecraftVersionRange[] COMPATIBLE_VERSIONS =
    //? if =1.21 {
    /*{new MinecraftVersionRange(1, 21, 0, 10)};
     *///?} elif =1.21.11 {
     {new MinecraftVersionRange(1, 21, 11, 11), new MinecraftVersionRange(26, 1, 0, 2)};
     //?} else {
        /*{new MinecraftVersionRange(26, 2, 0, -1)};
    *///?}

    public static boolean isCompatible(String minecraftVersion) {
        MinecraftVersion version = MinecraftVersion.of(minecraftVersion);
        for (MinecraftVersionRange range : COMPATIBLE_VERSIONS) {
            if (range.contains(version)) return true;
        }
        return false;
    }

    @SuppressWarnings("all")
    public static String getSupportedVersions() {
        StringBuilder sb = new StringBuilder();
        int i;
        for (i = 0; i < (COMPATIBLE_VERSIONS.length - 1); i++) {
            sb.append(COMPATIBLE_VERSIONS[i].toString());
            sb.append(", ");
        }
        sb.append(COMPATIBLE_VERSIONS[i].toString());
        return sb.toString();
    }

    public record MinecraftVersionRange(int major, int minor, int minPatch, int maxPatch) {
        public MinecraftVersionRange {
            if (major < 0 || minor < 0 || minPatch < 0) {
                throw new IllegalArgumentException("Version numbers cannot be negative");
            } else if (maxPatch >= 0 && minPatch > maxPatch) {
                throw new IllegalArgumentException("minPatch cannot be greater than maxPatch");
            }
        }

        public boolean contains(MinecraftVersion version) {
            if (maxPatch < 0) {
                return version.major == this.major && version.minor == this.minor
                        && version.patch >= this.minPatch;
            } else {
                return version.major == this.major && version.minor == this.minor
                        && version.patch >= this.minPatch && version.patch <= this.maxPatch;
            }
        }

        @Override
        public @NonNull String toString() {
            if (maxPatch < 0) {
                return minPatch == 0 ? "%s.%s+".formatted(major, minor) : "%s.%s.%s+".formatted(major, minor, minPatch);
            } else {
                String minVersion = minPatch == 0 ? "%s.%s".formatted(major, minor) : "%s.%s.%s".formatted(major, minor, minPatch);
                String maxVersion = "%s.%s.%s".formatted(major, minor, maxPatch);
                return minPatch == maxPatch ? minVersion : minVersion + "-" + maxVersion;
            }
        }
    }

    public record MinecraftVersion(int major, int minor, int patch) implements Comparable<MinecraftVersion> {
        public static MinecraftVersion of(String version) {
            String[] components = version.split("\\.");
            if (components.length == 3) {
                return new MinecraftVersion(Integer.parseInt(components[0]), Integer.parseInt(components[1]), Integer.parseInt(components[2]));
            } else if (components.length == 2) {
                return new MinecraftVersion(Integer.parseInt(components[0]), Integer.parseInt(components[1]), 0);
            } else {
                throw new IllegalArgumentException("%s is not a valid Minecraft version".formatted(version));
            }
        }

        public MinecraftVersion {
            if (major < 0 || minor < 0 || patch < 0) {
                throw new IllegalArgumentException("Version numbers cannot be negative");
            }
        }

        @Override
        public int compareTo(CompatibilityChecker.@NonNull MinecraftVersion other) {
            Objects.requireNonNull(other);

            int result = Integer.compare(major, other.major);
            if (result != 0) {
                return result;
            }

            result = Integer.compare(minor, other.minor);
            if (result != 0) {
                return result;
            }

            result = Integer.compare(patch, other.patch);
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }

            if (!(obj instanceof MinecraftVersion other)) {
                return false;
            }

            return major == other.major
                    && minor == other.minor
                    && patch == other.patch;
        }

        @Override
        public int hashCode() {
            return Objects.hash(major, minor, patch);
        }

        @Override
        public @NonNull String toString() {
            if (patch > 0) {
                return major + "." + minor + "." + patch;
            } else {
                return major + "." + minor;
            }
        }
    }
}
