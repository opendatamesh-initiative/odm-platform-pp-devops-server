package org.opendatamesh.platform.pp.devops.exceptions;

import org.springframework.http.HttpStatus;

public class NotImplemented extends DevOpsApiException {
    public NotImplemented(String message) {
        super(message);
    }

    public NotImplemented(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_IMPLEMENTED;
    }
}
