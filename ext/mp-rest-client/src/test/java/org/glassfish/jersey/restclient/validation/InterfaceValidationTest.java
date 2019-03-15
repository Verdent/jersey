package org.glassfish.jersey.restclient.validation;

import org.glassfish.jersey.restclient.RestClientModel;
import org.junit.Test;

/**
 * @author David Kral
 */
public class InterfaceValidationTest {

    @Test
    public void testValidInterface() {
        RestClientModel.from(CorrectInterface.class);
    }

}
