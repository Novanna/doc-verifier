package binus.thesis.docverifier.common;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripperByArea;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
public class Helper {

    private static Double PAGE_HEIGHT = 792.0; // default
    public static void setPageHeight(Double height) {
        PAGE_HEIGHT = height;
    }
    public static Double getPageHeight() {
        return PAGE_HEIGHT;
    }
    public static final ExecutorService ocrExecutor =
            Executors.newFixedThreadPool(
                    Math.max(4, Runtime.getRuntime().availableProcessors() * 2)
            );
    private final ConcurrentHashMap<Integer, ReentrantLock> pageLocks = new ConcurrentHashMap<>();



    protected static final int PAGE_COVER = 1;
    public enum BRD_CONTENT {
        INTRODUCTION,
        BACKGROUND,
        FUNCTIONAL_REQUIREMENT,
        NON_FUNCTIONAL_REQUIREMENT,
        SERVICE_CHARACTERISTICS,
        RISK_ASSESSMENT,
        ROLES_AND_RESPONSIBILITIES_MATRIX,
        APPENDIX
    }
    enum PVT_CONTENT {
        INTRODUCTION,
        SCOPE,
        PLAN_PVT,
        PVT_SCENARIO,
        PERFORMANCE_TEST,
        ROLLBACK_PLAN,
        PVT_RESULT,
        SUMMARY
    }
    enum UAT_CONTENT {
        INTRODUCTION,
        SCOPE_EVALUATION,
        PLAN_TESTING,
        ROLLBACK_TEST,
        BUDGET_DETAIL,
        RESULT_OF_TEST,
        USER_FEEDBACK_AND_SUGGESTION,
        SUMMARY_OF_TESTING,
        LESSON_LEARNED
    }

    protected String ocrProcessResult(PDDocument document, int page,
                                    String regionName, Rectangle2D region) throws IOException {
        //String text = "";
        ReentrantLock lock = pageLocks.computeIfAbsent(page, k -> new ReentrantLock());
        lock.lock();

        //synchronized(document) {
        try {
            PDFTextStripperByArea stripper = new PDFTextStripperByArea();
            stripper.setSortByPosition(true);
            stripper.addRegion(regionName, region);
            stripper.extractRegions(document.getPage(page - 1));
            return stripper.getTextForRegion(regionName).trim();
        } //}
        finally {
            lock.unlock();
        }
    }

    protected Boolean validateLogo (PDDocument document, String type) {
//        Boolean logoValid = validateArea(document, PAGE_COVER, "LOGO",
//                new Rectangle2D.Double(-68.0, getPageHeight() - 608.16 - 167.7, 200.7, 167.7));
        //log.info("Is Logo Valid: {}", logoValid);
//        Boolean titleValid = validateArea(document, PAGE_COVER, "TITLE_" + type,
//                new Rectangle2D.Double(-68.0, getPageHeight() - 298.4 - 300.1, 612.0, 300.1));
        //log.info("Is Title Valid: {}", titleValid);
//        return logoValid && titleValid;

        CompletableFuture<Boolean> logoValid =
                CompletableFuture.supplyAsync(() ->
                        validateArea(document, PAGE_COVER, "LOGO",
                                new Rectangle2D.Double(
                                        -68.0,
                                        getPageHeight() - 608.16 - 167.7,
                                        200.7,
                                        167.7)),
                        ocrExecutor);
        CompletableFuture<Boolean> titleValid =
                CompletableFuture.supplyAsync(() ->
                                validateArea(document, PAGE_COVER, "LOGO",
                                        new Rectangle2D.Double(
                                                -68.0,
                                                getPageHeight() - 298.4 - 300.1,
                                                612.0,
                                                300.1)),
                        ocrExecutor);
        return logoValid.join() && titleValid.join();
    }

