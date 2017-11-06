package com.ing.software.appscontrini2.OCR;

import android.app.Service;
import android.graphics.Bitmap;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.text.TextBlock;
import com.google.android.gms.vision.text.TextRecognizer;
import com.ing.software.appscontrini2.MainActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 *
 */

public class OcrAnalyzer {

    static void execute(Bitmap photo, Service service) {
        inspect(photo, service);
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        analyze(photo, service);
    }

    static void inspect(Bitmap photo, Service service) {

        TextRecognizer textRecognizer = new TextRecognizer.Builder(service).build();
        try {
            Frame frame = new Frame.Builder().setBitmap(photo).build();
            SparseArray<TextBlock> origTextBlocks = textRecognizer.detect(frame);
            List<TextBlock> textBlocks = new ArrayList<>();
            for (int i = 0; i < origTextBlocks.size(); i++) {
                TextBlock textBlock = origTextBlocks.valueAt(i);
                textBlocks.add(textBlock);
            }
            Collections.sort(textBlocks, new Comparator<TextBlock>() {
                @Override
                public int compare(TextBlock o1, TextBlock o2) {
                    int diffOfTops = o1.getBoundingBox().top - o2.getBoundingBox().top;
                    int diffOfLefts = o1.getBoundingBox().left - o2.getBoundingBox().left;
                    if (diffOfTops != 0) {
                        return diffOfTops;
                    }
                    return diffOfLefts;
                }
            });

            StringBuilder detectedText = new StringBuilder();
            for (TextBlock textBlock : textBlocks) {
                if (textBlock != null && textBlock.getValue() != null) {
                    detectedText.append(textBlock.getValue());
                    detectedText.append("\n");
                    Log.e("MYAPP", "detected: "+ textBlock.getValue());
                }
                Toast toast = Toast.makeText(service, detectedText, Toast.LENGTH_LONG);
                toast.show();
            }
        }
        finally {
            textRecognizer.release();
        }
    }

    static void analyze(Bitmap photo, Service service) {
        TextRecognizer textRecognizer = new TextRecognizer.Builder(service).build();
        try {
            Frame frame = new Frame.Builder().setBitmap(photo).build();
            SparseArray<TextBlock> origTextBlocks = textRecognizer.detect(frame);
            List<TextBlock> orderedTextBlocks = new ArrayList<>();
            for (int i = 0; i < origTextBlocks.size(); i++) {
                orderedTextBlocks.add(origTextBlocks.valueAt(i));
            }
            Collections.sort(orderedTextBlocks, new Comparator<TextBlock>() {
                @Override
                public int compare(TextBlock o1, TextBlock o2) {
                    int diffOfTops = o1.getBoundingBox().top - o2.getBoundingBox().top;
                    int diffOfLefts = o1.getBoundingBox().left - o2.getBoundingBox().left;
                    if (diffOfTops != 0) {
                        return diffOfTops;
                    }
                    return diffOfLefts;
                }
            });
            List<RawBlock> rawBlocks = new ArrayList<>();
            for (TextBlock textBlock : orderedTextBlocks) {
                rawBlocks.add(new RawBlock(textBlock, photo));
            }
            StringBuilder detectionList = new StringBuilder();
            for (RawBlock rawBlock : rawBlocks) {
                List<RawText> rawTexts = rawBlock.getRawTexts();
                for (RawText rawText : rawTexts) {
                    detectionList.append(rawText.getDetection())
                            .append("\n");
                }
                detectionList.append("\n");
            }
            Toast toast = Toast.makeText(service, detectionList, Toast.LENGTH_LONG);
            toast.show();
        }
        finally {
            textRecognizer.release();
        }
    }
}
