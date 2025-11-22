package com.example.filemanager.domain;

public enum Permission {
    READ(4),
    WRITE(2),
    EXECUTE(1);

    public final int value;

    Permission(int value) {
        this.value = value;
    }
}
