package org.opendatamesh.platform.pp.devops.exceptions;

import org.springframework.http.HttpStatus;

public class ResourceConflictException extends DevOpsApiException {
    public ResourceConflictException(String message) {
        super(message);
    }

    public ResourceConflictException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
