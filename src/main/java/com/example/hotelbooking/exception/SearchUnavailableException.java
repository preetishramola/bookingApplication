package com.example.hotelbooking.exception;

// Thrown when the embedding model (Amazon Bedrock) can't be reached, so vibe search can't run.
public class SearchUnavailableException extends RuntimeException {
    public SearchUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
