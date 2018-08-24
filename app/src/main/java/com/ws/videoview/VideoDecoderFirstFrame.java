package com.ws.videoview;

import android.annotation.TargetApi;
import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.opengl.GLES20;
import android.os.Build;
import android.util.Log;
import android.view.Surface;
import com.ws.videoview.videoview.opengl.*;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by wangshuo on 2018/5/4.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoDecoderFirstFrame
{
    private static final String TAG = VideoDecoderFirstFrame.class.getSimpleName();
    private String mVideoPath;
    private MediaExtractor extractor;
    private SurfaceTexture mSurfaceTexture;
    private Surface mDecoderSurface;
    private MediaCodec decoder;
    private TextureRender mTextureRender;

    private ByteBuffer[] inputBuffers;
    private BufferInfo info = new BufferInfo();
    private boolean isEOS = false;
    private int index;
    private long framestamp;
    private final Object mFrameSyncObject = new Object();     // guards mFrameAvailable
    private boolean mFrameAvailable;
    private float[] mMtx = new float[16];
    private int mWidth;
    private int mHeight;
    private GPUFilter mGPUFilter;
    private int textureId;

    public VideoDecoderFirstFrame(String videoPath,VideoBitmap videoBitmap) {
        mVideoPath =  videoPath;
        mVideoBitmap = videoBitmap;
        mTextureRender = new TextureRender(true);
        mTextureRender.createTexture();
        mSurfaceTexture = new SurfaceTexture(mTextureRender.getTextureId());
        mDecoderSurface = new Surface(mSurfaceTexture);

        mSurfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {

            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                synchronized (mFrameSyncObject) {
                    if (mFrameAvailable) {
                        throw new RuntimeException("mFrameAvailable already set, frame could be dropped");
                    }
                    mFrameAvailable = true;
                    mFrameSyncObject.notifyAll();
                }
            }
        });


        mGPUFilter = new GPUFilter(GPUOESTextureFilter.CAMERA_VERTEX_SHADER, GPUOESTextureFilter.CAMERA_FRAGMENT_SHADER, true);
        mGPUFilter.setAttribPointer(TextureRotationUtil.CUBE, TextureRotationUtil.getRotation(Rotation.NORMAL, false, false));
        boolean ret = mGPUFilter.init();
        if (ret) {
            mGPUFilter.setHasFrameBuffer(true);
        }

        initDecoder();
    }

    private void initDecoder()
    {
        try {
            extractor = new MediaExtractor();
            extractor.setDataSource(mVideoPath);

            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat format = extractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    extractor.selectTrack(i);
                    mWidth = format.getInteger(MediaFormat.KEY_WIDTH);
                    mHeight = format.getInteger(MediaFormat.KEY_HEIGHT);
                    decoder = MediaCodec.createDecoderByType(mime);
                    decoder.configure(format, mDecoderSurface, null, 0);
                    break;
                }
            }

            if (decoder == null) {
                Log.e(TAG, "Can't find video info!");
                return;
            }

            decoder.start();
            inputBuffers = decoder.getInputBuffers();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void start() {
        try {
            decodeNext();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void decodeNext() {
        outerloop:
        while (!Thread.interrupted()) {
            if (!isEOS) {
                int inIndex = decoder.dequeueInputBuffer(10000);
                if (inIndex >= 0) {
                    ByteBuffer buffer = inputBuffers[inIndex];
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) {
                        Log.d(TAG, "InputBuffer BUFFER_FLAG_END_OF_STREAM");
                        decoder.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        isEOS = true;
                    } else {
                        decoder.queueInputBuffer(inIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                        extractor.advance();
                    }
                }
            }
            int outIndex = decoder.dequeueOutputBuffer(info, 10000);

            // All decoded frames have been rendered, we can stop playing now
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.i(TAG, "total decode " + index + " frames");
                stop(true);
                Log.d(TAG, "OutputBuffer BUFFER_FLAG_END_OF_STREAM");
                break;
            }

            switch (outIndex) {
                case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                    Log.d(TAG, "INFO_OUTPUT_BUFFERS_CHANGED");
                    break;
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    Log.d(TAG, "New format " + decoder.getOutputFormat());
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    Log.d(TAG, "dequeueOutputBuffer timed out!");
                    try {
                        // wait 50ms
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                    }
                    break;
                default:
                    index++;
                    decoder.releaseOutputBuffer(outIndex, true);
                    framestamp = info.presentationTimeUs;
                    awaitNewImage();
                    break outerloop;
            }
        }
    }

    public long getFramestamp() {
        return framestamp;
    }

    public int getWidth() {
        return mWidth;
    }

    public int getHeight() {
        return mHeight;
    }

    private void awaitNewImage() {
        final int TIMEOUT_MS = 2500;

        synchronized (mFrameSyncObject) {
            while (!mFrameAvailable) {
                try {
                    // Wait for onFrameAvailable() to signal us.  Use a timeout to avoid
                    // stalling the test if it doesn't arrive.
                    mFrameSyncObject.wait(TIMEOUT_MS);
                    if (!mFrameAvailable) {
                        // TODO: if "spurious wakeup", continue while loop
//                        throw new RuntimeException("frame wait timed out");
                        break;
                    }
                } catch (InterruptedException ie) {
                    // shouldn't happen
                    throw new RuntimeException(ie);
                }
            }
            mFrameAvailable = false;
        }

        mSurfaceTexture.updateTexImage();
        mSurfaceTexture.getTransformMatrix(mMtx);
        mGPUFilter.onOutputSizeChanged(mWidth, mHeight);
        mGPUFilter.setTextureTransformMatrix(mMtx);
        GLES20.glViewport(0, 0, mWidth, mHeight);
        textureId = mGPUFilter.onDrawToTexture(mTextureRender.getTextureId());

        if(mVideoBitmap != null){
            mVideoBitmap.getVideoFirstBitmap(drawTextureToBitmap(textureId,mWidth,mHeight));
        }

        stop(true);
    }


    private VideoBitmap mVideoBitmap;
    public interface VideoBitmap {
        void getVideoFirstBitmap(Bitmap bitmap);
    }

    public static Bitmap drawTextureToBitmap(int textureID, int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        ByteBuffer ib = ByteBuffer.allocate(w * h * 4).order(ByteOrder.nativeOrder());
        ib.position(0);
        GPUFilter filter = new GPUFilter();
        filter.init();
        GLES20.glViewport(0, 0, w, h);
        filter.onDrawFrame(textureID);
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, ib);
        bmp.copyPixelsFromBuffer(ib);
        filter.destroy();
        return bmp;
    }

    public void stop(boolean doCompleted) {
        if (mDecoderSurface != null) {
            mDecoderSurface.release();
            mDecoderSurface = null;
        }
        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
        if (decoder != null) {
            decoder.stop();
            decoder.release();
            decoder = null;
        }
    }

}
