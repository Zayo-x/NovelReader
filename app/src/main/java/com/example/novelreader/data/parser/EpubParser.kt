package com.example.novelreader.data.parser

import java.io.File
import java.util.zip.ZipFile
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * 简易 EPUB 解析器。
 * 解析 container.xml → OPF → spine 章节顺序 → 每章 HTML 提取纯文本。
 */
object EpubParser {

    data class EpubChapter(val title: String, val content: String)

    fun parse(file: File): Pair<String, List<EpubChapter>> {
        ZipFile(file).use { zip ->
            val rootDir = resolveRootDir(zip)
            val opfPath = resolveOpfPath(zip, rootDir)
            val opfDir = opfPath.substringBeforeLast('/', "")

            val opfBytes = zip.getInputStream(zip.getEntry(opfPath)).readBytes()

            val title = findTagText(opfBytes, "title") ?: file.nameWithoutExtension
            val manifest = parseManifest(opfBytes)
            val spine = parseSpine(opfBytes)
            val toc = parseToc(opfBytes)

            val chapters = mutableListOf<EpubChapter>()
            for ((i, idRef) in spine.withIndex()) {
                val href = manifest[idRef] ?: continue
                val fullPath = normalizePath(opfDir, href)
                val entry = zip.getEntry(fullPath) ?: continue
                val html = String(zip.getInputStream(entry).readBytes(), Charsets.UTF_8)
                val text = htmlToText(html)
                if (text.isBlank()) continue
                val chapterTitle = toc.getOrElse(i) { "第 ${i + 1} 章" }
                chapters.add(EpubChapter(chapterTitle, text))
            }
            return Pair(title, chapters)
        }
    }

    private fun resolveRootDir(zip: ZipFile): String {
        val containerEntry = zip.entries().asSequence().firstOrNull {
            it.name.endsWith("META-INF/container.xml")
        } ?: throw IllegalArgumentException("无效的 EPUB：缺少 container.xml")
        return containerEntry.name.removeSuffix("META-INF/container.xml")
    }

    private fun resolveOpfPath(zip: ZipFile, rootDir: String): String {
        val containerEntry = zip.getEntry(rootDir + "META-INF/container.xml")
            ?: throw IllegalArgumentException("无效的 EPUB")
        val xml = parseXml(zip.getInputStream(containerEntry).readBytes())
        val fullPath = findAttrValue(xml, "rootfile", "full-path")
            ?: throw IllegalArgumentException("无法定位 OPF 文件")
        return normalizePath(rootDir, fullPath)
    }

    private fun parseManifest(bytes: ByteArray): Map<String, String> {
        val xml = parseXml(bytes)
        val map = mutableMapOf<String, String>()
        var event = xml.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && xml.name == "item") {
                val id = xml.getAttributeValue(null, "id")
                val href = xml.getAttributeValue(null, "href")
                if (id != null && href != null) map[id] = href
            }
            event = xml.next()
        }
        return map
    }

    private fun parseSpine(bytes: ByteArray): List<String> {
        val xml = parseXml(bytes)
        val list = mutableListOf<String>()
        var event = xml.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && xml.name == "itemref") {
                val idRef = xml.getAttributeValue(null, "idref")
                if (idRef != null) list.add(idRef)
            }
            event = xml.next()
        }
        return list
    }

    private fun parseToc(bytes: ByteArray): List<String> {
        val xml = parseXml(bytes)
        val list = mutableListOf<String>()
        var event = xml.eventType
        var inNav = false
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (xml.name == "navMap" || xml.name == "nav") inNav = true
                    if (inNav && xml.name == "text") {
                        val textEvent = xml.next()
                        if (textEvent == XmlPullParser.TEXT && xml.text != null) {
                            val t = xml.text.trim()
                            if (t.isNotEmpty()) list.add(t)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (xml.name == "navMap" || xml.name == "nav") inNav = false
                }
            }
            event = xml.next()
        }
        return list
    }

    private fun parseXml(bytes: ByteArray): XmlPullParser {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(bytes.inputStream(), "UTF-8")
        return parser
    }

    private fun findTagText(bytes: ByteArray, tag: String): String? {
        val xml = parseXml(bytes)
        var event = xml.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && xml.name == tag) {
                val e = xml.next()
                if (e == XmlPullParser.TEXT) return xml.text?.trim()
            }
            event = xml.next()
        }
        return null
    }

    private fun findAttrValue(xml: XmlPullParser, tag: String, attr: String): String? {
        var event = xml.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && xml.name == tag) {
                return xml.getAttributeValue(null, attr)
            }
            event = xml.next()
        }
        return null
    }

    private fun normalizePath(dir: String, href: String): String {
        val cleanHref = href.substringBefore('#').trim()
        if (cleanHref.startsWith('/')) return cleanHref.removePrefix("/")
        if (dir.isBlank()) return cleanHref
        val parts = (dir.split('/') + cleanHref.split('/')).filter { it.isNotBlank() }
        val stack = ArrayDeque<String>()
        for (p in parts) {
            when (p) {
                "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeLast()
                else -> stack.addLast(p)
            }
        }
        return stack.joinToString("/")
    }

    /** 将 HTML 转为纯文本 */
    fun htmlToText(html: String): String {
        val body = html.substringAfter("<body", html).substringAfter('>', html)
        val sb = StringBuilder()
        var inTag = false
        var inScript = false
        var inStyle = false
        var i = 0
        while (i < body.length) {
            val c = body[i]
            if (!inTag) {
                when {
                    c == '<' -> {
                        val tagName = readTagName(body, i)
                        if (tagName == "script") inScript = true
                        if (tagName == "style") inStyle = true
                        if (tagName == "p" || tagName == "div" || tagName == "br" ||
                            tagName == "h1" || tagName == "h2" || tagName == "h3" ||
                            tagName == "h4" || tagName == "li" || tagName == "tr"
                        ) {
                            sb.append('\n')
                        }
                        inTag = true
                    }
                    !inScript && !inStyle -> sb.append(c)
                }
            } else {
                if (c == '>') {
                    inTag = false
                    val tagName = readTagName(body, i)
                    if (tagName == "/script") inScript = false
                    if (tagName == "/style") inStyle = false
                }
            }
            i++
        }
        val text = sb.toString()
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("&#(\\d+);")) { m ->
                m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: m.value
            }
        return text.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n")
    }

    private fun readTagName(html: String, start: Int): String {
        var i = start + 1
        while (i < html.length && html[i] != '>' && html[i] != ' ' && html[i] != '/') i++
        return html.substring(start + 1, i).trim().lowercase()
    }
}
