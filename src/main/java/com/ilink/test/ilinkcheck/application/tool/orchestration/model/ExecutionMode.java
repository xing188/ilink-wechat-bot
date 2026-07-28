package com.ilink.test.ilinkcheck.application.tool.orchestration.model;

/**
 * 一个执行批次只有两种运行方式：
 * 串行执行，或者允许并行展开。
 */
public enum ExecutionMode {
    SERIAL,              // 串行
    PARALLEL             // 并行
}
