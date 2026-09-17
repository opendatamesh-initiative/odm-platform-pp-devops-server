package org.opendatamesh.platform.pp.devops.exceptions;

import org.springframework.http.HttpStatus;

public class BadRequestException extends DevOpsApiException {
    public BadRequestException(String message) {
        super(message);
    }

    public BadRequestException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
