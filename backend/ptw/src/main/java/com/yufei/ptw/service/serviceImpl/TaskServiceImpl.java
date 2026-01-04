package com.yufei.ptw.service.serviceImpl;

import com.yufei.ptw.entity.ConvertTask;
import com.yufei.ptw.entity.TaskStatus;
import com.yufei.ptw.service.TaskService;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 任务服务实现类
 * 使用内存存储任务信息，适合开发和测试环境
 * 生产环境建议使用数据库或Redis存储
 */
@Service
public class TaskServiceImpl implements TaskService {

    // 使用ConcurrentHashMap确保线程安全
    private final Map<String, ConvertTask> taskMap = new ConcurrentHashMap<>();

    @Override
    public ConvertTask createTask(String taskId, String originalFilename) {
        ConvertTask task = new ConvertTask(taskId, originalFilename);
        taskMap.put(taskId, task);
        return task;
    }

    @Override
    public ConvertTask getTask(String taskId) {
        return taskMap.get(taskId);
    }

    @Override
    public void updateTaskToProcessing(String taskId) {
        ConvertTask task = taskMap.get(taskId);
        if (task != null) {
            task.setStatus(TaskStatus.PROCESSING);
        }
    }

    @Override
    public void updateTaskToCompleted(String taskId, String fileUrl) {
        ConvertTask task = taskMap.get(taskId);
        if (task != null) {
            task.setStatus(TaskStatus.COMPLETED);
            task.setFileUrl(fileUrl);
        }
    }

    @Override
    public void updateTaskToFailed(String taskId, String errorMessage) {
        ConvertTask task = taskMap.get(taskId);
        if (task != null) {
            task.setStatus(TaskStatus.FAILED);
            task.setErrorMessage(errorMessage);
        }
    }
}