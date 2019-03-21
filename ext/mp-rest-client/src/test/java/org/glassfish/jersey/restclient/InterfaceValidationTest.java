package org.glassfish.jersey.restclient;

import java.util.ArrayList;
import java.util.HashSet;

import org.junit.Test;

/**
 * @author David Kral
 */
public class InterfaceValidationTest {

    @Test
    public void testValidInterface() {
        RestClientModel.from(CorrectInterface.class, new HashSet<>(), new HashSet<>(), new ArrayList<>());
    }

}
