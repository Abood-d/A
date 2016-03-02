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

    private Button captureButton;
    private Button stopButton;
    private SeekBar zoomSeekBar;

    private int quality;


    private MainService mService = null;
    Uri outputFileUri = null;
    private boolean mBound;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.d(TAG, "MainService Connected");
            // We've bound to MainService, cast the IBinder and get MainService.LocalBinder instance
            MainService.LocalBinder binder = (MainService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            try {
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
            mBound = false;
        }
    };

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
                if (mBound)
                    mService.setZoom(progress);
            }
        });
        Log.d(TAG, "FINISH onCreate");
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "Destroy");
        super.onDestroy();
        if (mBound) {
            Log.d(TAG, "Request to unbind from service (as we were bounded)");
            unbindService(mConnection);
            mBound = false;
        }
    }

    /**
     * The capture button controls start of recording via separate service.
     */
    public void onCaptureClick(View view) {
        Log.d(TAG, "CAPTURE clicked, create output file");
        //Log.d(TAG, "service Intent created, create a file that would be used by it");
        createFile(CameraHelper.getOutputMediaFileName());
    }

    /**
     * The stop button release video service. It destroys the service, stopping the recording
     * and storing video file.
     */
    public void onStopClick(View view) {
        Log.d(TAG, "STOP clicked, stopping service");
        //stopService(bgVideoServiceIntent); -- wrodked when we used `started` service instead of bound one
        // Unbind from the service
        if (mBound) {
            Log.d(TAG, "Request to unbind from service (as we were bounded)");
            unbindService(mConnection);
            mBound = false;
        }
        Log.d(TAG, "Request stopping service");
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

    // You'll SUFFER just to create a file on SD on Android 5, see below

    private static final int WRITE_REQUEST_CODE = 143; // just my favourite number

    private void createFile(String fileName) {
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
                Intent bgVideoServiceIntent = new Intent(this, MainService.class);
                Log.d(TAG, "about to start service");
                startService(bgVideoServiceIntent);
                Log.d(TAG, "service started, now trying to bind to it also");
                bindService(bgVideoServiceIntent, mConnection, Context.BIND_AUTO_CREATE);
                Log.d(TAG, "service bounded");
            }
        }
    }
}