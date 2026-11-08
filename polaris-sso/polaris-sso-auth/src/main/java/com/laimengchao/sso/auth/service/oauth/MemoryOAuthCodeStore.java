package com.laimengchao.sso.auth.service.oauth;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版授权码存储（无 Redis 时的兜底），并发重放同样只成功一次。
 */
public class MemoryOAuthCodeStore implements OAuthCodeStore {

    private final Map<String, Item> store = new ConcurrentHashMap<>();

    @Override
    public void save(String code, CodePayload payload, long ttlSeconds) {
        store.put(code, new Item(payload, System.currentTimeMillis() + ttlSeconds * 1000));
        purgeExpired();
    }

    @Override
    public CodePayload take(String code) {
        Item item = store.remove(code);
        if (item == null || item.expireAt < System.currentTimeMillis()) {
            return null;
        }
        return item.payload;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt < now);
    }

    private record Item(CodePayload payload, long expireAt) {
    }
}
