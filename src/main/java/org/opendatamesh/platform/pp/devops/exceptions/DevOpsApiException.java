package org.opendatamesh.platform.pp.devops.exceptions;

import org.springframework.http.HttpStatus;

public abstract class DevOpsApiException extends RuntimeException{

	/**
	 * 
	 */
	private static final long serialVersionUID = 3876573329263306459L;	
	
	public DevOpsApiException() {
		super();
	}

	public DevOpsApiException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}

	public DevOpsApiException(String message, Throwable cause) {
		super(message, cause);
	}

	public DevOpsApiException(String message) {
		super(message);
	}

	public DevOpsApiException(Throwable cause) {
		super(cause);
	}

	/**
	 * @return the errorName
	 */
	public String getErrorName() {
		return getClass().getSimpleName();	
	}

	/**
	 * @return the status
	 */
	public abstract HttpStatus getStatus();	
	

}