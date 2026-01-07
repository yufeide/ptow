<script setup>
import { ref, computed } from 'vue'

// 状态管理
const selectedFile = ref(null)
const convertDirection = ref('wordToPdf') // wordToPdf 或 pdfToWord
const isConverting = ref(false)
const convertedFile = ref(null)
const errorMsg = ref('')
const isLoading = ref(false)
const taskId = ref('')
const taskStatus = ref('')
const pollingInterval = ref(null)
const isDragging = ref(false)

// 后端API配置
const backendApiConfig = {
  baseUrl: 'http://localhost:8080/api'
}

// 文件选择
const selectFile = (event) => {
  const file = event.target.files[0]
  if (!file) return
  
  // 验证文件类型
  const allowedTypes = ['.doc', '.docx', '.pdf']
  const fileExtension = '.' + file.name.split('.').pop().toLowerCase()
  
  if (!allowedTypes.includes(fileExtension)) {
    errorMsg.value = '请选择.doc、.docx或.pdf格式的文件'
    return
  }
  
  selectedFile.value = file
  errorMsg.value = ''
  convertedFile.value = null
  
  // 自动检测转换方向
  if (fileExtension === '.pdf') {
    convertDirection.value = 'pdfToWord'
  } else {
    convertDirection.value = 'wordToPdf'
  }
}

// 处理文件拖放
const handleDrop = (event) => {
  isDragging.value = false
  const file = event.dataTransfer.files[0]
  if (!file) return
  
  // 验证文件类型
  const allowedTypes = ['.doc', '.docx', '.pdf']
  const fileExtension = '.' + file.name.split('.').pop().toLowerCase()
  
  if (!allowedTypes.includes(fileExtension)) {
    errorMsg.value = '请选择.doc、.docx或.pdf格式的文件'
    return
  }
  
  selectedFile.value = file
  errorMsg.value = ''
  convertedFile.value = null
  
  // 自动检测转换方向
  if (fileExtension === '.pdf') {
    convertDirection.value = 'pdfToWord'
  } else {
    convertDirection.value = 'wordToPdf'
  }
}

// 格式化文件大小
const formatFileSize = (size) => {
  if (size < 1024) {
    return size + ' B'
  } else if (size < 1024 * 1024) {
    return (size / 1024).toFixed(2) + ' KB'
  } else {
    return (size / (1024 * 1024)).toFixed(2) + ' MB'
  }
}

// 生成转换后的文件名
const generateConvertedFileName = () => {
  if (!selectedFile.value) return ''
  
  const originalName = selectedFile.value.name
  const baseName = originalName.substring(0, originalName.lastIndexOf('.'))
  if (convertDirection.value === 'wordToPdf') {
    return baseName + '.pdf'
  } else {
    return baseName + '.docx'
  }
}

// 查询任务状态
const checkTaskStatus = async () => {
  if (!taskId.value) {
    console.error('任务ID为空，无法查询任务状态')
    return
  }
  
  if (taskId.value === 'test') {
    console.error('无效的任务ID: test，无法查询任务状态')
    return
  }
  
  try {
    const apiUrl = `${backendApiConfig.baseUrl}/convert/task/status?taskId=${taskId.value}`
    console.log('调用API:', apiUrl)
    const response = await fetch(apiUrl, {
      credentials: 'include' // 包含凭证信息
    })
    
    if (!response.ok) {
      throw new Error('查询任务状态失败')
    }
    
    const taskResult = await response.json()
    
    // 更新任务状态显示
    if (taskResult.data && taskResult.data.status) {
      taskStatus.value = taskResult.data.status
    }
    
    if (taskResult.success && taskResult.data) {
      if (taskResult.data.status === 'COMPLETED' && taskResult.data.fileUrl) {
        // 任务完成，处理转换结果
        let outputExtension = '.pdf'
        if (convertDirection.value === 'pdfToWord') {
          outputExtension = '.docx'
        }
        convertedFile.value = {
          name: taskResult.data.originalFilename.replace(/\.[^/.]+$/, '') + outputExtension,
          size: 0, // 后端未返回文件大小
          url: taskResult.data.fileUrl.trim() // 移除可能的前后空格和引号
        }
        
        // 停止轮询
        if (pollingInterval.value) {
          clearInterval(pollingInterval.value)
          pollingInterval.value = null
        }
        
        taskId.value = ''
      } else if (taskResult.data.status === 'FAILED') {
        // 任务失败
        throw new Error('转换失败：' + (taskResult.data.errorMessage || '未知错误'))
      }
    }
    // 如果任务仍在处理中，继续轮询
    
  } catch (error) {
    let errorMessage = error.message || '未知错误'
    // 添加更详细的错误信息
    if (errorMessage === 'Failed to fetch') {
      errorMessage = '网络请求失败，请检查后端服务是否正常运行或网络连接是否正常'
    }
    errorMsg.value = '查询任务状态失败：' + errorMessage
    console.error('查询任务状态出错：', error)
    
    // 停止轮询
    if (pollingInterval.value) {
      clearInterval(pollingInterval.value)
      pollingInterval.value = null
    }
    
    taskId.value = ''
    taskStatus.value = ''
  }
}

