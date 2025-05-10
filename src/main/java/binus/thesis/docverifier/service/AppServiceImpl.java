package binus.thesis.docverifier.service;

import binus.thesis.docverifier.common.CheckerBRD;
import binus.thesis.docverifier.common.CheckerPVT;
import binus.thesis.docverifier.common.CheckerUAT;
import binus.thesis.docverifier.common.Helper;
import binus.thesis.docverifier.model.ResponseModel;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.io.RandomAccessBufferedFileInputStream;
import org.apache.pdfbox.pdfparser.PDFParser;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.util.StopWatch;
import org.springframework.web.multipart.MultipartFile;
import java.io.File;
import java.io.IOException;
import java.util.*;

@Service
@Slf4j
public class AppServiceImpl extends Helper implements AppService {

    /*
        README:
        1. Default Size
        > spring.servlet.multipart.max-file-size	1MB
        > spring.servlet.multipart.max-request-size	10MB
        > if found max -> (API Code: 413 Payload too large)

        2. Tech stack:
        - Spring Boot 3.3.7-SNAPSHOT,
        - Java 17,
        - API: spring-boot-starter-web
        - PDF Processing: pdfbox
        - OCR (Optical Character Recognition): tess4j v4.5.5 (Tesseract)

        http://localhost:8080/swagger-ui/index.html#/app-controller/getTransactions

     */
    public static double PAGE_HEIGHT;
    public AppServiceImpl() {
        PAGE_HEIGHT = 0;
    }
    @Override
    public ResponseEntity<ResponseModel> doVerification
            (MultipartFile docFile, String requestId, String docType) {
        ResponseModel responseModel = new ResponseModel();
        StopWatch stopwatch = new StopWatch();
        stopwatch.start();
        log.info("================ DO VERIFICATION START:{} ===================", requestId);
        File tempFile = null;
        Map<String, Boolean> validationResult = new HashMap<>();
        try {
            // 1. Collect the document
            log.info("File masuk: " + docFile.getOriginalFilename());
            tempFile = new File(System.getProperty("java.io.tmpdir")
                    + File.separator + getTime() + "_in.pdf");
            docFile.transferTo(tempFile);
            log.info("File temp: " + tempFile.getName());
            //PDFParser parser = new PDFParser(new RandomAccessBufferedFileInputStream(tempFile));
            //parser.setLenient(true); // add toleran for minor error
            //parser.parse();
            //PDDocument document = parser.getPDDocument();
            PDDocument document = PDDocument.load(tempFile);
            // 2. Measure Height
            PDRectangle rect = document.getPage(0).getMediaBox();
            log.info("Cover Page dimensions: width = {} points, height = {} points",
                    rect.getWidth(), rect.getHeight());
            PAGE_HEIGHT = rect.getHeight();
            Helper.setPageHeight(PAGE_HEIGHT);
            // 3. Go To Verification Process Based on Type
            if (docType.trim().equalsIgnoreCase("BRD")) {
                validationResult = new CheckerBRD().processBRD(document);
            } else if (docType.trim().equalsIgnoreCase("UAT")) {
                validationResult = new CheckerUAT().processUAT(document);
            } else if (docType.trim().equalsIgnoreCase("PVT")) {
                validationResult = new CheckerPVT().processPVT(document);
            } else {
                throw new IllegalArgumentException("Unsupported document type: " + docType);
            }
            document.close();
        } catch (IOException ie) {
            log.info("[IO] Something wrong at doVerification: {}", ie.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            log.info("[E] Something wrong at doVerification: {}", e.getMessage());
        } finally {
            // reset
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            // response time log
            stopwatch.stop();
            log.info("Response Time: {} ms", stopwatch.getTotalTimeMillis());
            // set up body response
            responseModel.setResponseId(requestId);
            responseModel.setDocType(docType);
            responseModel.setParameters(validationResult);
            return ResponseEntity.ok(responseModel);
        }
    }
    // referensi itungan titik y
    // https://www3.ntu.edu.sg/home/ehchua/programming/java/J8b_Game_2DGraphics.html
}