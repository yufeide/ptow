package com.yufei.ptw.service.serviceImpl;

import com.yufei.ptw.config.OssProperties;
import com.yufei.ptw.entity.Result;
import com.yufei.ptw.service.ConvertService;
import com.yufei.ptw.service.TaskService;
import com.yufei.ptw.util.OssUtil;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Slf4j
@Service
public class ConvertServiceImpl implements ConvertService {

    @Value("${file.upload-dir}")
    public String uploadDir;

    @Autowired
    private OssUtil ossUtil;

    @Autowired
    private OssProperties ossProperties;

    @Autowired
    private TaskService taskService;

    private final RestTemplate restTemplate = new RestTemplate();
    private final String PYTHON_API_BASE_URL = "http://localhost:5000/convert";

    @Override
    public Result<String> change(MultipartFile file) {
        log.info("正在接收文件转换请求");
        // 1. 验证文件
        if (file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return Result.error("无效的文件名");
        }

        // 2. 获取文件扩展名
        String extension = getFileExtension(originalFilename);

        // 3. 验证文件内容类型
        String contentType = file.getContentType();
        log.debug("文件扩展名: {}, Content-Type: {}", extension, contentType);

        // 4. 更严格的文件类型检测，结合扩展名和内容类型
        String actualExtension = determineActualFileType(file, extension, contentType);
        if (actualExtension == null) {
            return Result.error("只支持.doc和.docx格式的Word文件");
        }

        // 使用实际检测到的文件类型进行处理
        extension = actualExtension;

        try {
            // 3. 创建存储目录
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 4. 生成唯一文件名和任务ID
            String taskId = UUID.randomUUID().toString();
            String sourceFilename = taskId + "_" + originalFilename;
            String pdfFilename = sourceFilename.substring(0, sourceFilename.lastIndexOf('.')) + ".pdf";

            // 5. 保存上传文件
            Path sourceFilePath = uploadPath.resolve(sourceFilename);
            file.transferTo(sourceFilePath);

            // 6. 创建任务记录
            taskService.createTask(taskId, originalFilename);

            // 7. 异步执行转换和上传
            asyncConvertAndUpload(sourceFilePath, pdfFilename, extension, originalFilename, taskId);

            // 8. 立即返回任务ID
            return Result.success("任务已提交，任务ID: " + taskId);
        } catch (IOException e) {
            log.error("文件IO错误: {}", e.getMessage(), e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    @Override
    public Result<String> pdfToWord(MultipartFile file) {
        log.info("正在接收PDF转Word请求");
        return pdfToWordFormat(file, ".docx", true);
    }



    /**
     * PDF转Word通用方法，支持DOCX和DOC格式
     */
    private Result<String> pdfToWordFormat(MultipartFile file, String targetExtension, boolean isDocx) {
        // 1. 验证文件
        if (file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return Result.error("无效的文件名");
        }

        // 2. 获取文件扩展名
        String extension = getFileExtension(originalFilename);

        // 3. 验证文件内容类型
        String contentType = file.getContentType();
        log.debug("文件扩展名: {}, Content-Type: {}", extension, contentType);

        // 4. 更严格的文件类型检测，结合扩展名和内容类型
        String actualExtension = determinePdfFileType(file, extension, contentType);
        if (actualExtension == null) {
            return Result.error("只支持.pdf格式的文件");
        }

        // 使用实际检测到的文件类型进行处理
        extension = actualExtension;

        try {
            // 3. 创建存储目录
            Path uploadPath = Paths.get(uploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            // 4. 生成唯一文件名和任务ID
            String taskId = UUID.randomUUID().toString();
            String sourceFilename = taskId + "_" + originalFilename;
            String targetFilename = sourceFilename.substring(0, sourceFilename.lastIndexOf('.')) + targetExtension;

            // 5. 保存上传文件
            Path sourceFilePath = uploadPath.resolve(sourceFilename);
            file.transferTo(sourceFilePath);

            // 6. 创建任务记录
            taskService.createTask(taskId, originalFilename);

            // 7. 异步执行转换和上传
            if (isDocx) {
                asyncConvertPdfToWord(sourceFilePath, targetFilename, extension, originalFilename, taskId);
            } else {
                asyncConvertPdfToDoc(sourceFilePath, targetFilename, extension, originalFilename, taskId);
            }

            // 8. 立即返回任务ID
            return Result.success("任务已提交，任务ID: " + taskId);
        } catch (IOException e) {
            log.error("文件IO错误: {}", e.getMessage(), e);
            return Result.error("文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 异步执行文件转换和上传
     */
    @Async("taskExecutor")
    public void asyncConvertAndUpload(Path sourceFilePath, String pdfFilename, String extension, String originalFilename, String taskId) {
        log.info("开始异步转换任务，任务ID: {}", taskId);
        try {
            // 1. 更新任务状态为处理中
            taskService.updateTaskToProcessing(taskId);

            // 2. 执行转换
            Path pdfFilePath = sourceFilePath.getParent().resolve(pdfFilename);
            convertWordToPdf(sourceFilePath, pdfFilePath, extension, originalFilename);

            // 3. 上传到阿里云OSS
            String fileUrl = uploadToOss(pdfFilePath, pdfFilename);

            // 4. 更新任务状态为已完成
            taskService.updateTaskToCompleted(taskId, fileUrl);
            log.info("转换任务完成，任务ID: {}, 文件URL: {}", taskId, fileUrl);

        } catch (Exception e) {
            // 5. 更新任务状态为失败
            String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
            taskService.updateTaskToFailed(taskId, errorMessage);
            log.error("异步转换任务失败，任务ID: {}, 错误信息: {}", taskId, errorMessage, e);
        } finally {
            try {
                // 清理本地文件
                Files.deleteIfExists(sourceFilePath);
                Path pdfFilePath = sourceFilePath.getParent().resolve(pdfFilename);
                Files.deleteIfExists(pdfFilePath);
                log.info("已清理本地文件，任务ID: {}", taskId);
            } catch (IOException e) {
                log.error("清理本地文件失败，任务ID: {}, 错误信息: {}", taskId, e.getMessage(), e);
            }
        }
    }

    /**
     * 异步执行PDF转Word转换和上传(DOCX格式)
     */
    @Async("taskExecutor")
    public void asyncConvertPdfToWord(Path sourceFilePath, String docxFilename, String extension, String originalFilename, String taskId) {
        log.info("开始异步PDF转Word(DOCX)任务，任务ID: {}", taskId);
        try {
            // 1. 更新任务状态为处理中
            taskService.updateTaskToProcessing(taskId);

            // 2. 执行转换
            Path docxFilePath = sourceFilePath.getParent().resolve(docxFilename);
            convertPdfToWord(sourceFilePath, docxFilePath, extension, originalFilename);

            // 3. 上传到阿里云OSS
            String fileUrl = uploadToOss(docxFilePath, docxFilename);

            // 4. 更新任务状态为已完成
            taskService.updateTaskToCompleted(taskId, fileUrl);
            log.info("PDF转Word(DOCX)任务完成，任务ID: {}, 文件URL: {}", taskId, fileUrl);

        } catch (Exception e) {
            // 5. 更新任务状态为失败
            String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
            taskService.updateTaskToFailed(taskId, errorMessage);
            log.error("异步PDF转Word(DOCX)任务失败，任务ID: {}, 错误信息: {}", taskId, errorMessage, e);
        } finally {
            try {
                // 清理本地文件
                Files.deleteIfExists(sourceFilePath);
                Path docxFilePath = sourceFilePath.getParent().resolve(docxFilename);
                Files.deleteIfExists(docxFilePath);
                log.info("已清理本地文件，任务ID: {}", taskId);
            } catch (IOException e) {
                log.error("清理本地文件失败，任务ID: {}, 错误信息: {}", taskId, e.getMessage(), e);
            }
        }
    }

    /**
     * 异步执行PDF转Word转换和上传(DOC格式)
     */
    @Async("taskExecutor")
    public void asyncConvertPdfToDoc(Path sourceFilePath, String docFilename, String extension, String originalFilename, String taskId) {
        log.info("开始异步PDF转Word(DOC)任务，任务ID: {}", taskId);
        try {
            // 1. 更新任务状态为处理中
            taskService.updateTaskToProcessing(taskId);

            // 2. 执行转换
            Path docFilePath = sourceFilePath.getParent().resolve(docFilename);
            convertPdfToDoc(sourceFilePath, docFilePath, extension, originalFilename);

            // 3. 上传到阿里云OSS
            String fileUrl = uploadToOss(docFilePath, docFilename);

            // 4. 更新任务状态为已完成
            taskService.updateTaskToCompleted(taskId, fileUrl);
            log.info("PDF转Word(DOC)任务完成，任务ID: {}, 文件URL: {}", taskId, fileUrl);

        } catch (Exception e) {
            // 5. 更新任务状态为失败
            String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误";
            taskService.updateTaskToFailed(taskId, errorMessage);
            log.error("异步PDF转Word(DOC)任务失败，任务ID: {}, 错误信息: {}", taskId, errorMessage, e);
        } finally {
            try {
                // 清理本地文件
                Files.deleteIfExists(sourceFilePath);
                Path docFilePath = sourceFilePath.getParent().resolve(docFilename);
                Files.deleteIfExists(docFilePath);
                log.info("已清理本地文件，任务ID: {}", taskId);
            } catch (IOException e) {
                log.error("清理本地文件失败，任务ID: {}, 错误信息: {}", taskId, e.getMessage(), e);
            }
        }
    }

    /**
     * 将文件上传到阿里云OSS
     *
     * @param filePath 本地文件路径
     * @param fileName 在OSS中存储的文件名
     * @return 文件访问URL
     */
    private String uploadToOss(Path filePath, String fileName) throws IOException {
        try (InputStream inputStream = Files.newInputStream(filePath)) {
            // 确定文件类型
            String contentType = "application/pdf";
            if (fileName.endsWith(".doc")) {
                contentType = "application/msword";
            } else if (fileName.endsWith(".docx")) {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }

            return ossUtil.uploadStream(inputStream, fileName, contentType);
        }
    }

    // 获取文件扩展名
    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex + 1).toLowerCase();
    }

    // 检查是否支持的Word格式
    private boolean isSupportedWordFormat(String extension) {
        return "doc".equals(extension) || "docx".equals(extension);
    }

    /**
     * 更准确地确定文件类型，结合扩展名、Content-Type和文件头签名
     */
    private String determineActualFileType(MultipartFile file, String extension, String contentType) {
        // 检查扩展名
        if ("doc".equals(extension) || "docx".equals(extension)) {
            return extension;
        }

        // 检查Content-Type
        if (contentType != null) {
            if (contentType.equals("application/msword")) {
                return "doc";
            } else if (contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {
                return "docx";
            }
        }

        // 检查文件头签名（魔术数字）
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int bytesRead = is.read(header);
            if (bytesRead >= 4) {
                // DOC文件头：D0 CF 11 E0 A1 B1 1A E1（OLE2 Compound File）
                if (header[0] == (byte) 0xD0 && header[1] == (byte) 0xCF &&
                        header[2] == (byte) 0x11 && header[3] == (byte) 0xE0) {
                    return "doc";
                }
                // DOCX文件头：50 4B 03 04（ZIP文件）
                if (header[0] == (byte) 0x50 && header[1] == (byte) 0x4B &&
                        header[2] == (byte) 0x03 && header[3] == (byte) 0x04) {
                    return "docx";
                }
            }
        } catch (IOException e) {
            log.error("检测文件类型时发生IO错误: {}", e.getMessage(), e);
        }

        return null; // 无法确定或不支持的文件类型
    }

    /**
     * 更准确地确定PDF文件类型，结合扩展名、Content-Type和文件头签名
     */
    private String determinePdfFileType(MultipartFile file, String extension, String contentType) {
        // 检查扩展名
        if ("pdf".equals(extension)) {
            return extension;
        }

        // 检查Content-Type
        if (contentType != null && contentType.equals("application/pdf")) {
            return "pdf";
        }

        // 检查文件头签名（魔术数字）
        try (InputStream is = file.getInputStream()) {
            byte[] header = new byte[8];
            int bytesRead = is.read(header);
            if (bytesRead >= 4) {
                // PDF文件头：%PDF
                if (header[0] == (byte) 0x25 && header[1] == (byte) 0x50 &&
                        header[2] == (byte) 0x44 && header[3] == (byte) 0x46) {
                    return "pdf";
                }
            }
        } catch (IOException e) {
            log.error("检测PDF文件类型时发生IO错误: {}", e.getMessage(), e);
        }

        return null; // 无法确定或不支持的文件类型
    }

    // Word转PDF核心转换方法 - 调用Python API
    private void convertWordToPdf(Path inputPath, Path outputPath, String extension, String originalFilename) throws Exception {
        String url = PYTHON_API_BASE_URL + "/word-to-pdf";
        
        // 构建多部分表单数据
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(inputPath.toFile()));
        
        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        // 创建请求实体
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        
        // 发送请求并获取响应
        log.debug("调用Python API进行Word转PDF: {}", url);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                byte[].class
        );
        
        // 检查响应状态
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new Exception("Python API调用失败: " + response.getStatusCode());
        }
        
        // 保存响应体到输出文件
        byte[] pdfBytes = response.getBody();
        if (pdfBytes == null) {
            throw new Exception("Python API返回空响应");
        }
        
        Files.write(outputPath, pdfBytes);
        log.debug("Word转PDF成功，输出文件: {}", outputPath);
    }

    // PDF转Word核心转换方法 - 调用Python API(DOCX格式)
    private void convertPdfToWord(Path inputPath, Path outputPath, String extension, String originalFilename) throws Exception {
        String url = PYTHON_API_BASE_URL + "/pdf-to-word";
        
        // 构建多部分表单数据
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new FileSystemResource(inputPath.toFile()));
        
        // 设置请求头
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        // 创建请求实体
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        
        // 发送请求并获取响应
        log.debug("调用Python API进行PDF转Word: {}", url);
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                byte[].class
        );
        
        // 检查响应状态
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new Exception("Python API调用失败: " + response.getStatusCode());
        }
        
