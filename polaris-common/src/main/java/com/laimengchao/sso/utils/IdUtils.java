package com.laimengchao.sso.utils;

/**
 * ID 生成工具。内置雪花算法（单机/分布式均安全，无需依赖 MyBatis-Plus）。
 * 与 MyBatis-Plus 的 ASSIGN_ID 同属雪花思路，可混用。
 */
public final class IdUtils {

    /** 起始时间戳（2024-01-01），可自定义 */
    private static final long EPOCH = 1_704_067_200_000L;
    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_BITS = 12L;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID = 1L;
    private static final long DATACENTER_ID = 1L;

    private static long sequence = 0L;
    private static long lastTimestamp = -1L;

    private IdUtils() {
    }

    public static synchronized Long nextId() {
        long timestamp = System.currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时钟回拨，拒绝生成 ID");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return ((timestamp - EPOCH) << TIMESTAMP_SHIFT)
                | (DATACENTER_ID << DATACENTER_ID_SHIFT)
                | (WORKER_ID << WORKER_ID_SHIFT)
                | sequence;
    }

    public static String nextIdStr() {
        return String.valueOf(nextId());
    }

    private static long tilNextMillis(long last) {
        long t = System.currentTimeMillis();
        while (t <= last) {
            t = System.currentTimeMillis();
        }
        return t;
    }
}
