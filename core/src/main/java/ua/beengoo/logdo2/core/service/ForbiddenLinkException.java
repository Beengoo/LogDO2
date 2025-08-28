package ua.beengoo.logdo2.core.service;

/** Thrown when OAuth callback attempts to link a profile reserved for another Discord user. */
public class ForbiddenLinkException extends RuntimeException {
    public ForbiddenLinkException(String message) { super(message); }
}

