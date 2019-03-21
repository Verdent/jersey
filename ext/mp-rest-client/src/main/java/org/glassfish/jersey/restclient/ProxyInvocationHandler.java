package org.glassfish.jersey.restclient;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import javax.ws.rs.client.WebTarget;

/**
 * Invocation handler for interface proxy.
 *
 * @author David Kral
 */
class ProxyInvocationHandler implements InvocationHandler {

    private final WebTarget target;
    private final RestClientModel restClientModel;

    ProxyInvocationHandler(WebTarget target,
                           RestClientModel restClientModel) {
        this.target = target;
        this.restClientModel = restClientModel;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        if (method.getName().contains("toString") && (args == null || args.length == 0)) {
            return restClientModel.toString();
        }
        return restClientModel.invokeMethod(target, method, args);
    }

}
