# 文件格式转换API

本项目提供了一个基于Python的Web API，用于实现Word和PDF文件格式的互转功能。

## 功能特性

- ✅ Word转PDF
- ✅ PDF转Word
- ✅ 基于Fl[README.md](README.md)ask的Web API
- ✅ 支持文件上传和下载
- ✅ 跨域请求支持

## 技术栈

- Python 3.x
- Flask
- python-docx
- PyPDF2
- pdf2docx
- comtypes (用于Word转PDF)
- flask-cors

## 安装步骤

1. 确保已安装Python 3.x
2. 克隆或下载本项目到本地
3. 在项目目录下安装依赖：

```bash
pip install python-docx PyPDF2 pdf2docx flask comtypes flask-cors
```

## 运行服务器

在项目目录下执行：

```bash
python app.py
```

服务器将在 `http://localhost:5000` 上运行。

## API接口说明

### 1. Word转PDF

**端点：** `POST /convert/word-to-pdf`

**请求方式：** 表单提交，文件字段名为 `file`

**支持的文件类型：** `.docx`, `.doc`

**响应：** 转换后的PDF文件（附件下载）

**示例请求（使用curl）：**

```bash
curl -X POST http://localhost:5000/convert/word-to-pdf \
  -F "file=@example.docx" \
  -o output.pdf
```

### 2. PDF转Word

**端点：** `POST /convert/pdf-to-word`

**请求方式：** 表单提交，文件字段名为 `file`

**支持的文件类型：** `.pdf`

**响应：** 转换后的Word文件（附件下载）

**示例请求（使用curl）：**

```bash
curl -X POST http://localhost:5000/convert/pdf-to-word \
  -F "file=@example.pdf" \
  -o output.docx
```

## 测试方法

1. 启动服务器
2. 使用API测试工具（如Postman、curl等）发送请求
3. 检查响应是否正确

## 注意事项

1. 本项目使用comtypes调用Microsoft Word进行Word转PDF，因此需要在Windows系统上安装Microsoft Word
2. PDF转Word使用pdf2docx库，转换效果可能因PDF复杂度而异
3. 上传文件大小限制为100MB
4. 服务器运行时会在`uploads`目录中临时存储文件，转换完成后会自动清理

## 错误处理

- `400 Bad Request`：请求参数错误，如未提供文件或文件类型不支持
- `500 Internal Server Error`：转换过程中出错

## 项目结构

```
.
├── app.py              # Flask Web服务器
├── converter.py        # 文件转换核心功能
├── README.md           # 项目说明文档
└── uploads/            # 临时文件目录
```

## 许可证

本项目仅供学习和参考使用。
