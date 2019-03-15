package org.glassfish.jersey.restclient;

import java.lang.annotation.Annotation;
import java.util.Collection;

import javax.ws.rs.FormParam;
import javax.ws.rs.MatrixParam;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Form;

/**
 * @author David Kral
 */
class FormParamModel extends ParamModel<Form> {

    private final String formParamName;

    FormParamModel(Builder builder) {
        super(builder);
        formParamName = builder.formParamName();
    }


    String getFormParamName() {
        return formParamName;
    }

    @Override
    public Form handleParameter(Form form, Class<?> annotationClass, Object instance) {
        Object resolvedValue = interfaceModel.resolveParamValue(instance,
                                                                getType(),
                                                                getAnnotatedElement().getAnnotations());
        if (resolvedValue instanceof Collection) {
            for (final Object v : ((Collection) resolvedValue)) {
                form.param(formParamName, v.toString());
            }
        } else {
            form.param(formParamName, resolvedValue.toString());
        }
        return form;
    }

    @Override
    public boolean handles(Class<Annotation> annotation) {
        return FormParam.class.equals(annotation);
    }

}
