package org.glassfish.jersey.tests.e2e.client;

import org.glassfish.jersey.client.InjectionManagerClientProvider;
import org.glassfish.jersey.client.inject.ParameterInserter;
import org.glassfish.jersey.client.inject.ParameterInserterProvider;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.internal.inject.Providers;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.model.Parameter;
import org.glassfish.jersey.test.JerseyTest;
import org.junit.Test;

import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.client.ClientRequestContext;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.ParamConverter;
import javax.ws.rs.ext.ParamConverterProvider;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

public class ParametersTest extends JerseyTest {

    private static final String FROM = "from";
    private static final String TO = "to";

    public static class TestParamConverter implements ParamConverter<String> {
        @Override
        public String fromString(String s) {
            return FROM + s;
        }

        @Override
        public String toString(String s) {
            return TO + s;
        }
    }

    public static class TestParamConverterProvider implements ParamConverterProvider {
        @Override
        public <T> ParamConverter<T> getConverter(Class<T> aClass, Type type, Annotation[] annotations) {
            if (String.class.isAssignableFrom(aClass)) {
                return (ParamConverter<T>) new TestParamConverter();
            }
            return null;
        }
    }

    @Path("/resource")
    public static class ParameterResource {
        @POST
        @Path("/noparam")
        public String post(String entity) {
            return entity;
        }

        @GET
        @Path("/param/{id}")
        public String get(@PathParam("id") String id) {
            return id;
        }
    }

    @Override
    protected Application configure() {
        return new ResourceConfig(ParameterResource.class, Filter.class);
    }

    @PreMatching
    public static class Filter implements ContainerRequestFilter {

        @Override
        public void filter(ContainerRequestContext requestContext) throws IOException {
            System.out.println(requestContext.getUriInfo().getRequestUri().toASCIIString());
        }
    }

    public static class UriAlteringRequestFilter implements ClientRequestFilter {
        public static final String HELLO = "hello";


        public UriAlteringRequestFilter(Parameter parameter, String basePath) {
            this.parameter = parameter;
            this.basePath = basePath;
        }

        private final Parameter parameter;
        private final String basePath;

        @Override
        public void filter(ClientRequestContext requestContext) throws IOException {
            final InjectionManager injectionManager = InjectionManagerClientProvider.getInjectionManager(requestContext);
            final Iterable<ParameterInserterProvider> parameterInserterProviders
                    = Providers.getAllProviders(injectionManager, ParameterInserterProvider.class);
            for (final ParameterInserterProvider parameterInserterProvider : parameterInserterProviders) {
                if (parameterInserterProvider != null) {
                    ParameterInserter<Object, Object> inserter =
                            (ParameterInserter<Object, Object>) parameterInserterProvider.get(parameter);
                    alterRequest(inserter.insert(HELLO), requestContext);
                }
            }
        }

        private void alterRequest(Object value, ClientRequestContext requestContext) {
            Annotation annotation = parameter.getSourceAnnotation();
            if (annotation.getClass().getInterfaces()[0] == PathParam.class) {
                final String updatedBasePath = basePath.replace("{" + parameter.getSourceName() + "}", value.toString());
                URI uri = requestContext.getUri();
                try {
                    URI newURI =  new URI(uri.getScheme(), uri.getAuthority(), updatedBasePath,
                            uri.getQuery(), uri.getFragment());
                    requestContext.setUri(newURI);
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    private static String getPathFromAnnotation(final AnnotatedElement ae) {
        final Path p = ae.getAnnotation(Path.class);
        return p == null ? "" : p.value();
    }

    @Test
    public void testConverter() throws Throwable {
        final Method getMethod = ParameterResource.class.getMethod("get", String.class);
        final List<Parameter> parameters = Parameter.create(ParameterResource.class, ParameterResource.class,
                getMethod, false);
        StringBuilder pathBuilder = new StringBuilder();
        pathBuilder.append(getPathFromAnnotation(ParameterResource.class));
        pathBuilder.append(getPathFromAnnotation(getMethod));
        String response = target().register(TestParamConverterProvider.class)
                .register(new UriAlteringRequestFilter(parameters.get(0), pathBuilder.toString()))
                .path(pathBuilder.toString()).resolveTemplate("id", "Alan").request().buildGet().invoke(String.class);
        System.out.println(response);
    }
}
