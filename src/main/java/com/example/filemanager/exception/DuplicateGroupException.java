package com.example.filemanager.exception;

/** Raised when a name that must be unique is already taken. */
public class DuplicateGroupException extends RuntimeException {
    public DuplicateGroupException(String message) {
        super(message);
    }
}
