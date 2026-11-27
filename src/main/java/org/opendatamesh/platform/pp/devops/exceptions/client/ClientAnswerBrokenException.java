package org.opendatamesh.platform.pp.devops.exceptions.client;

/**
 * The remote service started answering, then the answer broke off while its body was read (connection reset, read
 * timeout, premature end of body). The request was received: the remote side may have acted on it.
 */
public class ClientAnswerBrokenException extends ClientTransportException {

    public ClientAnswerBrokenException(String message, Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getMessage() {
        return "answer broke off: " + getResponseBody();
    }
}
