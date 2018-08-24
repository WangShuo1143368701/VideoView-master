package com.ws.videoview.videoview;


import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.nio.ByteBuffer;
public class AudioAxtracting {
    private final static String TAG = "AudioAxtracting";
    private MediaExtractor mMediaExtractor;
    private MediaFormat mMediaFormat;
    private MediaMuxer mMediaMuxer;
    private String mime = null;

    public boolean clipAudio(String path,String outPath) {
        return clipAudio(path,0,0,outPath);
    }

    public boolean clipAudio(String url, long clipPoint, long clipDuration, String outPath) {

        int audioTrackIndex = -1;
        int audioMaxInputSize = 0;
        int sourceATrack = 0;
        long audioDuration;

        Log.d(TAG, ">>　url : " + url);
        //创建分离器
        mMediaExtractor = new MediaExtractor();
        try {
            //设置文件路径
            mMediaExtractor.setDataSource(url);
            //创建合成器
            mMediaMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            Log.e(TAG, "error path" + e.getMessage());
        }
        for (int i = 0; i < mMediaExtractor.getTrackCount(); i++) {
            try {
                mMediaFormat = mMediaExtractor.getTrackFormat(i);
                mime = mMediaFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    sourceATrack = i;
                    int sampleRate = mMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int channelCount = mMediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    audioMaxInputSize = mMediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    audioDuration = mMediaFormat.getLong(MediaFormat.KEY_DURATION);
                    Log.d(TAG, "sampleRate is " + sampleRate
                            + ";channelCount is " + channelCount
                            + ";audioMaxInputSize is " + audioMaxInputSize
                            + ";audioDuration is " + audioDuration
                    );
                    //添加音轨
                    audioTrackIndex = mMediaMuxer.addTrack(mMediaFormat);
                }
                Log.d(TAG, "file mime is " + mime);
            } catch (Exception e) {
                Log.e(TAG, " read error " + e.getMessage());
            }
        }

        //音频部分
        ByteBuffer inputBuffer = ByteBuffer.allocate(audioMaxInputSize);
        mMediaMuxer.start();
        mMediaExtractor.selectTrack(sourceATrack);
        MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
        audioInfo.presentationTimeUs = 0;
        mMediaExtractor.seekTo(clipPoint, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        while (true) {
            int sampleSize = mMediaExtractor.readSampleData(inputBuffer, 0);
            if (sampleSize < 0) {
                mMediaExtractor.unselectTrack(sourceATrack);
                break;
            }
            int trackIndex = mMediaExtractor.getSampleTrackIndex();
            long presentationTimeUs = mMediaExtractor.getSampleTime();
            Log.d(TAG, "trackIndex is " + trackIndex
                    + ";presentationTimeUs is " + presentationTimeUs);
            if ((clipDuration != 0) && (presentationTimeUs > (clipPoint + clipDuration))) {
                mMediaExtractor.unselectTrack(sourceATrack);
                break;
            }
            mMediaExtractor.advance();
            audioInfo.offset = 0;
            audioInfo.size = sampleSize;
            audioInfo.presentationTimeUs = presentationTimeUs;
            mMediaMuxer.writeSampleData(audioTrackIndex, inputBuffer, audioInfo);
        }
        //全部写完后释放MediaMuxer和MediaExtractor
        mMediaMuxer.stop();
        mMediaMuxer.release();
        mMediaExtractor.release();
        mMediaExtractor = null;
        return true;
    }

    public void stop(){
        if(mMediaMuxer != null){
            mMediaMuxer.stop();
            mMediaMuxer.release();
            mMediaMuxer = null;
        }
        if(mMediaExtractor != null){
            mMediaExtractor.release();
            mMediaExtractor = null;
        }
    }
}