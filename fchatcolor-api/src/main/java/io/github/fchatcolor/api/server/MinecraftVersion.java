package io.github.fchatcolor.api.server;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minecraft version parsed from the server string.
 *
 * <p>Supports classic format (1.8.8, 1.21.4) and year-based versioning
 * (26.1, 26.2.3, etc). Comparison is numeric by (major, minor, patch),
 * so 26.x sorts after any 1.x naturally.</p>
 */
public final class MinecraftVersion implements Comparable<MinecraftVersion> {

    private static final Pattern VERSION_PATTERN = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");

    /** Fallback when nothing could be parsed. */
    public static final MinecraftVersion UNKNOWN = new MinecraftVersion(0, 0, 0, "unknown");

    private final int major;
    private final int minor;
    private final int patch;
    private final String raw;

    private MinecraftVersion(int major, int minor, int patch, String raw) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.raw = raw;
    }

    /**
     * Extracts version from strings like "1.21.4-R0.1-SNAPSHOT",
     * "git-Paper-550" (has no version), "26.1" or "1.8.8-R0.1-SNAPSHOT".
     */
    public static MinecraftVersion parse(String input) {
        if (input == null || input.trim().isEmpty()) {
            return UNKNOWN;
        }
        String clean = input.trim();

        // Strip suffixes like "-R0.1-SNAPSHOT"
        int dash = clean.indexOf('-');
        String withoutSuffix = dash > 0 ? clean.substring(0, dash) : clean;

        Matcher matcher = VERSION_PATTERN.matcher(withoutSuffix);
        if (!matcher.find()) {
            matcher = VERSION_PATTERN.matcher(clean);
            if (!matcher.find()) {
                return new MinecraftVersion(0, 0, 0, clean);
            }
        }

        int major = toInt(matcher.group(1));
        int minor = toInt(matcher.group(2));
        int patch = matcher.group(3) != null ? toInt(matcher.group(3)) : 0;
        return new MinecraftVersion(major, minor, patch, clean);
    }

    private static int toInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    /** Raw string as reported by the server. */
    public String getRaw() {
        return raw;
    }

    /** True if version could not be determined. */
    public boolean isUnknown() {
        return major == 0 && minor == 0 && patch == 0;
    }

    /** Normalized version, e.g. "1.21.4" or "26.1". */
    public String toNormalizedString() {
        return major + "." + minor + (patch > 0 ? "." + patch : "");
    }

    /** Is version >= major.minor? */
    public boolean isAtLeast(int majorAtLeast, int minorAtLeast) {
        return isAtLeast(majorAtLeast, minorAtLeast, 0);
    }

    /** Is version >= major.minor.patch? */
    public boolean isAtLeast(int majorAtLeast, int minorAtLeast, int patchAtLeast) {
        if (major != majorAtLeast) {
            return major > majorAtLeast;
        }
        if (minor != minorAtLeast) {
            return minor > minorAtLeast;
        }
        return patch >= patchAtLeast;
    }

    /**
     * Wildcard for "1.16 or later, or any year-based version (26.x)".
     * Handy fallback when reflection can't confirm a feature.
     */
    public boolean isNewerGeneration() {
        return major > 1 || (major == 1 && minor >= 16);
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        if (major != other.major) {
            return Integer.compare(major, other.major);
        }
        if (minor != other.minor) {
            return Integer.compare(minor, other.minor);
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof MinecraftVersion)) {
            return false;
        }
        MinecraftVersion other = (MinecraftVersion) obj;
        return major == other.major && minor == other.minor && patch == other.patch;
    }

    @Override
    public int hashCode() {
        int result = major;
        result = 31 * result + minor;
        result = 31 * result + patch;
        return result;
    }

    @Override
    public String toString() {
        return toNormalizedString().toLowerCase(Locale.ROOT);
    }
}
