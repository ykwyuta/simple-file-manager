package com.example.filemanager.exception;

/** Raised when a name that must be unique is already taken. */
public class DuplicateUsernameException extends RuntimeException {
    public DuplicateUsernameException(String message) {
        super(message);
    }
}
