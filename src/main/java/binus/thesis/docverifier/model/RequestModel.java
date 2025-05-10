package binus.thesis.docverifier.model;

import lombok.Data;
import lombok.NonNull;
import org.springframework.web.multipart.MultipartFile;

@Data
public class RequestModel {
    @NonNull
    String requestId;
    @NonNull
    private MultipartFile docFile;
    @NonNull
    String docType;
}