package com.westcoastlabs.quickwifi;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import com.soundcloud.android.crop.*;

import android.content.pm.ActivityInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.ExifInterface;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.app.ActionBar;
import android.support.v4.app.Fragment;
import android.annotation.TargetApi;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.AutoFocusCallback;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.Size;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Build;
import android.provider.MediaStore;

@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
public class MainActivity extends ActionBarActivity {

    SensorManager sensorManager;
    public MainActivity main;
    protected int FOCUS = 0;
    protected int CAPTURE = 1;
    protected int AUTO = 3;
    private Sensor sensor;
    protected TextView load;
    protected ProgressBar prog;
    protected ImageView capture, grey, flash;
    public String rawText = "";
    protected String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath() + "/QuickWifi/";
    protected String TEMP_IMAGE = ROOT + "tmp.png";
    protected String TEMP_IMAGE_CROPPED = ROOT + "tmp_crop.png";
    protected String TRAINED_DATA = ROOT + "tesseract-ocr";
    protected String ENG_TRAINED = TRAINED_DATA + "/tessdata/eng.traineddata";

    public String ORIENTATION = "";
    public boolean port = true;
    protected boolean taken;
    boolean flashon = false;
    protected int CROP = 2;
    protected static final String PHOTO_TAKEN = "photo_taken";

    Camera mCamera;
    private CameraPreview mPreview;

    public void init() {
        // Create an instance of Camera
        mCamera = getCameraInstance();

        addCameraParams();

        // Create our Preview view and set it as the content of our activity.
        mPreview = new CameraPreview(this, mCamera);
        FrameLayout preview = (FrameLayout) findViewById(R.id.cam_prev);
        preview.addView(mPreview);

    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setContentView(R.layout.activity_main);

        //ExtractAssets extract = new ExtractAssets();
        //extract.copyFolder(ROOT, getApplicationContext());
        try {
            init();
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Failed to initialise camera and preview.", Toast.LENGTH_LONG).show();
            finish();
        }

        grey = (ImageView) findViewById(R.id.imageView2);
        flash = (ImageView) findViewById(R.id.imageView4);
        flash.setVisibility(View.VISIBLE);
        flash.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    flashon = !flashon;
                    Camera.Parameters params = mCamera.getParameters();

                    if (flashon)
                        params.setFlashMode(Camera.Parameters.FLASH_MODE_ON);
                    else
                        params.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);

                    setOrientation();

