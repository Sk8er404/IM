package org.com.code.im.utils;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.CreateBucketRequest;
import org.com.code.im.exception.OSSException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class OSSUtil {
    //避免硬编码
    @Value("${aliyun.oss.endpoint}")
    private String ENDPOINT;
    @Value("${aliyun.oss.accessKeyId}")
    private String ACCESS_KEY_ID;
    @Value("${aliyun.oss.accessKeySecret}")
    private String ACCESS_KEY_SECRET;
    @Value("${aliyun.oss.bucketName}")
    private String BUCKET_NAME;
    @Value("${aliyun.oss.bucketDomain}")
    private String BUCKET_DOMAIN;

    private static final Map<String, List<String>> ALLOWED_EXTENSIONS = new HashMap<>();
    private static final Map<String, Long> MAX_FILE_SIZES = new HashMap<>();

    static {
        // 允许的图片类型
        ALLOWED_EXTENSIONS.put("image", Arrays.asList("jpg", "jpeg", "png", "gif", "webp"));
        // 允许的视频类型
        ALLOWED_EXTENSIONS.put("video", Arrays.asList("mp4", "webm", "mov", "avi"));
        // 允许的文档类型
        ALLOWED_EXTENSIONS.put("file", Arrays.asList("txt","md","pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"));

        // 文件大小限制 (字节)
        MAX_FILE_SIZES.put("image", 10 * 1024 * 1024L);      // 10MB
        MAX_FILE_SIZES.put("video", 500 * 1024 * 1024L);     // 500MB
        MAX_FILE_SIZES.put("file", 50 * 1024 * 1024L);   // 50MB
    }

    public boolean checkFileType(String fileExtension, String fileType) {
        List<String> allowedExtensions = ALLOWED_EXTENSIONS.get(fileType);
        if (allowedExtensions != null && allowedExtensions.contains(fileExtension)) {
            return true;
        }
        return false;
    }

    public boolean checkFileSize(String fileType,Long fileSize){
        long maxSize = MAX_FILE_SIZES.get(fileType);
        if (fileSize!=0&&fileSize <= maxSize) {
            return true;
        }
        return false;
    }

    public OSSClient getOSSClient(){
        OSSClient ossClient = new OSSClient(ENDPOINT, ACCESS_KEY_ID, ACCESS_KEY_SECRET);

        if(!ossClient.doesBucketExist(BUCKET_NAME)){
            System.out.println("Bucket 不存在,重新创建...." + BUCKET_NAME);
            CreateBucketRequest createBucketRequest = new CreateBucketRequest(BUCKET_NAME);
            createBucketRequest.setCannedACL(CannedAccessControlList.PublicReadWrite);
            ossClient.createBucket(createBucketRequest);
            System.out.println("Bucket 创建成功...." + BUCKET_NAME);
        }
        return ossClient;
    }

    // 获取OSS文件路径
    public String getURL(String fileType,String fileName){
        StringBuilder url = new StringBuilder();

        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String formattedDate = now.format(formatter);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return  url.append(fileType).append("/").append(formattedDate).append("/")
                .append(SecurityContextHolder.getContext().getAuthentication().getName()).append("/")
                .append(uuid).append("-").append(fileName).toString();
    }

    public void deleteFile(String filePath){
        OSSClient ossClient= getOSSClient();
        try {
            /**
             *  filePath = "http://sk8erboi.oss-cn-fuzhou.aliyuncs.com/video/20250525/19233392121872384/94c0d50c88524b0494f76ae8112b1bf1-肯德基疯狂星期四.mp4"
             * BUCKET_DOMAIN = "http://sk8erboi.oss-cn-fuzhou.aliyuncs.com"
             *
             * => key = "video/20250525/19233392121872384/94c0d50c88524b0494f76ae8112b1bf1-肯德基疯狂星期四.mp4"
             */
            String key = filePath.replace(BUCKET_DOMAIN+"/", "");
            ossClient.deleteObject(BUCKET_NAME, key);
        } catch (Exception e) {
            ossClient.shutdown();
            e.printStackTrace();
            throw new OSSException("删除文件失败");
        }
        ossClient.shutdown();
    }

    public String getBucketName() {
        return this.BUCKET_NAME;
    }

    public String getBucketDomain() {
        return this.BUCKET_DOMAIN;
    }
}
