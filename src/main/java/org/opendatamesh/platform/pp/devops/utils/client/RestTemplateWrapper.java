package org.opendatamesh.platform.pp.devops.utils.client;

import org.opendatamesh.platform.pp.devops.exceptions.client.ClientException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientTransportException;
import org.opendatamesh.platform.pp.devops.exceptions.client.ClientUnreadableResponseException;
import org.opendatamesh.platform.pp.devops.utils.client.http.HttpEntity;
import org.opendatamesh.platform.pp.devops.utils.client.http.HttpMethod;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.UnknownContentTypeException;

import java.net.URI;
import java.util.Map;

class RestTemplateWrapper implements RestUtilsTemplate {

    private final RestTemplate restTemplate;

    private RestTemplateWrapper(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public static RestTemplateWrapper wrap(RestTemplate restTemplate) {
        return new RestTemplateWrapper(restTemplate);
    }


    public RestUtils build() {
        RestUtilsTemplate template = this;
        return new BaseRestUtils(template);
    }

    @Override
    public <T> T exchange(String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType, Object... uriVariables) throws ClientException {
        if (uriVariables == null || uriVariables.length == 0) {
            return exchangeLiteral(url, method, requestEntity, responseType);
        }
        try {
            LinkedMultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
            requestEntity.getRawHeaders().forEach(headers::add);
            return restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.valueOf(method.name()),
                    new org.springframework.http.HttpEntity<>(requestEntity.getBody(), headers),
                    responseType,
                    uriVariables
            ).getBody();
        } catch (RestClientException e) {
            throw translate(e);
        }
    }

    @Override
    public <T> T exchange(String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType, Map<String, ?> uriVariables) throws ClientException {
        if (uriVariables == null || uriVariables.isEmpty()) {
            return exchangeLiteral(url, method, requestEntity, responseType);
        }
        try {
            LinkedMultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
            requestEntity.getRawHeaders().forEach(headers::add);
            return restTemplate.exchange(
                    url,
                    org.springframework.http.HttpMethod.valueOf(method.name()),
                    new org.springframework.http.HttpEntity<>(requestEntity.getBody(), headers),
                    responseType,
                    uriVariables
            ).getBody();
        } catch (RestClientException e) {
            throw translate(e);
        }
    }

    private <T> T exchangeLiteral(String url, HttpMethod method, HttpEntity<?> requestEntity, Class<T> responseType) throws ClientException {
        try {
            LinkedMultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
            requestEntity.getRawHeaders().forEach(headers::add);
            return restTemplate.exchange(
                    URI.create(url),
                    org.springframework.http.HttpMethod.valueOf(method.name()),
                    new org.springframework.http.HttpEntity<>(requestEntity.getBody(), headers),
                    responseType
            ).getBody();
        } catch (RestClientException e) {
            throw translate(e);
        }
    }


    /**
     * An HTTP answer keeps its status and body, even when its content cannot be read. Only an I/O failure (connection
     * refused, timeout, unknown host) means there was no answer: {@link ClientTransportException}.
     */
    private static ClientException translate(RestClientException e) {
        if (e instanceof RestClientResponseException response) {
            return new ClientException(response.getStatusCode().value(), response.getResponseBodyAsString());
        }
        if (e instanceof UnknownContentTypeException unreadable) {
            String contentType = unreadable.getContentType() == null ? "" : " (" + unreadable.getContentType() + ")";
            return new ClientException(unreadable.getStatusCode().value(),
                    "Unreadable response" + contentType + ": " + unreadable.getResponseBodyAsString());
        }
        if (e instanceof ResourceAccessException) {
            return new ClientTransportException(e.getMessage(), e);
        }
        // An answer whose body could not be extracted (e.g. invalid JSON): its status is not known here.
        return new ClientUnreadableResponseException(e.getMessage(), e);
    }
}
