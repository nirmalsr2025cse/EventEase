package com.eventease.dto;

public record ErrorResponse(boolean success, String message, Object data) {
    public static ErrorResponse failure(String message, Object data) {
        return new ErrorResponse(false, message, data);
    }
}
