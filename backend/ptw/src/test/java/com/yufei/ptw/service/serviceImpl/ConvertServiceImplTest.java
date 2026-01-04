package com.yufei.ptw.service.serviceImpl;

import com.yufei.ptw.config.OssProperties;
import com.yufei.ptw.entity.Result;
import com.yufei.ptw.service.TaskService;
import com.yufei.ptw.util.OssUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConvertServiceImplTest {

    @InjectMocks
    private ConvertServiceImpl convertService;

    @Mock
    private OssUtil ossUtil;

    @Mock
    private OssProperties ossProperties;

    @Mock
    private TaskService taskService;

    @BeforeEach
    void setUp() throws IOException {
        
        // 设置上传目录
        convertService.uploadDir = "uploads";
        
        // 模拟OSS上传返回URL
        when(ossUtil.uploadStream(any(), anyString(), anyString())).thenReturn("https://example.com/test.docx");
    }

    @Test
    void testPdfToWord() throws IOException {
        // 使用实际的测试PDF文件
        InputStream inputStream = getClass().getResourceAsStream("/yufei简历 (1).pdf");
        assertNotNull(inputStream, "测试PDF文件未找到");
        
        MultipartFile mockFile = new MockMultipartFile(
                "file",
                "test.pdf",
                "application/pdf",
                inputStream
        );

        // 测试PDF转Word方法
        Result<String> result = convertService.pdfToDoc(mockFile);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        
        // 验证任务创建方法被调用
        verify(taskService, times(1)).createTask(anyString(), eq("test.pdf"));
        
        // 验证任务状态更新方法被调用
        verify(taskService, times(1)).updateTaskToProcessing(anyString());
    }

    @Test
    void testPdfToWordWithInvalidFile() throws IOException {
        // 创建一个无效的文件（文本文件伪装成PDF）
        byte[] invalidContent = "This is not a PDF file".getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(invalidContent);
        
        MultipartFile mockFile = new MockMultipartFile(
                "file",
                "invalid.pdf",
                "application/pdf",
                inputStream
        );

        // 测试PDF转Word方法，应该返回错误
        Result<String> result = convertService.pdfToWord(mockFile);
        
        // 验证结果
        assertNotNull(result);
        assertEquals(500, result.getCode());
        assertNotNull(result);
        
        // 验证任务创建方法没有被调用
        verify(taskService, times(0)).createTask(anyString(), anyString());
    }
}