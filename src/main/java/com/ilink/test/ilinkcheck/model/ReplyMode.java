package com.ilink.test.ilinkcheck.model;

public enum ReplyMode {
    /** 普通文本回复：直接以聊天文本返回。 */
    TEXT,
    /** 强制文本回复：保留给显式要求纯文本直出的场景。 */
    FORCE_TEXT,
    AUDIO_FILE
}
