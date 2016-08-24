package com.dzo.fit.sketchfit;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.Camera;
import android.os.Handler;
import android.os.Looper;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.view.SurfaceHolder;
import android.widget.ImageView;
import android.widget.TextView;
import java.io.IOException;
import java.util.List;

/**
 * Created by MartinDzurenko on 25.02.2016.
 */
public class CameraView implements SurfaceHolder.Callback, Camera.PreviewCallback {

    private Camera mCamera = null;
    private ImageView MyCameraPreview = null;
    private Bitmap outputBitmap = null;
    private byte[] FrameData = null;
    private int imageFormat;
    private int previewWidth;
    private int previewHeight;
    private int outWidth;
    private int outHeight;
    private boolean processingEnabled = true;
    private boolean bProcessing = false;

    private RenderScript kernelContext;
    private ScriptIntrinsicYuvToRGB scriptYuvToRGB;
    private ScriptC_imageprocessing scriptIP;
    private Allocation allocationIn;
    private Allocation allocationYUV;
    private Allocation allocationOut;
    private Allocation allocationTmp1;
    private Allocation allocationTmp2;
    private Allocation allocationTmp5;
    private Allocation allocationTmp3;
    private Allocation allocationTmp4;
    private Allocation allocationEdges;

    final long ONE_SEC_NANOS = 1000000000L;
    long previousTime;
    private TextView textFps;

    Handler mHandler = new Handler(Looper.getMainLooper());

    public CameraView(int idealPreviewWidth, int idealPreviewHeight, ImageView CameraPreview, boolean processingEnabled, TextView textFps, Context context)
    {
        Camera.Size suitableSize = FindSuitableCameraSize(idealPreviewWidth, idealPreviewHeight);
        if (suitableSize != null) {
            previewWidth = suitableSize.width;
            previewHeight = suitableSize.height;
        } else {
            outWidth = idealPreviewWidth;
            outHeight = idealPreviewHeight;
        }
        previewWidth = 2 * outWidth;
        previewHeight = 2 * outHeight;

        this.MyCameraPreview = CameraPreview;
        this.processingEnabled = processingEnabled;
        this.textFps = textFps;

        previousTime = 0;
        textFps.setText("x");

        outputBitmap = Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888);

        Bitmap tmpBitmap = Bitmap.createBitmap(previewWidth, previewHeight, Bitmap.Config.ARGB_8888);

        kernelContext = RenderScript.create(context);
        scriptIP = new ScriptC_imageprocessing(kernelContext);
        allocationOut = Allocation.createFromBitmap(kernelContext, outputBitmap);
        allocationIn =  Allocation.createFromBitmap(kernelContext, tmpBitmap);

        Type.Builder typeYUV = new Type.Builder(kernelContext, Element.createPixel(kernelContext, Element.DataType.UNSIGNED_8, Element.DataKind.PIXEL_YUV));
        typeYUV.setYuvFormat(ImageFormat.NV21);
        // allocation for the YUV input from the camera
        allocationYUV = Allocation.createTyped(kernelContext, typeYUV.setX(previewWidth).setY(previewHeight).create(), Allocation.USAGE_SCRIPT);
        // allocations for image processing
        allocationTmp1 = Allocation.createTyped(kernelContext, Type.createXY(kernelContext, Element.F32_3(kernelContext), previewWidth, previewHeight), Allocation.USAGE_SCRIPT);
        allocationTmp2 = Allocation.createTyped(kernelContext, allocationTmp1.getType(), Allocation.USAGE_SCRIPT);
        allocationTmp5 = Allocation.createTyped(kernelContext, allocationTmp1.getType(), Allocation.USAGE_SCRIPT);
        allocationEdges = Allocation.createTyped(kernelContext, Type.createXY(kernelContext, Element.F32(kernelContext), previewWidth, previewHeight), Allocation.USAGE_SCRIPT);
        allocationTmp3 = Allocation.createTyped(kernelContext, Type.createXY(kernelContext, Element.F32_3(kernelContext), outWidth, outHeight), Allocation.USAGE_SCRIPT);
        allocationTmp4 = Allocation.createTyped(kernelContext, allocationTmp3.getType(), Allocation.USAGE_SCRIPT);
        //create the instance of the YUV2RGB (built-in) RS intrinsic
        scriptYuvToRGB = ScriptIntrinsicYuvToRGB.create(kernelContext, Element.U8_4(kernelContext));

