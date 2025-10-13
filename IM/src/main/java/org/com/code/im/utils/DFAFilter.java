package org.com.code.im.utils;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@Component
public class DFAFilter {
    /**
     * 敏感词状态转移图
      */
    private static Map<Character, Map> sensitiveDict = new HashMap<>();

    // 使用实例变量接收配置值
    @Value("${app.data.sensitive-dict-path}")
    private String filePath;

    private static String FILE_PATH=null; // 静态变量存储路径

    @PostConstruct
    public void initFilePath() {
        FILE_PATH = filePath;
    }


    private static ReentrantReadWriteLock readWriteLock = new ReentrantReadWriteLock();

    /**
     * 添加敏感词到状态转移图
     */
    public static void addWord(String word) {
        readWriteLock.writeLock().lock();
        try {
            Map<Character, Map> currentDict = sensitiveDict;
            for (int i = 0; i < word.length(); i++) {
                char c = word.charAt(i);
                if (!currentDict.containsKey(c)) {
                    currentDict.put(c, new HashMap<>());
                }
                currentDict = currentDict.get(c);
            }
            // 标记为终止节点
            currentDict.put('$', null);
        } finally {
            readWriteLock.writeLock().unlock();
        }
    }

    /**
     * 过滤敏感词
     */
    public static String filter(String text, char replaceChar) {
        char[] c = text.toCharArray();
        int length = text.length();

        readWriteLock.readLock().lock();
        try {
            for (int i = 0; i < length; i++) {
                Map<Character, Map> currentDict = sensitiveDict;
                int j = recursion(currentDict, c, length, i);
                if (j != -1) {
                    for (int k = i; k <= j; k++) {
                        c[k] = replaceChar;
                    }
                    i = j;
                }
            }
            return new String(c);
        } finally {
            readWriteLock.readLock().unlock();
        }
    }

    public static int recursion(Map<Character, Map> currentDict, char[] c, int length, int currentIndex){

        if(currentDict.containsKey(c[currentIndex])){
            if(currentIndex+1<length)
                return recursion(currentDict.get(c[currentIndex]),c,length,currentIndex+1);
            else if(currentIndex+1==length){
                currentDict = currentDict.get(c[currentIndex]);
                if(currentDict.containsKey('$'))
                    //此时currentIndex的位置是敏感词结束的位置
                    return currentIndex;
                return -1;
            }
        }else{
            if(currentDict.containsKey('$'))
                //此时currentIndex的位置是敏感词结束的后面一个字符的位置,所以减1
                return currentIndex-1;
        }
        return -1;
    }


    /**
     * 持久化状态转移图
     */
    public static void saveSensitiveDict() {
        try {
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE_PATH))) {
                oos.writeObject(sensitiveDict);
            }
        } catch (IOException e) {
            System.err.println("保存状态转移图失败：" + e.getMessage());
        }
    }

    /**
     * 监听 ApplicationReadyEvent,应用完全启动后执行的任务
     * @postconstructor 执行早于 @EventListener
     */
    @EventListener(ApplicationReadyEvent.class)
    private void loadSensitiveDict() {
        try {
           try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILE_PATH))) {
                Object obj = ois.readObject();
                if (obj instanceof Map) {
                    sensitiveDict.putAll((Map<Character, Map>) obj);
                }
            }
        } catch (Exception e) {
            System.out.println("未找到持久化文件，请重新构建状态转移图");
        }
    }
}
