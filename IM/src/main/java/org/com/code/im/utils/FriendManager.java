package org.com.code.im.utils;

import jakarta.annotation.PostConstruct;
import org.roaringbitmap.longlong.Roaring64NavigableMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Paths;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * FriendManager 类用于管理用户的好友关系。
 * 他包含了两个静态的 位图，分别用于表示用户的关注关系和被关注关系。
 * user1FollowUser2存放的是用户的关注列表
 * user1FollowedByUser2存放的是用户的粉丝列表
 * lockForEachUser则是针对每个用户的锁，用于确保线程安全，同时保证锁的颗粒度不会太大
 */
@Component
public class FriendManager {

    @Value("${app.data.bitmap-path}")
    private String BASE_FILE_PATH;


    private static final ConcurrentHashMap<Long, Roaring64NavigableMap> user1FollowUser2 = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Roaring64NavigableMap> user1FollowedByUser2 = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<Long, ReentrantReadWriteLock> lockForFollowing = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, ReentrantReadWriteLock> lockForFollowers = new ConcurrentHashMap<>();

    /**
     * 通过user1的关注列表查看user1是否关注了user2
     */

    public static boolean checkIfUser1FollowUser2(long userId1, long userId2) {
        //获取user1的关注列表的锁
        ReentrantReadWriteLock lock1 = lockForFollowing.computeIfAbsent(userId1, k -> new ReentrantReadWriteLock());
        lock1.readLock().lock();
        try {
            Roaring64NavigableMap following = user1FollowUser2.get(userId1);
            return following != null && following.contains(userId2);
        } finally {
            lock1.readLock().unlock();
        }
    }

    /**
     * 如果 user1 关注了 user2，
     * 则添加 user2 到 user1 的关注列表中。
     * 再添加 user1 到 user2 的粉丝列表中。
     */
    public static void user1FollowUser2(long userId1, long userId2) {
        /**
         * user1关注了user2,所以
         * 1. 把user2添加到user1的关注列表中,意味着 要对user1的关注列表的位图进行修改,线程可能不安全,
         *    所以要先获取lockFollowing中的user1的锁
         * 2. 把user1添加到user2的粉丝列表中,意味着 要对user2的粉丝列表的位图进行修改,线程可能不安全,
         *    所以要先获取lockFollowers中的user2的锁
         *
         * 然后为了防止线程1获取了user1的锁的同时,线程2获取了user2的锁，
         * 线程1会一直等待线程2释放锁，线程2会一直等待线程1释放锁。
         * 然后造成死锁
         *
         * 所以我这里根据他们的哈希值进行排序，保证每个线程获取锁的顺序是固定的,避免死锁
         */
        //获取user1的关注列表的锁
        ReentrantReadWriteLock lock1 = lockForFollowing.computeIfAbsent(userId1, k -> new ReentrantReadWriteLock());
        //获取user2的粉丝列表的锁
        ReentrantReadWriteLock lock2 = lockForFollowers.computeIfAbsent(userId2, k -> new ReentrantReadWriteLock());

        // 按照锁的哈希值排序，避免死锁
        ReentrantReadWriteLock firstLock = lock1.hashCode() < lock2.hashCode() ? lock1 : lock2;
        ReentrantReadWriteLock secondLock = lock1.hashCode() < lock2.hashCode() ? lock2 : lock1;

        firstLock.writeLock().lock();
        secondLock.writeLock().lock();
        try {
            user1FollowUser2.computeIfAbsent(userId1, k -> new Roaring64NavigableMap()).add(userId2);
            user1FollowedByUser2.computeIfAbsent(userId2, k -> new Roaring64NavigableMap()).add(userId1);
        } catch (Exception e) {
            System.out.println("Error while updating follow relationships: " + e.getMessage());
        } finally {
            secondLock.writeLock().unlock();
            firstLock.writeLock().unlock();
        }
    }

