package com.yufei.ptw.service;

import com.yufei.ptw.entity.ConvertTask;
import com.yufei.ptw.entity.TaskStatus;

/**
 * 任务服务接口
 */
public interface TaskService {
    /**
     * 创建新任务
     */
    ConvertTask createTask(String taskId, String originalFilename);

    /**
     * 根据任务ID查询任务
     */
    ConvertTask getTask(String taskId);

    /**
     * 更新任务状态为处理中
     */
    void updateTaskToProcessing(String taskId);

    /**
     * 更新任务状态为已完成
     */
    void updateTaskToCompleted(String taskId, String fileUrl);

    /**
     * 更新任务状态为失败
     */
    void updateTaskToFailed(String taskId, String errorMessage);
}