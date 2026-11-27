package org.opendatamesh.platform.pp.devops.exceptions.client;

/**
 * A call that got no HTTP response (connection refused, timeout, unknown host). It keeps code 500 so callers that
 * branch on the code are unchanged; callers that explain the failure to a user can tell it apart from a real 500.
 */
public class ClientTransportException extends ClientException {

    public ClientTransportException(String message, Throwable cause) {
        super(500, message);
        initCause(cause);
    }

    @Override
    public String getMessage() {
        return "no HTTP response: " + getResponseBody();
    }
}
