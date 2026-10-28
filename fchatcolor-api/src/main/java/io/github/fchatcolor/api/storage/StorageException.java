package io.github.fchatcolor.api.storage;

/**
 * Error lanzado when el almacenamiento no puede realizarse
 * (conexión caída, tabla inaccesible, etc.).
 */
public class StorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