        // set initial RenderScript global values
        scriptIP.set_edgeBuffer(allocationEdges);
        scriptIP.set_imageWidth(previewWidth);
        scriptIP.set_imageHeight(previewHeight);
        scriptIP.set_sImageWidth(outWidth);
        scriptIP.set_sImageHeight(outHeight);
    }

    private Camera.Size FindSuitableCameraSize(int idealWidth, int idealHeight) {
        Camera.Size bestSize = null;
        int bestDist = Integer.MAX_VALUE;
        Camera staticCamera = Camera.open();
        if (staticCamera == null || staticCamera.getParameters() != null || staticCamera.getParameters().getSupportedPictureSizes() != null)
            return null;
        List<Camera.Size> supportedSizes = staticCamera.getParameters().getSupportedPictureSizes();
        for (Camera.Size camSize : supportedSizes) {
            int dist = Math.abs(camSize.width - idealWidth) + Math.abs(camSize.height - idealHeight);
            if (dist < bestDist && Contains2xSize(supportedSizes, camSize)) {
                bestSize = camSize;
                bestDist = dist;
            }
        }
        return bestSize;
    }

    private boolean Contains2xSize(List<Camera.Size> allSizes, Camera.Size smallerSize) {
        for (Camera.Size camSize : allSizes) {
            if (camSize.width == 2*smallerSize.width && camSize.height == 2*smallerSize.height)
                return true;
        }
        return false;
    }

    @Override
    public void onPreviewFrame(byte[] arg0, Camera arg1)
    {
        // At preview mode, the frame data will push to here.
        if (imageFormat == ImageFormat.NV21)
        {
            if (!bProcessing && processingEnabled)
            {
                UpdateFps();
                FrameData = arg0;
                mHandler.post(DoImageProcessing);
            }
        }
    }

    public Camera getCamera() {
        return mCamera;
    }

    public void onPause()
    {
        mCamera.stopPreview();
    }

    @Override
    public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3)
    {
        Camera.Parameters parameters = mCamera.getParameters();

        // Set the camera preview size
        parameters.setPreviewSize(previewWidth, previewHeight);

        imageFormat = parameters.getPreviewFormat();

        mCamera.setParameters(parameters);

        mCamera.startPreview();
    }

    @Override
    public void surfaceCreated(SurfaceHolder arg0)
    {
        mCamera = Camera.open();
        try
        {
            // If did not set the SurfaceHolder, the preview area will be black.
            mCamera.setPreviewDisplay(arg0);
            mCamera.setPreviewCallback(this);
        }
        catch (IOException e)
        {
            mCamera.release();
            mCamera = null;
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder arg0)
    {
        mCamera.setPreviewCallback(null);
        mCamera.stopPreview();
        mCamera.release();
        mCamera = null;
    }

    // runs image processing on a camera image (RenderScript kernels)
    private Runnable DoImageProcessing = new Runnable() {
        public void run()
        {
            bProcessing = true;

            // initialize buffer containing camera feed image (YUV format)
            allocationYUV.copyFrom(FrameData);

            // convert YUV image to RGBa
            scriptYuvToRGB.setInput(allocationYUV);
            scriptYuvToRGB.forEach(allocationIn);

            // transform RGBa byte pixels [0;255] to RGB float pixels [0;1]
            scriptIP.set_floatBuffer(allocationTmp5);
            scriptIP.forEach_initFloatBuffer(allocationIn);

            // apply smoothing on 640x480 resolution image
            scriptIP.forEach_applySmooth(allocationTmp2);
            scriptIP.set_floatBuffer(allocationTmp2);
            scriptIP.forEach_applySmooth(allocationTmp1);
            scriptIP.set_floatBuffer(allocationTmp1);
            // evalute edges in 640x480 resolution image and store them for later use
            scriptIP.forEach_applyEdgeDetection(allocationEdges);

            // scale down the original 640x480 image to 320x240
            scriptIP.set_floatBuffer(allocationTmp5);
            scriptIP.forEach_imgScaleDown(allocationTmp3);

            // apply evaluated edges on 320x240 image
            scriptIP.set_floatBuffer(allocationTmp3);
            scriptIP.forEach_applyEdges(allocationTmp4);

            // apply 4 passes of bilateral filter on 320x240 image
            scriptIP.set_floatBuffer(allocationTmp4);
            scriptIP.forEach_applyBilateralFilter_float3(allocationTmp3);
            scriptIP.set_floatBuffer(allocationTmp3);
            scriptIP.forEach_applyBilateralFilter_float3(allocationTmp4);
            scriptIP.set_floatBuffer(allocationTmp4);
            scriptIP.forEach_applyBilateralFilter_float3(allocationTmp3);
            scriptIP.set_floatBuffer(allocationTmp3);
            scriptIP.forEach_applyBilateralFilter_uchar4(allocationOut);

            // setup the resulting 320x240 image for preview
            allocationOut.syncAll(Allocation.USAGE_SHARED);
            MyCameraPreview.setImageBitmap(outputBitmap);
            MyCameraPreview.invalidate();

            bProcessing = false;
        }
    };

    // update the displayed time it took to evaluate last filter
    private void UpdateFps() {
        if (!processingEnabled) {
            textFps.setText("x");
            return;
        }
        if (previousTime == 0L) {
            previousTime = System.nanoTime();
            return;
        }
        long currentTime = System.nanoTime();
        textFps.setText(String.valueOf(ONE_SEC_NANOS / (currentTime - previousTime)));
        previousTime = currentTime;
    }

    // turn on/off abstration filter
    public void ToggleImageProcessing() {
        processingEnabled = !processingEnabled;
    }

    // set new edge detection treshold
    public void SetTreshold(float newTreshold) {
        scriptIP.set_treshold(newTreshold);
    }

    // says whether abstraction filter is currently enabled
    public boolean IsImageProcessingEnabled() {
        return processingEnabled;
    }
}