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

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.media.CamcorderProfile;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.SeekBar;

import com.example.android.common.media.CameraHelper;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/**
 *  This activity uses the camera/camcorder as the A/V source for the {@link android.media.MediaRecorder} API.
 *  A {@link android.view.TextureView} is used as the camera preview which limits the code to API 14+. This
 *  can be easily replaced with a {@link android.view.SurfaceView} to run on older devices.
 */
public class MainActivity extends Activity {

    private static final String TAG = "RecorderActivity";

    // UI
    private Button captureButton;
    private Button stopButton;
    private SeekBar zoomSeekBar;

    // Record parameters
    private int quality;

    // Resources for communication with MainService
    private MainService mService = null;
    Uri outputFileUri = null;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.d(TAG, "MainService Connected");
            // We've bound to MainService, cast the IBinder and get MainService.LocalBinder instance
            MainService.LocalBinder binder = (MainService.LocalBinder) service;
            mService = binder.getService();
            try {
                if (outputFileUri != null)
                    mService.startRecord(
                            getContentResolver().openFileDescriptor(outputFileUri, "w").getFileDescriptor(),
                            quality);
            } catch (FileNotFoundException e) {
                Log.e(TAG, "Record not started because of file-related problem: \n" + e.getStackTrace());
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            Log.d(TAG, "MainService Disconnected");
            mService = null;
        }
    };

    // ***** Lifecycle methods *****

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "START onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sample_main);

        // default quality
        ((RadioButton)findViewById(R.id.q1080)).setChecked(true);
        quality = CamcorderProfile.QUALITY_1080P;

        captureButton = (Button) findViewById(R.id.button_capture);
        stopButton    = (Button) findViewById(R.id.button_stop);
        zoomSeekBar   = (SeekBar) findViewById(R.id.zoom_seek_bar);

        zoomSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            int progress = 0;

            @Override
            public void onProgressChanged(SeekBar seekBar, int progresValue, boolean fromUser) {
                progress = progresValue;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // dummy so far
                //Toast.makeText(getApplicationContext(), "Started tracking seekbar", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mService != null)
                    mService.setZoom(progress);
            }
        });
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "Destroy");
        super.onDestroy();
        if (mService != null) {
            Log.d(TAG, "Request to unbind from service (as we were bounded)");
            unbindService(mConnection);
            mService = null;
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "Resume activity");
        super.onResume();
        if (mService != null)
            mService.showPreview();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "Pause activity");
        super.onPause();
        if (mService != null)
            mService.hidePreview();
    }

    // ***** UI callbacks *****

    /**
     * The capture button controls start of recording via separate service.
     */
    public void onCaptureClick(View view) {
        Log.d(TAG, "CAPTURE clicked, create output file");

        // Request for creation of a file
        Log.d(TAG, "create a file that would be used by it");
        createFile();

        // start (if not) and bind to service
        Intent bgVideoServiceIntent = new Intent(this, MainService.class);
        Log.d(TAG, "about to start service");
        startService(bgVideoServiceIntent);

        Log.d(TAG, "FINISH onCreate");
    }

    /**
     * The stop button release video service. It destroys the service, stopping the recording
     * and storing video file.
     */
    public void onStopClick(View view) {
        Log.d(TAG, "STOP clicked, stopping record");
        //stopService(bgVideoServiceIntent); -- wrodked when we used `started` service instead of bound one
        Log.d(TAG, "Request stopping service");
        if (mConnection != null)
            unbindService(mConnection);
        stopService(new Intent(this, MainService.class));
    }

    public void onRadioButtonClicked(View view) {
        // Disclaimer: this implementation seems ugly to me and I know 1000 and 1 way to improve the algo
        //             BUT I get it from official man and afraid to touch it.

        // Is the button now checked?
        boolean checked = ((RadioButton) view).isChecked();

        // Check which radio button was clicked
        switch(view.getId()) {
            case R.id.q1080:
                if (checked)
                    quality = CamcorderProfile.QUALITY_1080P;
                    break;
            case R.id.q720:
                if (checked)
                    quality = CamcorderProfile.QUALITY_720P;
                    break;
            case R.id.q480:
                if (checked)
                    quality = CamcorderProfile.QUALITY_480P;
                    break;
        }
    }

    // ***** File creation handling *****

    // You'll SUFFER just to create a file on SD on Android 5, see below

    private static final int WRITE_REQUEST_CODE = 143; // just my favourite number

    private void createFile() {
        String fileName = CameraHelper.getOutputMediaFileName();
        Log.d(TAG, "Request for file creation");
        String mimeType = "video/mp4";
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);

        // Filter to only show results that can be "opened", such as
        // a file (as opposed to a list of contacts or timezones).
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        // Create a file with the requested MIME type.
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(intent, WRITE_REQUEST_CODE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        Log.d(TAG, "File created callback triggered, we are about to fire up MainService and also bound to it");

        // The ACTION_CREATE_DOCUMENT intent was sent with the request code
        // WRITE_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCode == WRITE_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            if (resultData != null) {
                outputFileUri = resultData.getData();
                Log.d(TAG, "Uri: " + outputFileUri.toString());

                if (outputFileUri != null && mService != null)
                    try {
                        mService.startRecord(
                                getContentResolver().openFileDescriptor(outputFileUri, "w").getFileDescriptor(),
                                quality);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                if (mService == null)
                    bindService(new Intent(this, MainService.class), mConnection, Context.BIND_AUTO_CREATE);
            }
        }
    }
}