// 转换文件
const convertFile = async () => {
  if (!selectedFile.value) {
    errorMsg.value = '请先选择文件'
    return
  }
  
  isConverting.value = true
  errorMsg.value = ''
  convertedFile.value = null
  taskId.value = ''
  taskStatus.value = ''
  
  // 如果有正在进行的轮询，先停止
  if (pollingInterval.value) {
    clearInterval(pollingInterval.value)
    pollingInterval.value = null
  }
  
  try {
    // 准备表单数据
    const formData = new FormData()
    formData.append('file', selectedFile.value)
    formData.append('direction', convertDirection.value)
    
    // 调用后端转换API
    isLoading.value = true
    // 根据转换方向选择不同的API端点
    const apiEndpoint = convertDirection.value === 'wordToPdf' ? 'word-to-pdf' : 'pdf-to-word'
    const apiUrl = `${backendApiConfig.baseUrl}/convert/${apiEndpoint}`
    console.log('调用API:', apiUrl)
    const response = await fetch(apiUrl, {
      method: 'POST',
      body: formData,
      credentials: 'include' // 包含凭证信息
    })
    
    if (!response.ok) {
      throw new Error('转换请求失败')
    }
    
    const convertResult = await response.json()
    
    // 处理转换结果
    if (convertResult.success && convertResult.data) {
      console.log('后端返回的完整数据:', convertResult.data)
      // 检查返回数据类型，处理不同格式
      if (typeof convertResult.data === 'string') {
        // 旧格式：字符串包含任务ID
        const taskIdMatch = convertResult.data.match(/任务ID: (\S+)/)
        if (taskIdMatch && taskIdMatch[1]) {
          // 异步任务，开始轮询任务状态
          const extractedTaskId = taskIdMatch[1]
          console.log('从字符串中提取的任务ID:', extractedTaskId)
          if (extractedTaskId && extractedTaskId !== 'test') {
            taskId.value = extractedTaskId
            taskStatus.value = 'processing'
            
            // 开始轮询，每2秒查询一次
            pollingInterval.value = setInterval(checkTaskStatus, 2000)
          } else {
            console.error('无效的任务ID:', extractedTaskId)
            throw new Error('无效的任务ID')
          }
        } else {
          throw new Error('无法解析任务ID')
        }
      } else if (typeof convertResult.data === 'object' && convertResult.data.taskId) {
        // 新格式：对象包含taskId字段
        const returnedTaskId = convertResult.data.taskId
        console.log('从对象中提取的任务ID:', returnedTaskId)
        if (returnedTaskId && returnedTaskId !== 'test') {
          taskId.value = returnedTaskId
          taskStatus.value = 'processing'
          
          // 开始轮询，每2秒查询一次
          pollingInterval.value = setInterval(checkTaskStatus, 2000)
        } else {
          console.error('无效的任务ID:', returnedTaskId)
          throw new Error('无效的任务ID')
        }
      } else {
        throw new Error('无法解析任务ID')
      }
    } else if (convertResult.success && convertResult.url) {
      // 同步转换完成
      convertedFile.value = {
        name: convertResult.fileName || generateConvertedFileName(),
        size: convertResult.fileSize || 0,
        url: `${backendApiConfig.baseUrl}${convertResult.url}`
      }
    } else {
      throw new Error('转换失败：' + (convertResult.message || '未知错误'))
    }
  } catch (error) {
    let errorMessage = error.message || '未知错误'
    // 添加更详细的错误信息
    if (errorMessage === 'Failed to fetch') {
      errorMessage = '网络请求失败，请检查后端服务是否正常运行或网络连接是否正常'
    }
    errorMsg.value = '转换失败：' + errorMessage
    console.error('转换过程出错：', error)
    
    // 停止轮询
    if (pollingInterval.value) {
      clearInterval(pollingInterval.value)
      pollingInterval.value = null
    }
  } finally {
    isConverting.value = false
    isLoading.value = false
  }
}