    /**
     * 如果 user1 不再关注 user2，
     * 则从 user1 的关注列表中移除 user2
     * 从 user2 的粉丝列表中移除 user1
     */
    public static void removeFollowing(long userId1, long userId2) {
        //获取user1关注列表的锁
        ReentrantReadWriteLock lock1 = lockForFollowing.computeIfAbsent(userId1, k -> new ReentrantReadWriteLock());
        //获取user2粉丝列表的锁
        ReentrantReadWriteLock lock2 = lockForFollowers.computeIfAbsent(userId2, k -> new ReentrantReadWriteLock());

        // 按照锁的哈希值排序，避免死锁
        ReentrantReadWriteLock firstLock = lock1.hashCode() < lock2.hashCode() ? lock1 : lock2;
        ReentrantReadWriteLock secondLock = lock1.hashCode() < lock2.hashCode() ? lock2 : lock1;

        firstLock.writeLock().lock();
        secondLock.writeLock().lock();
        try {
            Roaring64NavigableMap following = user1FollowUser2.get(userId1);
            //从user1的关注列表中移除user2
            if (following != null) {
                following.removeLong(userId2);
            }

            //从user2的粉丝列表中移除user1
            Roaring64NavigableMap followedBy = user1FollowedByUser2.get(userId2);
            if (followedBy != null) {
                followedBy.removeLong(userId1);
            }
        } catch (Exception e) {
            System.out.println("Error while removing follow relationships: " + e.getMessage());
        } finally {
            secondLock.writeLock().unlock();
            firstLock.writeLock().unlock();
        }
    }

    /**
     * 如果两个用户互相关注了对方，则他们的好友列表中都有对方，那他们才算真正的好友
     * 这个方法用于判断两个用户是否是好友。
     *
     * 如果user1的关注列表中包含user2，并且user1的粉丝列表中也包含user2，则表示user1和user2是好友。
     */
    public static boolean areFriends(long userId1, long userId2) {
        //获取user1关注列表的锁
        ReentrantReadWriteLock lock1 = lockForFollowing.computeIfAbsent(userId1, k -> new ReentrantReadWriteLock());
        //获取user1粉丝列表的锁
        ReentrantReadWriteLock lock2 = lockForFollowers.computeIfAbsent(userId1, k -> new ReentrantReadWriteLock());

        // 按照锁的哈希值排序，避免死锁
        ReentrantReadWriteLock firstLock = lock1.hashCode() < lock2.hashCode() ? lock1 : lock2;
        ReentrantReadWriteLock secondLock = lock1.hashCode() < lock2.hashCode() ? lock2 : lock1;

        firstLock.readLock().lock();
        secondLock.readLock().lock();
        try {
            Roaring64NavigableMap following = user1FollowUser2.get(userId1);
            Roaring64NavigableMap followedBy = user1FollowedByUser2.get(userId1);

            //如果user1关注了user2,并且user1的粉丝列表中也包含user2，则表示user1和user2是好友
            return following != null && followedBy != null
                && following.contains(userId2) && followedBy.contains(userId2);
        } catch (Exception e) {
            System.out.println("Error while checking friendship: " + e.getMessage());
            return false;
        } finally {
            secondLock.readLock().unlock();
            firstLock.readLock().unlock();
        }
    }

    public static long[] getFriendList(long userId) {
        //获取user1关注列表的锁
        ReentrantReadWriteLock lock1 = lockForFollowing.computeIfAbsent(userId, k -> new ReentrantReadWriteLock());
        //获取user1粉丝列表的锁
        ReentrantReadWriteLock lock2 = lockForFollowers.computeIfAbsent(userId, k -> new ReentrantReadWriteLock());

        // 按照锁的哈希值排序，避免死锁
        ReentrantReadWriteLock firstLock = lock1.hashCode() < lock2.hashCode() ? lock1 : lock2;
        ReentrantReadWriteLock secondLock = lock1.hashCode() < lock2.hashCode() ? lock2 : lock1;

        firstLock.readLock().lock();
        secondLock.readLock().lock();
        try {
            Roaring64NavigableMap following = user1FollowUser2.get(userId);
            Roaring64NavigableMap followedBy = user1FollowedByUser2.get(userId);

            if (following != null && followedBy != null) {
                // 创建一个临时的 Roaring64NavigableMap 来存储交集结果
                Roaring64NavigableMap friends = new Roaring64NavigableMap();
                //通过并集复制following的内容
                friends.or(following);
                //用friends和followedBy进行交集操作
                friends.and(followedBy);
                //把结果转换成List并返回
                return friends.toArray();
            }
            return null;
        } catch (Exception e) {
            System.out.println("Error while checking friendship: " + e.getMessage());
            return null;
        } finally {
            secondLock.readLock().unlock();
            firstLock.readLock().unlock();
        }
    }

