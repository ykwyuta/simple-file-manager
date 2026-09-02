package com.example.filemanager.exception;

/** Raised when restoring an item whose parent folder is still in the trash. */
public class ParentDeletedException extends RuntimeException {
    public ParentDeletedException(String message) {
        super(message);
    }
}
