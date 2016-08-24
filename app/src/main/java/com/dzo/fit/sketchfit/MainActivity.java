package com.dzo.fit.sketchfit;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.util.Log;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.hardware.Camera.Size;

import java.util.List;

public class MainActivity extends Activity {

    private OrientationEventListener mOrientationListener;

    private CameraView camPreview;
    private ImageView MyCameraPreview = null;
    private FrameLayout mainLayout;
    private int inWidth = 640;
    private int inHeight = 480;
    private int outWidth = 320;
    private int outHeight = 240;
    private int currentResolution = 1;
    private int[] resIndexes = new int[] { 15 }; //{ 16, 15, 14, 13, 12, 11, 10 };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //Set this APK Full screen
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        //Set this APK no title
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        InitLayout(outWidth, outHeight, true);
        InitButtons();
        ToastResolution(outWidth, outHeight);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mOrientationListener.disable();
    }

    @Override
    protected void onPause()
    {
        if ( camPreview != null)
            camPreview.onPause();
        super.onPause();
    }

    private void InitButtons() {
        ImageButton imgToggle = (ImageButton)findViewById(R.id.imgToggle);
        imgToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                camPreview.ToggleImageProcessing();
                if (camPreview.IsImageProcessingEnabled())
                    MyCameraPreview.setVisibility(View.VISIBLE);
                else
                    MyCameraPreview.setVisibility(View.INVISIBLE);
            }
        });

        SeekBar seekBarTreshold = (SeekBar)findViewById(R.id.seekBarTreshold);
        final TextView textTreshold = (TextView)findViewById(R.id.textTreshold);
        float currentValue = (float)seekBarTreshold.getProgress()  / seekBarTreshold.getMax();
        String val = String.valueOf(currentValue);
        textTreshold.setText(String.valueOf(currentValue).substring(0, Math.min(8, val.length())));
        camPreview.SetTreshold(currentValue);
        seekBarTreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float newTreshold = 0.075f * (float) progress / seekBar.getMax();
                String val = String.valueOf(newTreshold);
                textTreshold.setText(String.valueOf(newTreshold).substring(0,Math.min(8, val.length())));
                camPreview.SetTreshold(newTreshold);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        ImageButton imgResolutions = (ImageButton)findViewById(R.id.imgResolutions);
        imgResolutions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ToggleResolution();
                }
            });
    }

    private void InitLayout(int width, int height, boolean processingEnabled) {
        MyCameraPreview = new ImageView(this);

        SurfaceView camView = new SurfaceView(this);

        SurfaceHolder camHolder = camView.getHolder();
        camPreview = new CameraView(inWidth, inHeight, outWidth, outHeight, MyCameraPreview, processingEnabled, (TextView)findViewById(R.id.textFps), getApplicationContext());

        camHolder.addCallback(camPreview);
        camHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mainLayout = (FrameLayout) findViewById(R.id.camera_view);
        int fixWidth = (int)((float)width * 1080f / (float)height);
        mainLayout.addView(camView, new FrameLayout.LayoutParams(fixWidth, 1080));
        mainLayout.addView(MyCameraPreview, new FrameLayout.LayoutParams(fixWidth, 1080));
    }

    private void ToggleResolution() {
        List<Camera.Size> sizeList = camPreview.getCamera().getParameters().getSupportedPictureSizes();
        boolean processingEnabled = camPreview.IsImageProcessingEnabled();
        ++currentResolution;
        currentResolution = currentResolution % resIndexes.length;
        mainLayout.removeAllViews();
        mainLayout = null;
        Camera.Size currentSize = sizeList.get(resIndexes[currentResolution]);
        InitLayout(currentSize.width, currentSize.height, processingEnabled);
        ToastResolution(currentSize.width, currentSize.height);
    }

    private void ToastResolution(int width, int height) {
        Context context = getApplicationContext();
        CharSequence text = "Res: " + width + " x " + height;
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }
}
