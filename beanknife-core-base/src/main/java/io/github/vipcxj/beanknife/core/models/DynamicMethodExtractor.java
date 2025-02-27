package io.github.vipcxj.beanknife.core.models;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.github.vipcxj.beanknife.core.utils.VarMapper;
import io.github.vipcxj.beanknife.runtime.annotations.InjectProperty;
import io.github.vipcxj.beanknife.runtime.annotations.InjectSelf;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;
import java.io.PrintWriter;
import java.util.List;

public class DynamicMethodExtractor implements Extractor {

    @NonNull
    private ViewContext context;
    @CheckForNull
    private final Type container;
    @NonNull
    private final Type viewType;
    @NonNull
    private final ExecutableElement executableElement;
    @NonNull
    private final Type returnType;

    public DynamicMethodExtractor(@NonNull ViewContext context, @NonNull ExecutableElement executableElement, @NonNull Type viewType) {
        this.context = context;
        this.container = Type.extract(context, context.getViewOf().getConfigElement());
        this.viewType = viewType;
        this.executableElement = executableElement;
        Type type = Type.extract(context, executableElement, null);
        if (type == null) {
            context.error("Failed to resolve the return type of property method" + executableElement.getSimpleName() + ".");
            this.returnType = Type.extract(context, Object.class);
        } else {
            this.returnType = type;
        }
    }

    @Override
    public boolean check() {
        if (container == null) {
            context.error("Unable to resolve the type config element: " + context.getViewOf().getConfigElement().getQualifiedName());
            return false;
        }
        Name name = executableElement.getSimpleName();
/*        if (!executableElement.getModifiers().contains(Modifier.STATIC)) {
            context.error("The dynamic property method \"" + name + "\" should be static.");
            return false;
        }*/
        List<? extends VariableElement> parameters = executableElement.getParameters();
        for (VariableElement parameter : parameters) {
            InjectSelf injectSelf = parameter.getAnnotation(InjectSelf.class);
            InjectProperty injectProperty = parameter.getAnnotation(InjectProperty.class);
            if (injectSelf == null && injectProperty == null) {
                context.error("All parameters of the dynamic property method \"" + name + "\" should annotated with the annotation @InjectSelf or @InjectProperty.");
                return false;
            }
            if (injectSelf != null) {
                if (!viewType.canAssignTo(Type.extract(context, parameter))) {
                    context.error("Unable to inject this object to the dynamic property method \""
                            + name + "\".");
                    return false;
                }
            } else {
                String propertyName = !injectProperty.value().isEmpty() ? injectProperty.value() : parameter.getSimpleName().toString();
                Property ip = context.getProperty(propertyName);
                if (ip == null) {
                    context.error("Unable to inject the property " +
                            propertyName + " to the dynamic property method \""
                            + name + "\"." +
                            " The property does not exists.");
                    return false;
                }
                if (!ip.getType().canAssignTo(Type.extract(context, parameter))) {
                    context.error("Unable to inject the property " +
                            propertyName + " to the dynamic property method \""
                            + name + "\"." +
                            " The property type mismatched.");
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    @NonNull
    public ViewContext getContext() {
        return context;
    }

    @Override
    @NonNull
    public Type getReturnType() {
        return returnType;
    }

    @Override
    @CheckForNull
    public Type getContainer() {
        return container;
    }

    @Override
    @NonNull
    public ExecutableElement getExecutableElement() {
        return executableElement;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public void print(PrintWriter writer, @CheckForNull VarMapper varMapper, @NonNull String indent, int indentNum) {
        printConfigBean(writer, "this");
        writer.print(".");
        writer.print(executableElement.getSimpleName());
        writer.print("(");
        int i = 0;
        List<? extends VariableElement> parameters = executableElement.getParameters();
        for (VariableElement parameter : parameters) {
            InjectSelf injectSelf = parameter.getAnnotation(InjectSelf.class);
            InjectProperty injectProperty = parameter.getAnnotation(InjectProperty.class);
            if (injectSelf != null) {
                writer.print("this");
            } else if (injectProperty != null){
                String propertyName = !injectProperty.value().isEmpty() ? injectProperty.value() : parameter.getSimpleName().toString();
                Property property = context.getProperty(propertyName);
                assert property != null;
                writer.print("this.");
                if (property.isDynamic()) {
                    writer.print(property.getGetterName());
                    writer.print("()");
                } else {
                    writer.print(context.getMappedFieldName(propertyName));
                }
            } else {
                writer.print("null");
            }
            if (i++ != parameters.size() - 1) {
                writer.print(", ");
            }
        }
        writer.print(")");
    }
}
