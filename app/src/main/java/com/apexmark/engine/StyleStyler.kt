package com.apexmark.engine

/**
 * 内联 CSS 样式注入器。
 *
 * 核心策略：把所有样式直接写进 style="" 属性，
 * 因为微信、WPS、钉钉等第三方 App 不会加载 <style> 块。
 *
 * 表格强制加 border="1" cellpadding="5" 确保移动端 Word 可见框线；默认无彩色表头填充（透明底 + 深色线框）。
 */
class StyleStyler {

    fun inlineAll(rawHtml: String): String {
        var html = rawHtml

        html = html.replace("<h1>", """<h1 style="$H1_STYLE">""")
        html = html.replace("<h2>", """<h2 style="$H2_STYLE">""")
        html = html.replace("<h3>", """<h3 style="$H3_STYLE">""")
        html = html.replace("<h4>", """<h4 style="$H4_STYLE">""")
        html = html.replace("<h5>", """<h5 style="$H5_STYLE">""")
        html = html.replace("<h6>", """<h6 style="$H5_STYLE">""")

        html = html.replace("<p>", """<p style="$P_STYLE">""")

        html = html.replace("<strong>", """<strong style="font-weight:700;">""")
        html = html.replace("<em>", """<em style="font-style:italic;">""")
        html = html.replace("<del>", """<del style="text-decoration:line-through;color:#999;">""")

        html = html.replace("<blockquote>",
            """<blockquote style="$BLOCKQUOTE_STYLE">""")

        // pre>code 必须先处理，否则内层 <code> 会被下面的行内规则吃掉
        html = html.replace(Regex("""<pre[^>]*>\s*<code[^>]*>"""),
            """<pre style="$PRE_STYLE"><code style="$PRE_CODE_STYLE">""")
        html = html.replace(Regex("""<pre>(?!\s*<code)"""),
            """<pre style="$PRE_STYLE">""")

        html = html.replace(Regex("""(?<!<pre[^>]{0,200})<code>"""),
            """<code style="$INLINE_CODE_STYLE">""")

        html = html.replace("<ul>", """<ul style="$UL_STYLE">""")
        html = html.replace("<ol>", """<ol style="$OL_STYLE">""")
        html = html.replace("<li>", """<li style="$LI_STYLE">""")

        // 先修 `<table><th` 再扩写 table 标签，否则 `[^>]*` 过长时与「在 table 后插 thead/tr」逻辑难配合
        html = repairLooseTableCells(html)
        // 微信 / WPS / 钉钉的富文本输入框不解析 CSS border 等属性，必须用 HTML 原生属性
        html = html.replace(Regex("""<table[^>]*>"""),
            """<table border="1" cellpadding="5" cellspacing="0" style="$TABLE_STYLE">""")
        html = html.replace(Regex("""<thead[^>]*>"""), "<thead>")
        html = html.replace(Regex("""<tbody[^>]*>"""), "<tbody>")
        // 须用 `<th\\b`，否则 `<thead>` 会被当成 `<th…>` 整块替换，表头行会碎成「table 下直接 th」
        html = html.replace(Regex("""<tr\b[^>]*>"""), """<tr style="$TR_STYLE">""")
        html = html.replace(Regex("""<th\b[^>]*>"""), """<th style="$TH_STYLE">""")
        html = html.replace(Regex("""<td\b[^>]*>"""), """<td style="$TD_STYLE">""")

        html = html.replace(Regex("""<a\s+href="""),
            """<a style="color:#1566c8;text-decoration:none;" href=""")

        html = html.replace(Regex("""<hr\s*/?>"""),
            """<hr style="border:none;border-top:2px solid #e0e0e0;margin:16px 0;"/>""")

        html = html.replace(Regex("""<img\s"""),
            """<img style="max-width:100%;height:auto;border-radius:6px;" """)

        return html
    }

