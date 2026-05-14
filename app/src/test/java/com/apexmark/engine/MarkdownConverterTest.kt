package com.apexmark.engine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * MarkdownConverter（内嵌 [com.apexmark.link.core.ApexLinkMarkdownCore]）单元测试。
 * 重点验证：表格框线保留、内联样式注入、微信/WPS 兼容性。
 */
class MarkdownConverterTest {

    private lateinit var converter: MarkdownConverter

    @Before
    fun setup() {
        converter = MarkdownConverter()
    }

    // ═══════════════════════════════════════════
    //  表格：微信粘贴时不丢框线（分隔行须至少 `---`，`| - |` 不会被 Flexmark 识别为表格）
    // ═══════════════════════════════════════════

    @Test
    fun `table has border attribute for WeChat compatibility`() {
        val md = """
            | 姓名 | 年龄 | 城市 |
            | --- | --- | --- |
            | 张三 | 25 | 北京 |
            | 李四 | 30 | 上海 |
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)

        assertTrue("table must have border=\"1\"", html.contains("border=\"1\""))
        assertTrue("table must have cellpadding=\"5\"", html.contains("cellpadding=\"5\""))
        assertTrue("table must have cellspacing=\"0\"", html.contains("cellspacing=\"0\""))
    }

    @Test
    fun `table cells have inline border style`() {
        val md = """
            | A | B |
            | --- | --- |
            | 1 | 2 |
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)

