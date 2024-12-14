package binus.thesis.docverifier.service;

import binus.thesis.docverifier.model.ResponseModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;


public interface AppService {

    ResponseEntity<ResponseModel> doVerification (MultipartFile docFile, String userId, String requestId);
}
