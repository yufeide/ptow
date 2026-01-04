package com.yufei.ptw.entity;

/**
 * 通用返回结果实体类
 * @param <T> 返回数据类型
 */
public class Result<T> {
    private int code;       // 状态码（200=成功，其他=失败）
    private String msg;     // 返回消息
    private T data;         // 返回数据
    private long timestamp; // 时间戳

    // 成功静态方法（无数据）
    public static <T> Result<T> success() {
        return success(null);
    }

    // 成功静态方法（带数据）
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMsg("操作成功");
        result.setData(data);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    // 失败静态方法
    public static <T> Result<T> error(int code, String msg) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMsg(msg);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }
    //失败静态方法 不带数据
    public static <T> Result<T> error(int code) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setTimestamp(System.currentTimeMillis());
        return result;
    }

    // 重载失败方法（默认400错误）
    public static <T> Result<T> error(String msg) {
        return error(400, msg);
    }

    // 检查是否成功
    public boolean isSuccess() {
        return code == 200;
    }

    // Getter & Setter 方法
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}