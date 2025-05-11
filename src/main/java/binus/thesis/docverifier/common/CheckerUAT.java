package binus.thesis.docverifier.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class CheckerUAT extends Helper {
    public Map<String, Boolean> processUAT(PDDocument document) throws JsonProcessingException {
        log.info(">>>>>>>>> [UAT DOCUMENT ON CHECK] <<<<<<<<<<");
        // Execute tasks concurrently
        CompletableFuture<Boolean> logoValidation =
                CompletableFuture.supplyAsync(() -> validateLogo(document, "UAT"), ocrExecutor); //[1]
        CompletableFuture<Boolean> approvalValidation =
                CompletableFuture.supplyAsync(() -> validateApprovalUAT(document), ocrExecutor); //[2]
        CompletableFuture<Boolean> tocValidation =
                CompletableFuture.supplyAsync(() -> validateTableOfContentUAT(document), ocrExecutor); //[3]

        CompletableFuture<List<Map<Object, Object>>> finalTableOfContent =
                tocValidation.thenCompose(valid -> {
                    if (valid) {
                        return CompletableFuture.supplyAsync(() -> collectContent(document));
                    } else {
                        return CompletableFuture.completedFuture(Collections.emptyList());
                    }
                });

        //--------------------------------------------------


        CompletableFuture<Boolean> introductionValidation = //[4]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    UAT_CONTENT.INTRODUCTION.name(),
                                    UAT_CONTENT.SCOPE_EVALUATION.name().replace("_", " "));
                        });

        CompletableFuture<Boolean> scopeValidation = //[5]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    UAT_CONTENT.SCOPE_EVALUATION.name().replace("_", " "),
                                    UAT_CONTENT.PLAN_TESTING
                                            .name().replace("_", " "));
                        });

        CompletableFuture<Boolean> planTestingValidation = //[6]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    UAT_CONTENT.PLAN_TESTING
                                            .name().replace("_", " "),
                                    UAT_CONTENT.ROLLBACK_TEST
                                            .name().replace("_", " "));
                        });

        CompletableFuture<Boolean> rollbackTestValidation = //[7]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    UAT_CONTENT.ROLLBACK_TEST
                                            .name().replace("_", " "),
                                    UAT_CONTENT.BUDGET_DETAIL
                                            .name().replace("_", " "));
                        });

        CompletableFuture<Boolean> budgetDetailValidation = //[8]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    UAT_CONTENT.BUDGET_DETAIL
                                            .name().replace("_", " "),
                                    UAT_CONTENT.RESULT_OF_TEST
                                            .name().replace("_", " "));
                        });

        CompletableFuture<Boolean> resultOfTestValidation = //[9]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    UAT_CONTENT.RESULT_OF_TEST
                                            .name().replace("_", " "),
                                    UAT_CONTENT.USER_FEEDBACK_AND_SUGGESTION
                                            .name().replace("_", " "));
                        });

        CompletableFuture<Boolean> userFeedbackValidation = //[10]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    UAT_CONTENT.USER_FEEDBACK_AND_SUGGESTION
                                            .name().replace("_", " "),
                                    UAT_CONTENT.SUMMARY_OF_TESTING
                                            .name().replace("_", " "));
                        });

        CompletableFuture<Boolean> summaryValidation = //[11]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    UAT_CONTENT.SUMMARY_OF_TESTING
                                            .name().replace("_", " "),
                                    UAT_CONTENT.LESSON_LEARNED
                                            .name().replace("_", " "));
                        });

        CompletableFuture<Boolean> lessonLearnedValidation = //[12]
                tocValidation.thenCombineAsync(
                        finalTableOfContent,
                        (tocValid, tableOfContent) -> {
                            if (!tocValid) return false;
                            return validateEachContent(document, tableOfContent,
                                    UAT_CONTENT.LESSON_LEARNED
                                            .name().replace("_", " "),
                                    "");
                        });

        CompletableFuture.allOf( //------------[param tag]
                logoValidation, //-------------[1]
                approvalValidation, //---------[2]
                tocValidation, //--------------[3]
                introductionValidation, //-----[4]
                scopeValidation, //------------[5]
                planTestingValidation, //------[6]
                rollbackTestValidation,//------[7]
                budgetDetailValidation, //-----[8]
                resultOfTestValidation, //-----[9]
                userFeedbackValidation, //-----[10]
                summaryValidation, //----------[11]
                lessonLearnedValidation //-----[12]
        ).join();

        // Collect all results
        Map<String, Boolean> validationResults = new HashMap<>();
        validationResults.put("1", logoValidation.join());
        validationResults.put("2", approvalValidation.join());
        validationResults.put("3", tocValidation.join());
        validationResults.put("4", introductionValidation.join());
        validationResults.put("5", scopeValidation.join());
        validationResults.put("6", planTestingValidation.join());
        validationResults.put("7", rollbackTestValidation.join());
        validationResults.put("8", budgetDetailValidation.join());
        validationResults.put("9", resultOfTestValidation.join());
        validationResults.put("10", userFeedbackValidation.join());
        validationResults.put("11", summaryValidation.join());
        validationResults.put("12", lessonLearnedValidation.join());
        return validationResults;
    }

    private Boolean validateTableOfContentUAT(PDDocument document) {
        Integer tocPage = searchPage(5,
                "TABLE OF CONTENT", "HEADER_CONTENT_UAT", document,
                new Rectangle2D.Double(180.0, 65.0, 250.0, 45.0));
        Boolean tocValid = validateArea(document,
                tocPage,"CONTENT_UAT",
                new Rectangle2D.Double(54.50, 90.38, 482.10, 604.10));
        return tocValid;
    }

    private Boolean validateApprovalUAT (PDDocument document) {
        Integer approvalPage = searchPage(3,
                "APPROVAL", "HEADER_APPROVAL_UAT", document,
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

        List<Boolean> validations = List.of(
                preparedBy_1,
                approval_1,
                date_1);
        return validations.stream().allMatch(Boolean::booleanValue);
    }

    private List<Map<Object, Object>> collectContent(PDDocument document) {
        List<Map<Object, Object>> result = new ArrayList<>();
        try {
            Integer tocPage = searchPage(5,
                    "TABLE OF CONTENT", "HEADER_CONTENT_UAT", document,
                    new Rectangle2D.Double(180.0, 65.0, 250.0, 45.0));
            String tocContent = ocrProcessResult(document,
                    tocPage, "CONTENTUAT",
                    new Rectangle2D.Double(54.50, 90.38, 482.10, 604.10));

            tocContent = tocContent.replaceAll("[\\r\\n]+", "\n");  // normalize line breaks
            String[] lines = tocContent.split("\\n");
            String regex = "(?m)^\\s*(\\d+)\\s*([A-Z0-9 \\-]+?)\\s*\\.{3,}\\s*(\\d+)\\s*$";
            Pattern pattern = Pattern.compile(regex);
            for (String line : lines) {
                Matcher matcher = pattern.matcher(line.trim());
                if (matcher.find()) {
                    int num = Integer.parseInt(matcher.group(1).trim());
                    String title = matcher.group(2).trim().replaceAll("\\s{2,}", " ");
                    int page = Integer.parseInt(matcher.group(3).trim());
                    Map<Object, Object> entry = new HashMap<>();
                    entry.put("page", page);
                    entry.put("title", num + " " + title);
                    result.add(entry);
                }
            }

            //log.info("collectContent --result: {}", mapper.writeValueAsString(result));
        } catch (Exception e) {
            log.info("Something wrong at collectContent: {}", e.getMessage());
            result = null;
        }
        return result;
    }
}
