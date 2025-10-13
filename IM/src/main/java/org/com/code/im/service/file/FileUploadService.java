package org.com.code.im.service.file;

import com.aliyun.oss.OSSClient;
import com.aliyun.oss.model.*;
import org.apache.tika.io.TikaInputStream;
import org.com.code.im.exception.BadRequestException;
import org.com.code.im.exception.DatabaseException;
import org.com.code.im.exception.OSSException;
import org.com.code.im.exception.VideoParseException;
import org.com.code.im.pojo.dto.PartETagDTO;
import org.com.code.im.utils.OSSUtil;
import org.com.code.im.utils.VideoMetadataExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class FileUploadService {
    @Qualifier("objRedisTemplate")
    @Autowired
    private RedisTemplate redisTemplateObj;
    @Autowired
    private OSSUtil ossUtil;

    // 每段上传文件大小为5MB
    private static final int PART_SIZE = 5 * 1024 * 1024;


    /**
     * 用于大文件的分段上传
     *
     * @param fileName
     * @param fileType
     * @param fileSize 单位是Byte
     * @return
     */
    public Map initMultipartUpload(String fileName, String fileType, long fileSize) {
        /**
         * fileType相当于文件类型，举个例子，image.jpg的文件的type为image，video.mp4的文件的type为video
         * fileExtension相当于文件后缀名,举个例子，image.jpg的文件的jpg
         */
        String fileExtension = fileName.substring(fileName.lastIndexOf(".")+1);
        if (!ossUtil.checkFileType(fileExtension, fileType)) {
            throw new BadRequestException("文件类型不支持");
        }
        if (!ossUtil.checkFileSize(fileType, fileSize)) {
            throw new BadRequestException("文件大小超过限制");
        }

        //给文件分成许多小段
        int totalPartCount = (int) Math.ceil((double) fileSize / PART_SIZE);

        //生成文件在OSS存储的路径
        String filePath = ossUtil.getURL(fileType, fileName);

        /**
         * InitiateMultipartUploadRequest 是阿里云 OSS 提供的一个请求类，用于初始化分段上传过程
         * InitiateMultipartUploadRequest 用来发起一个分段上传任务，并获取一个唯一的上传 uploadId
         * 这个 uploadId 在后续的所有操作中都是必需的，包括上传每个分段、完成分段上传以及取消分段上传等
         */
        OSSClient ossClient = ossUtil.getOSSClient();
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(
                ossUtil.getBucketName(), filePath);

        /**
         * 这行代码的作用是为即将上传到 OSS（对象存储服务）的对象设置内容类型（MIME 类型）
         * 这样，当有人尝试访问该文件时，OSS 就可以根据这个信息提供正确的响应头给客户端（如浏览器），
         * 让客户端知道该如何处理这个文件。
         *
         * 举个例子：
         * 用户上传一张 .png 格式的图片。
         * 在发起分段上传请求之前，确定该文件的 MIME 类型为 image/png。
         * 使用 setContentType("image/png") 来设置该文件的内容类型。
         * 这样，当其他用户查看该头像时，浏览器会自动识别这是一个 PNG 图片，并直接在页面上显示出来，而不是提示用户下载。
         */
        ObjectMetadata objectMetadata = new ObjectMetadata();
        objectMetadata.setContentType(fileType + "/" + fileExtension);
        request.setObjectMetadata(objectMetadata);

        /**
         * 调用 ossClient.initiateMultipartUpload(request) 方法来发送请求。
         * 返回的 InitiateMultipartUploadResult 包含了一个重要的信息——uploadId，
         * 这是分段上传任务的唯一标识符，之后的操作（如上传分段、完成上传）都需要使用它。
         *
         * 一旦你获得了 uploadId，就可以进行以下操作：
         * 上传分段：使用 UploadPartRequest 上传文件的各个部分，每次上传都需要指定 uploadId 和当前分段编号。
         * 完成上传：当所有分段都上传完成后，使用 CompleteMultipartUploadRequest 来合并所有分段并完成上传。
         * 取消上传：如果在上传过程中决定放弃，可以使用 AbortMultipartUploadRequest 来取消上传并清理已上传的部分。
         */
        InitiateMultipartUploadResult result = ossClient.initiateMultipartUpload(request);
        String uploadId = result.getUploadId();

        // 将上传信息存入Redis，用于跟踪上传进度
        String uploadKey = "upload:" + uploadId;
        Map<String, Object> uploadInfo = new HashMap<>();
        uploadInfo.put("filePath", filePath);
        uploadInfo.put("fileName", fileName);
        uploadInfo.put("fileType", fileType);
        uploadInfo.put("fileSize", fileSize);
        uploadInfo.put("createdTime", System.currentTimeMillis());
        uploadInfo.put("status", "初始化上传");
        uploadInfo.put("totalPartCount", totalPartCount);
        uploadInfo.put("completedParts", new ArrayList<>());
        uploadInfo.put("progress", 0);
        // 如果上传的文件是视频，则初始化视频时长为0
        if (fileType.equals("video"))
            uploadInfo.put("duration", 0.0);

        // 给上传文件的状态设置24小时过期
        redisTemplateObj.opsForHash().putAll(uploadKey, uploadInfo);
        redisTemplateObj.expire(uploadKey, 24, TimeUnit.HOURS);
        Map<String, Object> map = new HashMap<>();
        map.put("uploadId", uploadId);
        map.put("totalPartCount", totalPartCount);
        map.put("filePath", filePath);
        return map;
    }

    public void uploadPart(String uploadId, int partNumber, InputStream inputStream) throws Exception {
        //获取分段上传的唯一标识ID和上传信息
        String uploadKey = "upload:" + uploadId;
        Map<Object, Object> uploadInfo = redisTemplateObj.opsForHash().entries(uploadKey);

        if (uploadInfo == null || uploadInfo.isEmpty()) {
            throw new OSSException("上传任务不存在或已过期");
        }
        if(inputStream.available()>PART_SIZE)
            throw new BadRequestException("分片大小不能超过5MB");

        String filePath = (String) uploadInfo.get("filePath");
        String fileType = (String) uploadInfo.get("fileType");
        double updatedDuration = 0;
        OSSClient ossClient = ossUtil.getOSSClient();

        try {
            /**
             * 将输入流数据读入字节数组，以便创建两个独立的输入流
             * 一个流用于计算视频时长，另一个流用于上传到OSS
             */
            byte[] bytes = inputStream.readAllBytes();

            // 如果是视频文件，先用一个输入流计算视频时长
            if (fileType.equals("video")) {
                try (TikaInputStream durationStream = TikaInputStream.get(bytes)) {
                    updatedDuration=(double) uploadInfo.get("duration");
                    updatedDuration +=VideoMetadataExtractor.getDuration(durationStream);
                    uploadInfo.put("duration",updatedDuration);
                } catch (Exception e) {
                    throw new VideoParseException("视频解析异常,重新再试: ");
                }
            }

            // 创建新的输入流用于OSS上传
            InputStream uploadStream = new ByteArrayInputStream(bytes);

            /**
             * 构造分段上传请求:
             *
             * setBucketName: 设置存储桶名称
             * setKey: 设置 OSS 上的文件存储路径
             * setUploadId: 设置上传 uploadId
             * setPartNumber: 设置当前分段的编号（从 1 开始）
             * setInputStream: 设置分段上传的数据流
             * setPartSize: 设置分段大小, 通过 bytes.length 获取实际字节数。
             */
            UploadPartRequest uploadPartRequest = new UploadPartRequest();
            uploadPartRequest.setBucketName(ossUtil.getBucketName());
            uploadPartRequest.setKey(filePath);
            uploadPartRequest.setUploadId(uploadId);
            uploadPartRequest.setPartNumber(partNumber);
            uploadPartRequest.setInputStream(uploadStream);
            uploadPartRequest.setPartSize(bytes.length);

            /**
             * 每个分段（Part）在上传完成后都会生成一个的 ETag，这个 ETag 用于唯一标识该分段的数据。
             * 这个 ETag 是基于分段数据内容计算出来的哈希值，,如果两个分段恰好包含完全相同的数据，则它们的 ETag 将是相同的。
             *
             * 每个分段的 PartNumber 和 ETag 是成对存储的。
             * 客户端可以以任意顺序上传分段（例如先上传 PartNumber=3，再上传 PartNumber=1）。
             * OSS 只负责接收分段并计算，返回对应的 ETag，而不关心分段的上传顺序
             *
             * 如果需要校验上传文件的完整性的话,如下:
             * OSS返回对应的 ETag和客户端在本地计算的clientHash进行比较，如果发现不一致，则这个分段在上传过程中受损，需要重新上传。
             *
             * 最后每个分段上传完后，客户端需要将所有分段的 PartETag（包含 PartNumber 和 ETag）整理成一个列表，并提交给 OSS。
             * OSS 严格按照客户端提供的 completedParts 列表顺序进行合并成一个对象。
             * 如果 completedParts 列表顺序错误，即使每个分段的内容都完整无误，最终合并的对象也会出错。
             * 为了避免因 completedParts 列表顺序错误导致的上传失败，
             * 客户端需要在调用 ossClient.completeMultipartUpload 方法之前，对 completedParts 列表
             * 根据PartNumber的值从小到大进行排序，合并成一个完整对象
             *
             */

            //调用 OSSClient 上传当前分段
            UploadPartResult uploadPartResult = ossClient.uploadPart(uploadPartRequest);

            /**
             * 如果需要校验上传文件的完整性的话,如下:
             * 验证客户端计算的哈希值与服务器返回的ETag是否一致
             * 阿里云使用MD5算法进行哈希计算，因此客户端需要使用MD5算法进行哈希计算,
             *
             * 确保前端和OSS哈希计算一致:
             * 都使用MD5算法
             * 都是对相同的数据块进行计算
             * 输出格式都是32位的十六进制字符串
             * 需要注意的是：
             * 确保客户端和服务端使用相同的分片大小
             * 确保分片数据的边界完全一致
             * 确保使用相同的字符编码（UTF-8）
             *
             *  String serverETag = uploadPartResult.getPartETag().getETag();
             *             if (!serverETag.equals(clientHash)) {
             *                 throw new OSSException("分段数据在传输过程中可能已损坏，请重新上传");
             *             }
             */

            /**
             * 这里我之所以用自定义的PartETagDTO类,而不用阿里云自带的PartETag类是因为
             * 我需要把携带有partNumber和ETag的对象列表放入redis中
             * 这个过程涉及到了redis序列化与反序列化，如果使用阿里云自带的PartETag类，还需要再写一个PartETagDTO类的反序列化器,不然会出问题
             * 所以我选择自定义一个PartETagDTO类,并且redis的GenericJackson2JsonRedisSerializer序列化器也支持我自定义的PartETagDTO类的序列化
             * 这样我就不用重写一个专门针对PartETag类的序列化器，而是直接使用GenericJackson2JsonRedisSerializer序列化器
             * 然后我还为PartETagDTO类写了一个toPartETag()方法,用于将PartETagDTO类转换为PartETag类,因为最终阿里云OSS合并文件时候需要用到PartETag类
             */
            List<PartETagDTO> completedParts = (List<PartETagDTO>) uploadInfo.get("completedParts");
            completedParts.add(new PartETagDTO(partNumber, uploadPartResult.getPartETag().getETag()));

            /**
             * 计算进度百分比：已完成分段数 * 100% / 总分段数 。
             * 更新 Redis 中的 completedParts（已完成分段列表）、progress（进度百分比）和 status（状态为 uploading）。
             */
            int progress = (int) ((completedParts.size() * 100.0) / (int) uploadInfo.get("totalPartCount"));

            uploadInfo.put("completedParts", completedParts);
            uploadInfo.put("progress", progress);
            uploadInfo.put("status", "上传中");

            redisTemplateObj.opsForHash().putAll(uploadKey, uploadInfo);

        } finally {
            /**
             * 防止各种原因上传失败,然后ossClient未释放,
             * 所以这里用 try finally来保证ossClient的释放
             */
            ossClient.shutdown();
        }
    }

    public Map<Object, Object> completeMultipartUpload(String uploadId) {
        String uploadKey = "upload:" + uploadId;
        Map<Object, Object> uploadInfo = redisTemplateObj.opsForHash().entries(uploadKey);
        if (uploadInfo == null || uploadInfo.isEmpty()) {
            throw new OSSException("上传任务不存在或已过期");
        }

        /**
         * PartETag包含每个分段的 PartNumber 和 ETag
         */
        List<PartETagDTO> completedParts = (List<PartETagDTO>) uploadInfo.get("completedParts");
        if (completedParts == null || completedParts.isEmpty()) {
            throw new OSSException("上传失败，请重新上传");
        }
        String filePath = (String) uploadInfo.get("filePath");
        OSSClient ossClient = ossUtil.getOSSClient();

        try {
            /**
             * 为了避免因 completedParts 列表顺序错误导致的上传失败，
             * 客户端需要在调用 ossClient.completeMultipartUpload 方法之前，
             * 对 completedParts 列表根据PartNumber从小到大进行对PartTag排序,然后按照这个顺序对每一个PartETag的ETag进行合并成一个完整对象
             */
            completedParts.sort(Comparator.comparingInt(PartETagDTO::getPartNumber));
            /**
             * 将自定义的类 completedParts 列表中的 对象 转换为 PartETag 对象的列表
             */
            List<PartETag> partETags = completedParts.stream().map(PartETagDTO::toPartETag).collect(Collectors.toList());
            /**
             * 将uploadId编号 和 所有分段的 ETag 提交给 OSS，用于合并分段数据。
             */
            CompleteMultipartUploadRequest completeMultipartUploadRequest
                    = new CompleteMultipartUploadRequest(ossUtil.getBucketName(), filePath, uploadId, partETags);

            /**
             * OSS 在接收到 CompleteMultipartUploadRequest 后，会自动执行以下步骤：
             * 验证 PartNumber 顺序：OSS 按 completedParts 列表中的 PartNumber 顺序来验证每个分段。
             * 验证 ETag：OSS 验证每个分段的 ETag 是否与存储的分段数据的 ETag 一致。
             * 合并分段：如果所有 PartNumber 顺序正确且 ETag 匹配，OSS 将所有分段合并成一个完整的对象。
             */
            ossClient.completeMultipartUpload(completeMultipartUploadRequest);
            
            uploadInfo.put("status", "上传完成");
            uploadInfo.put("progress", 100);
            String url = ossUtil.getBucketDomain() + "/" + filePath;
            /**
             * 把原来的在云端OSS存储的文件路径的filePath替换为最终的可以通过网络访问的url
             */
            uploadInfo.put("filePath", url);
            // 删除redis中保存的上传信息
            redisTemplateObj.delete("upload:" + uploadId);

            return uploadInfo;
        } finally {
            ossClient.shutdown();
        }
    }


    // 取消上传
    public void abortMultipartUpload(String uploadId) {
        String uploadKey = "upload:" + uploadId;
        Map<Object, Object> uploadInfo = redisTemplateObj.opsForHash().entries(uploadKey);

        if (uploadInfo == null || uploadInfo.isEmpty()) {
            throw new OSSException("上传任务不存在或已过期");
        }

        String filePath = (String) uploadInfo.get("filePath");
        OSSClient ossClient = ossUtil.getOSSClient();

        try {
            // 取消分段上传
            AbortMultipartUploadRequest abortRequest
                    = new AbortMultipartUploadRequest(ossUtil.getBucketName(), filePath, uploadId);

            /**
             * ossClient.abortMultipartUpload 方法取消分段上传任务。
             * 这个操作会删除所有已上传的分段数据。
             */
            ossClient.abortMultipartUpload(abortRequest);

            redisTemplateObj.delete(uploadKey);

        } catch (com.aliyun.oss.OSSException e) {
            throw new OSSException("取消分段上传失败: " + e.getMessage());
        } finally {
            ossClient.shutdown();
        }
    }

    // 获取上传进度
    public Map getUploadProgress(String uploadId) {
        String uploadKey = "upload:" + uploadId;
        Map<Object, Object> uploadInfo = redisTemplateObj.opsForHash().entries(uploadKey);

        if (uploadInfo == null || uploadInfo.isEmpty()) {
            throw new DatabaseException("上传任务不存在或已过期");
        }

        Map<String, Object> map = new HashMap<>();
        map.put("fileType",uploadInfo.get("fileType"));
        map.put("status",uploadInfo.get("status"));
        map.put("progress",uploadInfo.get("progress"));
        map.put("totalParts",uploadInfo.get("totalPartCount"));
        map.put("completedParts",uploadInfo.get("completedParts"));
        return map;
    }
}
