package com.ws.videoview.videoview.genpai;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import com.ws.videoview.videoview.R;

public class GenPaiActivity extends AppCompatActivity
{

    private static final String videoPath = Environment.getExternalStorageDirectory().getPath()+"/1.mp4";
    private static final String audioPath = Environment.getExternalStorageDirectory().getPath()+"/input2.mp4";
    private static final String outPath = Environment.getExternalStorageDirectory().getPath()+"/wx_genpai.mp4";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gen_pai);

        initEvent();
    }

    private void initEvent()
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                final long startTime = System.currentTimeMillis();

                GenPai genPai = new GenPai();
                genPai.clipVideo(videoPath,audioPath,outPath);

                Log.e("wang","time = "+(System.currentTimeMillis() - startTime));
            }
        }).start();
    }

}