// 下载文件
const downloadFile = () => {
  if (!convertedFile.value) return
  
  try {
    isLoading.value = true
    const url = convertedFile.value.url
    const a = document.createElement('a')
    a.href = url
    a.download = convertedFile.value.name
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
  } catch (error) {
    errorMsg.value = '文件下载失败：' + (error.message || '未知错误')
    console.error('下载失败：', error)
  } finally {
    isLoading.value = false
  }
}

// 预览文件
const previewFile = () => {
  if (!convertedFile.value) return
  
  try {
    isLoading.value = true
    const url = convertedFile.value.url
    window.open(url, '_blank')
  } catch (error) {
    errorMsg.value = '文件预览失败：' + (error.message || '未知错误')
    console.error('预览失败：', error)
  } finally {
    isLoading.value = false
  }
}

// 计算属性
const isConvertButtonDisabled = computed(() => {
  return !selectedFile.value || isConverting.value
})
</script>

<template>
  <div class="container">
    <h1 class="title">PDF 和 Word 文件互转</h1>
    
    <!-- 文件选择区域 -->
    <div 
      class="file-selector" 
      @dragover.prevent 
      @drop.prevent="handleDrop"
      @dragenter.prevent="isDragging = true"
      @dragleave.prevent="isDragging = false"
      :class="{ 'dragging': isDragging }"
    >
      <label class="file-label">
        <input type="file" accept=".doc,.docx,.pdf" @change="selectFile" />
        <span>选择文件</span>
      </label>
    </div>
    
    <!-- 已选择文件信息 -->
    <div class="file-info" v-if="selectedFile">
      <div class="file-name">{{ selectedFile.name }}</div>
      <div class="file-size">{{ formatFileSize(selectedFile.size) }}</div>
    </div>
    
    <!-- 转换选项 -->
    <div class="convert-options" v-if="selectedFile">
      <div class="option-title">转换方向：</div>
      <div class="radio-group">
        <label>
          <input type="radio" v-model="convertDirection" value="wordToPdf" />
          Word → PDF
        </label>
        <label>
          <input type="radio" v-model="convertDirection" value="pdfToWord" />
          PDF → Word
        </label>
      </div>
    </div>
    
    <!-- 转换按钮 -->
    <button 
      class="convert-btn" 
      @click="convertFile" 
      :disabled="isConvertButtonDisabled"
    >
      {{ isConverting ? '转换中...' : '开始转换' }}
    </button>
    
    <!-- 任务状态 -->
    <div class="task-status" v-if="taskStatus">
      <div class="status-label">任务状态：</div>
      <div class="status-value">
        {{ taskStatus === 'processing' ? '正在处理中...' : taskStatus }}
      </div>
      <div class="task-id" v-if="taskId">任务ID：{{ taskId }}</div>
    </div>
    
    <!-- 转换结果 -->
    <div class="result-box" v-if="convertedFile">
      <div class="result-title">转换成功！</div>
      <div class="file-name">{{ convertedFile.name }}</div>
      <div class="result-buttons">
        <button class="btn btn-primary" @click="previewFile">预览</button>
        <button class="btn btn-secondary" @click="downloadFile">下载</button>
      </div>
    </div>
    
    <!-- 加载提示 -->
    <div class="loading-overlay" v-if="isLoading">
      <div class="loading-content">
        <div class="spinner"></div>
        <div>处理中...</div>
      </div>
    </div>
    
    <!-- 错误提示 -->
    <div class="error" v-if="errorMsg">
      {{ errorMsg }}
    </div>
  </div>
</template>

<style scoped>
/* 全局样式重置 */
* {
  box-sizing: border-box;
  margin: 0;
  padding: 0;
}

/* 赛博朋克背景 */
body {
  background-color: #0a0a1a;
  background-image: 
    radial-gradient(circle at 20% 80%, rgba(121, 68, 154, 0.3) 0%, transparent 50%),
    radial-gradient(circle at 80% 20%, rgba(255, 109, 194, 0.3) 0%, transparent 50%),
    radial-gradient(circle at 40% 40%, rgba(0, 255, 255, 0.1) 0%, transparent 50%),
    linear-gradient(90deg, rgba(0, 255, 255, 0.05) 1px, transparent 1px),
    linear-gradient(rgba(255, 109, 241, 0.05) 1px, transparent 1px);
  background-size: 100% 100%, 100% 100%, 100% 100%, 50px 50px, 50px 50px;
  min-height: 100vh;
  font-family: 'Orbitron', 'Rajdhani', 'Courier New', monospace;
  overflow-x: hidden;
  position: relative;
}

