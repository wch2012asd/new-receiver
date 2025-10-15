package com.example;

/**
 * 雪花ID生成器
 * 基于Twitter的Snowflake算法实现分布式唯一ID生成
 */
public class SnowflakeIdGenerator {
    
    // 起始时间戳（2020-01-01）
    private static final long START_TIMESTAMP = 1577808000000L;
    
    // 机器ID所占的位数
    private static final long MACHINE_ID_BITS = 5L;
    
    // 数据中心ID所占的位数
    private static final long DATACENTER_ID_BITS = 5L;
    
    // 序列号所占的位数
    private static final long SEQUENCE_BITS = 12L;
    
    // 机器ID的最大值
    private static final long MAX_MACHINE_ID = ~(-1L << MACHINE_ID_BITS);
    
    // 数据中心ID的最大值
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    
    // 序列号的最大值
    private static final long MAX_SEQUENCE = ~(-1L << SEQUENCE_BITS);
    
    // 机器ID左移位数
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    
    // 数据中心ID左移位数
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    
    // 时间戳左移位数
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATACENTER_ID_BITS;
    
    private final long machineId;
    private final long datacenterId;
    private long sequence = 0L;
    private long lastTimestamp = -1L;
    
    /**
     * 构造函数
     * @param machineId 机器ID (0-31)
     * @param datacenterId 数据中心ID (0-31)
     */
    public SnowflakeIdGenerator(long machineId, long datacenterId) {
        if (machineId > MAX_MACHINE_ID || machineId < 0) {
            throw new IllegalArgumentException("机器ID必须在0到" + MAX_MACHINE_ID + "之间");
        }
        if (datacenterId > MAX_DATACENTER_ID || datacenterId < 0) {
            throw new IllegalArgumentException("数据中心ID必须在0到" + MAX_DATACENTER_ID + "之间");
        }
        this.machineId = machineId;
        this.datacenterId = datacenterId;
    }
    
    /**
     * 默认构造函数，使用默认的机器ID和数据中心ID
     */
    public SnowflakeIdGenerator() {
        this(1L, 1L);
    }
    
    /**
     * 生成下一个ID
     * @return 唯一ID
     */
    public synchronized long nextId() {
        long timestamp = getCurrentTimestamp();
        
        // 时间回拨检查
        if (timestamp < lastTimestamp) {
            throw new RuntimeException("时间回拨异常，拒绝生成ID");
        }
        
        // 同一毫秒内序列号递增
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            // 序列号溢出，等待下一毫秒
            if (sequence == 0) {
                timestamp = waitNextMillis(lastTimestamp);
            }
        } else {
            // 不同毫秒，序列号重置
            sequence = 0L;
        }
        
        lastTimestamp = timestamp;
        
        // 组装ID
        return ((timestamp - START_TIMESTAMP) << TIMESTAMP_SHIFT)
                | (datacenterId << DATACENTER_ID_SHIFT)
                | (machineId << MACHINE_ID_SHIFT)
                | sequence;
    }
    
    /**
     * 等待下一毫秒
     * @param lastTimestamp 上次时间戳
     * @return 新的时间戳
     */
    private long waitNextMillis(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }
    
    /**
     * 获取当前时间戳
     * @return 当前时间戳
     */
    private long getCurrentTimestamp() {
        return System.currentTimeMillis();
    }
}