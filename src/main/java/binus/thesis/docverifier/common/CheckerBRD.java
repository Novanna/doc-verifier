package binus.thesis.docverifier.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class CheckerBRD extends Helper {
    public Map<String, Boolean> processBRD(PDDocument document) throws JsonProcessingException {
        log.info(">>>>>>>>> [BRD DOCUMENT ON CHECK] <<<<<<<<<<");

        // Execute tasks concurrently
        CompletableFuture<Boolean> logoValidation =
                CompletableFuture.supplyAsync(() -> validateLogo(document, "BRD"), ocrExecutor); //[1]
        CompletableFuture<Boolean> approvalValidation =
                CompletableFuture.supplyAsync(() -> validateApprovalBRD(document), ocrExecutor); //[2]
        CompletableFuture<Boolean> tocValidation =
                CompletableFuture.supplyAsync(() -> validateTableOfContentBRD(document), ocrExecutor); //[3]

        CompletableFuture<List<Map<Object, Object>>> finalTableOfContent =
                tocValidation.thenCompose(valid -> {
                    if (valid) {
                        return CompletableFuture.supplyAsync(() -> collectContent(document));
                    } else {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }
                });

        CompletableFuture<Boolean> introductionValidation = //[4]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    BRD_CONTENT.INTRODUCTION.name(),
                                    BRD_CONTENT.BACKGROUND.name());
                        });

        CompletableFuture<Boolean> backgroundValidation = //[5]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    BRD_CONTENT.BACKGROUND.name(),
                                    BRD_CONTENT.FUNCTIONAL_REQUIREMENT
                                            .name().replace("_", " "));
                        });

        CompletableFuture<Boolean> functionalReqValidation = //[6]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    BRD_CONTENT.FUNCTIONAL_REQUIREMENT
                                            .name().replace("_", " "),
                                    BRD_CONTENT.NON_FUNCTIONAL_REQUIREMENT
                                            .name().replace("_", " "));
                        });

        CompletableFuture<Boolean> nonFunctionalReqValidation = //[7]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    BRD_CONTENT.NON_FUNCTIONAL_REQUIREMENT
                                            .name().replace("_", " "),
                                    BRD_CONTENT.SERVICE_CHARACTERISTICS
                                            .name().replace("_", " "));
                        });

        CompletableFuture<Boolean> serviceCharacteristics = //[8]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    BRD_CONTENT.SERVICE_CHARACTERISTICS
                                            .name().replace("_", " "),
                                    BRD_CONTENT.RISK_ASSESSMENT
                                            .name().replace("_", " "));
                        });

        CompletableFuture<Boolean> riskAssesment = //[9]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    BRD_CONTENT.RISK_ASSESSMENT
                                            .name().replace("_", " "),
                                    BRD_CONTENT.ROLES_AND_RESPONSIBILITIES_MATRIX
                                            .name().replace("_", " "));
                        });

        CompletableFuture<Boolean> rolesResponsibilities = //[10]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    BRD_CONTENT.ROLES_AND_RESPONSIBILITIES_MATRIX
                                            .name().replace("_", " "),
                                    BRD_CONTENT.APPENDIX
                                            .name());
                        });

        CompletableFuture.allOf( //------------[param tag]
                logoValidation, //-------------[1]
                approvalValidation, //---------[2]
                tocValidation, //--------------[3]
                introductionValidation, //-----[4]
                backgroundValidation, //-------[5]
                functionalReqValidation, //----[6]
                nonFunctionalReqValidation,//--[7]
                serviceCharacteristics, //-----[8]
                riskAssesment, //--------------[9]
                rolesResponsibilities //-------[10]
        ).join();

        // Collect all results
        Map<String, Boolean> validationResults = new HashMap<>();
        validationResults.put("1", logoValidation.join());
        validationResults.put("2", approvalValidation.join());
        validationResults.put("3", tocValidation.join());
        validationResults.put("4", introductionValidation.join());
        validationResults.put("5", backgroundValidation.join());
        validationResults.put("6", functionalReqValidation.join()); //ok
        validationResults.put("7", nonFunctionalReqValidation.join());
        validationResults.put("8", serviceCharacteristics.join());
        validationResults.put("9", riskAssesment.join());
        validationResults.put("10", rolesResponsibilities.join());
        return validationResults;
    }


    private Boolean validateApprovalBRD (PDDocument document) {
        Integer approvalPage = searchPage(3,
                "APPROVAL", "HEADER_APPROVAL_BRD", document,
                new Rectangle2D.Double(200.0, 45.0, 250.0, 45.0));

        Boolean preparedBy_1 = validateArea(document,
                approvalPage, "PREPARED_BY1",
                new Rectangle2D.Double(73.13, 150.71, 158.52, 56.24));
        //log.info("Is Approval-Prepared By 1 Valid: {}", preparedBy_1);

        Boolean approval_1 = validateArea(document,
                approvalPage,"P1_APPROVAL",
                new Rectangle2D.Double(232.5, 190.71, 158.52, 56.24));
        //log.info("Is Approval 1 Signed Valid: {}", approval_1);

        Boolean date_1 = validateArea(document,
                approvalPage,"P1_DATE",
                new Rectangle2D.Double(445.01, 190.71, 158.52, 56.24));
        //log.info("Is Approval 1 Date Valid: {}", date_1);
        //-------------------------------------------------------------
        Boolean preparedBy_2 = validateArea(document,
                approvalPage,"PREPARED_BY2",
                new Rectangle2D.Double(73.13, 190.71, 158.52, 56.24));
        //log.info("Is Approval-Prepared By 2 Valid: {}", preparedBy_2);

        Boolean approval_2 = validateArea(document,
                approvalPage,"P2_APPROVAL",
                new Rectangle2D.Double(232.5, 190.71, 158.52, 56.24));
        //log.info("Is Approval 2 Signed Valid: {}", approval_2);

        Boolean date_2 = validateArea(document,
                approvalPage,"P2_DATE",
                new Rectangle2D.Double(445.01, 190.71, 158.52, 56.24));
        //log.info("Is Approval 2 Date Valid: {}", date_2);

        List<Boolean> validations = List.of(
                preparedBy_1, preparedBy_2,
                approval_1, approval_2,
                date_1, date_2);
        return validations.stream().allMatch(Boolean::booleanValue);
    }

    private Boolean validateTableOfContentBRD(PDDocument document) {
        Integer tocPage = searchPage(3,
                "TABLE OF CONTENT", "HEADER_CONTENT_BRD", document,
                new Rectangle2D.Double(200.0, 45.0, 250.0, 45.0));
        Boolean tocValid = validateArea(document,
                tocPage,"CONTENT_BRD",
                new Rectangle2D.Double(54.50, 121.38, 482.10, 604.10));

        return tocValid;
    }


    protected List<Map<Object, Object>> collectContent(PDDocument document) {
        List<Map<Object, Object>> result = new ArrayList<>();
        try {
            Integer tocPage = searchPage(3,
                    "TABLE OF CONTENT", "HEADER_CONTENT_BRD", document,
                    new Rectangle2D.Double(200.0, 45.0, 250.0, 45.0));
            String tocContent = ocrProcessResult(document,
                    tocPage, "CONTENT_BRD",
                    new Rectangle2D.Double(54.50, 121.38, 482.10, 604.10));

            if(!tocContent.isEmpty()) {
                String regex = "([A-Za-z0-9\\.\\s]+?)\\s+\\.\\.{2,}\\s+(\\d+)";
                Pattern pattern = Pattern.compile(regex);
                Matcher matcher = pattern.matcher(tocContent);
                while (matcher.find()) {
                    String heading = matcher.group(1).trim();
                    int page = Integer.parseInt(matcher.group(2).trim());
                    Map<Object, Object> entry = new HashMap<>();
                    entry.put("page", page);
                    entry.put("title", heading);
                    result.add(entry);
                }
            }

            //og.info("collectContent --result: {}", mapper.writeValueAsString(result));
        } catch (Exception e) {
            log.info("Something wrong at collectContent: {}", e.getMessage());
            result = null;
        }
        return result;
    }
}
