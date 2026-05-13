package com.apexmark.engine

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * MarkdownConverter + StyleStyler 单元测试。
 * 重点验证：表格框线保留、内联样式注入、微信/WPS 兼容性。
 */
class MarkdownConverterTest {

    private lateinit var converter: MarkdownConverter
    private lateinit var styler: StyleStyler

    @Before
    fun setup() {
        styler = StyleStyler()
        converter = MarkdownConverter(styler)
    }

    // ═══════════════════════════════════════════
    //  表格：微信粘贴时不丢框线
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
            | - | - |
            | 1 | 2 |
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)

        assertTrue("th must have inline border style",
            html.contains("<th style=") && html.contains("border:1px solid"))
        assertTrue("td must have inline border style",
            html.contains("<td style=") && html.contains("border:1px solid"))
    }

    @Test
    fun `table header has background color`() {
        val md = """
            | 列1 | 列2 |
            | --- | --- |
            | 值1 | 值2 |
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)

        assertTrue("th must have background color",
            html.contains("background:#1a73e8"))
        assertTrue("th must have white text",
            html.contains("color:#fff"))
    }

    @Test
    fun `table rows have inline border`() {
        val md = """
            | X | Y |
            | - | - |
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

        assertTrue("h1 must have blue color", html.contains("color:#1a73e8"))
        assertTrue("h2 must have dark blue color", html.contains("color:#1557b0"))
        assertTrue("h3 must have accent blue color", html.contains("color:#185abc"))
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
            html.contains("background:#f1f3f4"))
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
            html.contains("<pre style=") && html.contains("background:#f5f5f5"))
    }

    // ═══════════════════════════════════════════
    //  引用块
    // ═══════════════════════════════════════════

    @Test
    fun `blockquote has left border`() {
        val md = "> 这是一段引用"
        val html = converter.toInlineStyledHtml(md)
        assertTrue("blockquote must have border-left",
            html.contains("border-left:4px solid #1a73e8"))
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
            html.contains("color:#1a73e8"))
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
    fun `convertText returns both plain and html`() {
        val md = "# Hello\n\n**bold** text"
        val (plain, html) = converter.convertText(md)
        assertEquals("plain text preserved", md, plain)
        assertTrue("html contains styled h1", html.contains("<h1 style="))
        assertTrue("html contains bold", html.contains("<strong style="))
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
        assertTrue("th background", html.contains("background:#1a73e8"))

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
        val success: ConvertResult = ConvertResult.Success(100)
        val empty: ConvertResult = ConvertResult.Empty
        val notMd: ConvertResult = ConvertResult.NotMarkdown
        val tooLarge: ConvertResult = ConvertResult.TooLarge("2.5")
        val error: ConvertResult = ConvertResult.Error("timeout")

        assertTrue(success is ConvertResult.Success)
        assertTrue(empty is ConvertResult.Empty)
        assertTrue(notMd is ConvertResult.NotMarkdown)
        assertTrue(tooLarge is ConvertResult.TooLarge)
        assertEquals("2.5", (tooLarge as ConvertResult.TooLarge).sizeMb)
        assertEquals("timeout", (error as ConvertResult.Error).message)
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
            | - | - |
            | 1 | 2 |
            | 3 | 4 |
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)
        assertFalse("no bare <tr>", Regex("""<tr>""").containsMatchIn(html))
        assertTrue("tr has style", html.contains("<tr style="))
    }

    @Test
    fun `td cells have white background for WeChat transparency defense`() {
        val md = """
            | X | Y |
            | - | - |
            | a | b |
        """.trimIndent()

        val html = converter.toInlineStyledHtml(md)
        assertTrue("td must have background:#fff", html.contains("background:#fff"))
    }
}