                    mCamera.setParameters(params);
                } catch(Exception e) {}
            }
        });
        prog = (ProgressBar) findViewById(R.id.progressBar1);
        capture = (ImageView) findViewById(R.id.imageView3);
        load = (TextView) findViewById(R.id.textView1);
        load.setTextColor(Color.parseColor("#FF8800"));
        capture.setOnLongClickListener(new OnLongClickListener() {

            @Override
            public boolean onLongClick(View arg0) {

                AutoFocusCallback AutoFocusCallBack = new AutoFocusCallback() {

                    @Override
                    public void onAutoFocus(boolean success, Camera camera) {
                        cameraSound(CAPTURE);

                        processingView();
                        mCamera.takePicture(null, null, mPicture);
                    }
                };

                cameraSound(AUTO);
                mCamera.autoFocus(AutoFocusCallBack);
                return true;

            }
        });

        capture.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                    if (ORIENTATION.equals("port-up"))
                        capture.setImageResource(R.drawable.capture_press);
                    else if(ORIENTATION.equals("land-left"))
                        capture.setImageResource(R.drawable.capture_presslandl);
                    else if(ORIENTATION.equals("land-right"))
                        capture.setImageResource(R.drawable.capture_presslandr);
                    Log.d("TouchTest", "Touch down");
                }

                else if (event.getAction() == android.view.MotionEvent.ACTION_UP) {
                    capture.setImageResource(R.drawable.capture);
                    Log.d("TouchTest", "Touch up");
                    //new TakePicture(mCamera, TEMP_IMAGE, main).execute();
                    cameraSound(CAPTURE);
                    processingView();
                    mCamera.takePicture(null, null, mPicture);
                }

                return true;
            }
        });

        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        List<android.hardware.Sensor> sensorList = sensorManager.getSensorList(Sensor.TYPE_ORIENTATION);
        if (sensorList.size() > 0) {
            sensor = sensorList.get(0);
        }
        else {
            Log.i("Sensor", "Orientation sensor not present");
        }
        sensorManager.registerListener(orientationListener, sensor, 0, null);

        /*
        SensorManager sensorManager = (SensorManager) this.getSystemService(Context.SENSOR_SERVICE);
        sensorManager.registerListener(new SensorEventListener() {
            int orientation=-1;

            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.values[1]<7.5 && event.values[1]>-7.5 ) {
                    if (orientation!=1) {
                        Log.d("Sensor", "Landscape");
                        port = false;
                        setOrientation();
                    }
                    orientation=1;
                } else {
                    if (orientation!=0) {
                        Log.d("Sensor", "Portrait");
                        port = true;
                        setOrientation();
                    }
                    orientation=0;
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
                // TODO Auto-generated method stub

            }
        }, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);*/

        main = this;

        File f = new File(ENG_TRAINED);
        if (!f.isFile()) {
            Log.i("Init", "Init required");
            new Init(this).execute();
        }
    }
    private SensorEventListener orientationListener = new SensorEventListener() {

        @Override
        public void onAccuracyChanged(Sensor arg0, int arg1) {

        }

        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            if (sensorEvent.sensor.getType() == Sensor.TYPE_ORIENTATION) {
                float azimuth = sensorEvent.values[0];
                float pitch = sensorEvent.values[1];
                float roll = sensorEvent.values[2];

                if (pitch < -45 && pitch > -135) {
                    if (!ORIENTATION.equals("port-up")) {
                        Log.i("Sensor", "Top side of the phone is Up!");
                        ORIENTATION = "port-up";
                        setOrientation();
                    }
                } else if (pitch > 45 && pitch < 135) {
                    if (!ORIENTATION.equals("port-down")) {
                        Log.i("Sensor", "Bottom side of the phone is Up!");
                        ORIENTATION = "port-down";
                        //setOrientation();
                    }

                } else if (roll > 45) {
                    if (!ORIENTATION.equals("land-left")) {
                        Log.i("Sensor", "Right side of the phone is Up!");
                        ORIENTATION = "land-left";
                        setOrientation();
                    }

                } else if (roll < -45) {
                    if (!ORIENTATION.equals("land-right")) {
                        Log.i("Sensor", "Left side of the phone is Up!");
                        ORIENTATION = "land-right";
                        setOrientation();
                    }
                }

            }
        }

    };

    public void setOrientation() {

        if (ORIENTATION.equals("port-up")) {
            capture.setImageResource(R.drawable.capture);
            if (flashon)
                flash.setImageResource(R.drawable.flash);
            else
                flash.setImageResource(R.drawable.noflash);
        }
        /*else if (ORIENTATION.equals("port-down")) {

        }*/
        else if (ORIENTATION.equals("land-right")) {
            capture.setImageResource(R.drawable.capturelandr);
            if (flashon)
                flash.setImageResource(R.drawable.flashlandr);
            else
                flash.setImageResource(R.drawable.noflashlandr);

        }
        else if (ORIENTATION.equals("land-left")) {
            capture.setImageResource(R.drawable.capturelandl);
            if (flashon)
                flash.setImageResource(R.drawable.flashlandl);
            else
                flash.setImageResource(R.drawable.noflashlandl);
        }
    }

    private PictureCallback mPicture = new PictureCallback() {

        String TAG = "Picture Callback";

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.i("Picture Callback", "Saving Image");
            if (data == null) {
                Log.e(TAG, "NULL DATA IN CALLBACK");
            }

            try {
                camera.startPreview();
            } catch (Exception e) {}

            File pictureFile = new File(TEMP_IMAGE);
            if (pictureFile == null) {
                Log.d(TAG, "Error creating media file, check storage permissions: ");
                return;
            }

            Log.i("Picture Callback", "Writing image");
            try {
                FileOutputStream fos = new FileOutputStream(pictureFile);
                //bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }

            String rotate = "";
            if (ORIENTATION.equals("port-up"))
                rotate = String.valueOf(ExifInterface.ORIENTATION_ROTATE_90);
            else if (ORIENTATION.equals("land-right"))
                rotate = String.valueOf(ExifInterface.ORIENTATION_ROTATE_180);
            else if (ORIENTATION.equals("port-down"))
                rotate = String.valueOf(ExifInterface.ORIENTATION_ROTATE_270);

            if (!rotate.equals("")) {
                try {
                    ExifInterface exif = new ExifInterface(TEMP_IMAGE);
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION, rotate);
                    exif.saveAttributes();
                } catch (IOException e) {
                    Log.e(TAG, "Failed");
                }
            }

            onPhotoTaken();
        }


    };

    public void processingView() {
        //grey.setVisibility(View.VISIBLE);
        capture.setVisibility(View.INVISIBLE);
        prog.setVisibility(View.VISIBLE);
        load.setVisibility(View.VISIBLE);
        flash.setVisibility(View.INVISIBLE);
        //Animation fadeInAnimation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.fade_in);
        //fadeInAnimation.setFillAfter(true);

        //grey.startAnimation(fadeInAnimation);
    }

    public void addCameraParams() {
        Camera.Parameters params = mCamera.getParameters();

        List<String> focusModes = params.getSupportedFocusModes();

        if (focusModes.contains(Camera.Parameters.FOCUS_MODE_MACRO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_MACRO);
        } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
            params.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        }

        List<Size> sizes = params.getSupportedPictureSizes();

        params.setPictureSize(sizes.get(0).width, sizes.get(0).height);

        //params.setColorEffect(Camera.Parameters.EFFECT_MONO);

        mCamera.setParameters(params);

    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            float x = event.getX();
            float y = event.getY();
            float touchMajor = event.getTouchMajor();
            float touchMinor = event.getTouchMinor();

            Rect touchRect = new Rect((int) (x - touchMajor / 2), (int) (y - touchMinor / 2), (int) (x + touchMajor / 2), (int) (y + touchMinor / 2));

            cameraSound(FOCUS);
            this.submitFocusAreaRect(touchRect);
        }
        return true;
    }

    private void cameraSound(int sound) {
        MediaPlayer mediaPlayer = null;
        if (sound == FOCUS)
            mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.auto);
        else if (sound == CAPTURE)
            mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.camerashutter);
        else if (sound == AUTO)
            mediaPlayer = MediaPlayer.create(getApplicationContext(), R.raw.auto);
        mediaPlayer.start(); // no need to call prepare(); create() does that for you
    }

    private void submitFocusAreaRect(final Rect touchRect) {
        Camera.Parameters cameraParameters = mCamera.getParameters();

        if (cameraParameters.getMaxNumFocusAreas() == 0) {
            return;
        }

        // Convert from View's width and height to +/- 1000

        Rect focusArea = new Rect();

        focusArea.set(touchRect.left * 2000 / mPreview.getWidth() - 1000,
                touchRect.top * 2000 / mPreview.getHeight() - 1000,
                touchRect.right * 2000 / mPreview.getWidth() - 1000,
                touchRect.bottom * 2000 / mPreview.getHeight() - 1000);

        // Submit focus area to camera

        ArrayList<Camera.Area> focusAreas = new ArrayList<Camera.Area>();
        focusAreas.add(new Camera.Area(focusArea, 1000));

        cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
        cameraParameters.setFocusAreas(focusAreas);
        mCamera.setParameters(cameraParameters);

        // Start the autofocus operation

        mCamera.autoFocus(null);
    }

    private boolean checkCameraHardware(Context context) {
        if (context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
            // this device has a camera
            return true;
        } else {
            // no camera on this device
            return false;
        }
    }

    public static Camera getCameraInstance() {
        Camera c = null;
        try {
            c = Camera.open(); // attempt to get a Camera instance
            c.setDisplayOrientation(90);
        } catch (Exception e) {
            // Camera is not available (in use or does not exist)
        }

        return c; // returns null if camera is unavailable
    }

    private void stopPreviewAndFreeCamera() {

        try {
            if (mCamera != null) {
                // Call stopPreview() to stop updating the preview surface.
                mCamera.stopPreview();

                // Important: Call release() to release the camera for use by other
                // applications. Applications should release the camera immediately
                // during onPause() and re-open() it during onResume()).
                mCamera.release();

                mPreview = null;
                mCamera = null;
            }
        } catch (Exception e) {
            Toast.makeText(getApplicationContext(), "Error when closing camera", Toast.LENGTH_LONG).show();
        }
    }

    public void extractAndConnect() {
        new ExtractAndConnect(main, rawText).execute();
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        stopPreviewAndFreeCamera();
        sensorManager.unregisterListener(orientationListener);
    }



    @Override
    protected void onPause() {
        super.onPause();
        stopPreviewAndFreeCamera();
    }


    protected void onPhotoTaken() {
        prog.setVisibility(View.VISIBLE);

        load.setText("Saving Image");
        load.setVisibility(View.VISIBLE);

        cropImage();

    }

    @Override
    protected void onResume() {
        try {
            stopPreviewAndFreeCamera();
            init();
        } catch (Exception e) {}
        super.onResume();
    }

    protected void cropImage() {
        Uri inputUri = Uri.parse("file:///" + TEMP_IMAGE);
        Uri outputUri = Uri.parse("file:///" + TEMP_IMAGE_CROPPED);
        Log.i("Crop", "Starting crop");
        new Crop(inputUri).output(outputUri).start(main);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == Crop.REQUEST_CROP) {
            load.setText("Extracting Text");
            if (res == RESULT_CANCELED) {
                Toast.makeText(getApplicationContext(), "Retake Image.", Toast.LENGTH_SHORT).show();
                fadeBack();
            } else {
                new GetText(TEMP_IMAGE_CROPPED, "eng", TRAINED_DATA, this).execute();
            }
            try {
                init();
            } catch (Exception e) {}
            mCamera.startPreview();
        }
        setOrientation();
    }

    public void fadeBack() {
        //Animation fadeOutAnimation = AnimationUtils.loadAnimation(main.getApplicationContext(), R.anim.fade_out);
        //fadeOutAnimation.setFillAfter(false);
        //main.grey.startAnimation(fadeOutAnimation);

        capture.setVisibility(View.VISIBLE);
        flash.setVisibility(View.VISIBLE);
        load.setVisibility(View.INVISIBLE);
        prog.setVisibility(View.INVISIBLE);
        load.setText("Saving Image");
    }

}
