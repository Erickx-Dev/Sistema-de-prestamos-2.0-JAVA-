package com.credinoda.domain.exception;

public class BackupException extends CrediNodaException {
    public BackupException(String message) {
        super(message);
    }

    public BackupException(String message, Throwable cause) {
        super(message, cause);
    }
}