    protected Rectangle2D.Double getRectangledArea(PDDocument document,
                                                   Integer startPage,
                                                   String startKey,
                                                   String endKey,
                                                   Boolean isAtSamePage) {
        float maxWidth = 0;
        Double heightToCheck = null;
        Double finalY = null;
        Double finalHeight = null;

        //log.info("[OC] startPage: {} | startKey: {} | endKey: {}", startPage,startKey, endKey);
        try {
            // ocr start from 0
            PDRectangle rect = document.getPage(startPage-1).getMediaBox();
            //log.info("Page [{}] : width = {} points, height = {} points",
            //        startPage, rect.getWidth(), rect.getHeight());
            maxWidth = rect.getWidth();
            //maxHeight = rect.getHeight();

            Double x = -68.0; //start point x
            Double y = 30.00; //start point y [44.02]
            Double newY_s = y, newY_e = y;
            String text_s = "", text_e = "";
            heightToCheck = 20.00; //height of regular font

            // 1. periksa posisi startKey
            do {
                text_s = ocrProcessResult(document, startPage, startKey,
                        new Rectangle2D.Double(x, newY_s, maxWidth, heightToCheck));
                newY_s += 10;
            } while (!text_s.toUpperCase().contains(startKey.toUpperCase()));
            // 1.2. jadikan acuan Y & Height
            finalY = newY_s;
            finalHeight = heightToCheck;

            // 2. apakah startKey dan endKey di page yang sama ?
            if (isAtSamePage) {
                // 2.1. iya di page yg sama, coba cari posisi end nya!
                do {
                    text_e = ocrProcessResult(document, startPage, endKey,
                            new Rectangle2D.Double(x, newY_e, maxWidth, heightToCheck));
                    newY_e += 10;
                } while (!text_e.toUpperCase().contains(endKey.toUpperCase()));
                // titik Y tetap di start
                finalY = newY_s;
                if ((newY_e - newY_s) <= 30) { // heightToCheck + 10 [30 ok!]
                    //log.info("[CHECK POINT] {} in the same page with CLOSE distance", startKey);
                    finalY = finalY + 10; // scroll down position
                    finalHeight = heightToCheck + 10; //30
                } else {
                    finalHeight =  newY_e - newY_s - 10;
                }
            }
            // kalau beda, yaudah pake posisi startKey + content dibawahnya saja.
            // yg di return itu area content dibawah title nya
            return new Rectangle2D.Double(x, finalY + 10, maxWidth, finalHeight); // + 10 OK
        } catch (Exception e) {
            //e.printStackTrace();
            log.info("Something wrong at getRectangledArea: {}", e.getMessage());
            return null;
        }
    }
    
    protected Boolean isAtSamePage(Object currentPage, Object nextPage){
        return currentPage.equals(nextPage);
    }
//

    public Boolean detailCriteriaOfRegion(String regionName,
                                          String textCaptured) {
        //additional check
        return switch(regionName) {
            case "TITLE_BRD" -> textCaptured.toUpperCase()
                    .contains("BUSINESS REQUIREMENT DEFINITION");
            case "TITLE_PVT" -> textCaptured.toUpperCase()
                    .contains("PRODUCTION VERIFICATION TEST");
            case "TITLE_UAT" -> textCaptured.toUpperCase()
                    .contains("USER ACCEPTANCE TEST");
            case "CONTENT_BRD" -> checkAllContentBRD(textCaptured);
            case "CONTENT_UAT" -> checkAllContentUAT(textCaptured);
            case "CONTENT_PVT" -> checkAllContentPVT(textCaptured);
            default -> true;
        };
    }

    public Boolean checkAllContentBRD(String textCaptured) {
        //log.info("[1] checkAllContentBRD | textCaptured: {}", textCaptured);
        List<String> onListText = extractSectionTitles(textCaptured);
        //log.info("[1a] checkAllContentBRD | onListText: {}", onListText);
        for (BRD_CONTENT content : BRD_CONTENT.values()) {
            String contentAsText = content.name()
                    .replace("_", " ");
            boolean found = false;
            int size = onListText.size();
            for (int i = 0; i < size; i++){
                if (onListText
                        .get(i).trim()
                        .equals(contentAsText)) {
                    found = true;
                    //log.info("[2a] checkAllContentBRD | onListText: {} FOUND", contentAsText);
                    break;
                }
            }
            if (!found) {
                //log.warn("[2b] checkAllContentBRD | '{}' NOT FOUND in section list", contentAsText);
                return false;
            }
        }
        return true;
    }

    public Boolean checkAllContentPVT(String textCaptured) {
        List<String> onListText = extractSectionTitles(textCaptured);
        for (PVT_CONTENT content : PVT_CONTENT.values()) {
            String contentAsText = content.name()
                    .replace("_", " ");
            boolean found = false;
            int size = onListText.size();
            for (int i = 0; i < size; i++){
                if (onListText.get(i).trim().equals(contentAsText)) {
                    found = true;
                    break;
                }
            }
            if (!found) {return false;}
        }
        return true;
    }

    public Boolean checkAllContentUAT(String textCaptured) {
        List<String> onListText = extractSectionTitles(textCaptured);
        for (UAT_CONTENT content : UAT_CONTENT.values()) {
            String contentAsText = content.name()
                    .replace("_", " ");
            boolean found = false;
            int size = onListText.size();
            for (int i = 0; i < size; i++){
                if (onListText.get(i).trim().equals(contentAsText)) {
                    found = true;
                    break;
                }
            }
            if (!found) {return false;}
        }
        return true;
    }

