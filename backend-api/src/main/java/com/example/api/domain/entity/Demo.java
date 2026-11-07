package com.example.api.domain.entity;
public record Demo(String message) {
    public Demo {
        if (message == null || message.isBlank()) throw new IllegalArgumentException("Mensaje inválido");
    }
}
