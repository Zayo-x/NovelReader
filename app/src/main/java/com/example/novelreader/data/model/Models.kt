package com.example.novelreader.data.model

/** 书籍类型 */
enum class BookType {
    /** 本地 txt 文件 */
    LOCAL_TXT,

    /** 本地 epub 文件 */
    LOCAL_EPUB,

    /** 漫画压缩包（cbz / zip） */
    LOCAL_CBZ,

    /** PDF 文档 */
    LOCAL_PDF,

    /** 漫画图片文件夹（SAF 目录） */
    LOCAL_IMAGE_DIR;

    /** 是否漫画类内容 */
    val isComic: Boolean
        get() = this == LOCAL_CBZ || this == LOCAL_PDF || this == LOCAL_IMAGE_DIR
}
