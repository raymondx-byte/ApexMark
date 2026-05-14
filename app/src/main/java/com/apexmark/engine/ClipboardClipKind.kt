package com.apexmark.engine

/** [MarkdownConverter.peekClipboardKind] 的枚举值。 */
enum class ClipboardClipKind {
    EMPTY,
    /** 剪贴板已变化且本进程仍无法从描述推断类型（Android 10+）；通知用通用按钮，点按转换时由透明 Activity 再读。 */
    REMOTE_UPDATE,
    MARKDOWN,
    HTML,
    WPS,
    PLAIN,
    IMAGE
}
