package com.example.ecommerce.order.service;

import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class WordExportService {

    public byte[] createSimpleWordFile() throws Exception{
        WordprocessingMLPackage wordprocessingMLPackage = WordprocessingMLPackage.createPackage();

        MainDocumentPart mainDocumentPart = wordprocessingMLPackage.getMainDocumentPart();

        mainDocumentPart.addStyledParagraphOfText("Title", "BÁO CÁO KẾT QUẢ CÔNG VIỆC");
        mainDocumentPart.addParagraphOfText("Chào mừng bạn đến với ứng dụng Spring Boot sử dụng docx4j.");
        mainDocumentPart.addParagraphOfText("Đây là đoạn văn bản được tạo tự động từ hệ thống.");

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        wordprocessingMLPackage.save(byteArrayOutputStream);

        return byteArrayOutputStream.toByteArray();
    }
}
