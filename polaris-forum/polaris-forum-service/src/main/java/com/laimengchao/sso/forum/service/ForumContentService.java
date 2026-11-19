package com.laimengchao.sso.forum.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

@Service
public class ForumContentService {

    private static final Safelist ALLOWED = new Safelist().addTags(
            "p", "br", "strong", "b", "em", "i", "u", "s", "del",
            "h2", "h3", "ul", "ol", "li", "blockquote");
    private static final Document.OutputSettings OUTPUT_SETTINGS = new Document.OutputSettings().prettyPrint(false);

    public String sanitize(String value) {
        return Jsoup.clean(value == null ? "" : value, "", ALLOWED, OUTPUT_SETTINGS).trim();
    }

    public String plainText(String html) {
        return Jsoup.parse(html == null ? "" : html).text().trim();
    }
}
