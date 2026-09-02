package com.example.filemanager.exception;

/** Raised when a file or folder name is empty, too long, or contains path separators. */
public class InvalidNameException extends RuntimeException {
    public InvalidNameException(String message) {
        super(message);
    }
}
