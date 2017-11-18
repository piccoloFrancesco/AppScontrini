package com.ing.software.ticketapp.OCR;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import android.util.SparseArray;

import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Class containing different methods to analyze a picture
 * @author Michelon
 * @author Zaglia
 */
class OcrAnalyzer {

    private TextRecognizer ocrEngine = null;
    private OnOcrResultReadyListener ocrResultCb = null;
    private Context context;
    private RawImage mainImage;
    private final String amountString = "TOTALE";
    private final int targetPrecision = 100; //Should be passed with image, or calculated with
        //resolution of source image


    /**
     * @author Michelon
     * @author Zaglia
     * Initialize the component.
     * If this call returns -1, check if the device has enough free disk space.
     * If so, try to call this method again.
     * When this method returned 0, it will be possible to call getOcrResult.
     * @param ctx Android context.
     * @return 0 if successful, negative otherwise.
     */
    int initialize(Context ctx) {
        context = ctx;
        ocrEngine = new TextRecognizer.Builder(ctx).build();
        ocrEngine.setProcessor(new Detector.Processor<TextBlock>() {
            @Override
            public void release() {
                ocrEngine.release();
            }

            @Override
            public void receiveDetections(Detector.Detections<TextBlock> detections) {
                //check if getOcrResult has been called to assign ocrResultCb.
                if (ocrResultCb != null) {
                    SparseArray<TextBlock> tempArray = detections.getDetectedItems();
                    List<RawBlock> rawBlocks = orderBlocks(mainImage, tempArray);
                    List<RawBlock.RawText> rawTexts = searchContinuousString(rawBlocks, amountString);
                    List<RawStringResult> valuedTexts = searchContinuousStringExtended(rawBlocks, rawTexts, targetPrecision);
                    OcrResult newOcrResult = new OcrResult();
                    newOcrResult.setAmountResults(valuedTexts);
                    List<RawGridResult> dateList = getDateList(rawBlocks);
                    newOcrResult.setDateMap(dateList);
                    ocrResultCb.onOcrResultReady(newOcrResult);
                }
            }
        });

        return ocrEngine.isOperational() ? 0 : -1;
        //failure causes: GSM package is not yet downloaded due to lack of time or lack of space.
    }

    /**
     * @author Michelon
     * @author Zaglia
     * Get an OcrResult from a Bitmap
     * @param frame Bitmap from which to extract an OcrResult. Not null.
     * @param resultCb Callback to get an OcrResult. Not null.
     */
    void getOcrResult(Bitmap frame, OnOcrResultReadyListener resultCb){
        ocrResultCb = resultCb;
        frame = getCroppedPhoto(frame, context);
        mainImage = new RawImage(frame);
        ocrEngine.receiveFrame(new Frame.Builder().setBitmap(frame).build());
    }

    /**
     * @author Michelon
     * Orders TextBlock decoded by detector in a list of RawBlocks
     * @param photo RawImage of the photo
     * @param origTextBlocks detected blocks
     * @return list of ordered RawBlocks
     */
    private static List<RawBlock> orderBlocks(RawImage photo, SparseArray<TextBlock> origTextBlocks) {
        Log.d("OcrAnalyzer.analyzeST:" , "Preferred grid is: " + photo.getGrid());
        List<TextBlock> newOrderedTextBlocks = new ArrayList<>();
        for (int i = 0; i < origTextBlocks.size(); i++) {
            newOrderedTextBlocks.add(origTextBlocks.valueAt(i));
        }
        newOrderedTextBlocks = OCRUtils.orderBlocks(newOrderedTextBlocks);
        Log.d("OcrAnalyzer.analyzeST:" , "New Blocks ordered");
        List<RawBlock> rawBlocks = new ArrayList<>();
        for (TextBlock textBlock : newOrderedTextBlocks) {
            rawBlocks.add(new RawBlock(textBlock, photo));
        }
        return rawBlocks;
    }

    /**
     * @author Michelon
     * Find first occurrence of chosen string in blocks from detector
     * @param rawBlocks list of ordered RawBlocks
     * @param testString string to find
     * @return First RawText in first RawBlock with target string
     */
    private static RawBlock.RawText searchFirstString(List<RawBlock> rawBlocks, String testString) {
        RawBlock.RawText targetText = null;
        for (RawBlock rawBlock : rawBlocks) {
            targetText = rawBlock.findFirst(testString);
            if (targetText != null)
                break;
        }
        if (targetText != null) {
            Log.d("OcrAnalyzer.analyzeBFS", "Found first target string: "+ testString + " \nat: " + targetText.getDetection());
            Log.d("OcrAnalyzer.analyzeBFS", "Target text is at (left, top, right, bottom): "+ targetText.getRect().left + "; "
                    + targetText.getRect().top + "; " + targetText.getRect().right + "; "+ targetText.getRect().bottom + ".");
        }
        return targetText;
    }

