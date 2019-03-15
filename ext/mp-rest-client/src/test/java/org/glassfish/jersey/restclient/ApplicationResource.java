package org.glassfish.jersey.restclient;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;

/**
 * Created by David Kral.
 */

@Path("resource")
public interface ApplicationResource {

    @GET
    String getValue();

    @POST
    String postAppendValue(String value);


}
