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
    /** accountId -> sid 集合（强制下线反查） */
    private final Map<Long, java.util.Set<String>> byAccount = new ConcurrentHashMap<>();

    @Override
    public void put(String sid, Long accountId, long ttlSeconds) {
        store.put(sid, new Entry(accountId, System.currentTimeMillis() + ttlSeconds * 1000));
        byAccount.computeIfAbsent(accountId, k -> ConcurrentHashMap.newKeySet()).add(sid);
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
        Entry entry = store.remove(sid);
        if (entry != null) {
            java.util.Set<String> sids = byAccount.get(entry.accountId);
            if (sids != null) {
                sids.remove(sid);
                if (sids.isEmpty()) {
                    byAccount.remove(entry.accountId);
                }
            }
        }
    }

    @Override
    public java.util.Set<String> sidsOf(Long accountId) {
        java.util.Set<String> sids = byAccount.get(accountId);
        if (sids == null) {
            return java.util.Set.of();
        }
        java.util.Set<String> alive = ConcurrentHashMap.newKeySet();
        long now = System.currentTimeMillis();
        for (String sid : sids) {
            Entry e = store.get(sid);
            if (e != null && e.expireAt >= now) {
                alive.add(sid);
            } else {
                sids.remove(sid);
            }
        }
        return alive;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> {
            boolean expired = e.getValue().expireAt < now;
            if (expired) {
                java.util.Set<String> sids = byAccount.get(e.getValue().accountId);
                if (sids != null) {
                    sids.remove(e.getKey());
                }
            }
            return expired;
        });
    }

    private record Entry(Long accountId, long expireAt) {
    }
}
