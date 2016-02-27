package com.example.android.mediarecorder;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.example.android.common.media.CameraHelper;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.List;

/**
 * Created by Artem Pelenitsyn on 22.02.16.
 */
public class MainService extends Service implements TextureView.SurfaceTextureListener {


    private WindowManager windowManager;
    private static final String TAG = "RecorderService";

    private Camera mCamera = null;
    private TextureView mPreview;
    private MediaRecorder mMediaRecorder = null;
    private FileDescriptor outputFileDescriptor = null;
    private int quality;

    private boolean isSurfaceCreated = false;
    private ViewGroup.LayoutParams previewLayout;
    private WindowManager.LayoutParams layoutParams;

    // ***** Lifecycle methods *****

    @Override
    public void onCreate() {
        Log.d(TAG, "START Creating Background Recorder Service");
        // Start foreground service to avoid unexpected kill
        Notification notification = new Notification.Builder(this)
                .setContentTitle("Background Video Recorder")
                .setContentText("Background Video Recorder")
                .setSmallIcon(R.drawable.ic_launcher)
                .build();
        startForeground(1234, notification);

        windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        mPreview = new TextureView(this);
        hidePreview();
        layoutParams = new WindowManager.LayoutParams(
                768, 432,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL;
        windowManager.addView(mPreview, layoutParams);
        mPreview.setSurfaceTextureListener(this);
        Log.d(TAG, "FINISH Creating Background Recorder Service");
    }

    // Stop recording and remove preview
    @Override
    public void onDestroy() {
        Log.d(TAG, "About to destroy");
        stopRecord();

        windowManager.removeView(mPreview);
        isSurfaceCreated = false;
    }

    // ***** SurfaceTextureListener methods *****

    // Method called right after Surface created (initializing and starting MediaRecorder)
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "START surfaceCreated handler: about to prepare MediaRecorder");
        // BEGIN_INCLUDE(prepare_start_media_recorder)

        isSurfaceCreated = true;
        if (outputFileDescriptor != null)
            new MediaPrepareTask().execute(null, null, null);

        Log.d(TAG, "FINISH surfaceCreated handler: MediaRecorder fired");
        // END_INCLUDE(prepare_start_media_recorder)
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // empty so far
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        // dummy so far
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // empty so far
    }

    public void stopRecord() {
        // BEGIN_INCLUDE(stop_release_media_recorder)
        // stop recording and release camera
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();  // stop the recording
            releaseMediaRecorder(); // release the MediaRecorder object
            outputFileDescriptor = null;
        }

        if (mCamera != null) {
            mCamera.lock(); // take camera access back from MediaRecorder
            releaseCamera();
        }
        // END_INCLUDE(stop_release_media_recorder)
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private boolean prepareVideoRecorder(){
        Log.d(TAG, "START prepareVideoRecorder");
        // BEGIN_INCLUDE (configure_preview)
        try {
            mCamera = CameraHelper.getDefaultCameraInstance();
        } catch (RuntimeException e) {
            return false;
        }

        mCamera.setZoomChangeListener(new Camera.OnZoomChangeListener() {
            @Override
            public void onZoomChange(int zoomValue, boolean stopped, Camera camera) {
                focus();
            }
        });

        // We need to make sure that our preview and recording video size are supported by the
        // camera. Query camera to find all the sizes and choose the optimal size given the
        // dimensions of our preview surface.
        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
        Camera.Size optimalSize = CameraHelper.getOptimalPreviewSize(mSupportedPreviewSizes,
                mPreview.getWidth(), mPreview.getHeight());

        // Use the same size for recording profile.
        CamcorderProfile profile = CamcorderProfile.get(quality);
        //profile.videoFrameWidth = optimalSize.width;
        //profile.videoFrameHeight = optimalSize.height;

        // likewise for the camera object itself.
        parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mCamera.setParameters(parameters);
        focus();
        try {
            // Requires API level 11+, For backward compatibility use {@link setPreviewDisplay}
            // with {@link SurfaceView}
            mCamera.setPreviewTexture(mPreview.getSurfaceTexture());
        } catch (IOException e) {
            Log.e(TAG, "Surface texture is unavailable or unsuitable" + e.getMessage());
            return false;
        }
        // END_INCLUDE (configure_preview)


        // BEGIN_INCLUDE (configure_media_recorder)
        mMediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT );
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(profile);

        // Step 4: Set output file
        mMediaRecorder.setOutputFile(outputFileDescriptor);

        // END_INCLUDE (configure_media_recorder)

        Log.d(TAG, "MediaRecorder successfully configured, now prepare it");

        // Step 5: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    /**
     * Asynchronous task for preparing the {@link android.media.MediaRecorder} since it's a long blocking
     * operation.
     */
    class MediaPrepareTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            // initialize video camera
            if (prepareVideoRecorder()) {
                // Camera is available and unlocked, MediaRecorder is prepared,
                // now you can start recording
                Log.d(TAG, "Starting record");
                mMediaRecorder.start();
            } else {
                // prepare didn't work, release the camera
                releaseMediaRecorder();
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            // dummy so far
        }
    }

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            // release the camera for other applications
            mCamera.release();
            mCamera = null;
        }
    }

    // Communication interface goes below

    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Binding a client");
        showPreview();
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        boolean res = super.onUnbind(intent);
        hidePreview();
        return res;
    }

    private LocalBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        MainService getService() {
            // Return this instance of MainService so clients can call public methods
            return MainService.this;
        }
    }

    public void setZoom(int newZoom)
    {
        mCamera.startSmoothZoom(newZoom);
    }

    public void startRecord(FileDescriptor out, int desiredQuality)
    {
        outputFileDescriptor = out;
        quality = desiredQuality;
        if (isSurfaceCreated)
            new MediaPrepareTask().execute(null, null, null);
    }

    private Camera.AutoFocusCallback afcb = new Camera.AutoFocusCallback() {
        @Override
        public void onAutoFocus(boolean success, Camera camera) {
            // empty
        }
    };

    public void focus()
    {
        mCamera.autoFocus(afcb);
    }

    public void showPreview()
    {
//        mPreview.setVisibility(View.VISIBLE);
        Log.d(TAG, "Request for show preview");
        if (mPreview != null && previewLayout != null) {
            mPreview.setLayoutParams(previewLayout);
        }
    }

    public void hidePreview()
    {
        Log.d(TAG, "Request for hide preview");
//        mPreview.setVisibility(View.INVISIBLE);
        if (mPreview != null) {
            previewLayout = mPreview.getLayoutParams();
            mPreview.layout(1, 1, 1, 1);
        }
        Log.d(TAG, "Success on hiding preview");
    }
}