/* 数字雨效果增强 */
body::before {
  content: '';
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: 
    url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100"><text x="50%" y="50%" font-family="monospace" font-size="10" fill="rgba(0,255,255,0.1)" text-anchor="middle">01</text></svg>'),
    url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100"><text x="50%" y="50%" font-family="monospace" font-size="10" fill="rgba(255,109,241,0.1)" text-anchor="middle">10</text></svg>'),
    url('data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" width="100" height="100" viewBox="0 0 100 100"><text x="50%" y="50%" font-family="monospace" font-size="8" fill="rgba(0,255,255,0.05)" text-anchor="middle">0011</text></svg>');
  background-size: 40px 40px, 60px 60px, 80px 80px;
  pointer-events: none;
  z-index: -1;
  animation: digitalRain 15s linear infinite, digitalRainShift 30s ease-in-out infinite;
}

body::after {
  content: '';
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background: 
    linear-gradient(45deg, transparent 49%, rgba(0, 255, 255, 0.05) 50%, transparent 51%),
    linear-gradient(-45deg, transparent 49%, rgba(255, 109, 241, 0.05) 50%, transparent 51%),
    linear-gradient(90deg, transparent 0%, rgba(0, 255, 255, 0.02) 50%, transparent 100%);
  background-size: 20px 20px, 30px 30px, 100% 4px;
  background-position: 0 0, 10px 10px, 0 0;
  pointer-events: none;
  z-index: -1;
  animation: gridMove 20s linear infinite, backgroundScanLine 8s linear infinite;
}

/* 背景扫描线效果 */
@keyframes backgroundScanLine {
  0% {
    background-position: 0 0, 10px 10px, 0 -100%;
  }
  100% {
    background-position: 0 0, 10px 10px, 0 100%;
  }
}

@keyframes digitalRain {
  0% {
    transform: translateY(-100%);
  }
  100% {
    transform: translateY(100vh);
  }
}

@keyframes digitalRainShift {
  0%, 100% {
    transform: translateX(0);
  }
  50% {
    transform: translateX(20px);
  }
}

@keyframes gridMove {
  0% {
    transform: translateX(0) translateY(0);
  }
  100% {
    transform: translateX(20px) translateY(20px);
  }
}

.container {
  max-width: 650px;
  margin: 0 auto;
  padding: 50px 30px;
  position: relative;
  backdrop-filter: blur(15px);
  background: linear-gradient(135deg, rgba(10, 10, 26, 0.9) 0%, rgba(15, 15, 35, 0.8) 100%);
  border: 1px solid rgba(0, 255, 255, 0.3);
  border-radius: 10px;
  box-shadow: 
    0 0 20px rgba(0, 255, 255, 0.4),
    0 0 40px rgba(255, 109, 241, 0.2),
    inset 0 0 20px rgba(0, 255, 255, 0.1),
    inset 0 0 40px rgba(255, 109, 241, 0.05);
  margin-top: 60px;
  margin-bottom: 60px;
  overflow: hidden;
  animation: containerFloat 4s ease-in-out infinite;
}

/* 容器浮动效果 */
@keyframes containerFloat {
  0%, 100% {
    transform: translateY(0px);
  }
  50% {
    transform: translateY(-10px);
  }
}