    public static List<String> extractSectionTitles(String textCaptured) {
        List<String> sectionTitles = new ArrayList<>();
        String regex = "(\\d+(\\.\\d+)*\\s+([A-Za-z0-9 ]+))";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(textCaptured.toUpperCase());
        while (matcher.find()) {
            sectionTitles.add(matcher
                    .group(3)
                    .replaceAll("^[\\d\\.]+", "")
                    .replaceAll("^\\s+", "")
                    .trim()
            );
        }
        return sectionTitles;
    }

    protected Boolean validateEachContent(PDDocument document, List<Map<Object, Object>> tocData,
                                        String startKey, String endKey) {
        try {
            List<Map<Object, Object>> listOfContent = new ArrayList<>();
            boolean isCollecting = false;
            // special case for latest section
            if (endKey.isBlank() || endKey == null) {
                endKey = startKey;
            }

            // get all content between start and end key
            for (Map<Object, Object> entry : tocData) {
                String title = (String) entry.get("title");
                if (!isCollecting && title.toUpperCase()
                        .contains(startKey.toUpperCase())) {
                    isCollecting = true;
                }
                if (isCollecting) {
                    if ((startKey != endKey) &&
                            (title.toUpperCase()
                            .contains(endKey.toUpperCase()))) {
                        break;
                    }
                    listOfContent.add(entry);
                }
            }
            List<Boolean> validations = new ArrayList<>();
            int size = listOfContent.size();
            for (int i = 0; i < size; i++) {
                Map<Object, Object> current = listOfContent.get(i);
                Map<Object, Object> next = (i + 1 < listOfContent.size()) ?
                        listOfContent.get(i + 1) : listOfContent.get(i);
                Rectangle2D.Double area = getRectangledArea(
                        document,
                        (Integer) current.get("page"),
                        (String) current.get("title"),
                        (String) next.get("title"),
                        isAtSamePage(
                                current.get("page"),
                                next.get("page")
                        )
                );
                Boolean result = validateArea(
                        document,
                        (Integer) current.get("page"),
                        (String) current.get("title"),
                        area);
                validations.add(result);
            }
            return validations.stream().allMatch(valid -> valid);
        } catch (Exception e) {
            log.info("Something wrong at validateEachContent: {}", e.getMessage());
            return false;
        }
    }


    protected Boolean validateArea(PDDocument document, int page,
                                 String regionName, Rectangle2D region) {
        boolean isValid;
        try {
            String text = ocrProcessResult(document,
                    page, regionName, region);
            //log.info("[CHECK HERE 1] content for region [{}] => {}", regionName, text);
            isValid = !text.isEmpty();
            //log.info("[CHECK HERE 2] validateArea for region [{}] => {}", regionName, isValid);
            if (isValid) {
                return detailCriteriaOfRegion(regionName, text);
            }
            return isValid;
        } catch (IOException e) {
            log.info("Exception at validateArea: {}",
                    e.getMessage());
            return false;
        } catch (Exception ex) {
            log.info("Exception at validateArea: {} | {}",
                    ex.getCause(), ex.getMessage());
            return false;

        }
    }

    protected Integer searchPage(Integer possiblePage,
                               String keyWord, String regionName,
                               PDDocument document,
                               Rectangle2D positionTitle) {
        Integer resultPage;
        String text;
        Boolean checkerStatus;
        Integer maxRange = 5;
        try {
            do {
                text = ocrProcessResult(document, possiblePage, regionName, positionTitle);
                checkerStatus = pageKeywordCheck(text, keyWord);
                if (checkerStatus) {break;}
                possiblePage++;
            }
            while (possiblePage <= maxRange);
        } catch (IOException e) {
            log.info("Something wrong while searchPage: {}", e.getMessage());
        }
        //log.info("[OC] {} found at page {}", keyWord, possiblePage);
        resultPage = possiblePage;
        return resultPage;
    }
    
    protected Boolean pageKeywordCheck(String text,
                                     String keyWord){
//        if (!text.isEmpty() && text.toUpperCase().contains(keyWord.toUpperCase())) {
//            return true;
//        } else {
//            return false;
//        }
        return !text.isEmpty() && text.toUpperCase().contains(keyWord.toUpperCase());
    }

//    protected  Integer findPageForTitle(List<Map<Object, Object>> tocData, String searchTitle) {
//        Integer result = 0;
//        String title = "";
//        for (Map<Object, Object> entry : tocData) {
//            title = (String) entry.get("title");
//            if (title.toUpperCase().contains(searchTitle.toUpperCase())) {
//                result = (Integer) entry.get("page");
//                //log.info("findPageForTitle: {} found at {}", title, result);
//            }
//        }
//        return result;
//    }
}
