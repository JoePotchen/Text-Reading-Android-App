package com.potchen.apps.cameratest;

import android.annotation.TargetApi;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.speech.tts.TextToSpeech;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ProgressBar;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    //CONSTANTS
    public static final String DATA_PATH = Environment
            .getExternalStorageDirectory().toString() + "/CamTest";
    public static final String TAG = "CamTestApp";
    public static final String LANG = "eng";

    //BOOLS
    boolean _taken = false;
    boolean notWorking = true;//Disallow onclick during processing
    Camera camera;

    Preview preview;
    Camera.ShutterCallback shutterCallback = new Camera.ShutterCallback() {
        public void onShutter() {
            //			 Log.d(TAG, "onShutter'd");
        }
    };
    Camera.PictureCallback rawCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            //			 Log.d(TAG, "onPictureTaken - raw");
        }
    };
    private byte[] threadData;
    private TextToSpeech myTTS;
    private ProgressBar spinner;
    private Thread computeThread = new Thread(new Runnable() {
        @Override
        public void run() {
            saveImageTask(threadData);
        }
    });

    SharedPreferences sharedPreference;

    private Thread speakTreat = new Thread(new Runnable(){
        @Override
    public void run(){
            while(computeThread.isAlive()){
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                    ttsGreater21("Reading Text Please Wait");
                }else{
                    ttsUnder20("Reading Text Please Wait");
                }
                try{
                    Thread.sleep(1000);
                }catch (InterruptedException e){
                    Log.d("ERROR", "Thread.Sleep failed");
                }

            }
        }
    });



    Camera.PictureCallback jpegCallback = new Camera.PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            //new SaveImageTask().execute(data);
            preview.setVisibility(View.INVISIBLE);

            threadData = data;
            computeThread.run();

            resetCam();
            preview.setVisibility(View.VISIBLE);
            Log.d("SUCCESS", "onPictureTaken - jpeg");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);


        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);
        preview = new Preview(this, (SurfaceView)findViewById(R.id.surfaceView));
        preview.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ((FrameLayout) findViewById(R.id.layout)).addView(preview);
        preview.setKeepScreenOn(true);
        sharedPreference = PreferenceManager.getDefaultSharedPreferences(getBaseContext());

        myTTS = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR){
                    myTTS.setLanguage(Locale.US);
                }
            }
        });

        String[] paths = new String[]{DATA_PATH, DATA_PATH + "/tessdata/"};
        for (String path : paths){
            File dir = new File(path);
            if(!dir.exists()){
                if(!dir.mkdirs()){
                    Log.v(TAG, "ERROR: Creation of directory " + path + " on sdcard failed");
                    return;
                }else{
                    Log.v(TAG, "Created directory " + path + " on sdcard");
                }
            }
        }

        putTessData();

        preview.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View arg0) {
                if(notWorking)
                    camera.takePicture(shutterCallback, rawCallback, jpegCallback);
            }
        });

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu){
        getMenuInflater().inflate(R.menu.menu_main, menu);




        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item){

        int itemId= item.getItemId();
        switch (itemId){
            case R.id.devSettings:
                Intent i = new Intent(this, SettingsActivity.class);
                startActivity(i);
                return true;
        }

        return true;
    }

    private void putTessData() {
        if(!(new File(DATA_PATH + "/tessdata/" + LANG + ".traineddata")).exists()){
            try{
                AssetManager assetManager = getAssets();
                InputStream in = assetManager.open("eng.traineddata");
                OutputStream out = new FileOutputStream(DATA_PATH + "/tessdata/" + LANG + ".traineddata");

                byte[] buf = new byte[1024];
                int len;

                while((len = in.read(buf)) > 0){
                    out.write(buf, 0, len);
                }
                in.close();
                out.close();

                Log.v(TAG, "Copied " + LANG + " traineddata");
            }catch (IOException e){
                Log.e(TAG, "Was unable to copy " + LANG + " traineddata " + e.toString());
            }


        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        int numCams = Camera.getNumberOfCameras();
        if(numCams > 0){
            try{
                camera = Camera.open(0);
                camera.setDisplayOrientation(90);
                camera.startPreview();
                preview.setCamera(camera);
            } catch (RuntimeException ex){
                Log.d("ERROR", "Failed onResume");
            }
        }
    }

    @Override
    protected void onPause() {
        if(camera != null) {
            camera.stopPreview();
            preview.setCamera(null);
            camera.release();
            camera = null;
        }
        super.onPause();
    }

    private void resetCam() {
        camera.startPreview();
        preview.setCamera(camera);
    }

    private void refreshGallery(File file) {
        Intent mediaScanIntent = new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        mediaScanIntent.setData(Uri.fromFile(file));
        sendBroadcast(mediaScanIntent);
    }

    private void saveImageTask(byte[] data){
        notWorking = false;

        /*
        FileOutputStream outstream = null;
        try {
            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File(DATA_PATH);
            dir.mkdir();

            File outFile = new File(DATA_PATH, "img.jpg");
            outstream = new FileOutputStream(outFile);
            outstream.write(data[0]);
            outstream.flush();
            outstream.close();

            refreshGallery(outFile);
            Log.d("SUCCESS", "Saved image to " + outFile.getAbsolutePath());
        } catch (FileNotFoundException e) {
            Log.d("ERROR", "On SaveImageTask " + e.toString());
        } catch (IOException e) {
            Log.d("ERROR", "On SaveImageTask " + e.toString());
        }
        */

        _taken = true;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = Integer.parseInt(sharedPreference.getString("sampleSize", "4"));

        //Bitmap bitmap = BitmapFactory.decodeFile(DATA_PATH + "/img.jpg", options);
        Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
        Log.d(TAG, "Width: " + bitmap.getWidth() + "| Height: " + bitmap.getHeight());
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HH_mm_ss");


        FileOutputStream out = null;
        try{
            File imageFileFolder = new File(Environment.getExternalStorageDirectory(), "CameraTest");
            imageFileFolder.mkdir();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
            File f = new File(Environment.getExternalStorageDirectory(), "CameraTest/" + "PreThresh_" + dateFormat.format(new Date()) + ".png");
            out = new FileOutputStream(f);
            out.write(bytes.toByteArray());
            out.close();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try{
                if(out != null){
                    out.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }

        Bitmap threshed;
        threshed =  AdaptiveThreshold.threshold(bitmap, Integer.parseInt(sharedPreference.getString("maxValue", "255")), AdaptiveThreshold.ADAPTIVE_THRESHOLD_MEAN,
                Integer.parseInt(sharedPreference.getString("blockSize", "5")), Integer.parseInt(sharedPreference.getString("constant", "10"))).copy(bitmap.getConfig(), true);

        //MediaStore.Images.Media.insertImage(getContentResolver(), threshed, "Threshold_Test", "");

        Mat mat = new Mat();
        Mat matThresh = new Mat();
        Utils.bitmapToMat(bitmap, mat);
        Imgproc.adaptiveThreshold(mat, matThresh, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 15, 40);

        out = null;
        try{
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            threshed.compress(Bitmap.CompressFormat.PNG, 100, bytes);
            File f = new File(Environment.getExternalStorageDirectory(), "CameraTest/" + "PostThresh_" + dateFormat.format(new Date()) + ".png");
            out = new FileOutputStream(f);
            out.write(bytes.toByteArray());
            out.close();
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try{
                if(out != null){
                    out.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }

        //bitmap = adaptiveThresh
        /*
        Mat mat = new Mat();
        Mat matThresh = new Mat();
        Utils.bitmapToMat(bitmap, mat);
        Imgproc.adaptiveThreshold(mat, matThresh, 255, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY, 15, 40);

        try {
            bitmap = null;
            bitmap = Bitmap.createBitmap(matThresh.cols(), matThresh.rows(), Bitmap.Config.ARGB_8888);
            Utils.matToBitmap(matThresh, bitmap);
        }catch (CvException e){
            Log.e("ERROR", "Mat to bitmap");
        }
        */

        /*
        try {
            ExifInterface exif = new ExifInterface(DATA_PATH);
            int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

            Log.v(TAG, "Orient: " + exifOrientation);
            int rotate = 0;
            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
            }
            Log.v(TAG, "Rotation: " + rotate);

            if (rotate != 0) {

                // Getting width & height of the given image.
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();

                // Setting pre rotate
                Matrix mtx = new Matrix();
                mtx.preRotate(rotate);

                // Rotating Bitmap
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
            }

            // Convert to ARGB_8888, required by tess
            bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        } catch (IOException e) {
            Log.e(TAG, "Couldn't correct orientation: " + e.toString());
        }

        FileOutputStream out = null;
        try{
            out = new FileOutputStream("OcrImage");
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try{
                if(out != null){
                    out.close();
                }
            }catch (IOException e){
                e.printStackTrace();
            }
        }
        */





        /*TessBaseAPI baseAPI = new TessBaseAPI();
        baseAPI.setDebug(true);
        baseAPI.init(DATA_PATH, LANG);
        //baseAPI.setImage(bitmap);
        baseAPI.setImage((uchar*)matThresh.dataAddr());
        String recognizedText = baseAPI.getUTF8Text();
        baseAPI.end();

        Log.v(TAG, "OCRED TEXT: " + recognizedText);

        if( LANG.equalsIgnoreCase("eng")){
            recognizedText = recognizedText.replaceAll("[^a-zA-Z0-9]+", " ");
        }


        recognizedText = recognizedText.trim();




        if(recognizedText.length() != 0){



            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                ttsGreater21(recognizedText);
            }else{
                ttsUnder20(recognizedText);
            }
        */
        notWorking = true;
    }

 /*   private class SaveImageTask extends AsyncTask<byte[], Void, Void> {

        @Override
        protected Void doInBackground(byte[]... data) {
            FileOutputStream outstream = null;
            try {
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File(DATA_PATH);
                dir.mkdir();

                File outFile = new File(DATA_PATH, "img.jpg");
                outstream = new FileOutputStream(outFile);
                outstream.write(data[0]);
                outstream.flush();
                outstream.close();

                refreshGallery(outFile);
            } catch (FileNotFoundException e) {
                Log.d("ERROR", "On SaveImageTask " + e.toString());
            } catch (IOException e) {
                Log.d("ERROR", "On SaveImageTask " + e.toString());
            }


            _taken = true;
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;

            Bitmap bitmap = BitmapFactory.decodeFile(DATA_PATH + "/img.jpg", options);


            try {
                ExifInterface exif = new ExifInterface(DATA_PATH);
                int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);

                Log.v(TAG, "Orient: " + exifOrientation);
                int rotate = 0;
                switch (exifOrientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        rotate = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        rotate = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        rotate = 270;
                        break;
                }
                Log.v(TAG, "Rotation: " + rotate);

                if (rotate != 0) {

                    // Getting width & height of the given image.
                    int w = bitmap.getWidth();
                    int h = bitmap.getHeight();

                    // Setting pre rotate
                    Matrix mtx = new Matrix();
                    mtx.preRotate(rotate);

                    // Rotating Bitmap
                    bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
                }

                // Convert to ARGB_8888, required by tess
                bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
            } catch (IOException e) {
                Log.e(TAG, "Couldn't correct orientation: " + e.toString());
            }

            TessBaseAPI baseAPI = new TessBaseAPI();
            baseAPI.setDebug(true);
            baseAPI.init(DATA_PATH, LANG);
            baseAPI.setImage(bitmap);
            String recognizedText = baseAPI.getUTF8Text();
            baseAPI.end();

            Log.v(TAG, "OCRED TEXT: " + recognizedText);

            if( LANG.equalsIgnoreCase("eng")){
                recognizedText = recognizedText.replaceAll("[^a-zA-Z0-9]+", " ");
            }

            if(recognizedText.length() != 0){



                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP){
                    ttsGreater21(recognizedText);
                }else{
                    ttsUnder20(recognizedText);
                }
            }

            recognizedText = recognizedText.trim();

            *//*FileOutputStream outStream = null;


            // Write to SD Card
            try {
                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File (sdCard.getAbsolutePath() + "/camtest");
                dir.mkdirs();

                String fileName = String.format("%d.jpg", System.currentTimeMillis());
                File outFile = new File(dir, fileName);

                outStream = new FileOutputStream(outFile);
                outStream.write(data[0]);
                outStream.flush();
                outStream.close();

                Log.d("SUCCESS", "onPictureTaken - wrote bytes: " + data.length + " to " + outFile.getAbsolutePath());

                refreshGallery(outFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
            }
            return null;*//*
            return null;
        }

    }*/

    @SuppressWarnings("deprecation")
    private void ttsUnder20(String text) {
        HashMap<String, String> map = new HashMap<>();
        map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MessageId");
        myTTS.speak(text, TextToSpeech.QUEUE_FLUSH, map);
    }
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void ttsGreater21(String text){
        String utteranceID = this.hashCode() + "";
        myTTS.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceID);
    }



}
