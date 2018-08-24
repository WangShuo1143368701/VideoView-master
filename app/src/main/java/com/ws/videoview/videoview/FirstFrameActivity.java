package com.ws.videoview.videoview;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Display;
import android.widget.ImageView;
import com.ws.videoview.VideoDecoderFirstFrame;
import com.ws.videoview.videoview.opengl.EGL14Helper;

public class FirstFrameActivity extends AppCompatActivity
{
    private ImageView mImageView;
    private EGL14Helper mEGLHelper;
    private int mWidth;
    private int mHeight;

    private String videoPath = Environment.getExternalStorageDirectory().getPath() + "/1/a.mp4.download";
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_frame);

        initView();
        //initEvent();

        final long startTime = System.currentTimeMillis();
        mImageView.setImageBitmap(getVideoFirstFrame(videoPath));
        Log.e("wangshuo","time = "+ (System.currentTimeMillis() - startTime));
    }

    private void initEvent()
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                initEGL();

                final long startTime = System.currentTimeMillis();
                VideoDecoderFirstFrame videoDecoderFirstFrame = new VideoDecoderFirstFrame(Environment.getExternalStorageDirectory().getPath() + "/1/1.mp4.download", new VideoDecoderFirstFrame.VideoBitmap()
                {
                    @Override
                    public void getVideoFirstBitmap(final Bitmap bitmap)
                    {
                        Log.e("wangshuo","time = "+ (System.currentTimeMillis() - startTime));
                        mImageView.post(new Runnable()
                        {
                            @Override
                            public void run()
                            {
                                mImageView.setImageBitmap(bitmap);

                            }
                        });
                    }
                });
                videoDecoderFirstFrame.start();

            }

        }).start();
    }

    private void initView()
    {
        mImageView = (ImageView) findViewById(R.id.image);
        Display display = getWindowManager().getDefaultDisplay();
        mHeight = display.getWidth();
        mWidth = display.getHeight();
    }


    //GL Thread
    private void initEGL() {
        mEGLHelper = EGL14Helper.createEGLSurface(null, null, null, mWidth, mHeight);
    }

    //GL Thread
    private void destroyEGL() {
        if (mEGLHelper != null) {
            mEGLHelper.release();
            mEGLHelper = null;
        }
    }

    public static Bitmap getVideoFirstFrame(String path){
        if(path == null) return null;

        Bitmap bitmap = null;
        MediaMetadataRetriever mediaRetriever = null;
        try
        {
            mediaRetriever = new MediaMetadataRetriever();
            mediaRetriever.setDataSource(path);
            bitmap = mediaRetriever.getFrameAtTime(1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        }catch (Exception e){
            return null;
        }finally
        {
            if(mediaRetriever != null){
                mediaRetriever.release();
            }
        }
        return bitmap;
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        destroyEGL();
    }
}
