package io.github.fchatcolor.api.server;

/**
 * Server software / fork el servidor.
 */
public enum ServerPlatform {

    /** No reconocido (o vanilla sin plugin API completa). */
    UNKNOWN,

    /** Vanilla Mojang server. */
    VANILLA,

    /** Plain CraftBukkit. */
    CRAFTBUKKIT,

    /** Spigot. */
    SPIGOT,

    /** Paper and derivatives sin scheduler Folia. */
    PAPER,

    /** Pufferfish (optimized Paper fork). */
    PUFFERFISH,

    /** Purpur (Pufferfish fork). */
    PURPUR,

    /** Folia (regionized multithread Paper). */
    FOLIA,

    /** Canvas (optimized Folia fork). */
    CANVAS;

    /** {@code true} para forks basados en Folia (Folia y Canvas). */
    public boolean isFoliaBased() {
        return this == FOLIA || this == CANVAS;
    }

    /** {@code true} para forks con API de Paper (incluye Folia/Canvas). */
    public boolean isPaperBased() {
        return this == PAPER || this == PUFFERFISH || this == PURPUR
                || this == FOLIA || this == CANVAS;
    }
}
