package com.dream.www.dreamclient;

/**
 * Created by solid_000 on 03-Jun-16.
 */
import android.app.Activity;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.VideoView;

//Implement SurfaceHolder interface to Play video
//Implement this interface to receive information about changes to the surface
public class AndroidVideoPlayer extends Activity implements SurfaceHolder.Callback{

    MediaPlayer mediaPlayer;
    SurfaceView surfaceView;
    SurfaceHolder surfaceHolder;
    boolean pausing = false;;
    VideoView mVideoView;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Button buttonPlayVideo = (Button)findViewById(R.id.playvideoplayer);
        Button buttonStopVideo = (Button)findViewById(R.id.stopvideoplayer);
        getWindow().setFormat(PixelFormat.UNKNOWN);

        //Displays a video file.
        mVideoView = (VideoView)findViewById(R.id.videoview);

        String uriPath = "android.resource://com.android.AndroidVideoPlayer/"+R.raw.k;
        Uri uri = Uri.parse(uriPath);
        mVideoView.setVideoURI(uri);
        mVideoView.requestFocus();
        //mVideoView.start();



        buttonPlayVideo.setOnClickListener(new Button.OnClickListener(){

            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                //VideoView mVideoView = (VideoView)findViewById(R.id.videoview);
                // VideoView mVideoView = new VideoView(this);
                //String uriPath = "android.resource://com.android.AndroidVideoPlayer/"+R.raw.k;
                //Uri uri = Uri.parse(uriPath);
                //mVideoView.setVideoURI(uri);
                //mVideoView.requestFocus();

                mVideoView.start();

            }
        });

        buttonStopVideo.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                // TODO Auto-generated method stub
                mVideoView.stopPlayback();
            }
        });


    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width,
                               int height) {
        // TODO Auto-generated method stub

    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // TODO Auto-generated method stub

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        // TODO Auto-generated method stub

    }
}