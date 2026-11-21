package com.example.api.domain.exception;

public class DependencyUnavailableException extends RuntimeException {
    public DependencyUnavailableException() { super("Dependencia no disponible"); }
    public DependencyUnavailableException(Throwable cause) { super("Dependencia no disponible", cause); }
}
