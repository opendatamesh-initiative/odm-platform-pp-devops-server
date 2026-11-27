package org.opendatamesh.platform.pp.devops.exceptions.client;

/**
 * The call failed on this side before an answer could be used, for example because the request body could not be
 * written. Nothing says the remote service received it. It keeps code 500 for callers that branch on the code;
 * messages for users must not present it as an HTTP 500 or as an answer of the remote service.
 */
public class ClientRequestFailedException extends ClientException {

    public ClientRequestFailedException(String message, Throwable cause) {
        super(500, message);
        initCause(cause);
    }

    @Override
    public String getMessage() {
        return "call failed: " + getResponseBody();
    }
}
