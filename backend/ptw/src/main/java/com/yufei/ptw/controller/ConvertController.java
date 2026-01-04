package com.yufei.ptw.controller;


import com.yufei.ptw.entity.ConvertTask;
import com.yufei.ptw.entity.Result;
import com.yufei.ptw.service.ConvertService;
import com.yufei.ptw.service.TaskService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Api(tags = "文件转换接口")
@RestController
@RequestMapping("/api/convert")
public class ConvertController {

    @Autowired
    private ConvertService convertService;

    @Autowired
    private TaskService taskService;

    @ApiOperation("文件转换接口 - 目前只支持word转pdf")
    @PostMapping("/word-to-pdf")
    public Result<String> convertFile(@ApiParam(name = "file", value = "要转换的Word文件", required = true) @RequestParam("file") MultipartFile file){
        log.info("开始处理文件");
        return convertService.change(file);
    }

    @ApiOperation("PDF转Word接口 (DOCX格式)")
    @PostMapping("/pdf-to-word")
    public Result<String> pdfToWord(@ApiParam(name = "file", value = "要转换的PDF文件", required = true) @RequestParam("file") MultipartFile file){
        log.info("开始处理PDF转Word(DOCX)请求");
        return convertService.pdfToWord(file);
    }
    
    @ApiOperation("PDF转Word接口 (DOC格式)")
    @PostMapping("/pdf-to-doc")
    public Result<String> pdfToDoc(@ApiParam(name = "file", value = "要转换的PDF文件", required = true) @RequestParam("file") MultipartFile file){
        log.info("开始处理PDF转Word(DOC)请求");
        return convertService.pdfToDoc(file);
    }

    @ApiOperation("查询任务状态接口")
    @GetMapping("/task/status")
    public Result<ConvertTask> getTaskStatus(@ApiParam(name = "taskId", value = "任务ID", required = true) @RequestParam("taskId") String taskId){
        log.info("查询任务状态，任务ID: {}", taskId);
        ConvertTask task = taskService.getTask(taskId);
        if (task == null) {
            return Result.error("任务不存在");
        }
        return Result.success(task);
    }
}
