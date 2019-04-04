/*
 * Copyright (c) 2019 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package org.glassfish.jersey.restclient;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.client.WebTarget;
import javax.ws.rs.ext.ParamConverterProvider;

import org.eclipse.microprofile.rest.client.ext.AsyncInvocationInterceptorFactory;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;

/**
 * Model of the rest client interface.
 *
 * @author David Kral
 */
class RestClientModel {

    private final InterfaceModel classModel;
    private final Map<Method, MethodModel> methodModels;

    /**
     * Creates new instance of the {@link RestClientModel} base on interface class.
     *
     * @param restClientClass          rest client interface
     * @param responseExceptionMappers registered exception mappers
     * @param paramConverterProviders  registered param converters
     * @param interceptorFactories     registered async interceptor factories
     * @return new instance
     */
    static RestClientModel from(Class<?> restClientClass,
                                Set<ResponseExceptionMapper> responseExceptionMappers,
                                Set<ParamConverterProvider> paramConverterProviders,
                                List<AsyncInvocationInterceptorFactory> interceptorFactories) {
        InterfaceModel interfaceModel = InterfaceModel.from(restClientClass,
                                                            responseExceptionMappers,
                                                            paramConverterProviders,
                                                            interceptorFactories);
        return new Builder()
                .interfaceModel(interfaceModel)
                .methodModels(parseMethodModels(interfaceModel))
                .build();
    }

    private RestClientModel(Builder builder) {
        this.classModel = builder.classModel;
        this.methodModels = builder.methodModels;
    }

    /**
     * Invokes desired rest client method.
     *
     * @param baseWebTarget path to endpoint
     * @param method        desired method
     * @param args          actual method parameters
     * @return method return value
     */
    Object invokeMethod(WebTarget baseWebTarget, Method method, Object[] args) {
        WebTarget classLevelTarget = baseWebTarget.path(classModel.getPath());
        MethodModel methodModel = methodModels.get(method);
        return new InterceptorInvocationContext(classLevelTarget, methodModel, method, args).proceed();
    }

    private static Map<Method, MethodModel> parseMethodModels(InterfaceModel classModel) {
        Map<Method, MethodModel> methodMap = new HashMap<>();
        for (Method method : classModel.getRestClientClass().getMethods()) {
            if (method.isDefault() || Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            //Skip method processing if method does not have HTTP annotation
            //and is not sub resource (does not have Path annotation)
            methodMap.put(method, MethodModel.from(classModel, method));
        }
        return methodMap;
    }

    private static class Builder {

        private InterfaceModel classModel;
        private Map<Method, MethodModel> methodModels;

        private Builder() {
        }

        /**
         * Rest client class converted to {@link InterfaceModel}
         *
         * @param classModel {@link InterfaceModel} instance
         * @return Updated Builder instance
         */
        Builder interfaceModel(InterfaceModel classModel) {
            this.classModel = classModel;
            return this;
        }

        /**
         * Rest client class methods converted to {@link Map} of {@link MethodModel}
         *
         * @param methodModels Method models
         * @return Updated Builder instance
         */
        Builder methodModels(Map<Method, MethodModel> methodModels) {
            this.methodModels = methodModels;
            return this;
        }

        /**
         * Creates new RestClientModel instance.
         *
         * @return new instance
         */
        public RestClientModel build() {
            return new RestClientModel(this);
        }
    }

    @Override
    public String toString() {
        return classModel.getRestClientClass().getName();
    }
}
