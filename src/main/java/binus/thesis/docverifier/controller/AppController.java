package binus.thesis.docverifier.controller;


import binus.thesis.docverifier.model.RequestModel;
import binus.thesis.docverifier.model.ResponseModel;
import binus.thesis.docverifier.service.AppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Slf4j
@RequiredArgsConstructor
@RequestMapping("/api/v1/")
public class AppController {
    private final AppService appService;

    @PostMapping(value = "/doc-verification", consumes = "multipart/form-data", produces = "application/json")
    public ResponseEntity<ResponseModel> getTransactions(@RequestParam("docFile") MultipartFile docFile,
                                                         @RequestParam("requestId") String requestId,
                                                         @RequestParam("userId") String userId) {
        return appService.doVerification(docFile, userId, requestId);
    }
}
