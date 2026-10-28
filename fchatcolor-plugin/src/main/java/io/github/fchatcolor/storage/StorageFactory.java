/*
 * Decompiled with CFR 0.152.
 */
package io.github.fchatcolor.storage;

import io.github.fchatcolor.api.storage.ColorStorage;
import io.github.fchatcolor.api.storage.DatabaseConfig;
import io.github.fchatcolor.api.storage.DatabaseType;
import io.github.fchatcolor.storage.JdbcColorStorage;
import java.io.File;
import java.util.logging.Logger;

public final class StorageFactory {
    private StorageFactory() {
    }

    public static ColorStorage create(DatabaseConfig config, File dataFolder, Logger logger, long cacheMillis) {
        if (config.getType() == DatabaseType.MYSQL) {
            logger.info("Usando MySQL " + config.getHost() + ":" + config.getPort() + "/" + config.getDatabase());
        } else {
            logger.info("Usando SQLite en " + config.getSqliteFile());
        }
        return new JdbcColorStorage(config, dataFolder.getAbsolutePath(), logger, cacheMillis);
    }
}