        assertTrue("th must have inline border style",
            html.contains("<th style=") && html.contains("border:1px solid"))
        assertTrue("td must have inline border style",
            html.contains("<td style=") && html.contains("border:1px solid"))
    }

    @Test
    fun `table header uses neutral transparent style`() {
        val md = """
            | 列1 | 列2 |
            | --- | --- |
            | 值1 | 值2 |
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)

        assertTrue("th should not force blue header fill",
            html.contains("background:transparent") && html.contains("<th style="))
        assertTrue("th text should be dark on transparent",
            html.contains("color:#000000"))
    }

    @Test
    fun `table rows have inline border`() {
        val md = """
            | X | Y |
            | --- | --- |
            | a | b |
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)
        assertTrue("tr must have inline style", html.contains("<tr style="))
    }

    // ═══════════════════════════════════════════
    //  标题
    // ═══════════════════════════════════════════

    @Test
    fun `headings have inline styles with correct colors`() {
        val md = """
            # 一级标题
            ## 二级标题
            ### 三级标题
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)

        assertTrue("h1 must have theme blue", html.contains("color:#1566c8"))
        assertTrue("h2 must have dark blue", html.contains("color:#0050b0"))
        assertTrue("h3 must have deeper blue", html.contains("color:#003e90"))
    }

    @Test
    fun `h1 has border-bottom`() {
        val md = "# 主标题"
        val html = converter.toInlineStyledHtml(md)
        assertTrue("h1 must have border-bottom",
            html.contains("border-bottom:2px solid"))
    }

    // ═══════════════════════════════════════════
    //  段落和强调
    // ═══════════════════════════════════════════

    @Test
    fun `paragraphs have inline font-family`() {
        val md = "这是一个段落"
        val html = converter.toInlineStyledHtml(md)
        assertTrue("p must have font-family", html.contains("font-family:"))
    }

    @Test
    fun `bold text has font-weight 700`() {
        val md = "这里有**加粗文本**"
        val html = converter.toInlineStyledHtml(md)
        assertTrue("strong must have font-weight:700",
            html.contains("<strong style=\"font-weight:700;\">"))
    }

    @Test
    fun `strikethrough has line-through`() {
        val md = "这里有~~删除线~~"
        val html = converter.toInlineStyledHtml(md)
        assertTrue("del must have line-through",
            html.contains("text-decoration:line-through"))
    }

    // ═══════════════════════════════════════════
    //  代码
    // ═══════════════════════════════════════════

    @Test
    fun `inline code has background color`() {
        val md = "使用 `println()` 打印"
        val html = converter.toInlineStyledHtml(md)
        assertTrue("inline code must have background",
            html.contains("background:#f1f3f5"))
    }

    @Test
    fun `code block has pre style`() {
        val md = """
            ```
            val x = 1
            ```
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)
        assertTrue("pre must have background",
            html.contains("<pre style=") && html.contains("background:#f1f3f5"))
    }

    // ═══════════════════════════════════════════
    //  引用块
    // ═══════════════════════════════════════════

    @Test
    fun `blockquote has left border`() {
        val md = "> 这是一段引用"
        val html = converter.toInlineStyledHtml(md)
        assertTrue("blockquote must have border-left",
            html.contains("border-left:4px solid #1566c8"))
    }

    // ═══════════════════════════════════════════
    //  列表
    // ═══════════════════════════════════════════

    @Test
    fun `unordered list has padding`() {
        val md = "- 项目一\n- 项目二"
        val html = converter.toInlineStyledHtml(md)
        assertTrue("ul must have padding-left",
            html.contains("<ul style=") && html.contains("padding-left"))
    }

    @Test
    fun `ordered list has padding`() {
        val md = "1. 第一\n2. 第二"
        val html = converter.toInlineStyledHtml(md)
        assertTrue("ol must have padding-left",
            html.contains("<ol style=") && html.contains("padding-left"))
    }

    // ═══════════════════════════════════════════
    //  链接和分割线
    // ═══════════════════════════════════════════

    @Test
    fun `link has inline color style`() {
        val md = "[Google](https://google.com)"
        val html = converter.toInlineStyledHtml(md)
        assertTrue("link must have inline color",
            html.contains("color:#1566c8"))
    }

    @Test
    fun `horizontal rule has inline style`() {
        val md = "---"
        val html = converter.toInlineStyledHtml(md)
        assertTrue("hr must have inline style",
            html.contains("border-top:2px solid"))
    }

    // ═══════════════════════════════════════════
    //  双格式输出
    // ═══════════════════════════════════════════

    @Test
    fun `convertText returns styled html for markdown`() {
        val md = "# Hello\n\n**bold** text"
        val (plain, html) = converter.convertText(md)
        assertTrue(plain.isNotBlank())
        assertTrue("html contains styled h1", html.contains("<h1 style="))
        assertTrue("html contains bold", html.contains("<strong style="))
    }

    @Test
    fun `convertText converts simple html through pipeline`() {
        val (plain, html) = converter.convertText("<p>Hello <strong>world</strong></p>")
        assertTrue(html.isNotBlank())
        assertTrue("has inline styles", html.contains("style="))
    }

    @Test
    fun `convertText returns empty html for plain non md non html`() {
        val (_, html) = converter.convertText("just plain words")
        assertEquals("", html)
    }

    @Test
    fun `clipboard html wrapper produces full document for fragments`() {
        val fragment = """<p style="margin:0;">Hi</p>"""
        val doc = converter.wrapClipboardHtmlDocument(fragment)
        assertTrue(doc.contains("<!DOCTYPE html>", ignoreCase = true))
        assertTrue(doc.contains("http-equiv", ignoreCase = true))
        assertTrue(doc.contains("text/html", ignoreCase = true))
        assertTrue(doc.contains("""<meta charset="utf-8">"""))
        assertTrue(doc.contains("body style="))
        assertTrue(doc.contains(MarkdownConverter.CLIPBOARD_BODY_STYLE))
        assertTrue(doc.contains(fragment))
    }

    @Test
    fun `clipboard html wrapper leaves existing documents unchanged`() {
        val already = "<!DOCTYPE html><html><body><p>x</p></body></html>"
        assertEquals(already, converter.wrapClipboardHtmlDocument(already))
    }

    // ═══════════════════════════════════════════
    //  Markdown 检测
    // ═══════════════════════════════════════════

    @Test
    fun `raw HTML output is valid for basic markdown`() {
        val md = "# Title\n\nParagraph with **bold**."
        val raw = converter.toRawHtml(md)
        assertTrue("must contain h1 tag", raw.contains("<h1>"))
        assertTrue("must contain strong tag", raw.contains("<strong>"))
        assertTrue("must contain p tag", raw.contains("<p>"))
    }

    // ═══════════════════════════════════════════
    //  综合：AI 生成内容复制场景
    // ═══════════════════════════════════════════

    @Test
    fun `full AI output conversion preserves all formatting`() {
        val md = """
            # API 接口文档
            
            ## 用户模块
            
            ### 1. 登录接口
            
            **请求方式**：`POST`
            
            > 注意：需要携带 Token
            
            | 参数 | 类型 | 必填 | 说明 |
            | --- | --- | --- | --- |
            | username | string | 是 | 用户名 |
            | password | string | 是 | 密码 |
            
            ```json
            {
              "code": 200,
              "data": { "token": "xxx" }
            }
            ```
            
            ---
            
            - 支持 OAuth 2.0
            - 支持 ~~基础认证~~ JWT
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)

        // 表格必须有框线属性
        assertTrue("table border attr", html.contains("border=\"1\""))
        assertTrue("table cellpadding attr", html.contains("cellpadding=\"5\""))
        assertTrue("td border style", html.contains("border:1px solid"))
        assertTrue("th neutral background", html.contains("background:transparent"))

        // 所有标签必须有内联样式
        assertTrue("h1 styled", html.contains("<h1 style="))
        assertTrue("h2 styled", html.contains("<h2 style="))
        assertTrue("h3 styled", html.contains("<h3 style="))
        assertTrue("p styled", html.contains("<p style="))
        assertTrue("blockquote styled", html.contains("<blockquote style="))
        assertTrue("pre styled", html.contains("<pre style="))
        assertTrue("ul styled", html.contains("<ul style="))
        assertTrue("li styled", html.contains("<li style="))
        assertTrue("strong styled", html.contains("<strong style="))
        assertTrue("del styled", html.contains("<del style="))
        assertTrue("hr styled", html.contains("<hr style="))

        // 不能有无样式的裸标签（微信会丢失格式）
        assertFalse("no bare <p>", Regex("""<p>(?!style)""").containsMatchIn(html))
        assertFalse("no bare <h1>", html.contains("<h1>"))
        assertFalse("no bare <h2>", html.contains("<h2>"))
        assertFalse("no bare <table>", Regex("""<table>""").containsMatchIn(html))
        assertFalse("no bare <th>", Regex("""<th>""").containsMatchIn(html))
        assertFalse("no bare <td>", Regex("""<td>""").containsMatchIn(html))
    }

    // ═══════════════════════════════════════════
    //  ConvertResult 类型完整性
    // ═══════════════════════════════════════════

    @Test
    fun `ConvertResult sealed class covers all variants`() {
        val success: ConvertResult = ConvertResult.Success(100, hint = "hint")
        val empty: ConvertResult = ConvertResult.Empty
        val notMd: ConvertResult = ConvertResult.NotMarkdown
        val notHtml: ConvertResult = ConvertResult.NotHtml
        val imgUn: ConvertResult = ConvertResult.ClipboardImageUnsupported
        val tooLarge: ConvertResult = ConvertResult.TooLarge("2.5")
        val error: ConvertResult = ConvertResult.Error("timeout")
        val plainLines: ConvertResult = ConvertResult.PlainBlankLinesCollapsed(42)

        assertTrue(success is ConvertResult.Success)
        assertEquals("hint", (success as ConvertResult.Success).hint)
        assertTrue(empty is ConvertResult.Empty)
        assertTrue(notMd is ConvertResult.NotMarkdown)
        assertTrue(notHtml is ConvertResult.NotHtml)
        assertTrue(imgUn is ConvertResult.ClipboardImageUnsupported)
        assertTrue(tooLarge is ConvertResult.TooLarge)
        assertEquals("2.5", (tooLarge as ConvertResult.TooLarge).sizeMb)
        assertEquals("timeout", (error as ConvertResult.Error).message)
        assertTrue(plainLines is ConvertResult.PlainBlankLinesCollapsed)
        assertEquals(42, (plainLines as ConvertResult.PlainBlankLinesCollapsed).charCount)
    }

    @Test
    fun `MAX_SIZE_BYTES is 1MB`() {
        assertEquals(1_048_576L, MarkdownConverter.MAX_SIZE_BYTES)
    }

    // ═══════════════════════════════════════════
    //  表格行 <tr> 内联样式
    // ═══════════════════════════════════════════

    @Test
    fun `all tr elements have inline style`() {
        val md = """
            | A | B |
            | --- | --- |
            | 1 | 2 |
            | 3 | 4 |
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)
        assertFalse("no bare <tr>", Regex("""<tr>""").containsMatchIn(html))
        assertTrue("tr has style", html.contains("<tr style="))
    }

    @Test
    fun `td cells use transparent background by default`() {
        val md = """
            | X | Y |
            | --- | --- |
            | a | b |
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)
        assertTrue(
            "td should use transparent cell fill",
            Regex("""<td style="[^"]*background:transparent""", RegexOption.IGNORE_CASE).containsMatchIn(html)
        )
    }

    /** Gmail/WPS 对「table 下直接 th」会整条降级为纯文本；须保证 tr 包裹。 */
    @Test
    fun `Chinese sample doc table has no th directly under table or bare thead`() {
        val md = """
            # 示例文档

            这是一个简单的 Markdown 示例。

            ## 常见水果营养简表

            | 水果名称 | 热量 (kcal/100g) | 主要营养素 | 推荐季节 |
            | --- | --- | --- | --- |
            | 苹果 | 52 | 纤维素、维生素C | 四季 |
            | 香蕉 | 89 | 钾、维生素B6 | 四季 |

            ## 结语

            Markdown 格式非常适合用于快速记录笔记。
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)
        assertFalse(
            "th must not be direct child of table",
            Regex("""<table[^>]*>\s*<th\b""", RegexOption.IGNORE_CASE).containsMatchIn(html)
        )
        assertFalse(
            "th must not sit directly under thead without tr",
            Regex("""<thead[^>]*>\s*<th\b""", RegexOption.IGNORE_CASE).containsMatchIn(html)
        )
    }
}
