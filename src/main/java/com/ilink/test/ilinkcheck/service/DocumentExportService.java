package com.ilink.test.ilinkcheck.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * 长文本转换为.docx字节数组
 */
@Service
@Slf4j
public class DocumentExportService {
    public byte[] exportToDocx(String title, String content){
        try(XWPFDocument document = new XWPFDocument();
            ByteArrayOutputStream out = new ByteArrayOutputStream()){
            // 1.添加大标题
            XWPFParagraph titleParagraph = document.createParagraph();
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setText(title);
            titleRun.setBold(true);
            titleRun.setFontSize(16);
            titleRun.addBreak();

            // 2.按行写入正文内容
            String[] lines = content.split("\n");
            for(String line : lines){
                XWPFParagraph paragraph = document.createParagraph();
                XWPFRun lineRun = paragraph.createRun();
                lineRun.setText(line);
                lineRun.setFontSize(11);
                lineRun.setFontFamily("微软雅黑");
            }
            document.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("生成Word文档失败：", e);
            throw new RuntimeException("生成文档失败", e);
        }
    }
}
