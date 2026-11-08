package com.laimengchao.sso.auth.service.session;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版会话存储（无 Redis 时的单实例兜底）。
 * <p>
 * 注意：多实例部署下各实例互相看不见对方的会话，单点会时灵时不灵，
 * 所以生产必须用 Redis 版。
 */
public class MemorySessionStore implements SessionStore {

    /** sid -> [accountId, 过期 epoch 毫秒] */
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void put(String sid, Long accountId, long ttlSeconds) {
        store.put(sid, new Entry(accountId, System.currentTimeMillis() + ttlSeconds * 1000));
        purgeExpired();
    }

    @Override
    public Long get(String sid) {
        Entry e = store.get(sid);
        if (e == null) {
            return null;
        }
        if (e.expireAt < System.currentTimeMillis()) {
            store.remove(sid);
            return null;
        }
        return e.accountId;
    }

    @Override
    public void remove(String sid) {
        store.remove(sid);
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt < now);
    }

    private record Entry(Long accountId, long expireAt) {
    }
}
