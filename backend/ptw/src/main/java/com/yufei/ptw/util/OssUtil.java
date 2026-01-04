package com.yufei.ptw.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectRequest;
import com.yufei.ptw.config.OssProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Component
public class OssUtil {

    private final OSS ossClient;
    private final OssProperties ossProperties;

    @Autowired
    public OssUtil(OssProperties ossProperties) {
        this.ossProperties = ossProperties;
        this.ossClient = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
        );
    }

    /**
     * 上传文件到OSS
     * @param file 文件对象
     * @param fileName 存储的文件名
     * @return 文件访问URL
     */
    public String uploadFile(MultipartFile file, String fileName) throws IOException {
        return uploadStream(file.getInputStream(), fileName, file.getContentType());
    }

    /**
     * 上传文件到OSS
     * @param inputStream 文件流
     * @param fileName 存储的文件名
     * @param contentType 文件类型
     * @return 文件访问URL
     */
    public String uploadStream(InputStream inputStream, String fileName, String contentType) throws IOException {
        // 构建完整对象名称
        String objectName = ossProperties.getFolder() + fileName;

        // 创建ObjectMetadata并设置内容类型
        ObjectMetadata metadata = new ObjectMetadata();
        if (contentType != null) {
            metadata.setContentType(contentType);
        }

        // 创建上传请求
        PutObjectRequest putObjectRequest = new PutObjectRequest(
                ossProperties.getBucketName(),
                objectName,
                inputStream,
                metadata
        );

        // 执行上传
        ossClient.putObject(putObjectRequest);

        // 返回访问URL
        return ossProperties.getUrlPrefix() + objectName;
    }

    /**
     * 上传字节数组到OSS
     * @param bytes 文件字节数组
     * @param fileName 存储的文件名
     * @param contentType 文件类型
     * @return 文件访问URL
     */
    public String uploadBytes(byte[] bytes, String fileName, String contentType) {
        try (InputStream inputStream = new ByteArrayInputStream(bytes)) {
            return uploadStream(inputStream, fileName, contentType);
        } catch (IOException e) {
            log.error("上传字节数组到OSS失败", e);
            return null;
        }
    }

    /**
     * 关闭OSS客户端（在应用关闭时调用）
     */
    public void shutdown() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }
}