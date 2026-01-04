package com.yufei.ptw.service.serviceImpl;

import com.yufei.ptw.config.OssProperties;
import com.yufei.ptw.entity.Result;
import com.yufei.ptw.service.ConvertService;
import com.yufei.ptw.service.TaskService;
import com.yufei.ptw.util.OssUtil;

import javax.annotation.Resource;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
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
    
    @Override
    public Result<String> pdfToDoc(MultipartFile file) {
        log.info("正在接收PDF转Doc请求");
        return pdfToWordFormat(file, ".doc", false);
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
        }
//        finally {
//            try {
//                // 清理本地文件
//                Files.deleteIfExists(sourceFilePath);
//                Path docxFilePath = sourceFilePath.getParent().resolve(docxFilename);
//                Files.deleteIfExists(docxFilePath);
//                log.info("已清理本地文件，任务ID: {}", taskId);
//            } catch (IOException e) {
//                log.error("清理本地文件失败，任务ID: {}, 错误信息: {}", taskId, e.getMessage(), e);
//            }
//        }
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
        }
//        finally {
//            try {
//                // 清理本地文件
//                Files.deleteIfExists(sourceFilePath);
//                Path docFilePath = sourceFilePath.getParent().resolve(docFilename);
//                Files.deleteIfExists(docFilePath);
//                log.info("已清理本地文件，任务ID: {}", taskId);
//            } catch (IOException e) {
//                log.error("清理本地文件失败，任务ID: {}, 错误信息: {}", taskId, e.getMessage(), e);
//            }
//        }
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
     * 获取LibreOffice的soffice可执行文件路径
     *
     * @return soffice.exe的完整路径
     * @throws IOException 如果找不到LibreOffice安装
     */
    private String getSofficePath() throws IOException {
        // Windows上LibreOffice的常见安装路径
        String[] commonPaths = {
                "D:\\libeoffice\\program\\soffice.exe"
        };

        // 检查每个常见路径
        for (String path : commonPaths) {
            if (Files.exists(Paths.get(path))) {
                log.debug("找到LibreOffice安装: {}", path);
                return path;
            }
        }

        // 如果没有找到，尝试从环境变量中获取
        String path = System.getenv("PATH");
        if (path != null) {
            String[] directories = path.split(System.getProperty("path.separator"));
            for (String directory : directories) {
                String sofficePath = directory + "\\soffice.exe";
                if (Files.exists(Paths.get(sofficePath))) {
                    log.debug("从环境变量找到LibreOffice: {}", sofficePath);
                    return sofficePath;
                }
            }
        }

        // 如果仍然没有找到，抛出异常
        throw new IOException("未找到LibreOffice安装。请安装LibreOffice并确保soffice.exe在上述路径之一。");
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

    // Word转PDF核心转换方法 - 使用LibreOffice命令行转换
    private void convertWordToPdf(Path inputPath, Path outputPath, String extension, String originalFilename) throws Exception {
        convertFile(inputPath, outputPath, extension, originalFilename, "pdf");
    }

    // PDF转Word核心转换方法 - 使用LibreOffice命令行转换(DOCX格式)
    private void convertPdfToWord(Path inputPath, Path outputPath, String extension, String originalFilename) throws Exception {
        convertFile(inputPath, outputPath, extension, originalFilename, "docx");
    }
    
    // PDF转Word核心转换方法 - 使用LibreOffice命令行转换(DOC格式)
    private void convertPdfToDoc(Path inputPath, Path outputPath, String extension, String originalFilename) throws Exception {
        convertFile(inputPath, outputPath, extension, originalFilename, "doc");
    }

    // 检测PDF文件是否包含可提取的文本内容
    private boolean isPdfWithText(Path pdfPath) throws Exception {
        try (RandomAccessFile raf = new RandomAccessFile(pdfPath.toFile(), "r")) {
            // 检查PDF文件头
            byte[] header = new byte[5];
            raf.read(header);
            String headerStr = new String(header);
            if (!headerStr.startsWith("%PDF")) {
                return false;
            }
            
            // 读取文件内容并查找文本相关模式
            byte[] buffer = new byte[4096];
            long fileLength = raf.length();
            long bytesRead = 0;
            
            // 检查文件中是否包含文本内容标记
            while (bytesRead < fileLength) {
                int read = raf.read(buffer);
                if (read == -1) break;
                
                String content = new String(buffer, 0, read);
                
                // 查找文本对象标记
                if (content.contains("BT") && content.contains("ET")) { // BT = Begin Text, ET = End Text
                    return true;
                }
                // 查找字体定义
                if (content.contains("/Font")) {
                    return true;
                }
                // 查找文本字符串
                if (content.contains("Tj") || content.contains("TJ")) { // Tj/TJ = Show Text operators
                    return true;
                }
                
                bytesRead += read;
                // 只检查前5MB，避免大文件处理过久
                if (bytesRead > 5 * 1024 * 1024) break;
            }
            
            // 如果没有找到文本相关模式，可能是扫描版PDF
            return false;
        } catch (Exception e) {
            log.error("检测PDF文本内容时发生错误: {}", e.getMessage());
            // 出错时默认认为可以尝试转换
            return true;
        }
    }
    
    // 通用文件转换方法 - 使用LibreOffice命令行转换
    private void convertFile(Path inputPath, Path outputPath, String extension, String originalFilename, String targetFormat) throws Exception {
        // 获取LibreOffice的soffice可执行文件路径
        String sofficePath = getSofficePath();
        
        // 确保使用绝对路径
        Path absoluteInputPath = inputPath.toAbsolutePath().normalize();
        Path absoluteOutputParentPath = outputPath.getParent().toAbsolutePath().normalize();
        
        // 构建LibreOffice命令
        // PDF转Word时使用更具体的参数
        String convertParams = targetFormat;
        boolean isPdf = "pdf".equalsIgnoreCase(extension);
        boolean hasText = true;
        boolean isWordFormat = "docx".equals(targetFormat) || "doc".equals(targetFormat);
        
        if (isWordFormat) {
            // 对于PDF转Word，使用更合适的参数格式，明确指定Word格式并启用文本提取
            // 添加更多优化参数以提高文本提取质量
            if ("docx".equals(targetFormat)) {
                convertParams = "docx:MS Word 2007 XML:UTF8";
            } else if ("doc".equals(targetFormat)) {
                convertParams = "doc:MS Word 97";
            }
            
            // 如果是PDF转Word，检测PDF是否包含可提取的文本
            if (isPdf) {
                hasText = isPdfWithText(absoluteInputPath);
                log.debug("PDF文件{}包含可提取文本: {}", absoluteInputPath, hasText);
                
                // 如果是扫描版PDF，添加额外参数尝试OCR（如果LibreOffice支持）
                if (!hasText) {
                    log.info("检测到可能是扫描版PDF文件，将尝试使用OCR参数进行转换");
                }
            }
        }
        
        // 构建LibreOffice命令参数列表
        StringBuilder commandBuilder = new StringBuilder();
        commandBuilder.append(String.format("\"%s\" --headless --nologo --norestore --nofirststartwizard --nodefault ", sofficePath));
        
        // PDF转Word时使用更强大的文本提取参数
        if (isWordFormat && isPdf) {
            // 统一使用完整导入参数，确保所有PDF内容都能被正确转换
            commandBuilder.append("--infilter=writer_pdf_import --enable-ole-support --convert-to %s \"%s\" --outdir \"%s\"");
        } else {
            // 其他转换类型使用默认参数
            commandBuilder.append("--convert-to %s \"%s\" --outdir \"%s\"");
        }
        
        // 构建最终命令字符串
        String command = String.format(
                commandBuilder.toString(),
                convertParams,
                absoluteInputPath.toString(),
                absoluteOutputParentPath.toString()
        );
        
        log.debug("执行LibreOffice命令: {}", command);
        
        // 执行命令
        log.debug("准备执行命令: {}", command);
        ProcessBuilder processBuilder = new ProcessBuilder();
        
        // 直接传递命令和参数，避免转义问题
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            // Windows系统：使用vbscript隐藏窗口执行LibreOffice命令
            StringBuilder fullCommand = new StringBuilder();
            fullCommand.append(sofficePath).append(" ");
            fullCommand.append("--headless ");
            fullCommand.append("--nologo ");
            fullCommand.append("--norestore ");
            fullCommand.append("--nofirststartwizard ");
            fullCommand.append("--nodefault ");
            
            // PDF转Word时使用优化的参数
            if (isWordFormat && isPdf) {
                // 统一使用完整导入参数，确保所有PDF内容都能被正确转换
                fullCommand.append("--infilter=writer_pdf_import ");
                fullCommand.append("--enable-ole-support ");
            }
            
            fullCommand.append("--convert-to ").append(convertParams).append(" ");
            fullCommand.append("\"").append(absoluteInputPath.toString()).append("\" ");
            fullCommand.append("--outdir \"").append(absoluteOutputParentPath.toString()).append("\"");
            
            // 使用VBScript创建完全隐藏的进程
            String vbScript = "Set objShell = CreateObject(\"WScript.Shell\")\n" +
                              "objShell.Run \"" + fullCommand.toString().replace("\"", "\"\"") + "\", 0, False";
            
            try {
                // 将VBScript写入临时文件
                Path tempVbs = Files.createTempFile("libreoffice", ".vbs");
                Files.write(tempVbs, vbScript.getBytes(StandardCharsets.UTF_8));
                
                // 执行VBScript文件
                List<String> cmdList = new ArrayList<>();
                cmdList.add("cscript.exe");
                cmdList.add("//B"); // 批处理模式，不显示脚本标题
                cmdList.add(tempVbs.toAbsolutePath().toString());
                
                processBuilder.command(cmdList);
            } catch (IOException e) {
                log.error("创建临时VBScript文件失败: {}", e.getMessage());
                // 回退到直接执行方式
                List<String> commandList = new ArrayList<>();
                commandList.add(sofficePath);
                commandList.add("--headless");
                commandList.add("--nologo");
                commandList.add("--norestore");
                commandList.add("--nofirststartwizard");
                commandList.add("--nodefault");
                
                // PDF转Word时使用优化的参数
                if (isWordFormat && isPdf) {
                    // 统一使用完整导入参数，确保所有PDF内容都能被正确转换
                    commandList.add("--infilter=writer_pdf_import");
                    commandList.add("--enable-ole-support");
                }
                
                commandList.add("--convert-to");
                commandList.add(convertParams);
                commandList.add(absoluteInputPath.toString());
                commandList.add("--outdir");
                commandList.add(absoluteOutputParentPath.toString());
                
                processBuilder.command(commandList);
            }
        } else {
            // 其他系统：通过bash执行命令
            processBuilder.command(
                "bash", "-c", command
            );
        }
        
        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        // 读取命令输出，防止进程阻塞
        StringBuilder outputBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "GBK"))) {
            // 记录输出信息
            String line;
            while ((line = reader.readLine()) != null) {
                outputBuilder.append(line).append(System.lineSeparator());
                log.debug("LibreOffice输出: {}", line);
            }
        } catch (UnsupportedEncodingException e) {
            log.error("不支持的编码: {}", e.getMessage());
        }

        // 等待命令执行完成
        int exitCode = process.waitFor();
        String output = outputBuilder.toString();

        if (exitCode != 0) {
            log.error("LibreOffice转换失败，退出码: {}, 完整输出: {}", exitCode, output);
            throw new IOException("LibreOffice转换失败，退出码: " + exitCode + ", 输出: " + output);
        } else {
            log.debug("LibreOffice转换成功，退出码: {}, 输出: {}", exitCode, output);
        }

        // 验证转换后的文件是否存在
        Path absoluteOutputPath = outputPath.toAbsolutePath().normalize();
        log.debug("检查输出文件是否存在: {}", absoluteOutputPath);
        
        // 列出输出目录内容，查看实际生成的文件
        try {
            log.debug("输出目录 {} 的内容:", absoluteOutputParentPath);
            DirectoryStream<Path> directoryStream = Files.newDirectoryStream(absoluteOutputParentPath);
            for (Path path : directoryStream) {
                log.debug("  - {}", path.getFileName());
            }
            directoryStream.close();
        } catch (IOException e) {
            log.error("列出目录内容失败: {}", e.getMessage());
        }
        
        if (!Files.exists(absoluteOutputPath)) {
            // 检查可能的文件名变体
            String inputFileName = absoluteInputPath.getFileName().toString();
            String expectedOutputFileName = inputFileName.replaceFirst("\\.[^.]+$", "." + targetFormat);
            
            // 尝试查找所有可能的输出文件
            try {
                DirectoryStream<Path> directoryStream = Files.newDirectoryStream(absoluteOutputParentPath);
                Path foundFile = null;
                
                for (Path path : directoryStream) {
                    String fileName = path.getFileName().toString();
                    log.debug(" 检查文件: {}", fileName);
                    
                    // 检查完全匹配
                    if (fileName.equals(expectedOutputFileName)) {
                        foundFile = path;
                        break;
                    }
                    // 检查不区分大小写的匹配
                    if (fileName.equalsIgnoreCase(expectedOutputFileName)) {
                        foundFile = path;
                        break;
                    }
                    // 检查是否有相似的文件名（去掉空格、特殊字符等）
                    String normalizedFileName = fileName.replaceAll("\\s+", "").toLowerCase();
                    String normalizedExpected = expectedOutputFileName.replaceAll("\\s+", "").toLowerCase();
                    if (normalizedFileName.equals(normalizedExpected)) {
                        foundFile = path;
                        break;
                    }
                    // 检查是否以预期文件名开头
                    if (fileName.startsWith(expectedOutputFileName.substring(0, Math.max(10, expectedOutputFileName.length() - 5)))) {
                        log.debug(" 找到可能的匹配文件: {}", fileName);
                        foundFile = path;
                        break;
                    }
                }
                
                directoryStream.close();
                
                if (foundFile != null) {
                    // 如果找到匹配的文件，重命名为预期文件名
                    log.debug("找到生成的文件: {}, 重命名为: {}", foundFile, absoluteOutputPath);
                    Files.move(foundFile, absoluteOutputPath, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    // 检查是否生成了任何同类型文件
                    DirectoryStream<Path> sameTypeStream = Files.newDirectoryStream(absoluteOutputParentPath, "*" + targetFormat);
                    boolean hasSameType = false;
                    for (Path path : sameTypeStream) {
                        log.debug(" 找到同类型文件: {}", path.getFileName());
                        hasSameType = true;
                    }
                    sameTypeStream.close();
                    
                    String errorMessage = "转换后的文件不存在: " + absoluteOutputPath;
                    if (hasSameType) {
                        errorMessage += "，但找到了其他同类型文件，可能是文件名匹配问题";
                    }
                    throw new IOException(errorMessage);
                }
            } catch (IOException e) {
                log.error("查找生成文件时发生错误: {}", e.getMessage());
                throw new IOException("转换后的文件不存在: " + absoluteOutputPath, e);
            }
        }

        log.debug("LibreOffice转换成功，输出文件: {}", outputPath);
    }
}