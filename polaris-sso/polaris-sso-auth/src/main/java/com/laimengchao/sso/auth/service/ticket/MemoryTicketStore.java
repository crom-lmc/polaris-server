package com.laimengchao.sso.auth.service.ticket;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存版票据存储（无 Redis 时的兜底）。
 * <p>
 * {@code remove} 的返回值即「有没有抢到」，ConcurrentHashMap 的 remove 是原子的，
 * 所以并发重放同样只会成功一次。
 */
public class MemoryTicketStore implements TicketStore {

    private final Map<String, Item> store = new ConcurrentHashMap<>();

    @Override
    public void save(String ticket, TicketPayload payload, long ttlSeconds) {
        store.put(ticket, new Item(payload, System.currentTimeMillis() + ttlSeconds * 1000));
        purgeExpired();
    }

    @Override
    public TicketPayload take(String ticket) {
        Item item = store.remove(ticket);
        if (item == null || item.expireAt < System.currentTimeMillis()) {
            return null;
        }
        return item.payload;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        store.entrySet().removeIf(e -> e.getValue().expireAt < now);
    }

    private record Item(TicketPayload payload, long expireAt) {
    }
}
