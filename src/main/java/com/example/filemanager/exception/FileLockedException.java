package com.example.filemanager.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.LOCKED)
public class FileLockedException extends RuntimeException {
    public FileLockedException(String message) {
        super(message);
    }
}