    /**
     * html→md→html 等路径可能产生 `<table><th` 或 `<thead><th` 缺少 `<tr>`，WPS 会拒粘并卡住。
     * 用索引插入，避免 `String.replace(Regex)` 与 `$1$2` 转义问题。
     */
    private fun repairLooseTableCells(html: String): String {
        var s = html
        val tableOpen = Regex("""<table[^>]*>""", RegexOption.IGNORE_CASE)
        var searchFrom = 0
        while (true) {
            val m = tableOpen.find(s, searchFrom) ?: break
            val afterGt = m.range.last + 1
            if (afterGt >= s.length) break
            val tail = s.substring(afterGt)
            val wsLen = tail.takeWhile { it.isWhitespace() }.length
            val slice = tail.drop(wsLen)
            // 不能用 startsWith("<th")：会与 `<thead>` 误判混淆
            if (!Regex("""^<th\b""", RegexOption.IGNORE_CASE).containsMatchIn(slice)) {
                searchFrom = afterGt
                continue
            }
            val insertPos = afterGt + wsLen
            val insert = "<thead><tr>"
            s = s.substring(0, insertPos) + insert + s.substring(insertPos)
            searchFrom = insertPos + insert.length
        }
        s = Regex("""(<thead\s*>)(\s*)<th\b""", RegexOption.IGNORE_CASE).replace(s) { mr ->
            "${mr.groupValues[1]}${mr.groupValues[2]}<tr><th"
        }
        s = Regex("""(<tbody\s*>)(\s*)<th\b""", RegexOption.IGNORE_CASE).replace(s) { mr ->
            "${mr.groupValues[1]}${mr.groupValues[2]}<tr><th"
        }
        s = Regex("""(<tbody\s*>)(\s*)<td\b""", RegexOption.IGNORE_CASE).replace(s) { mr ->
            "${mr.groupValues[1]}${mr.groupValues[2]}<tr><td"
        }
        return s
    }

    /** 纯文本包一层带样式的段落，供「剪贴板 → HTML」等路径使用。 */
    fun styledPlainParagraph(plain: String): String {
        val esc = escapeXmlText(plain)
        return """<p style="$P_STYLE">$esc</p>"""
    }

    private fun escapeXmlText(s: String): String = buildString(s.length + 8) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                else -> append(ch)
            }
        }
    }

    companion object {
        private const val FONT_STACK = "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Noto Sans SC','Microsoft YaHei',sans-serif;"

        // 色值统一对齐图标色谱: #0050B0(Apex600) → #1566C8(Apex500) → #3380E0(Apex400)
        const val H1_STYLE = "${FONT_STACK}font-size:22px;font-weight:700;color:#1566c8;margin:20px 0 10px;padding-bottom:6px;border-bottom:2px solid #cce0ff;line-height:1.4;"
        const val H2_STYLE = "${FONT_STACK}font-size:19px;font-weight:600;color:#0050b0;margin:18px 0 8px;line-height:1.4;"
        const val H3_STYLE = "${FONT_STACK}font-size:16px;font-weight:600;color:#003e90;margin:14px 0 6px;line-height:1.4;"
        const val H4_STYLE = "${FONT_STACK}font-size:15px;font-weight:600;color:#002e70;margin:12px 0 6px;line-height:1.4;"
        const val H5_STYLE = "${FONT_STACK}font-size:14px;font-weight:600;color:#3380e0;margin:10px 0 4px;line-height:1.4;"

        const val P_STYLE = "${FONT_STACK}font-size:14px;line-height:1.6;color:#212529;margin:6px 0;"

        const val BLOCKQUOTE_STYLE = "border-left:4px solid #1566c8;padding:8px 14px;margin:10px 0;background:#e6f0ff;color:#495057;${FONT_STACK}font-size:14px;line-height:1.6;"

        const val INLINE_CODE_STYLE = "background:#f1f3f5;padding:1px 5px;border-radius:3px;font-family:'Consolas','Courier New',monospace;font-size:13px;color:#0050b0;"

        const val PRE_STYLE = "background:#f1f3f5;padding:12px;border-radius:6px;overflow-x:auto;margin:10px 0;font-family:'Consolas','Courier New',monospace;font-size:13px;line-height:1.5;color:#212529;"
        const val PRE_CODE_STYLE = "background:none;color:inherit;padding:0;font-size:13px;font-family:inherit;"

        const val UL_STYLE = "padding-left:22px;margin:6px 0;"
        const val OL_STYLE = "padding-left:22px;margin:6px 0;"
        const val LI_STYLE = "${FONT_STACK}font-size:14px;line-height:1.6;margin:3px 0;color:#212529;"

        const val TABLE_STYLE = "border-collapse:collapse;width:100%;margin:10px 0;${FONT_STACK}font-size:13px;"
        const val TR_STYLE = "border:1px solid #000000;"
        const val TH_STYLE = "background:transparent;color:#000000;padding:8px 10px;text-align:left;font-weight:600;border:1px solid #000000;"
        const val TD_STYLE = "padding:6px 10px;border:1px solid #000000;color:#000000;background:transparent;"
    }
}
