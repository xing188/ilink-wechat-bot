package com.ilink.test.ilinkcheck.application.tool.orchestration.meta;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
/**
 * 标记某个工具方法是否可以被视为只读方法。
 * 只有只读方法才有资格被放入并行批次。
 * 未标注的方法默认按只读处理。
 */
public @interface ToolExecutionPolicy {

    boolean readOnly() default true;
}
