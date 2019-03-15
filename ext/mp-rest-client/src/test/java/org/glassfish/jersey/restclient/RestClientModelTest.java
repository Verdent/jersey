package org.glassfish.jersey.restclient;

import java.net.URI;
import java.net.URISyntaxException;

import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.junit.Test;


/**
 * Created by David Kral.
 */
public class RestClientModelTest extends JerseyTest {
    @Override
    protected ResourceConfig configure() {
        enable(TestProperties.LOG_TRAFFIC);
        return new ResourceConfig(ApplicationResourceImpl.class);
    }

    @Test
    public void testGetIt() throws URISyntaxException {
        ApplicationResource app = RestClientBuilder.newBuilder()
                .baseUri(new URI("http://localhost:9998"))
                .build(ApplicationResource.class);
        System.out.println(app.getValue());
    }
}
