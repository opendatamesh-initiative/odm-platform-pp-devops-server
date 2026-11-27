package org.opendatamesh.platform.pp.devops.exceptions.client;

/**
 * An answer came back but could not be read (for example invalid JSON), and its HTTP status is not known. It keeps
 * code 500 for callers that branch on the code; messages for users must not present it as an HTTP 500.
 */
public class ClientUnreadableResponseException extends ClientException {

    public ClientUnreadableResponseException(String message, Throwable cause) {
        super(500, message);
        initCause(cause);
    }

    @Override
    public String getMessage() {
        return "unreadable answer: " + getResponseBody();
    }
}
