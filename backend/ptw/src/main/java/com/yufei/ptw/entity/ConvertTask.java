package com.yufei.ptw.entity;

import java.time.LocalDateTime;

/**
 * 转换任务实体类
 */
public class ConvertTask {
    private String taskId;
    private String originalFilename;
    private TaskStatus status;
    private String fileUrl;
    private String errorMessage;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public ConvertTask() {
    }

    public ConvertTask(String taskId, String originalFilename) {
        this.taskId = taskId;
        this.originalFilename = originalFilename;
        this.status = TaskStatus.PENDING;
        this.createTime = LocalDateTime.now();
        this.updateTime = LocalDateTime.now();
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
        this.updateTime = LocalDateTime.now();
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}