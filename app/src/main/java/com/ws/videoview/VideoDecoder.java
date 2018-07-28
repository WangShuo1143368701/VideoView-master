package com.ws.videoview;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import java.nio.ByteBuffer;

/**
 * Created by wangshuo on 2018/5/4.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
public class VideoDecoder {
    private static final String TAG = VideoDecoder.class.getSimpleName();
    private String mVideoPath;
    private MediaExtractor extractor;
    private Surface mDecoderSurface;
    private MediaCodec decoder;

    private ByteBuffer[] inputBuffers;
    private BufferInfo info = new BufferInfo();
    private boolean isEOS = false;
    private int index;
    private long framestamp;

    public VideoDecoder(String videoPath, Surface surface) {
        mVideoPath =  videoPath;
        mDecoderSurface = surface;
    }

    public void start() {
        try {
            Start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void Start() throws Exception {
        extractor = new MediaExtractor();
        extractor.setDataSource(mVideoPath);

        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("video/")) {
                extractor.selectTrack(i);
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
        decodeNext();
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

                    try
                    {
                        Thread.sleep(35);
                    } catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                    break;
            }
        }
    }

    public long getFramestamp() {
        return framestamp;
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
