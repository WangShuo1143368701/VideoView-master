package com.ws.videoview.videoview;

import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.view.Surface;
import android.view.TextureView;
import com.ws.videoview.VideoDecoder;

public class VideoPlayerActivity extends AppCompatActivity implements TextureView.SurfaceTextureListener

{
    private TextureView mTextureView;
    private VideoDecoder VideoDecoder;
    private Surface mSurface;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_player);

        mTextureView = (TextureView) findViewById(R.id.textureView);
        mTextureView.setSurfaceTextureListener(this);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1)
    {
        mSurface = new Surface(surfaceTexture);

        new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                VideoDecoder = new VideoDecoder(Environment.getExternalStorageDirectory().getPath()+"/360.mp4",mSurface);
                VideoDecoder.start();
            }
        }).start();

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1)
    {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture)
    {
        VideoDecoder.stop(true);
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture)
    {

    }
}
