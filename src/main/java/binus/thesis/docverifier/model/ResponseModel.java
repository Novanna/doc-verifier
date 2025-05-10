package binus.thesis.docverifier.model;

import lombok.Data;

import java.util.Map;

@Data
public class ResponseModel {
    String responseId;
    String docType;
    Map<String, Boolean> parameters;
}
