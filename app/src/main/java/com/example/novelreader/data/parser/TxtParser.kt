package com.example.novelreader.data.parser

import java.io.File
import java.nio.charset.Charset

/**
 * TXT 章节切分器。
 * 识别常见章节标题：第X章 / 第X节 / 第X回 / 序章 / 楔子 / 番外 等。
 * 自动检测编码（UTF-8 / GBK）。
 */
object TxtParser {

    /** 匹配章节标题的正则 */
    private val chapterRegex = Regex(
        "^\\s*(第[0-9零一二三四五六七八九十百千万]+[章节回卷部集篇]|[0-9]+[、.．][^\\n]{0,20}|" +
            "(序章|序言|楔子|前言|后记|尾声|番外|外传|正文|终章|大结局))[\\s:：.、．]?.*$",
        RegexOption.IGNORE_CASE
    )

    data class TxtChapter(val title: String, val content: String)

    /**
     * 读取文件内容并切分为章节。
     * @return Pair<书名, 章节列表>
     */
    fun parse(file: File): Pair<String, List<TxtChapter>> {
        val raw = file.readBytes()
        val text = decode(raw)
        val title = file.nameWithoutExtension
        return Pair(title, splitChapters(text))
    }

    fun decode(bytes: ByteArray): String {
        // 先尝试 UTF-8
        val utf8 = tryDecode(bytes, Charsets.UTF_8)
        if (utf8 != null) return utf8
        // 再尝试 GBK
        val gbk = tryDecode(bytes, Charset.forName("GBK"))
        if (gbk != null) return gbk
        // 兜底：UTF-8 替换
        return String(bytes, Charsets.UTF_8)
    }

    private fun tryDecode(bytes: ByteArray, charset: java.nio.charset.Charset): String? {
        val text = String(bytes, charset)
        val replacement = text.count { it == '\uFFFD' }
        // 替换字符过多说明编码不对
        return if (replacement * 20 > text.length) null else text
    }

    fun splitChapters(text: String): List<TxtChapter> {
        // 统一换行符
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalized.split('\n')

        // 找到所有章节标题行
        data class Hit(val lineIndex: Int, val title: String)
        val hits = mutableListOf<Hit>()
        for ((i, line) in lines.withIndex()) {
            val trimmed = line.trim()
            if (trimmed.length in 1..40 && chapterRegex.matches(trimmed)) {
                hits.add(Hit(i, trimmed))
            }
        }

        if (hits.isEmpty()) {
            // 没有检测到章节，整本书作为一章
            val content = normalized.trim()
            return if (content.isNotEmpty()) listOf(TxtChapter("全文", content)) else emptyList()
        }

        val chapters = mutableListOf<TxtChapter>()
        // 第一个章节标题之前的内容作为序言（若存在）
        val first = hits.first()
        if (first.lineIndex > 0) {
            val preContent = lines.subList(0, first.lineIndex).joinToString("\n").trim()
            if (preContent.isNotEmpty()) {
                chapters.add(TxtChapter("序言", preContent))
            }
        }

        for (idx in hits.indices) {
            val hit = hits[idx]
            val startLine = hit.lineIndex + 1 // 跳过标题行
            val endLine = if (idx + 1 < hits.size) hits[idx + 1].lineIndex else lines.size
            val content = lines.subList(startLine, endLine).joinToString("\n").trim()
            chapters.add(TxtChapter(hit.title, content))
        }
        return chapters
    }
}
