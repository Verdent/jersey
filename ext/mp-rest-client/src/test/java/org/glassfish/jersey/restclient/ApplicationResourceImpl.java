package org.glassfish.jersey.restclient;

/**
 * Created by David Kral.
 */
public class ApplicationResourceImpl implements ApplicationResource {
    @Override
    public String getValue() {
        return "This is default value!";
    }

    @Override
    public String postAppendValue(String value) {
        return null;
    }
}
