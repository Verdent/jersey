package org.glassfish.jersey.restclient;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import javax.ws.rs.Priorities;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Configuration;
import javax.ws.rs.ext.ParamConverterProvider;

import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.ext.AsyncInvocationInterceptor;
import org.eclipse.microprofile.rest.client.ext.AsyncInvocationInterceptorFactory;
import org.eclipse.microprofile.rest.client.ext.ResponseExceptionMapper;
import org.eclipse.microprofile.rest.client.spi.RestClientListener;
import org.glassfish.jersey.client.JerseyClientBuilder;

/**
 * Rest client builder implementation. Creates proxy instance of requested interface.
 *
 * @author David Kral
 */
public class RestClientBuilderImpl implements RestClientBuilder {

    private static final String CONFIG_DISABLE_DEFAULT_MAPPER = "microprofile.rest.client.disable.default.mapper";
    private static final String CONFIG_PROVIDERS = "/mp-rest/providers";
    private static final String PROVIDER_SEPARATOR = ",";

    private final Set<ResponseExceptionMapper> responseExceptionMappers;
    private final Set<ParamConverterProvider> paramConverterProviders;
    private final List<AsyncInvocationInterceptorFactory> asyncInterceptorFactories;
    private final Config config;
    private final ConfigWrapper configWrapper;
    private URI uri;
    private JerseyClientBuilder jerseyClientBuilder;
    private ExecutorService executorService;

    RestClientBuilderImpl() {
        jerseyClientBuilder = new JerseyClientBuilder();
        responseExceptionMappers = new HashSet<>();
        paramConverterProviders = new HashSet<>();
        asyncInterceptorFactories = new ArrayList<>();
        config = ConfigProvider.getConfig();
        configWrapper = new ConfigWrapper(jerseyClientBuilder.getConfiguration());
        executorService = Executors.newCachedThreadPool(); //TODO upravit?? - ClientProperties.ASYNC_THREADPOOL_SIZE
    }

