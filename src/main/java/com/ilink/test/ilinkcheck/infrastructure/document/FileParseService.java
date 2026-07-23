package com.ilink.test.ilinkcheck.infrastructure.document;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

/**
 * 提取解析文件
 */
@Service
@Slf4j
public class FileParseService {
    public String extractText(String fileName, byte[] fileBytes){
        if(fileName == null || fileBytes == null || fileBytes.length == 0){
            return "";
        }

        String lowerName =  fileName.toLowerCase();
        try{
            if(lowerName.endsWith(".txt") || lowerName.endsWith(".md") || lowerName.endsWith(".json")){
                return new String(fileBytes, StandardCharsets.UTF_8);
            }else if(lowerName.endsWith(".pdf")){
                try(PDDocument doc = Loader.loadPDF(fileBytes)){
                    return new PDFTextStripper().getText(doc);
                }
            }else if(lowerName.endsWith(".docx")){
                try(ByteArrayInputStream is = new ByteArrayInputStream(fileBytes);
                    XWPFDocument doc = new XWPFDocument(is);
                    XWPFWordExtractor extractor = new XWPFWordExtractor(doc)){
                    return extractor.getText();
                }
            }
            throw new IllegalArgumentException("暂不支持该文件格式：" + fileName);
        }catch(Exception e){
            log.error("解析文件失败：{}", fileName, e);
            throw new RuntimeException("文件内容解析失败", e);
        }
    }

}
