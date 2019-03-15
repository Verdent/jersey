package org.glassfish.jersey.restclient;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.spi.RestClientBuilderResolver;

/**
 * Created by David Kral.
 */
public class JerseyRestClientBuilderResolver extends RestClientBuilderResolver {
    @Override
    public RestClientBuilder newBuilder() {
        return new RestClientBuilderImpl();
    }
}
