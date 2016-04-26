/***********************

    AdaptiveThreshold.java
    Joseph Potchen
    Class made to do adaptive thresholding

 ************************/




package com.potchen.apps.VisuallyImpairedReading;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.util.Log;

import java.util.Arrays;

/**
 * Created by Joe on 4/13/16.
 */
public class AdaptiveThreshold {

    static final int ADAPTIVE_THRESHOLD_GAUSSIAN = 1;
    static final int ADAPTIVE_THRESHOLD_MEAN = 2;
    private Bitmap bitmap;




    AdaptiveThreshold(Bitmap bitmap){
        this.bitmap = bitmap;
    }

    public static Bitmap threshold(Bitmap bitmap, int maxValue, int thresholdType, int blockSize, int constant){
        Bitmap holder = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);;
        switch (thresholdType){
            case 1:
                holder = gaussianThresh(bitmap, maxValue, 2, constant).copy(bitmap.getConfig(), true);
                break;
            case 2:
                holder = meanThresh(bitmap, maxValue, blockSize, constant).copy(bitmap.getConfig(), true);
                break;
            default:
                Log.e("ERROR", "Needs to be 1 or 2");
                break;
        }
        return holder;
    }

    private static Bitmap gaussianThresh(Bitmap bitmap, int maxValue, int blockSize, int constant){
        Bitmap holder = bitmap.copy(bitmap.getConfig(), true);

        int[][] luminanceArray = getInts(holder);
        int[][] gaussianMask = new int[(blockSize * 2) + 1][(blockSize * 2) + 1];
        /*int count = 0;
        int midX = gaussianMask[0].length;
        int midY = gaussianMask.length;
        for(int x1 = 0; x1 < blockSize + 1; x1++){
            for(int y1 = 0; y1 < blockSize + 1; y1++){
                gaussianMask[x1][y1] = (int)Math.exp(-((Math.pow(x1 - midX, 2)/(2*Math.pow(blockSize , 2))) + (Math.pow(x1 - midY, 2)/(2*Math.pow(blockSize , 2)))));
            }
        }*/
        int arrMid = (blockSize / 2) + 1;

        Arrays.fill(gaussianMask, 1);

        int addNum = 1;
        int holdNum;
        int contNum = 0;
        int y1;
        for(y1 = 0; y1 <= (gaussianMask[0].length / 2) + 1; y1++){
            holdNum = addNum;
            gaussianMask[y1][arrMid] += addNum;
            addNum -= 1;
            for(int x1 = 1; x1 <= gaussianMask.length / 2; x1++){
                if(addNum >= 0) {
                    gaussianMask[y1][arrMid - x1] += addNum;
                    gaussianMask[y1][arrMid + x1] += addNum;
                }
                addNum -= 1;
            }
            addNum = holdNum + 1;

        }

        for(int y2 = y1; y2 < gaussianMask[0].length; y2++){
            holdNum = addNum;

        }

        gaussianMask = new int[][]{
                {1 , 1 , 1 , 1 , 1},
                {1 , 1 , 2 , 1 , 1},
                {1 , 2 , 3 , 2 , 1},
                {0 , 1 , 2 , 1 , 1},
                {0 , 1 , 1 , 1 , 1}
        };

        for(int i = 0; i < holder.getWidth(); i++){//Increment down the x coordinates
            for(int j = 0; j < holder.getHeight(); j++){//Increment down the y coordinates
                int total=0;
                int counter=0;
                for(int x = i -blockSize; x <= i + blockSize; x++){
                    int gaussX = 0;
                    for(int y = j - blockSize; y <= j + blockSize; y++){
                        int gaussY = 0;
                        if(x != i && x < holder.getWidth() && x >= 0 && y != j && y < holder.getHeight() && y >=0){
                            total += luminanceArray[x][y] * gaussianMask [gaussY][gaussX];
                            counter += 1;
                        }
                        gaussY++;
                    }
                    gaussX++;
                }
                int threshNum = (total / counter) - constant;//average - constant

                //If number is less than the threshold (maxValue / 2 probably 127) then set black, else set white
                if(threshNum < maxValue / 2 && i < holder.getWidth() && j < holder.getHeight()){
                    holder.setPixel(i, j, 0xFF000000);
                }else{
                    holder.setPixel(i, j, 0xFFFFFFFF);
                }
            }
        }

        return holder;

    }

    private static int[][] getInts(Bitmap holder) {
        int luminanceArray[][] = new int[holder.getWidth()][holder.getHeight()];
        for(int i = 0; i < holder.getWidth(); i++) {//Increment down the x coordinates
            for (int j = 0; j < holder.getHeight(); j++) {//Increment down the y coordinates
                int pixel = holder.getPixel(i, j);//curent pixel
                luminanceArray[i][j] = (int)((Color.red(pixel) * 0.3) + (Color.green(pixel) * 0.59) + (Color.blue(pixel) * 0.11));//get weighted grayscale value
            }
        }
        return luminanceArray;
    }


    /*************************************
        Take average grayscale value of pixels blockSize above, to the right, to the left, and below center pixel
        Average them
        Subtract by a constant value
        If the ending value is less than half the maxValue (which should be ~127) make center pixel black
        Else set center pixel white

        NOTE: Could be more efficient if you put it into one for loop
     *************************************/
    private static Bitmap meanThresh(Bitmap bitmap, int maxValue, int blockSize, int constant){
        Bitmap holder = bitmap.copy(bitmap.getConfig(), true);

        //Storing luminance values in a 2D array so don't have to recalculate
        int[][] luminanceArray = getInts(holder);
        //Run through image and change pixels based on pixels surrounding.
        for(int i = 0; i < holder.getWidth(); i++){//Increment down the x coordinates
            for(int j = 0; j < holder.getHeight(); j++){//Increment down the y coordinates
                int total=0;
                int counter=0;
                for(int x = i - blockSize; x <= i + blockSize; x++){
                    for(int y = j - blockSize; y <= j + blockSize; y++){
                        if(x != i && x < holder.getWidth() && x >= 0 && y != j && y < holder.getHeight() && y >=0){
                            total += luminanceArray[x][y];
                            counter += 1;
                        }
                    }
                }
                int threshNum = (total / counter) - constant;//average - constant
                //If number is less than the threshold (maxValue / 2 probably 127) then set black, else set white
                if(threshNum < maxValue / 2 && i < holder.getWidth() && j < holder.getHeight()){
                    holder.setPixel(i, j, 0xFF000000);
                }else{
                    holder.setPixel(i, j, 0xFFFFFFFF);
                }
            }
        }



        return holder;

    }

    private Bitmap getGrayscaleBitmap(Bitmap bitmap){
        Bitmap grayscaleBmp = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c =  new Canvas(grayscaleBmp);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bitmap, 0, 0, paint);
        return grayscaleBmp;
    }

   /*private int[] getValues(int[][] luminanceArray, int x, int y, int radius){
       int count = 0;
       int returnArray[];
       for(int i = 0; i <= (radius*2) +1; i++){
           luminanceArray[(x - radius) + i][y - radius];
       }

       for(int i = luminanceArray[x - radius][y - radius]; i <= luminanceArray[x + radius][y - radius]; i++){

       }
       for(int i = luminanceArray[x - radius][y - radius]; i <=)


   }*/

    public Bitmap getBitmap() {
        return bitmap;
    }

    public void setBitmap(Bitmap bitmap) {
        this.bitmap = bitmap;
    }
}