/* 容器发光边框效果 */
.container::before {
  content: '';
  position: absolute;
  top: -2px;
  left: -2px;
  right: -2px;
  bottom: -2px;
  background: linear-gradient(45deg, #00ffff, #ff6df1, #00ffff, #ff6df1);
  border-radius: 12px;
  z-index: -1;
  animation: borderGlow 3s linear infinite;
  opacity: 0.7;
}

@keyframes borderGlow {
  0% {
    background-position: 0% 50%;
  }
  50% {
    background-position: 100% 50%;
  }
  100% {
    background-position: 0% 50%;
  }
}

/* 标题样式 - 霓虹效果增强 */
.title {
  font-size: 36px;
  font-weight: bold;
  text-align: center;
  margin-bottom: 50px;
  color: #00ffff;
  text-shadow: 
    0 0 5px #00ffff,
    0 0 10px #00ffff,
    0 0 20px #00ffff,
    0 0 40px #00ffff,
    0 0 80px #00ffff;
  letter-spacing: 3px;
  animation: neonPulse 2s ease-in-out infinite alternate, titleFloat 3s ease-in-out infinite, glitch 3s infinite;
  position: relative;
}

/* 故障艺术效果 */
@keyframes glitch {
  0%, 90%, 100% {
    transform: translate(0);
  }
  91% {
    transform: translate(-2px, 2px);
    text-shadow: 
      2px 0 0 rgba(255, 0, 0, 0.7),
      -2px 0 0 rgba(0, 255, 255, 0.7);
  }
  92% {
    transform: translate(2px, -2px);
    text-shadow: 
      -2px 0 0 rgba(255, 0, 0, 0.7),
      2px 0 0 rgba(0, 255, 255, 0.7);
  }
  93% {
    transform: translate(-2px, -2px);
  }
  94% {
    transform: translate(2px, 2px);
  }
  95% {
    transform: translate(0);
  }
}

.title::after {
  content: 'FILE CONVERTER 3000';
  position: absolute;
  top: 100%;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12px;
  color: rgba(255, 109, 241, 0.8);
  text-shadow: 0 0 5px rgba(255, 109, 241, 0.8);
  letter-spacing: 4px;
  margin-top: 10px;
  font-family: 'Courier New', monospace;
}

@keyframes neonPulse {
  from {
    text-shadow: 
      0 0 5px #00ffff,
      0 0 10px #00ffff,
      0 0 20px #00ffff,
      0 0 40px #00ffff,
      0 0 80px #00ffff;
  }
  to {
    text-shadow: 
      0 0 10px #00ffff,
      0 0 20px #00ffff,
      0 0 30px #00ffff,
      0 0 60px #00ffff,
      0 0 120px #00ffff;
  }
}

@keyframes titleFloat {
  0%, 100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-5px);
  }
}

/* 文件选择区域 */
.file-selector {
  margin-bottom: 40px;
  text-align: center;
  position: relative;
  transition: all 0.3s ease;
}

/* 拖放时的样式 */
.file-selector.dragging {
  transform: scale(1.05);
}

.file-selector.dragging .file-label {
  background: linear-gradient(135deg, rgba(255, 109, 241, 0.4) 0%, rgba(121, 68, 154, 0.3) 100%);
  box-shadow: 
    0 0 30px rgba(255, 109, 241, 0.8),
    inset 0 0 25px rgba(255, 109, 241, 0.4);
  border-color: #ff6df1;
}

.file-selector.dragging .file-label span {
  color: #ffffff;
  text-shadow: 0 0 10px #ff6df1, 0 0 20px #ff6df1;
}

.file-label {
  display: inline-block;
  padding: 20px 40px;
  background: linear-gradient(135deg, rgba(121, 68, 154, 0.3) 0%, rgba(100, 50, 130, 0.2) 100%);
  color: #ff6df1;
  border: 1px solid #ff6df1;
  border-radius: 6px;
  cursor: pointer;
  font-size: 18px;
  font-weight: bold;
  transition: all 0.3s ease;
  text-shadow: 0 0 5px #ff6df1, 0 0 10px #ff6df1;
  box-shadow: 
    0 0 15px rgba(255, 109, 241, 0.4),
    inset 0 0 5px rgba(255, 109, 241, 0.1);
  letter-spacing: 2px;
  position: relative;
  overflow: hidden;
  text-transform: uppercase;
  font-family: 'Orbitron', monospace;
}

.file-label::before {
  content: '';
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, transparent, rgba(255, 109, 241, 0.3), transparent);
  transition: left 0.5s ease;
}

.file-label:hover::before {
  left: 100%;
}

.file-label:hover {
  background: linear-gradient(135deg, rgba(121, 68, 154, 0.4) 0%, rgba(100, 50, 130, 0.3) 100%);
  box-shadow: 
    0 0 25px rgba(255, 109, 241, 0.7),
    inset 0 0 20px rgba(255, 109, 241, 0.3);
  transform: translateY(-3px) scale(1.02);
  border-color: #ff8eff;
}

.file-label input[type="file"] {
  display: none;
}

