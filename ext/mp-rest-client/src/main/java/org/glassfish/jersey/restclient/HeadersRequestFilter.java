package org.glassfish.jersey.restclient;

import javax.ws.rs.ConstrainedTo;
import javax.ws.rs.RuntimeType;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;

/**
 * Created by David Kral.
 */
@ConstrainedTo(RuntimeType.SERVER)
public class HeadersRequestFilter implements ContainerRequestFilter {

    @Override
    public void filter(ContainerRequestContext requestContext) {
        HeadersContext.compute(() -> HeadersContext.create(requestContext.getHeaders()));
    }

}
