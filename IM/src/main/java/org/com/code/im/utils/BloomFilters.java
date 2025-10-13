package org.com.code.im.utils;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class BloomFilters {
    private static CopyOnWriteArrayList<BloomFilter<String>> bloomFilterList;
    /**
     * 读写锁多个线程可以同时进行读操作，只有写操作会阻塞其他线程。
     */
    private static ReentrantReadWriteLock lockForUpdateAndIterator = new ReentrantReadWriteLock ();
    private static final int BLOOM_FILTER_LIST_SIZE = 5;
    private static final int EXPECTED_INSERTIONS = 10000;
    private static final double FPP = 0.01;

    public static BloomFilter<String> getBloomFilter() {
        return BloomFilter.create(Funnels.stringFunnel(StandardCharsets.UTF_8), EXPECTED_INSERTIONS, FPP);
    }
    /**
     * 静态初始化,确保只初始化一次。
     */
    static {
        bloomFilterList = new CopyOnWriteArrayList<>();
        for (int i = 0; i < BLOOM_FILTER_LIST_SIZE; i++) {
            bloomFilterList.add(getBloomFilter());
        }
    }


    /**
     * 分片布隆过滤器将一个大的布隆过滤器分成多个小的布隆过滤器，
     * 每个小布隆过滤器负责一部分数据。通过轮换这些分片，可以实现类似滑动窗口的效果
     * 每隔一个小时，把队列开头最旧的布隆过滤器的分片移除掉，并添加一个新的分片到队列末尾
     */
    @Scheduled(fixedRate = 1,timeUnit = TimeUnit.HOURS)
    public static void updateBloomFilterList() {
        lockForUpdateAndIterator.writeLock().lock();
        try {
            bloomFilterList.remove(0);
            bloomFilterList.add(getBloomFilter());
        } finally {
            lockForUpdateAndIterator.writeLock().unlock();
        }
    }

    public static boolean checkIfDuplicatedMessage(String message) {
        lockForUpdateAndIterator.readLock().lock();
        try {
            for (int i = 0; i < BLOOM_FILTER_LIST_SIZE; i++) {
                if (bloomFilterList.get(i).mightContain(message)) {
                    return true;
                }
            }
        } finally {
            lockForUpdateAndIterator.readLock().unlock();
        }
        lockForUpdateAndIterator.writeLock().lock();
        try {
            bloomFilterList.get(BLOOM_FILTER_LIST_SIZE - 1).put(message);
        } finally {
            lockForUpdateAndIterator.writeLock().unlock();
        }
        return false;
    }
}
