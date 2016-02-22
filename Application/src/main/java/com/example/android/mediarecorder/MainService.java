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
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.WindowManager;

import com.example.android.common.media.CameraHelper;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Created by ulysses on 22.02.16.
 */
public class MainService extends Service implements TextureView.SurfaceTextureListener {


    private WindowManager windowManager;
    private static final String TAG = "RecorderService";

    private Camera mCamera = null;
    private TextureView mPreview;
    private MediaRecorder mMediaRecorder = null;



        @Override
    public void onCreate() {
        Log.d(TAG, "START Creating Background Recorder Service");
        // Start foreground service to avoid unexpected kill
        Notification notification = new Notification.Builder(this)
                .setContentTitle("Background Video Recorder")
                .setContentText("")
                .setSmallIcon(R.drawable.ic_launcher)
                .build();
        startForeground(1234, notification);

        windowManager = (WindowManager) this.getSystemService(Context.WINDOW_SERVICE);
        mPreview = new TextureView(this);
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams(
                1024, 576,
                WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY,
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.CENTER | Gravity.TOP;
        windowManager.addView(mPreview, layoutParams);
        mPreview.setSurfaceTextureListener(this);
        Log.d(TAG, "FINISH Creating Background Recorder Service");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int res = super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "onStartCommand (empty)");
        return res;
    }

    // Method called right after Surface created (initializing and starting MediaRecorder)
    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "START surfaceCreated handler: about to prepare MediaRecorder");
        //TODO: start media recorder task
        // BEGIN_INCLUDE(prepare_start_media_recorder)

        new MediaPrepareTask().execute(null, null, null);

        Log.d(TAG, "FINISH surfaceCreated handler: MediaRecorder fired");
        // END_INCLUDE(prepare_start_media_recorder)

/*        camera = Camera.open();
        mediaRecorder = new MediaRecorder();
        camera.unlock();

        mediaRecorder.setPreviewDisplay(surfaceHolder.getSurface());
        mediaRecorder.setCamera(camera);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        mediaRecorder.setProfile(CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH));

        mediaRecorder.setOutputFile(
                Environment.getExternalStorageDirectory()+"/"+
                        DateFormat.format("yyyy-MM-dd_kk-mm-ss", new Date().getTime())+
                        ".mp4"
        );

        try { mediaRecorder.prepare(); } catch (Exception e) {}
        mediaRecorder.start();
*/
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

    // Stop recording and remove SurfaceView
    @Override
    public void onDestroy() {

        // BEGIN_INCLUDE(stop_release_media_recorder)
        // stop recording and release camera
        if (mMediaRecorder != null) {
            mMediaRecorder.stop();  // stop the recording
            releaseMediaRecorder(); // release the MediaRecorder object
        }

        if (mCamera != null) {
            mCamera.lock(); // take camera access back from MediaRecorder
            // inform the user that recording has stopped
//        setCaptureButtonText("Capture");
//        isRecording = false;
            releaseCamera();
        }
        // END_INCLUDE(stop_release_media_recorder)

        windowManager.removeView(mPreview);
    }


    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private boolean prepareVideoRecorder(){
        Log.d(TAG, "START prepareVideoRecorder");
        // BEGIN_INCLUDE (configure_preview)
        mCamera = CameraHelper.getDefaultCameraInstance();

        // We need to make sure that our preview and recording video size are supported by the
        // camera. Query camera to find all the sizes and choose the optimal size given the
        // dimensions of our preview surface.
        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
        Camera.Size optimalSize = CameraHelper.getOptimalPreviewSize(mSupportedPreviewSizes,
                mPreview.getWidth(), mPreview.getHeight());

        // Use the same size for recording profile.
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_HIGH);
        profile.videoFrameWidth = optimalSize.width;
        profile.videoFrameHeight = optimalSize.height;

        // likewise for the camera object itself.
        parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mCamera.setParameters(parameters);
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
        mMediaRecorder.setOutputFile(CameraHelper.getOutputMediaFile(
                CameraHelper.MEDIA_TYPE_VIDEO).toString());
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
            /*if (!result) {
                MainService.this.finish();
            }
            */
        }
    }

    private void releaseMediaRecorder(){
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();   // clear recorder configuration
            mMediaRecorder.release(); // release the recorder object
            mMediaRecorder = null;

            // Lock camera for later use i.e taking it back from MediaRecorder.
            // MediaRecorder doesn't need it anymore and we will release it if the activity pauses.
            // mCamera.lock();
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            // release the camera for other applications
            mCamera.release();
            mCamera = null;
        }
    }



    @Override
    public IBinder onBind(Intent intent) { return null; }
}
