package org.com.code.im.controller.file;

import org.com.code.im.pojo.Files;
import org.com.code.im.responseHandler.ResponseHandler;
import org.com.code.im.service.file.FileService;
import org.com.code.im.service.file.FileUploadService;
import org.com.code.im.service.video.VideoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@RestController
public class FileManageController {

    @Autowired
    private FileUploadService fileUploadService;
    @Autowired
    private VideoService videoService;
    @Autowired
    private FileService fileService;

    @Qualifier("objRedisTemplate")
    @Autowired
    private RedisTemplate redisTemplateObj;

    /**
     * @param id
     * @return
     *
     * 这是视频,图片,其他文件的统一删除接口
     */
    @DeleteMapping("/api/file/delete")
    public ResponseHandler deleteFile(@RequestParam(value = "id") long id,
                                      @RequestParam(value = "fileType") String fileType) {
        /**
         * fileType的两个参数二选一,video or others
         */
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        int deleteCount = 0;
        if(fileType.equals("video")){
            deleteCount = videoService.deleteVideo(id,userId);
        }else if(fileType.equals("others")){
            deleteCount = fileService.deleteFileById(id,userId);
        }
        if (deleteCount > 0) {
            return new ResponseHandler(ResponseHandler.SUCCESS,"文件删除成功");
        }
        else {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "文件删除失败");
        }
    }

    /**
     * 初始化分段上传
     * @param fileName 文件名包含后缀,例如: 地平线4.mp4
     * @param fileType 文件类型,只有3种: video,image,file
     * @param fileSize 单位是Byte
     * @return
     *
     * 这个包括接下来的所有接口
     * 1.初始化分段上传
     * 2.上传分段
     * 3.完成分段上传
     * 4.取消上传
     * 5.获取上传进度
     *
     * 这些接口统一用于视频,图片 和 其他文件的通用接口,
     * 前端通过这些接口上传文件后,获取到文件的url
     *   文件的作者可以通过调用/api/file/delete接口主动撤回(即删除)这些文件
     *
     */
    @PostMapping("/api/file/initMultipartUpload")
    public ResponseHandler initMultipartUpload(
            @RequestParam("fileName") String fileName,
            @RequestParam("fileType") String fileType,
            @RequestParam("fileSize") long fileSize) {

        Map uploadInfo = fileUploadService.initMultipartUpload(fileName, fileType,fileSize);
        return new ResponseHandler(ResponseHandler.SUCCESS, "初始化分段上传成功", uploadInfo);
    }

    // 上传分段
    @PostMapping("/api/file/uploadPart")
    public ResponseHandler uploadPart(
            @RequestParam("uploadId") String uploadId,
            @RequestParam("partNumber") int partNumber,
            @RequestParam("file") MultipartFile file) throws Exception {
        Integer totalPartCount =(Integer)redisTemplateObj.opsForHash().get("upload:"+uploadId, "totalPartCount");
        if (totalPartCount == null) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "分段上传失败,请重新初始化上传");
        }
        if(partNumber<=0||partNumber>totalPartCount)
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "分段上传失败,分段序号大于全部分段的数量");

        /**
         * clientHash是客户端本地计算分段文件的哈希值,用于在分段文件上传后和云端OSS计算出的哈希值进行比较来校验,
         * 如果哈希值相等则文件完整传输了，否则文件传输失败
         */
        fileUploadService.uploadPart(uploadId, partNumber, file.getInputStream());
        return new ResponseHandler(ResponseHandler.SUCCESS, "分段上传成功");
    }

    // 完成分段上传
    @PostMapping("/api/file/completeMultipartUpload")
    public ResponseHandler completeMultipartUpload(@RequestParam("uploadId") String uploadId,
                                                   @RequestBody Files fileInfo) {

        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        String fileType = (String) redisTemplateObj.opsForHash().get("upload:"+uploadId, "fileType");

        Map<String, Object> map;
        if(fileType.equals("video")){
            /**
             * 如果是上传视频的话,fileInfo需要包含的字段是
             * String title
             * List<String> tags
             * String category
             * String description
             */

            // 构建插入视频的参数
            Map<String, Object> videoParams = new HashMap<>();
            videoParams.put("userId", userId);

            String title = fileInfo.getTitle();
            if(title == null|| title.isEmpty())
                return new ResponseHandler(ResponseHandler.SERVER_ERROR, "视频标题不能为空");

            videoParams.put("title", fileInfo.getTitle());

            List<String> tags = fileInfo.getTags();
            if (tags != null && !tags.isEmpty()) {
                videoParams.put("tags", tags);
            }
            String category = fileInfo.getCategory();
            if (category != null && !category.isEmpty()) {
                videoParams.put("category", category);
            }

            String description = fileInfo.getDescription();
            if (description != null && !description.isEmpty()) {
                videoParams.put("description", description);
            }

            /**
             * 只有前面的视频标题不为空,其他字段可空可不空的前提下,才能进行fileUploadService.completeMultipartUpload操作
             * 因为如果一个用户反复故意不输入视频标题,那么每次试错这个接口被调用多次,所以为了保证
             * fileUploadService.completeMultipartUpload永远只被执行一次,所以必须要放在参数检查的代码后面
             */
            Map<Object, Object> uploadInfo = fileUploadService.completeMultipartUpload(uploadId);

            // 把最终可以通过网络访问的url存入数据库表格
            videoParams.put("url", uploadInfo.get("filePath"));
            videoParams.put("duration", uploadInfo.get("duration"));

            // 调用service插入视频记录
            map=videoService.insertVideo(videoParams);

        }else{
            /**
             * 如果是上传其他文件的话,fileInfo需要包含的字段是
             * String fileName
             */
            if(fileInfo.getFileName() == null || fileInfo.getFileName().isEmpty()) {
                return new ResponseHandler(ResponseHandler.SERVER_ERROR, "文件名不能为空");
            }
            Map fileParam = new HashMap();
            fileParam.put("userId", userId);

            fileParam.put("fileName", fileInfo.getFileName());

            /**
             * 只有前面的文件名不为空的前提下,才能进行fileUploadService.completeMultipartUpload操作
             * 因为如果一个用户反复故意不输入文件名,那么每次试错这个接口被调用多次,所以为了保证
             * fileUploadService.completeMultipartUpload永远只被执行一次,所以必须要放在参数检查的代码后面
             */
            Map<Object, Object> uploadInfo = fileUploadService.completeMultipartUpload(uploadId);

            fileParam.put("url", uploadInfo.get("filePath"));
            String fileName = (String) uploadInfo.get("fileName");
            fileParam.put("fileType", fileName.substring(fileName.lastIndexOf(".") + 1));

            map = fileService.insertFile(fileParam);
        }
        // 删除redis中保存的上传信息
        redisTemplateObj.delete("upload:" + uploadId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "上传成功", map);
    }

    // 取消上传
    @DeleteMapping("/api/file/abortMultipartUpload")
    public ResponseHandler abortMultipartUpload(@RequestParam("uploadId") String uploadId) {

        fileUploadService.abortMultipartUpload(uploadId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "上传已取消");
    }

    // 获取上传进度
    @GetMapping("/api/file/uploadProgress")
    public ResponseHandler getUploadProgress(@RequestParam("uploadId") String uploadId) {
        Map progress = fileUploadService.getUploadProgress(uploadId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "获取上传进度成功", progress);
    }

    @GetMapping("/api/file/search")
    public ResponseHandler searchFilesByKeyword(@RequestParam("keyword") String keyword) {
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        List<Files> filesList = fileService.searchFileByKeyWords(keyword,  userId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功.", filesList);
    }

    @GetMapping("/api/file/searchFileByTime")
    public ResponseHandler searchFileByDay(@RequestParam("startTime") String startTime,
                                             @RequestParam("endTime") String endTime) {

        LocalDateTime startDateTime= null;
        LocalDateTime endDateTime= null;
        // 定义日期时间格式化器
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        try {
            // 将字符串转换为LocalDate
            LocalDate startDate = LocalDate.parse(startTime, formatter);
            LocalDate endDate = LocalDate.parse(endTime, formatter);

            // 后续需要 LocalDateTime，结合默认时间进行转换
            startDateTime = startDate.atStartOfDay();
            endDateTime = endDate.atStartOfDay();

        }catch (Exception e) {
            e.printStackTrace();
            return new ResponseHandler(ResponseHandler.BAD_REQUEST, "日期格式错误");
        }
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", fileService.searchFileByTime(startDateTime,endDateTime,userId));
    }

    @GetMapping("/api/file/allFiles")
    public ResponseHandler queryAllFilesForUser() {
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        List<Files> filesList = fileService.queryAllFiles(userId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", filesList);
    }

    /**
     * 根据文件类型查询文件
     * @param fileType 参数枚举: "jpg", "jpeg", "png", "gif", "webp",
     *                          "mp4", "webm", "mov", "avi"
     *                          "txt","md","pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
     * @return
     */
    @GetMapping("/api/file/search/fileByType")
    public ResponseHandler searchFileByType(@RequestParam("fileType") String fileType) {
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        List<Files> filesList = fileService.queryFileByType(fileType, userId);
        return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", filesList);
    }

    /**
     * 根据文件分类查询文件
     * @param category 参数枚举: "video", "image", "file"
     * @return
     */
    @GetMapping("/api/file/search/fileByCategory")
    public ResponseHandler searchFileByCategory(@RequestParam("category") String category) {
        long userId = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        List<Files> filesList;
        
        try {
            switch (category.toLowerCase()) {
                case "video":
                    // 查询视频文件
                    String[] videoTypes = {"mp4", "webm", "mov", "avi", "flv", "mkv", "wmv"};
                    filesList = fileService.queryFileByTypes(videoTypes, userId);
                    break;
                case "image":
                    // 查询图片文件
                    String[] imageTypes = {"jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"};
                    filesList = fileService.queryFileByTypes(imageTypes, userId);
                    break;
                case "file":
                    // 查询文档文件
                    String[] fileTypes = {"txt", "md", "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx"};
                    filesList = fileService.queryFileByTypes(fileTypes, userId);
                    break;
                default:
                    return new ResponseHandler(ResponseHandler.BAD_REQUEST, "不支持的文件分类");
            }
            
            return new ResponseHandler(ResponseHandler.SUCCESS, "查询成功", filesList);
        } catch (Exception e) {
            return new ResponseHandler(ResponseHandler.SERVER_ERROR, "查询失败: " + e.getMessage());
        }
    }
}
