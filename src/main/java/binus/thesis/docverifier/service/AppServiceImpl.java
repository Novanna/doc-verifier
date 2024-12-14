package binus.thesis.docverifier.service;

import binus.thesis.docverifier.model.ResponseModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.web.multipart.MultipartFile;


import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class AppServiceImpl implements AppService {

    @Override
    public ResponseEntity<ResponseModel> doVerification(MultipartFile docFile, String userId, String requestId) {
        ResponseModel responseModel = new ResponseModel();
        log.info("DO VERIFICATION START");
        try {
            // 1. Collect the document
            log.info("File masuk: " + docFile.getOriginalFilename());

            File tempFile = new File(System.getProperty("java.io.tmpdir") + File.separator + getTime() + "_in.pdf");
            docFile.transferTo(tempFile);
            log.info("File temp: " + tempFile.getName());

            PDDocument document = PDDocument.load(tempFile);

            // 1. Logo
            boolean isLogoPresent = checkLogo(document, 1, 50, 650, 100, 100);

            if (!isLogoPresent) {
                responseModel.setResponseId(requestId);
                responseModel.setCode(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
                responseModel.setMessage(("Tidak Ada Logo di Page 1!"));
                return ResponseEntity.ok(responseModel);
            }

            // 2. Title
            boolean isTitlePresent = checkAreaForText(document, 1, 50, 50, 500, 100);

            if (!isTitlePresent) {
                responseModel.setResponseId(requestId);
                responseModel.setCode(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
                responseModel.setMessage(("Tidak Ada Judul di Page 1!"));
                return ResponseEntity.ok(responseModel);
            }

            boolean isApprovalPageOk = checkAreaForApproval(document, 3, 50, 50, 500, 100);

            if (!isTitlePresent) {
                responseModel.setResponseId(requestId);
                responseModel.setCode(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
                responseModel.setMessage(("Approval Page Tidak Valid!"));
                return ResponseEntity.ok(responseModel);
            }


            // Close File Process
            document.close();
            responseModel.setResponseId(requestId);
            responseModel.setCode(String.valueOf(HttpStatus.OK.value()));
            responseModel.setMessage((HttpStatus.OK.name()));

        } catch (IOException ie) {
            ie.printStackTrace();
            responseModel.setResponseId(requestId);
            responseModel.setCode(String.valueOf(HttpStatus.INTERNAL_SERVER_ERROR.value()));
            responseModel.setMessage("IOException: " + ie.getMessage());
        } finally {
            return ResponseEntity.ok(responseModel);
        }
    }

    private String getTime(){
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
        return now.format(formatter);
    }

    private boolean checkAreaForText(PDDocument document, int page, int x, int y, int width, int height) throws IOException {
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.setSortByPosition(true);

        Rectangle2D region = new Rectangle2D.Double(x, y, width, height);
        stripper.addRegion("JUDUL", region);

        PDPage pdPage = document.getPage(page - 1);
        stripper.extractRegions(pdPage);

        String text = stripper.getTextForRegion("JUDUL").trim();
        log.info("HASIL READ TEXT: " + text);

        return !text.isEmpty();
    }

    private boolean checkLogo(PDDocument document, int page, int x, int y, int width, int height) throws IOException {
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.setSortByPosition(true);

        Rectangle2D region = new Rectangle2D.Double(x, y, width, height);
        stripper.addRegion("LOGO", region);

        PDPage pdPage = document.getPage(page - 1);
        stripper.extractRegions(pdPage);

        String text = stripper.getTextForRegion("JUDUL").trim();
        log.info("HASIL READ TEXT: " + text);

        return !text.isEmpty();
    }

    private boolean checkAreaForApproval (PDDocument document, int page, int x, int y, int width, int height) throws IOException {
        PDFTextStripperByArea stripper = new PDFTextStripperByArea();
        stripper.setSortByPosition(true);

        Rectangle2D region = new Rectangle2D.Double(x, y, width, height);
        stripper.addRegion("APPROVAL", region);

        PDPage pdPage = document.getPage(page - 1);
        stripper.extractRegions(pdPage);

        String text = stripper.getTextForRegion("JUDUL").trim();
        log.info("HASIL READ TEXT: " + text);

        return !text.isEmpty();
    }

}
