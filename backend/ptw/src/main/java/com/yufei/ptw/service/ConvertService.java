package com.yufei.ptw.service;


import com.yufei.ptw.entity.Result;

import org.springframework.web.multipart.MultipartFile;

public interface ConvertService {

    /**
     * 文件转换接口 - 自动识别文件类型进行转换（word转pdf，pdf转word）
     */
    Result<String> change(MultipartFile file);
    
    /**
     * PDF转Word接口 (DOCX格式)
     */
    Result<String> pdfToWord(MultipartFile file);
    

}
