package com.ilink.test.ilinkcheck.application.tool.orchestration;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolExecutionPolicy {

    boolean readOnly() default true;
}
