import os
import tempfile
import comtypes
from comtypes.client import CreateObject
from pdf2docx import parse

class FileConverter:
    def word_to_pdf(self, input_path, output_path):
        """
        将Word文档转换为PDF
        :param input_path: Word文档路径
        :param output_path: 输出PDF路径
        :return: 转换是否成功
        """
        try:
            comtypes.CoInitialize()
            word = CreateObject('Word.Application')
            word.Visible = False
            doc = word.Documents.Open(os.path.abspath(input_path))
            doc.SaveAs(os.path.abspath(output_path), FileFormat=17)  # 17是PDF格式
            doc.Close()
            word.Quit()
            return True
        except Exception as e:
            print(f"Word转PDF失败: {str(e)}")
            return False
        finally:
            try:
                comtypes.CoUninitialize()
            except:
                pass
    
    def pdf_to_word(self, input_path, output_path):
        """
        将PDF文档转换为Word
        :param input_path: PDF文档路径
        :param output_path: 输出Word路径
        :return: 转换是否成功
        """
        try:
            parse(input_path, output_path)
            return True
        except Exception as e:
            print(f"PDF转Word失败: {str(e)}")
            return False

if __name__ == "__main__":
    converter = FileConverter()
    # 测试示例
    converter.word_to_pdf("C:\\Users\\19949\\Desktop\\简历\\【论文格式AI检测与校准系统】.docx",
                          "C:\\Users\\19949\\Desktop\\简历\\【论文格式AI检测与校准系统】test.pdf")
    # converter.pdf_to_word("C:\\Users\\19949\\Desktop\\简历\\The resume of YuFei.pdf",
    #                       "C:\\Users\\19949\\Desktop\\简历\\The resume of YuFei Test.docx")
