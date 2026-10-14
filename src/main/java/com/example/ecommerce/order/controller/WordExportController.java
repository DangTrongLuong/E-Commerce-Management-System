package com.example.ecommerce.order.controller;


import com.example.ecommerce.order.service.WordExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/export")
@Tag(name = "Xuất File Word (Word Export)", description = "Tạo và tải báo cáo định dạng Microsoft Word (.docx) (Quyền: Công khai / Người dùng hệ thống)")
public class WordExportController {

    @Autowired
    private WordExportService wordExportService;

    @GetMapping("/word")
    @Operation(
            summary = "Tải file báo cáo Microsoft Word (.docx)",
            description = "Sinh tệp tài liệu mẫu định dạng Microsoft Word (.docx) và trả về tệp tải xuống đính kèm cho người dùng. Quyền truy cập: Công khai."
    )
    public ResponseEntity<byte[]> downloadWordFile() {
        try {
            byte[] documentContent = wordExportService.createSimpleWordFile();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
            headers.setContentDispositionFormData("attachment", "BaoCao.docx");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(documentContent);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }
}
