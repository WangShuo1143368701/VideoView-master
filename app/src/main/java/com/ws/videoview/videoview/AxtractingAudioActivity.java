package com.ws.videoview.videoview;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;

public class AxtractingAudioActivity extends AppCompatActivity
{

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_axtracting_audio);

        initEvent();
    }

    private void initEvent()
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                new AudioAxtracting().clipAudio(
                        Environment.getExternalStorageDirectory().getPath() + "/" + "wx.mp4",Environment.getExternalStorageDirectory().getPath()+"/1/aaaaa_output2.aac");
            }
        }).start();

    }

}