    /**
     * @author Michelon
     * Search for all occurrences of target string in detected (and ordered) RawBlocks
     * @param rawBlocks list of RawBlocks from detector
     * @param testString string to find
     * @return list of RawText where string is present
     */
    private static List<RawBlock.RawText> searchContinuousString(List<RawBlock> rawBlocks, String testString) {
        List<RawBlock.RawText> targetTextList = new ArrayList<>();
        for (RawBlock rawBlock : rawBlocks) {
            List<RawBlock.RawText> tempTextList;
            tempTextList = rawBlock.findContinuous(testString);
            if (tempTextList != null) {
                for (int i = 0; i < tempTextList.size(); i++) {
                    targetTextList.add(tempTextList.get(i));
                }
            }
        }
        if (targetTextList.size() >0) {
            for (RawBlock.RawText text : targetTextList) {
                Log.d("OcrAnalyzer", "Found target string: " + testString + " \nat: " + text.getDetection());
                Log.d("OcrAnalyzer", "Target text is at (left, top, right, bottom): " + text.getRect().left
                        + "; " + text.getRect().top + "; " + text.getRect().right + "; " + text.getRect().bottom + ".");
            }
        }
        return targetTextList;
    }

    /**
     * @author Michelon
     * From a list with all occurrences of a string in a photo, retrieves also text with similar
     * distance from top and bottom. Max difference from string detection is defined by precision. Order is top->bottom, left->right
     * @param rawBlocks list of blocks from original photo
     * @param targetTextList list of texts where string was found
     * @param precision precision to extend rect. See RawBlock.RawText.extendRect()
     * @return list of RawTexts in proximity of RawTexts containing target string
     */
    private static List<RawStringResult> searchContinuousStringExtended(List<RawBlock> rawBlocks, List<RawBlock.RawText> targetTextList, int precision) {
        List<RawStringResult> results = new ArrayList<>();
        for (RawBlock rawBlock : rawBlocks) {
            for (RawBlock.RawText rawText : targetTextList) {
                RawStringResult singleResult = new RawStringResult(rawText);
                List<RawBlock.RawText> tempResultList = rawBlock.findByPosition(OCRUtils.getExtendedRect(rawText.getRect(), rawText.getRawImage()), precision);
                if (tempResultList != null) {
                    singleResult.setDetectedTexts(tempResultList);
                    results.add(singleResult);
                    Log.d("OcrAnalyzer", "Found target string in: " + rawText.getDetection() + "\nin " + tempResultList.size() + " blocks.");
                }
            }
        }
        if (results.size() ==0) {
            Log.d("OcrAnalyzer", "Nothing found ");
        }
        else {
            Log.d("OcrAnalyzer", "Final list: ");
            for (RawStringResult rawStringResult : results) {
                List<RawBlock.RawText> textList = rawStringResult.getDetectedTexts();
                if (textList == null)
                    Log.d("OcrAnalyzer", "Value not found.");
                else {
                    for (RawBlock.RawText rawText : textList) {
                        Log.d("OcrAnalyzer", "Value: " + rawText.getDetection());
                    }
                }
            }
        }
        return results;
    }

    /**
     * Merges Lists with RawText + probability to find date from all blocks.
     * And orders it. Equal RawText are overwritten.
     * @param rawBlocks blocks from which retrieve maps
     * @return HashMap containing RawText + probability
     */
    private List<RawGridResult> getDateList(List<RawBlock> rawBlocks) {
        List<RawGridResult> fullList = new ArrayList<>();
        for (RawBlock rawBlock : rawBlocks) {
            fullList.addAll(rawBlock.getDateList());
        }
        Log.d("FINAL_LIST_SIZE_IS", " " + fullList.size());
        Collections.sort(fullList);
        return fullList;
    }

    /**
     * @author Michelon
     * Performs a quick detection on chosen photo and returns blocks detected
     * @param photo photo to analyze
     * @param context context to run analyzer
     * @return list of all blocks found (ordered)
     */
    private static List<TextBlock> quickAnalysis(Bitmap photo, Context context) {
        SparseArray<TextBlock> origTextBlocks;
        TextRecognizer textRecognizer = new TextRecognizer.Builder(context).build();
        try {
            Frame frame = new Frame.Builder().setBitmap(photo).build();
            origTextBlocks = textRecognizer.detect(frame);
        }
        finally {
            textRecognizer.release();
        }
        List<TextBlock> orderedTextBlocks = new ArrayList<>();
        for (int i = 0; i < origTextBlocks.size(); i++) {
            orderedTextBlocks.add(origTextBlocks.valueAt(i));
        }
        return orderedTextBlocks;
    }

    /**
     * @author Michelon
     * Crops photo using the smallest rect that contains all TextBlock in source photo
     * @param photo source photo
     * @param context context to run first analyzer
     * @return smallest cropped photo containing all TextBlocks
     */
    private static Bitmap getCroppedPhoto(Bitmap photo, Context context) {
        List<TextBlock> orderedTextBlocks = quickAnalysis(photo, context);
        Log.d("OcrAnalyzer.analyze:" , "Blocks detected");
        orderedTextBlocks = OCRUtils.orderBlocks(orderedTextBlocks);
        Log.d("OcrAnalyzer.analyze:" , "Blocks ordered");
        int[] borders = OCRUtils.getRectBorders(orderedTextBlocks, new RawImage(photo));
        int left = borders[0];
        int right = borders[2];
        int top = borders[1];
        int bottom = borders[3];
        return OCRUtils.cropImage(photo, left, top, right, bottom);
    }
}