    /**
     * 把位图信息每隔一小时更新到磁盘中,最新的位图数据序列化并覆盖磁盘上的旧文件。
     *
     * Spring 的 @Scheduled 默认使用一个单线程的任务调度器，这意味着在同一时间只有一个线程会执行被 @Scheduled 标记的方法。
     * 如果定时任务的执行时间超过设定的时间间隔（如 fixedRate 或 fixedDelay），后续的任务会被推迟执行，而不会并发运行。
     */
    @Scheduled(fixedRate = 10000)
    public void saveAllUsersToDisk() {
        /**
         *  _friendsXX.dat 这里的XX号是占位符,是为了凑成统一长度的文件名,方便加载数据的
         */
        saveUsersToDisk(user1FollowUser2, "_friendsXX.dat");
        saveUsersToDisk(user1FollowedByUser2, "_followers.dat");
    }

    private void saveUsersToDisk(ConcurrentHashMap<Long, Roaring64NavigableMap> userMap, String suffix) {
        for (Long userId : userMap.keySet()) {
            //获取user1关注列表的锁
            ReentrantReadWriteLock lock1 = lockForFollowing.computeIfAbsent(userId, k -> new ReentrantReadWriteLock());
            //获取user1粉丝列表的锁a
            ReentrantReadWriteLock lock2 = lockForFollowers.computeIfAbsent(userId, k -> new ReentrantReadWriteLock());

            // 按照锁的哈希值排序，避免死锁
            ReentrantReadWriteLock firstLock = lock1.hashCode() < lock2.hashCode() ? lock1 : lock2;
            ReentrantReadWriteLock secondLock = lock1.hashCode() < lock2.hashCode() ? lock2 : lock1;

            firstLock.writeLock().lock();
            secondLock.writeLock().lock();
            try {
                Roaring64NavigableMap bitmap = userMap.get(userId);
                if (bitmap != null) {
                    try {
                        String filePath = Paths.get(BASE_FILE_PATH, "user_" + userId + suffix).toString();
                        try (DataOutputStream fos = new DataOutputStream(new FileOutputStream(filePath))) {
                            bitmap.serialize(fos);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    } catch (Exception e) {
                        System.out.println("Failed to save user " + userId + " data: " + e.getMessage());
                    }
                }
            } finally {
                secondLock.writeLock().unlock();
                firstLock.writeLock().unlock();
            }
        }
    }

    /**
     * 每一次服务器重启后,从磁盘加载位图信息。
     * @PostConstruct 方法是单线程执行的
     */
    @PostConstruct
    public void loadAllUsersFromDisk() {
        File dir = new File(BASE_FILE_PATH);
        if (dir.exists() && dir.isDirectory()) {
            for (File file : dir.listFiles()) {
                String fileName = file.getName();
                if (fileName.startsWith("user_") && fileName.endsWith("_friendsXX.dat")) {
                    loadFromDisk(file, fileName, user1FollowUser2);
                }else if (fileName.startsWith("user_") && fileName.endsWith("_followers.dat")){
                    loadFromDisk(file, fileName, user1FollowedByUser2);
                }
            }
        }else{
            if (dir.mkdirs()) {
                System.out.println("Directory created: " + BASE_FILE_PATH);
            } else {
                System.out.println("Failed to create directory: " + BASE_FILE_PATH);
            }
        }
    }

    private static void loadFromDisk(File file, String fileName,ConcurrentHashMap<Long, Roaring64NavigableMap> userMap) {
        long userId = Long.parseLong(fileName.substring(5, fileName.length() -14)); // 提取用户 ID
        try (DataInputStream fis = new DataInputStream(new FileInputStream(file))) {
            Roaring64NavigableMap bitmap = new Roaring64NavigableMap();
            bitmap.deserialize((DataInput) fis); // 反序列化到位图
            userMap.put(userId, bitmap);
        } catch (IOException e) {
            System.out.println("Failed to load user " + userId + " data: " + e.getMessage());
        }
    }
}