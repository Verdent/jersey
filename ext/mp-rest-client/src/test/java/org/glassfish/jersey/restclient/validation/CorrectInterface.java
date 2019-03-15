package org.glassfish.jersey.restclient.validation;

import javax.ws.rs.BeanParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

import org.eclipse.microprofile.rest.client.annotation.ClientHeaderParam;

/**
 * Correct test interface for validation
 *
 * @author David Kral
 */

@Path("test/{first}")
public interface CorrectInterface {

    @GET
    @Path("{second}")
    @ClientHeaderParam(name = "test", value = "someValue")
    void firstMethod(@PathParam("first") String first, @PathParam("second") String second);

    @GET
    @ClientHeaderParam(name = "test", value = "{value}")
    void secondMethod(@PathParam("first") String first, String second);

    @POST
    @ClientHeaderParam(name = "test", value = "org.glassfish.jersey.restclient.CustomHeaderGenerator.customHeader")
    void thirdMethod(@PathParam("first") String first);

    @GET
    @Path("{second}")
    void fourthMethod(@PathParam("first") String first, @BeanParam BeanWithPathParam second);

    default String value() {
        return "testValue";
    }

    class CustomHeaderGenerator {

        public static String customHeader() {
            return "static";
        }

    }

    class BeanWithPathParam {

        @PathParam("second")
        public String pathParam;

    }

}
