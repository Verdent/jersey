package org.glassfish.jersey.restclient;

import java.lang.annotation.Annotation;
import java.util.Collection;

import javax.ws.rs.MatrixParam;
import javax.ws.rs.PathParam;
import javax.ws.rs.client.WebTarget;

/**
 * @author David Kral
 */
class MatrixParamModel extends ParamModel<WebTarget> {

    private final String matrixParamName;

    MatrixParamModel(Builder builder) {
        super(builder);
        matrixParamName = builder.matrixParamName();
    }

    String getMatrixParamName() {
        return matrixParamName;
    }

    @Override
    public WebTarget handleParameter(WebTarget requestPart, Class<?> annotationClass, Object instance) {
        Object resolvedValue = interfaceModel.resolveParamValue(instance,
                                                                getType(),
                                                                getAnnotatedElement().getAnnotations());
        if (resolvedValue instanceof Collection) {
            return requestPart.matrixParam(matrixParamName, ((Collection) resolvedValue).toArray());
        } else {
            return requestPart.matrixParam(matrixParamName, resolvedValue);
        }
    }

    @Override
    public boolean handles(Class<Annotation> annotation) {
        return MatrixParam.class.equals(annotation);
    }

}
