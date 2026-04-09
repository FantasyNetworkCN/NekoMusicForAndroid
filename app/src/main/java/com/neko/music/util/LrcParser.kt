package com.neko.music.util

object LrcParser {
    init {
        System.loadLibrary("LrcParser")
    }

    /**
     * 检查LRC文件内容是否包含有效的时间戳
     * @param content LRC文件内容
     * @return 是否包含有效的时间戳
     */
    external fun nativeIsValidLrcContent(content: String): Boolean

    /**
     * 解析LRC文件内容，返回前几行作为预览
     * @param content LRC文件内容
     * @param maxLines 最大行数
     * @return 预览内容
     */
    external fun nativeParseLrcPreview(content: String, maxLines: Int): String

    /**
     * 检查文件扩展名是否为lrc
     * @param fileName 文件名
     * @return 是否为lrc文件
     */
    external fun nativeIsLrcFile(fileName: String): Boolean

    /**
     * 验证LRC文件内容是否包含有效的时间戳（包装方法）
     */
    fun isValidLrcContent(content: String): Boolean {
        return nativeIsValidLrcContent(content)
    }

    /**
     * 解析LRC文件内容，返回前3行作为预览（包装方法）
     */
    fun parseLrcPreview(content: String): String {
        return nativeParseLrcPreview(content, 3)
    }

    /**
     * 检查文件扩展名是否为lrc（包装方法）
     */
    fun isLrcFile(fileName: String): Boolean {
        return nativeIsLrcFile(fileName)
    }
}