/* 文件选择区域装饰 */
.file-selector::after {
  content: 'DRAG & DROP';
  position: absolute;
  bottom: -25px;
  left: 50%;
  transform: translateX(-50%);
  font-size: 12px;
  color: rgba(0, 255, 255, 0.6);
  text-shadow: 0 0 3px rgba(0, 255, 255, 0.6);
  letter-spacing: 2px;
  font-family: 'Courier New', monospace;
  animation: pulse 2s ease-in-out infinite alternate;
}

/* 文件信息 */
.file-info {
  background: linear-gradient(135deg, rgba(10, 10, 30, 0.9) 0%, rgba(15, 15, 40, 0.8) 100%);
  border: 1px solid rgba(0, 255, 255, 0.4);
  padding: 25px;
  border-radius: 6px;
  margin-bottom: 35px;
  box-shadow: 
    inset 0 0 15px rgba(0, 255, 255, 0.15),
    0 0 10px rgba(0, 255, 255, 0.2);
  position: relative;
  overflow: hidden;
}

.file-info::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: linear-gradient(90deg, transparent, #00ffff, transparent);
  animation: scanLine 3s ease-in-out infinite;
}

.file-name {
  font-size: 16px;
  color: #00ffff;
  margin-bottom: 12px;
  word-break: break-all;
  text-shadow: 0 0 5px #00ffff, 0 0 10px #00ffff;
  font-family: 'Courier New', monospace;
  position: relative;
}

.file-name::before {
  content: '>';
  color: #ff6df1;
  margin-right: 8px;
  text-shadow: 0 0 5px #ff6df1;
}

.file-size {
  font-size: 14px;
  color: #ff6df1;
  text-shadow: 0 0 5px #ff6df1;
  font-family: 'Courier New', monospace;
  background-color: rgba(10, 10, 20, 0.8);
  padding: 6px 12px;
  border-radius: 4px;
  display: inline-block;
  border: 1px solid rgba(255, 109, 241, 0.3);
}

/* 转换选项 */
.convert-options {
  background: linear-gradient(135deg, rgba(10, 10, 30, 0.9) 0%, rgba(15, 15, 40, 0.8) 100%);
  border: 1px solid rgba(255, 109, 241, 0.4);
  padding: 25px;
  border-radius: 6px;
  margin-bottom: 35px;
  box-shadow: 
    inset 0 0 15px rgba(255, 109, 241, 0.15),
    0 0 10px rgba(255, 109, 241, 0.2);
  position: relative;
}

.convert-options::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 2px;
  background: linear-gradient(90deg, transparent, #ff6df1, transparent);
  animation: scanLine 3s ease-in-out infinite reverse;
}

.option-title {
  font-size: 18px;
  color: #ff6df1;
  margin-bottom: 20px;
  font-weight: bold;
  text-shadow: 0 0 5px #ff6df1, 0 0 10px #ff6df1;
  text-transform: uppercase;
  letter-spacing: 2px;
  font-family: 'Orbitron', monospace;
}

.radio-group {
  display: flex;
  gap: 40px;
  justify-content: center;
  flex-wrap: wrap;
}

.radio-group label {
  display: flex;
  align-items: center;
  cursor: pointer;
  color: #00ffff;
  text-shadow: 0 0 5px #00ffff, 0 0 10px #00ffff;
  font-size: 16px;
  transition: all 0.3s ease;
  padding: 10px 20px;
  border-radius: 4px;
  border: 1px solid transparent;
  position: relative;
  overflow: hidden;
}

.radio-group label:hover {
  color: #ff6df1;
  text-shadow: 0 0 8px #ff6df1, 0 0 15px #ff6df1;
  border-color: rgba(255, 109, 241, 0.5);
  background-color: rgba(121, 68, 154, 0.1);
}

/* 自定义单选按钮 */
.radio-group input[type="radio"] {
  appearance: none;
  width: 20px;
  height: 20px;
  border: 2px solid #00ffff;
  border-radius: 50%;
  margin-right: 12px;
  position: relative;
  transition: all 0.3s ease;
  box-shadow: 0 0 5px rgba(0, 255, 255, 0.5);
}

.radio-group input[type="radio"]:hover {
  border-color: #ff6df1;
  box-shadow: 0 0 10px rgba(255, 109, 241, 0.7);
  transform: scale(1.1);
}

.radio-group input[type="radio"]:checked {
  border-color: #ff6df1;
  box-shadow: 0 0 15px rgba(255, 109, 241, 0.8);
  background-color: rgba(255, 109, 241, 0.2);
}

