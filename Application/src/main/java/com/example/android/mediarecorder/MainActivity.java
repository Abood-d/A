/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.mediarecorder;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;

import com.example.android.common.media.CameraHelper;

import java.io.IOException;
import java.util.List;

/**
 *  This activity uses the camera/camcorder as the A/V source for the {@link android.media.MediaRecorder} API.
 *  A {@link android.view.TextureView} is used as the camera preview which limits the code to API 14+. This
 *  can be easily replaced with a {@link android.view.SurfaceView} to run on older devices.
 */
public class MainActivity extends Activity {

    private static final String TAG = "RecorderActivity";
    private Button captureButton;
    private Button stopButton;
    Intent bgVideoService = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "START onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_main);

        //mPreview = (TextureView) findViewById(R.id.surface_view);
        captureButton = (Button) findViewById(R.id.button_capture);
        stopButton    = (Button) findViewById(R.id.button_stop);
        Log.d(TAG, "FINISH onCreate");
    }

    /**
     * The capture button controls all user interaction. When recording, the button click
     * stops recording, releases {@link android.media.MediaRecorder} and {@link android.hardware.Camera}. When not recording,
     * it prepares the {@link android.media.MediaRecorder} and starts recording.
     *
     * @param view the view generating the event.
     */
    public void onCaptureClick(View view) {
        Log.d(TAG, "CAPTURE clicked, create service Intent");
        bgVideoService = new Intent(this, MainService.class);
        Log.d(TAG, "service Intent created, starting it");
        startService(bgVideoService);
        Log.d(TAG, "service Intent started");
    }

    public void onStopClick(View view) {
        Log.d(TAG, "STOP clicked, stopping service");
        stopService(bgVideoService);
        Log.d(TAG, "service stopped");
    }

    /*
    @Override
    protected void onPause() {
        super.onPause();
        // if we are using MediaRecorder, release it first
        releaseMediaRecorder();
        // release the camera immediately on pause event
        releaseCamera();
    }
    */
}