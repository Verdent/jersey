package org.glassfish.jersey.restclient;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

/**
 * Created by David Kral.
 */
public class DefaultResponseExceptionMapper implements ResponseExceptionMapper {
    @Override
    public Throwable toThrowable(Response response) {
        return new WebApplicationException("Unknown error, status code " + response.getStatus(), response);
    }

    @Override
    public int getPriority() {
        return Integer.MAX_VALUE;
    }
}