        // 保存响应体到输出文件
        byte[] docxBytes = response.getBody();
        if (docxBytes == null) {
            throw new Exception("Python API返回空响应");
        }
        
        Files.write(outputPath, docxBytes);
        log.debug("PDF转Word成功，输出文件: {}", outputPath);
    }
    
    // PDF转Word核心转换方法 - 调用Python API(DOC格式)
    private void convertPdfToDoc(Path inputPath, Path outputPath, String extension, String originalFilename) throws Exception {
        // 首先转换为DOCX
        Path docxOutputPath = Paths.get(outputPath.toString().replace(".doc", ".docx"));
        convertPdfToWord(inputPath, docxOutputPath, extension, originalFilename);
        
        // 这里添加DOCX转DOC的逻辑，由于Python API不直接支持DOC格式
        // 我们可以通过重命名的方式，或者在后续添加更复杂的转换逻辑
        // 目前先将DOCX文件复制并重命名为DOC文件
        Files.copy(docxOutputPath, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        log.debug("PDF转Word(DOC)成功，输出文件: {}", outputPath);
    }

    // 检测PDF文件是否包含可提取的文本内容
    private boolean isPdfWithText(Path pdfPath) throws Exception {
        try {
            // 这里可以添加PDF文本检测逻辑
            // 由于现在使用Python API，暂时返回true
            return true;
        } catch (Exception e) {
            log.error("检测PDF文本内容时发生错误: {}", e.getMessage());
            // 出错时默认认为可以尝试转换
            return true;
        }
    }
}
