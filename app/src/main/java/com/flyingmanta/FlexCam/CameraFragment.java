package com.flyingmanta.FlexCam;
/*
 * AudioVideoRecordingSample
 * Sample project to cature audio and video from internal mic/camera and save as MPEG4 file.
 *
  * Copyright (c) 2014-2015 saki t_saki@serenegiant.com
 * Copyright (c) 2017 Cédric Portmann cedric.portmann@gmail.com
 *
 * File name: CameraFragment.java
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * All files in the folder are under this Apache License, Version 2.0.
*/

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.app.Fragment;

import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;


import com.googlecode.mp4parser.BasicContainer;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.AppendTrack;
import com.flyingmanta.encoder.MediaAudioEncoder;
import com.flyingmanta.encoder.MediaEncoder;
import com.flyingmanta.encoder.MediaMuxerWrapper;
import com.flyingmanta.encoder.MediaVideoEncoder;

public class CameraFragment extends Fragment {
    private static final boolean DEBUG = false;    // TODO set false on release
    private static final String TAG = "CameraFragment";
    private static final int MAX_HEIGHT = 1920;
    private static final int MAX_WIDTH = 1080;


    /**
     * for camera preview display
     */
    private CameraGLView mCameraView;
    /**
     * for scale mode display
     */
    private TextView mScaleModeView;
    /**
     * button for start/stop recording
     */
    private ImageButton mRecordButton;
    /**
     * muxer for audio/video recording
     */


    private Button mMergeButton;

    private MediaMuxerWrapper mMuxer;

    private String OUTPUT_FILENAME = "Merged.mp4";

    public CameraFragment() {
        // need default constructor
    }

    List<File> parts;

    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
        final View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mCameraView = (CameraGLView) rootView.findViewById(R.id.cameraView);
        mCameraView.setVideoSize(MAX_HEIGHT, MAX_WIDTH);
        mCameraView.setOnClickListener(mOnClickListener);

        mRecordButton = (ImageButton) rootView.findViewById(R.id.record_button);
        mRecordButton.setOnClickListener(mOnClickListener);

        mMergeButton = (Button) rootView.findViewById(R.id.merge_button);
        mMergeButton.setOnClickListener(mOnClickListener);

        parts = new ArrayList<>();

        return rootView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.v(TAG, "onResume:");
        mCameraView.onResume();
    }

    @Override
    public void onPause() {
        if (DEBUG) Log.v(TAG, "onPause:");
        stopRecording();
        mCameraView.onPause();
        super.onPause();
    }

    /**
     * method when touch record button
     */
    private final OnClickListener mOnClickListener = new OnClickListener() {
        @Override
        public void onClick(final View view) {
            switch (view.getId()) {
                case R.id.merge_button:
                    File mergedFile = new File(Environment.getExternalStorageDirectory(), OUTPUT_FILENAME);
                    mergeVideo(parts, mergedFile);
                    break;
                case R.id.record_button:
                    if (mMuxer == null)
                        startRecording();
                    else
                        stopRecording();
                    break;
            }
        }
    };




    private void mergeVideo(List<File> parts, File outFile) {
        try {
            Movie finalMovie = new Movie();
            List<Track> videoTracks = new LinkedList<>();
            List<Track> audioTracks = new LinkedList<>();

            for (int i = 0; i < parts.size(); i++) {
                String videoPath = parts.get(i).getPath();
                Movie movie = MovieCreator.build(videoPath);

                for (Track t : movie.getTracks()) {
                    if (t.getHandler().equals("vide")) {
                        videoTracks.add(t);
                    } else if (t.getHandler().equals("soun")) {
                        audioTracks.add(t);
                    }
                }
            }

            if (videoTracks.size() > 0) {
                finalMovie.addTrack(new AppendTrack(videoTracks.toArray(new Track[videoTracks.size()])));
            }

            if (audioTracks.size() > 0) {
                finalMovie.addTrack(new AppendTrack(audioTracks.toArray(new Track[audioTracks.size()])));
            }

            FileOutputStream fos = new FileOutputStream(outFile);
            BasicContainer container = (BasicContainer) new DefaultMp4Builder().build(finalMovie);
            container.writeContainer(fos.getChannel());
        } catch (IOException e) {
            Log.e(TAG, "Merge failed", e);
        }

        parts.clear();

    }

    /**
     * start resorcing
     * This is a sample project and call this on UI thread to avoid being complicated
     * but basically this should be called on private thread because prepareing
     * of encoder is heavy work
     */
    private void startRecording() {
        if (DEBUG) Log.v(TAG, "startRecording:");
        try {
            mRecordButton.setColorFilter(0xffff0000);    // turn red
            mMuxer = new MediaMuxerWrapper(".mp4");    // if you record audio only, ".m4a" is also OK.
            if (true) {
                // for video capturing
                new MediaVideoEncoder(mMuxer, mMediaEncoderListener, mCameraView.getVideoWidth(), mCameraView.getVideoHeight());
            }
            if (true) {
                // for audio capturing
                new MediaAudioEncoder(mMuxer, mMediaEncoderListener);
            }
            mMuxer.prepare();
            mMuxer.startRecording();
        } catch (final IOException e) {
            mRecordButton.setColorFilter(0);
            Log.e(TAG, "startCapture:", e);
        }
    }

    /**
     * request stop recording
     */
    private void stopRecording() {
        if (DEBUG) Log.v(TAG, "stopRecording:mMuxer=" + mMuxer);
        mRecordButton.setColorFilter(0);    // return to default color
        if (mMuxer != null) {
            mMuxer.stopRecording();
            parts.add(new File(mMuxer.getOutputPath(), ""));
            mMuxer = null;
            // you should not wait here
        }
    }

    /**
     * callback methods from encoder
     */
    private final MediaEncoder.MediaEncoderListener mMediaEncoderListener = new MediaEncoder.MediaEncoderListener() {
        @Override
        public void onPrepared(final MediaEncoder encoder) {
            if (DEBUG) Log.v(TAG, "onPrepared:encoder=" + encoder);
            if (encoder instanceof MediaVideoEncoder)
                mCameraView.setVideoEncoder((MediaVideoEncoder) encoder);
        }

        @Override
        public void onStopped(final MediaEncoder encoder) {
            if (DEBUG) Log.v(TAG, "onStopped:encoder=" + encoder);
            if (encoder instanceof MediaVideoEncoder)
                mCameraView.setVideoEncoder(null);
        }
    };
}
