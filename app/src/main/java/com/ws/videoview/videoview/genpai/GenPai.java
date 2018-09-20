package com.ws.videoview.videoview.genpai;


import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.nio.ByteBuffer;

public class GenPai
{
    private final static String TAG = "GenPai";
    private MediaExtractor mVidoeMediaExtractor;
    private MediaExtractor mAudioMediaExtractor;
    private MediaFormat mVideoMediaFormat;
    private MediaFormat mAudioMediaFormat;
    private MediaMuxer mMediaMuxer;
    private String mime = null;
    private int sampleRate;

    public boolean clipVideo(String videoPath,String audioPath, String outPath) {
       return  clipVideo(videoPath,audioPath,0,0,outPath);
    }

    //剪切的视频  剪切的起点  剪切的时长
    public boolean clipVideo(String videoPath,String audioPath, long clipPoint, long clipDuration,String outPath) {
        int videoTrackIndex = -1;
        int audioTrackIndex = -1;
        int videoMaxInputSize = 0;
        int audioMaxInputSize = 0;
        int sourceVTrack = 0;
        int sourceATrack = 0;
        long videoDuration, audioDuration;
        Log.d(TAG, ">>　videoPath : " + videoPath);
        Log.d(TAG, ">>　audioPath : " + audioPath);
        //创建分离器
        mVidoeMediaExtractor = new MediaExtractor();
        mAudioMediaExtractor = new MediaExtractor();
        try {
            //设置文件路径
            mVidoeMediaExtractor.setDataSource(videoPath);
            mAudioMediaExtractor.setDataSource(audioPath);
            //创建合成器
            mMediaMuxer = new MediaMuxer(outPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (Exception e) {
            Log.e(TAG, "error path" + e.getMessage());
        }

        //获取视频轨道的信息
        for (int i = 0; i < mVidoeMediaExtractor.getTrackCount(); i++) {
            try {
                mVideoMediaFormat = mVidoeMediaExtractor.getTrackFormat(i);
                mime = mVideoMediaFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    sourceVTrack = i;
                    int width = mVideoMediaFormat.getInteger(MediaFormat.KEY_WIDTH);
                    int height = mVideoMediaFormat.getInteger(MediaFormat.KEY_HEIGHT);
                    videoMaxInputSize = mVideoMediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    videoDuration = mVideoMediaFormat.getLong(MediaFormat.KEY_DURATION);

                    Log.d(TAG, "width and height is " + width + " " + height
                            + ";maxInputSize is " + videoMaxInputSize
                            + ";duration is " + videoDuration
                    );
                    //向合成器添加视频轨
                    videoTrackIndex = mMediaMuxer.addTrack(mVideoMediaFormat);
                }
            } catch (Exception e) {
                Log.e(TAG, " read error " + e.getMessage());
            }
        }

        //获取音频轨道的信息
        for (int i = 0; i < mAudioMediaExtractor.getTrackCount(); i++) {
            try {
                mAudioMediaFormat = mAudioMediaExtractor.getTrackFormat(i);
                mime = mAudioMediaFormat.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("audio/")) {
                    sourceATrack = i;
                    sampleRate = mAudioMediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int channelCount = mAudioMediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    audioMaxInputSize = mAudioMediaFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
                    audioDuration = mAudioMediaFormat.getLong(MediaFormat.KEY_DURATION);
                    Log.d(TAG, "sampleRate is " + sampleRate
                            + ";channelCount is " + channelCount
                            + ";audioMaxInputSize is " + audioMaxInputSize
                            + ";audioDuration is " + audioDuration
                    );
                    //添加音轨
                    audioTrackIndex = mMediaMuxer.addTrack(mAudioMediaFormat);
                }
                Log.d(TAG, "file mime is " + mime);
            } catch (Exception e) {
                Log.e(TAG, " read error " + e.getMessage());
            }
        }

        if(audioTrackIndex == -1){
            Log.e(TAG, "no audio Track");
            return false;
        }


        //分配缓冲
        ByteBuffer inputBuffer = ByteBuffer.allocate(videoMaxInputSize);
        mMediaMuxer.start();

        //视频处理部分
        mVidoeMediaExtractor.selectTrack(sourceVTrack);
        MediaCodec.BufferInfo videoInfo = new MediaCodec.BufferInfo();
        videoInfo.presentationTimeUs = 0;
        //选择起点
        mVidoeMediaExtractor.seekTo(clipPoint, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
        while (true) {
            int sampleSize = mVidoeMediaExtractor.readSampleData(inputBuffer, 0);
            if (sampleSize < 0) {
                //这里一定要释放选择的轨道，不然另一个轨道就无法选中了
                mVidoeMediaExtractor.unselectTrack(sourceVTrack);
                break;
            }
            int trackIndex = mVidoeMediaExtractor.getSampleTrackIndex();
            //获取时间戳
            long presentationTimeUs = mVidoeMediaExtractor.getSampleTime();
            //获取帧类型，只能识别是否为I帧
            int sampleFlag = mVidoeMediaExtractor.getSampleFlags();
            Log.d(TAG, "trackIndex is " + trackIndex
                    + ";presentationTimeUs is " + presentationTimeUs
                    + ";sampleFlag is " + sampleFlag
                    + ";sampleSize is " + sampleSize);
            //剪辑时间到了就跳出
            if ((clipDuration != 0) && (presentationTimeUs > (clipPoint + clipDuration))) {
                mVidoeMediaExtractor.unselectTrack(sourceVTrack);
                break;
            }
            mVidoeMediaExtractor.advance();
            videoInfo.offset = 0;
            videoInfo.size = sampleSize;
            videoInfo.flags = sampleFlag;
            videoInfo.presentationTimeUs = presentationTimeUs;
            mMediaMuxer.writeSampleData(videoTrackIndex, inputBuffer, videoInfo);
        }

        //音频处理部分
        mAudioMediaExtractor.selectTrack(sourceATrack);
        MediaCodec.BufferInfo audioInfo = new MediaCodec.BufferInfo();
        audioInfo.presentationTimeUs = 0;

        mAudioMediaExtractor.seekTo(clipPoint, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        while (true) {
            int sampleSize = mAudioMediaExtractor.readSampleData(inputBuffer, 0);
            if (sampleSize < 0) {
                mAudioMediaExtractor.unselectTrack(sourceATrack);
                break;
            }
            int trackIndex = mAudioMediaExtractor.getSampleTrackIndex();
            long presentationTimeUs = mAudioMediaExtractor.getSampleTime();
            Log.d(TAG, "trackIndex is " + trackIndex
                    + ";presentationTimeUs is " + presentationTimeUs);
            if ((clipDuration != 0) && (presentationTimeUs > (clipPoint + clipDuration))) {
                mAudioMediaExtractor.unselectTrack(sourceATrack);
                break;
            }
            mAudioMediaExtractor.advance();
            audioInfo.offset = 0;
            audioInfo.size = sampleSize;
            audioInfo.presentationTimeUs = presentationTimeUs;
            mMediaMuxer.writeSampleData(audioTrackIndex, inputBuffer, audioInfo);
        }
        //全部写完后释放MediaMuxer和MediaExtractor
        mMediaMuxer.stop();
        mMediaMuxer.release();
        mVidoeMediaExtractor.release();
        mVidoeMediaExtractor = null;
        mAudioMediaExtractor.release();
        mAudioMediaExtractor = null;
        return true;
    }
}
