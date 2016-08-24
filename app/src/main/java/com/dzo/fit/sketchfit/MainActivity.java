package com.dzo.fit.sketchfit;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

public class MainActivity extends Activity {

    private OrientationEventListener mOrientationListener;
    private CameraView camPreview;
    private ImageView MyCameraPreview = null;
    private FrameLayout mainLayout;
    private int idealWidth = 320;
    private int idealHeight = 240;
    private final int MY_PERMISSIONS_REQUEST_READ_CONTACTS = 420;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.CAMERA}, MY_PERMISSIONS_REQUEST_READ_CONTACTS);
        else
            OnCameraGranted();
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

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_CONTACTS: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    OnCameraGranted();
                }
            }
        }
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
                textTreshold.setText(String.valueOf(newTreshold).substring(0, Math.min(8, val.length())));
                camPreview.SetTreshold(newTreshold);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
    }

    private void InitLayout(int width, int height, boolean processingEnabled) {
        MyCameraPreview = new ImageView(this);

        SurfaceView camView = new SurfaceView(this);

        SurfaceHolder camHolder = camView.getHolder();
        camPreview = new CameraView(idealWidth, idealHeight, MyCameraPreview, processingEnabled, (TextView)findViewById(R.id.textFps), getApplicationContext());

        camHolder.addCallback(camPreview);
        camHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

        mainLayout = (FrameLayout) findViewById(R.id.camera_view);
        int defHeight = getWindowManager().getDefaultDisplay().getHeight();
        int fixWidth = (int)((float)width * defHeight / (float)height);
        mainLayout.addView(camView, new FrameLayout.LayoutParams(fixWidth, defHeight));
        mainLayout.addView(MyCameraPreview, new FrameLayout.LayoutParams(fixWidth, defHeight));
    }

    private void OnCameraGranted() {
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_main);

        InitLayout(idealWidth, idealHeight, true);
        InitButtons();
    }
}
