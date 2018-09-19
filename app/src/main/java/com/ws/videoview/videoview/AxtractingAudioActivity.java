package com.ws.videoview.videoview;

import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

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
                final long startTime = System.currentTimeMillis();

                AudioAxtracting audioAxtracting =  new AudioAxtracting();
                audioAxtracting.setAudioAxtractingListener(new AudioAxtracting.AudioAxtractingListener()
                {
                    @Override
                    public void onClipAudioComplete()
                    {
                        Log.e("AxtractingAudioActivity","onClipAudioComplete = "+(System.currentTimeMillis()-startTime));
                        runOnUiThread(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                Toast.makeText(AxtractingAudioActivity.this,"onClipAudioComplete",Toast.LENGTH_LONG).show();
                            }
                        });

                    }
                });

                audioAxtracting.clipAudio(
                        Environment.getExternalStorageDirectory().getPath() + "/vtmp" + "/youxi.mp4",2*1000*1000,15*1000*1000,Environment.getExternalStorageDirectory().getPath()+"/aaaaa_output3.aac");

                //audioAxtracting.clipAudio(
                //        Environment.getExternalStorageDirectory().getPath() + "/" + "1.mp4",Environment.getExternalStorageDirectory().getPath()+"/aaaaa_output3.aac");
            }
        }).start();

    }

}
