package com.laimengchao.sso.forum.module.admin.service;

import com.laimengchao.sso.common.BizException;
import com.laimengchao.sso.common.ErrorCode;
import com.laimengchao.sso.forum.module.admin.mapper.SensitiveWordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class SensitiveWordService {

    private final SensitiveWordMapper mapper;

    public void check(String... values) {
        List<String> words = mapper.listEnabled();
        if (words.isEmpty()) return;
        for (String value : values) {
            String normalized = normalize(value);
            if (normalized.isEmpty()) continue;
            for (String word : words) {
                String normalizedWord = normalize(word);
                if (!normalizedWord.isEmpty() && normalized.contains(normalizedWord)) {
                    throw new BizException(ErrorCode.PARAM_ERROR, "内容包含敏感词：" + word);
                }
            }
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