.radio-group input[type="radio"]:checked::after {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 10px;
  height: 10px;
  background-color: #ff6df1;
  border-radius: 50%;
  box-shadow: 0 0 10px #ff6df1, 0 0 20px #ff6df1;
  animation: pulse 1s ease-in-out infinite alternate;
}

@keyframes scanLine {
  0%, 100% {
    transform: translateX(-100%);
  }
  50% {
    transform: translateX(100%);
  }
}

/* 转换按钮 */
.convert-btn {
  width: 100%;
  padding: 20px;
  background: linear-gradient(135deg, rgba(0, 255, 255, 0.15) 0%, rgba(0, 200, 200, 0.1) 100%);
  color: #00ffff;
  border: 1px solid #00ffff;
  border-radius: 6px;
  font-size: 20px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.3s ease;
  text-shadow: 0 0 8px #00ffff, 0 0 16px #00ffff;
  box-shadow: 
    0 0 20px rgba(0, 255, 255, 0.4),
    inset 0 0 5px rgba(0, 255, 255, 0.2);
  letter-spacing: 2px;
  position: relative;
  overflow: hidden;
  text-transform: uppercase;
  font-family: 'Orbitron', monospace;
}

.convert-btn:hover:not(:disabled) {
  background: linear-gradient(135deg, rgba(0, 255, 255, 0.25) 0%, rgba(0, 200, 200, 0.2) 100%);
  box-shadow: 
    0 0 30px rgba(0, 255, 255, 0.8),
    inset 0 0 25px rgba(0, 255, 255, 0.4),
    0 0 10px rgba(255, 109, 241, 0.5);
  transform: translateY(-3px) scale(1.02);
  border-color: #00ffff;
}

.convert-btn:disabled {
  background: linear-gradient(135deg, rgba(50, 50, 50, 0.2) 0%, rgba(30, 30, 30, 0.1) 100%);
  border-color: rgba(100, 100, 100, 0.5);
  color: rgba(100, 100, 100, 0.5);
  text-shadow: none;
  box-shadow: none;
  cursor: not-allowed;
  animation: none;
}

/* 按钮点击效果增强 */
.convert-btn::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background: linear-gradient(
    45deg,
    transparent,
    rgba(0, 255, 255, 0.3),
    transparent,
    rgba(255, 109, 241, 0.3),
    transparent
  );
  transform: rotate(45deg);
  animation: shine 2s infinite;
  opacity: 0;
}

.convert-btn:hover::before {
  animation: shine 1s infinite;
  opacity: 1;
}

.convert-btn:active:not(:disabled) {
  transform: translateY(0) scale(0.98);
  box-shadow: 
    0 0 15px rgba(0, 255, 255, 0.6),
    inset 0 0 20px rgba(0, 255, 255, 0.4);
}

@keyframes shine {
  0% {
    transform: translateX(-100%) translateY(-100%) rotate(45deg);
    opacity: 0;
  }
  50% {
    opacity: 1;
  }
  100% {
    transform: translateX(100%) translateY(100%) rotate(45deg);
    opacity: 0;
  }
}

/* 转换结果 */
.result-box {
  background-color: rgba(10, 30, 30, 0.8);
  border: 1px solid rgba(0, 255, 255, 0.4);
  padding: 20px;
  border-radius: 4px;
  margin-top: 30px;
  box-shadow: 
    0 0 15px rgba(0, 255, 255, 0.3),
    inset 0 0 15px rgba(0, 255, 255, 0.1);
}

.result-title {
  font-size: 20px;
  font-weight: bold;
  color: #00ffff;
  margin-bottom: 15px;
  text-shadow: 0 0 5px #00ffff;
  animation: neonPulse 2s ease-in-out infinite alternate;
}

.result-buttons {
  display: flex;
  gap: 15px;
  margin-top: 20px;
  justify-content: center;
}

/* 通用按钮样式 */
.btn {
  padding: 10px 20px;
  border: 1px solid;
  border-radius: 4px;
  font-size: 16px;
  font-weight: bold;
  cursor: pointer;
  transition: all 0.3s ease;
  text-shadow: 0 0 5px;
  box-shadow: 0 0 10px;
  letter-spacing: 1px;
  position: relative;
  overflow: hidden;
}

