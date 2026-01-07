from flask import Flask, request, send_file, jsonify
from flask_cors import CORS
import os
import tempfile
from converter import FileConverter

app = Flask(__name__)
CORS(app)  # 允许跨域请求
converter = FileConverter()

# 确保上传目录存在
UPLOAD_FOLDER = 'D:\\PtwOrWtp\\backend\\ptw\\uploads'
if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

app.config['UPLOAD_FOLDER'] = UPLOAD_FOLDER
app.config['MAX_CONTENT_LENGTH'] = 100 * 1024 * 1024  # 最大上传文件大小100MB

@app.route('/')
def index():
    return '''
    <h1>文件格式转换API</h1>
    <p>支持Word转PDF和PDF转Word功能</p>
    <p>API端点：</p>
    <ul>
        <li>POST /convert/word-to-pdf - Word转PDF</li>
        <li>POST /convert/pdf-to-word - PDF转Word</li>
    </ul>
    '''

@app.route('/convert/word-to-pdf', methods=['POST'])
def convert_word_to_pdf():
    if 'file' not in request.files:
        return jsonify({'error': 'No file provided'}), 400
    
    file = request.files['file']
    if file.filename == '':
        return jsonify({'error': 'No file selected'}), 400
    
    if not file.filename.endswith(('.docx', '.doc')):
        return jsonify({'error': 'Only Word files are allowed'}), 400
    
    # 保存上传的文件
    input_path = os.path.join(app.config['UPLOAD_FOLDER'], file.filename)
    file.save(input_path)
    
    # 生成输出文件名
    output_filename = os.path.splitext(file.filename)[0] + '.pdf'
    output_path = os.path.join(app.config['UPLOAD_FOLDER'], output_filename)
    
    # 执行转换
    success = converter.word_to_pdf(input_path, output_path)
    
    # 删除输入文件
    os.remove(input_path)
    
    if not success:
        return jsonify({'error': 'Conversion failed'}), 500
    
    # 返回转换后的文件
    response = send_file(output_path, as_attachment=True)
    
    # 设置响应结束后删除文件
    @response.call_on_close
    def cleanup():
        if os.path.exists(output_path):
            os.remove(output_path)
    
    return response

@app.route('/convert/pdf-to-word', methods=['POST'])
def convert_pdf_to_word():
    if 'file' not in request.files:
        return jsonify({'error': 'No file provided'}), 400
    
    file = request.files['file']
    if file.filename == '':
        return jsonify({'error': 'No file selected'}), 400
    
    if not file.filename.endswith('.pdf'):
        return jsonify({'error': 'Only PDF files are allowed'}), 400
    
    # 保存上传的文件
    input_path = os.path.join(app.config['UPLOAD_FOLDER'], file.filename)
    file.save(input_path)
    
    # 生成输出文件名
    output_filename = os.path.splitext(file.filename)[0] + '.docx'
    output_path = os.path.join(app.config['UPLOAD_FOLDER'], output_filename)
    
    # 执行转换
    success = converter.pdf_to_word(input_path, output_path)
    
    # 删除输入文件
    os.remove(input_path)
    
    if not success:
        return jsonify({'error': 'Conversion failed'}), 500
    
    # 返回转换后的文件
    response = send_file(output_path, as_attachment=True)
    
    # 设置响应结束后删除文件
    @response.call_on_close
    def cleanup():
        if os.path.exists(output_path):
            os.remove(output_path)
    
    return response

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