    @Override
    public RestClientBuilder baseUrl(URL url) {
        try {
            this.uri = url.toURI();
            return this;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    @Override
    public RestClientBuilder connectTimeout(long timeout, TimeUnit unit) {
        jerseyClientBuilder.connectTimeout(timeout, unit);
        return this;
    }

    @Override
    public RestClientBuilder readTimeout(long timeout, TimeUnit unit) {
        jerseyClientBuilder.readTimeout(timeout, unit);
        return this;
    }

    @Override
    public RestClientBuilder executorService(ExecutorService executor) {
        if (executor == null) {
            throw new IllegalArgumentException("ExecutorService cannot be null.");
        }
        executorService = executor;
        jerseyClientBuilder.executorService(executor);
        return this;
    }

    @Override
    public <T> T build(Class<T> interfaceClass) throws IllegalStateException, RestClientDefinitionException {

        if (uri == null) {
            throw new IllegalStateException("Base uri/url cannot be null!");
        }

        //Provider registration part
        Object providersFromJerseyConfig = jerseyClientBuilder.getConfiguration()
                .getProperty(interfaceClass.getName() + CONFIG_PROVIDERS);
        if (providersFromJerseyConfig instanceof String && !((String) providersFromJerseyConfig).isEmpty()) {
            String[] providerArray = ((String) providersFromJerseyConfig).split(PROVIDER_SEPARATOR);
            for (String provider : providerArray) {
                try {
                    Class<?> providerClass = Class.forName(provider);
                    register(providerClass);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Class with name " + provider + " not found");
                }
            }
        }
        Optional<String> providersFromConfig = config.getOptionalValue(interfaceClass.getName() + CONFIG_PROVIDERS, String.class);
        if (providersFromConfig.isPresent() && !providersFromConfig.get().isEmpty()) {
            String[] providerArray = providersFromConfig.get().split(PROVIDER_SEPARATOR);
            for (String provider : providerArray) {
                try {
                    Class<?> providerClass = Class.forName(provider);
                    register(providerClass);
                } catch (ClassNotFoundException e) {
                    throw new IllegalStateException("Class with name " + provider + " not found");
                }
            }
        }
        RegisterProvider[] registerProviders = interfaceClass.getAnnotationsByType(RegisterProvider.class);
        for (RegisterProvider registerProvider : registerProviders) {
            register(registerProvider.value(), registerProvider.priority() < 0 ? Priorities.USER : registerProvider.priority());
        }

        for (RestClientListener restClientListener : ServiceLoader.load(RestClientListener.class)) {
            restClientListener.onNewClient(interfaceClass, this);
        }

        //We need to check first if default exception mapper was not disabled by property on builder.
        Object disableDefaultMapperJersey = jerseyClientBuilder.getConfiguration().getProperty(CONFIG_DISABLE_DEFAULT_MAPPER);
        if (disableDefaultMapperJersey != null && disableDefaultMapperJersey.equals(Boolean.FALSE)) {
            register(new DefaultResponseExceptionMapper());
        } else if (disableDefaultMapperJersey == null) {
            //If property was not set on Jersey ClientBuilder, we need to check config.
            Optional<Boolean> disableDefaultMapperConfig = config.getOptionalValue(CONFIG_DISABLE_DEFAULT_MAPPER, boolean.class);
            if (!disableDefaultMapperConfig.isPresent() || !disableDefaultMapperConfig.get()) {
                register(new DefaultResponseExceptionMapper());
            }
        }


        RestClientModel restClientModel = RestClientModel.from(interfaceClass,
                                                               responseExceptionMappers,
                                                               paramConverterProviders,
                                                               asyncInterceptorFactories);
        //AsyncInterceptors initialization
        List<AsyncInvocationInterceptor> asyncInterceptors = asyncInterceptorFactories.stream()
                .map(AsyncInvocationInterceptorFactory::newInterceptor)
                .collect(Collectors.toList());
        asyncInterceptors.forEach(AsyncInvocationInterceptor::prepareContext);
        executorService(new ExecutorServiceWrapper(executorService,
                                                   asyncInterceptors));

        Client client = jerseyClientBuilder.build();
        WebTarget webTarget = client.target(this.uri);
        return (T) Proxy.newProxyInstance(interfaceClass.getClassLoader(),
                                          new Class[] {interfaceClass},
                                          new ProxyInvocationHandler(webTarget, restClientModel)
        );
    }

    @Override
    public Configuration getConfiguration() {
        return configWrapper;
    }

    @Override
    public RestClientBuilder property(String name, Object value) {
        jerseyClientBuilder.property(name, value);
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> aClass) {
        if (isSupportedCustomProvider(aClass)) {
            register(ReflectionUtil.createInstance(aClass));
        } else {
            jerseyClientBuilder.register(aClass);
        }
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> aClass, int i) {
        if (isSupportedCustomProvider(aClass)) {
            register(ReflectionUtil.createInstance(aClass), i);
        } else {
            jerseyClientBuilder.register(aClass, i);
        }
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> aClass, Class<?>... classes) {
        if (isSupportedCustomProvider(aClass)) {
            register(ReflectionUtil.createInstance(aClass), classes);
        } else {
            jerseyClientBuilder.register(aClass, classes);
        }
        return this;
    }

    @Override
    public RestClientBuilder register(Class<?> aClass, Map<Class<?>, Integer> map) {
        if (isSupportedCustomProvider(aClass)) {
            register(ReflectionUtil.createInstance(aClass), map);
        } else {
            jerseyClientBuilder.register(aClass, new HashMap<>(map));
        }
        return this;
    }

    @Override
    public RestClientBuilder register(Object o) {
        if (o instanceof ResponseExceptionMapper) {
            ResponseExceptionMapper mapper = (ResponseExceptionMapper) o;
            registerCustomProvider(o, -1);
            jerseyClientBuilder.register(mapper, mapper.getPriority());
        } else {
            jerseyClientBuilder.register(o);
            registerCustomProvider(o, -1);
        }
        return this;
    }

    @Override
    public RestClientBuilder register(Object o, int i) {
        jerseyClientBuilder.register(o, i);
        registerCustomProvider(o, i);
        return this;
    }

    @Override
    public RestClientBuilder register(Object o, Class<?>... classes) {
        for (Class<?> clazz : classes) {
            if (isSupportedCustomProvider(clazz)) {
                register(o);
            }
        }
        jerseyClientBuilder.register(o, classes);
        return this;
    }

    @Override
    public RestClientBuilder register(Object o, Map<Class<?>, Integer> map) {
        if (isSupportedCustomProvider(o.getClass())) {
            if (o instanceof ResponseExceptionMapper) {
                registerCustomProvider(o, map.get(ResponseExceptionMapper.class));
            } else if (o instanceof ParamConverterProvider) {
                registerCustomProvider(o, map.get(ParamConverterProvider.class));
            }
        }
        jerseyClientBuilder.register(o, new HashMap<>(map));
        return this;
    }

    private boolean isSupportedCustomProvider(Class<?> providerClass) {
        return ResponseExceptionMapper.class.isAssignableFrom(providerClass)
                || ParamConverterProvider.class.isAssignableFrom(providerClass)
                || AsyncInvocationInterceptorFactory.class.isAssignableFrom(providerClass);
    }

    private void registerCustomProvider(Object instance, int priority) {
        if (!isSupportedCustomProvider(instance.getClass())) {
            return;
        }
        if (instance instanceof ResponseExceptionMapper) {
            responseExceptionMappers.add((ResponseExceptionMapper) instance);
            //needs to be registered separately due to it is not possible to register custom provider in jersey
            Map<Class<?>, Integer> contracts = new HashMap<>();
            contracts.put(ResponseExceptionMapper.class, priority);
            configWrapper.addCustomProvider(instance.getClass(), contracts);
        }
        if (instance instanceof ParamConverterProvider) {
            paramConverterProviders.add((ParamConverterProvider) instance);
        }
        if (instance instanceof AsyncInvocationInterceptorFactory) {
            asyncInterceptorFactories.add((AsyncInvocationInterceptorFactory) instance);
        }
    }

}