/* 主要按钮 */
.btn-primary {
  background-color: rgba(255, 109, 241, 0.2);
  color: #ff6df1;
  border-color: #ff6df1;
  box-shadow: 0 0 10px rgba(255, 109, 241, 0.3);
  text-shadow: 0 0 5px #ff6df1;
}

.btn-primary:hover {
  background-color: rgba(255, 109, 241, 0.4);
  box-shadow: 
    0 0 15px rgba(255, 109, 241, 0.6),
    inset 0 0 15px rgba(255, 109, 241, 0.2);
  transform: translateY(-2px);
}

/* 次要按钮 */
.btn-secondary {
  background-color: rgba(0, 255, 255, 0.2);
  color: #00ffff;
  border-color: #00ffff;
  box-shadow: 0 0 10px rgba(0, 255, 255, 0.3);
  text-shadow: 0 0 5px #00ffff;
}

.btn-secondary:hover {
  background-color: rgba(0, 255, 255, 0.4);
  box-shadow: 
    0 0 15px rgba(0, 255, 255, 0.6),
    inset 0 0 15px rgba(0, 255, 255, 0.2);
  transform: translateY(-2px);
}

/* 加载覆盖层 */
.loading-overlay {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(10, 10, 26, 0.9);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
  backdrop-filter: blur(5px);
}

.loading-content {
  background-color: rgba(10, 10, 30, 0.9);
  border: 1px solid #00ffff;
  padding: 30px;
  border-radius: 8px;
  text-align: center;
  box-shadow: 0 0 20px rgba(0, 255, 255, 0.5);
}

/* 赛博朋克加载动画 */
.spinner {
  border: 3px solid rgba(0, 255, 255, 0.2);
  width: 50px;
  height: 50px;
  border-radius: 50%;
  border-left-color: #00ffff;
  border-top-color: #ff6df1;
  animation: spin 1s linear infinite;
  margin: 0 auto 20px;
  box-shadow: 
    0 0 15px rgba(0, 255, 255, 0.5),
    0 0 15px rgba(255, 109, 241, 0.5);
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.loading-content div:last-child {
  color: #00ffff;
  font-size: 18px;
  font-weight: bold;
  text-shadow: 0 0 5px #00ffff;
  letter-spacing: 1px;
  animation: pulse 1.5s ease-in-out infinite alternate;
}

@keyframes pulse {
  from { opacity: 0.7; }
  to { opacity: 1; }
}

/* 错误提示 */
.error {
  color: #ff4d4f;
  font-size: 16px;
  margin-top: 20px;
  text-align: center;
  text-shadow: 0 0 5px #ff4d4f;
  padding: 15px;
  background-color: rgba(255, 77, 79, 0.1);
  border: 1px solid rgba(255, 77, 79, 0.3);
  border-radius: 4px;
  box-shadow: 0 0 10px rgba(255, 77, 79, 0.3);
  animation: errorFlicker 0.5s ease-in-out infinite alternate;
}

@keyframes errorFlicker {
  from { opacity: 0.8; }
  to { opacity: 1; }
}

/* 任务状态样式 */
.task-status {
  background-color: rgba(10, 20, 40, 0.8);
  border: 1px solid rgba(0, 255, 255, 0.3);
  padding: 20px;
  border-radius: 4px;
  margin-top: 30px;
  box-shadow: 
    0 0 15px rgba(0, 255, 255, 0.2),
    inset 0 0 15px rgba(0, 255, 255, 0.1);
}

.status-label {
  font-size: 18px;
  color: #00ffff;
  font-weight: bold;
  margin-bottom: 10px;
  text-shadow: 0 0 5px #00ffff;
}

.status-value {
  font-size: 16px;
  color: #ff6df1;
  margin-bottom: 10px;
  text-shadow: 0 0 3px #ff6df1;
  font-family: 'Courier New', monospace;
}

.task-id {
  font-size: 14px;
  color: #00ffff;
  font-family: 'Courier New', monospace;
  background-color: rgba(10, 10, 20, 0.8);
  padding: 8px;
  border-radius: 4px;
  border: 1px solid rgba(0, 255, 255, 0.2);
  box-shadow: inset 0 0 5px rgba(0, 255, 255, 0.1);
  word-break: break-all;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .container {
    margin: 20px;
    padding: 30px 20px;
  }
  
  .title {
    font-size: 24px;
  }
  
  .result-buttons {
    flex-direction: column;
    align-items: center;
  }
  
  .btn {
    width: 100%;
    max-width: 200px;
  }
}
</style>
