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

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.enterprise.context.Dependent;
import javax.enterprise.context.spi.CreationalContext;
import javax.enterprise.inject.Default;
import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.BeanManager;
import javax.enterprise.inject.spi.DeploymentException;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.enterprise.inject.spi.PassivationCapable;

import io.helidon.common.CollectionsHelper;
import io.helidon.common.OptionalHelper;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Handles proper rest client injection.
 *
 * Contains information about the rest client interface and extracts additional parameters from
 * config.
 *
 * @author David Kral
 */
class RestClientProducer implements Bean<Object>, PassivationCapable {

    private static final String CONFIG_URL = "/mp-rest/url";
    private static final String CONFIG_URI = "/mp-rest/uri";
    private static final String CONFIG_SCOPE = "/mp-rest/scope";
    private static final String CONFIG_CONNECTION_TIMEOUT = "/mp-rest/connectTimeout";
    private static final String CONFIG_READ_TIMEOUT = "/mp-rest/readTimeout";

    private final RestClientExtension.MpRestClientQualifier qualifier;
    private final BeanManager beanManager;
    private final Class<?> interfaceType;
    private final Class<? extends Annotation> scope;
    private final Config config;
    private final String baseUrl;

    /**
     * Creates new instance of RestClientProducer.
     *
     * @param qualifier qualifier which defines rest client interface
     * @param interfaceType rest client interface
     * @param beanManager bean manager
     */
    RestClientProducer(RestClientExtension.MpRestClientQualifier qualifier,
                       Class<?> interfaceType,
                       BeanManager beanManager) {
        this.qualifier = qualifier;
        this.interfaceType = interfaceType;
        this.beanManager = beanManager;
        this.config = ConfigProvider.getConfig();
        this.baseUrl = getBaseUrl(interfaceType);
        this.scope = resolveProperClientScope();
    }

    private String getBaseUrl(Class<?> interfaceType) {
        return OptionalHelper.from(config.getOptionalValue(interfaceType.getName() + CONFIG_URI, String.class))
                .or(() -> config.getOptionalValue(interfaceType.getName() + CONFIG_URL, String.class))
                .asOptional()
                .orElseGet(() -> {
                    RegisterRestClient registerRestClient = interfaceType.getAnnotation(RegisterRestClient.class);
                    if (registerRestClient != null) {
                        return registerRestClient.baseUri();
                    }
                    throw new DeploymentException("This interface has to be annotated with @RegisterRestClient annotation.");
                });
    }

    @Override
    public Class<?> getBeanClass() {
        return interfaceType;
    }

    @Override
    public Set<InjectionPoint> getInjectionPoints() {
        return CollectionsHelper.setOf();
    }

    @Override
    public boolean isNullable() {
        return false;
    }

    @Override
    public Object create(CreationalContext<Object> creationalContext) {
        try {
            RestClientBuilder restClientBuilder = RestClientBuilder.newBuilder().baseUrl(new URL(baseUrl));
            config.getOptionalValue(interfaceType.getName() + CONFIG_CONNECTION_TIMEOUT, Long.class)
                    .ifPresent(aLong -> restClientBuilder.connectTimeout(aLong, TimeUnit.MILLISECONDS));
            config.getOptionalValue(interfaceType.getName() + CONFIG_READ_TIMEOUT, Long.class)
                    .ifPresent(aLong -> restClientBuilder.readTimeout(aLong, TimeUnit.MILLISECONDS));
            return restClientBuilder.build(interfaceType);
        } catch (MalformedURLException e) {
            throw new IllegalStateException("URL is not in valid format: " + baseUrl);
        }
    }

    @Override
    public void destroy(Object instance, CreationalContext<Object> creationalContext) {
    }

    @Override
    public Set<Type> getTypes() {
        return CollectionsHelper.setOf(interfaceType);
    }

    @Override
    public Set<Annotation> getQualifiers() {
        if (qualifier == null) {
            return CollectionsHelper.setOf(Default.Literal.INSTANCE);
        }
        return CollectionsHelper.setOf(qualifier, RestClient.LITERAL);
    }

    @Override
    public Class<? extends Annotation> getScope() {
        return scope;
    }

    @Override
    public String getName() {
        if (qualifier == null) {
            return interfaceType.getName() + "RestClient";
        }
        return interfaceType.getName();
    }

    @Override
    public Set<Class<? extends Annotation>> getStereotypes() {
        return CollectionsHelper.setOf();
    }

    @Override
    public boolean isAlternative() {
        return false;
    }

    @Override
    public String toString() {
        return "RestClientProducer [ interfaceType: " + interfaceType.getSimpleName()
                + " ] with Qualifiers [" + getQualifiers() + "]";
    }

    @Override
    public String getId() {
        return interfaceType.getName();
    }

    private Class<? extends Annotation> resolveProperClientScope() {
        String configScope = config.getOptionalValue(interfaceType.getName() + CONFIG_SCOPE, String.class).orElse(null);
        if (configScope != null) {
            try {
                return (Class<? extends Annotation>) Class.forName(configScope);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid scope from config: " + configScope, e);
            }
        }

        List<Annotation> possibleScopes = Arrays.stream(interfaceType.getDeclaredAnnotations())
                .filter(annotation -> beanManager.isScope(annotation.annotationType()))
                .collect(Collectors.toList());

        if (possibleScopes.size() == 1) {
            return possibleScopes.get(0).annotationType();
        } else if (possibleScopes.isEmpty()) {
            return Dependent.class;
        } else {
            throw new IllegalArgumentException("Client should have only one scope defined: " +
                                                       interfaceType + " has " + possibleScopes);
        }
    }
}
