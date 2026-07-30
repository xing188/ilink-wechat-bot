package com.ilink.test.ilinkcheck.model;

public enum ReplyMode {
    /** 普通文本回复：超过长度阈值时自动转为 Word 文档。 */
    TEXT,
    /** 强制文本回复：无论多长都以纯文本发送，不转 Word 文档（适用于路线/报告等结构化输出）。 */
    FORCE_TEXT,
    AUDIO_FILE
}
