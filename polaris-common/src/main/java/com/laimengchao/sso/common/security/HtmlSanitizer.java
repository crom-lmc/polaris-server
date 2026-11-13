package com.laimengchao.sso.common.security;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/** 富文本入库前的统一 XSS 白名单清洗。 */
public final class HtmlSanitizer {

    private static final Safelist RICH_TEXT = Safelist.basicWithImages()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "p", "pre", "blockquote", "table",
                    "thead", "tbody", "tr", "th", "td", "hr", "del", "figure", "figcaption")
            .addAttributes(":all", "style", "class", "target");

    private HtmlSanitizer() {
    }

    public static String cleanRichText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        return Jsoup.clean(html, RICH_TEXT);
    }
}
