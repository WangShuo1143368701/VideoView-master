package com.ws.videoview.videoview.opengl;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.PointF;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;


public class GPUFilter
{

    public static final boolean DEBUG_MODE = false;

    public interface OnFilterListener {
        void onFilterListener(int textureID);
    }

    public static final String NO_FILTER_VERTEX_SHADER = "" +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            " \n" +
            "varying vec2 textureCoordinate;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = position;\n" +
            "    textureCoordinate = inputTextureCoordinate.xy;\n" +
            "}";
    public static final String NO_FILTER_FRAGMENT_SHADER = "" +
            "varying lowp vec2 textureCoordinate;\n" +
            " \n" +
            "uniform sampler2D inputImageTexture;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "     gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "}";

    private final LinkedList<Runnable> mRunOnDraw;
    private final String mVertexShader;
    private final String mFragmentShader;
    protected int mGLProgId;
    protected int mGLAttribPosition;
    protected int mGLUniformTexture;
    protected int mGLAttribTextureCoordinate;
    protected int mOutputWidth;
    protected int mOutputHeight;
    protected int mIntputWidth;
    protected int mIntputHeight;
    protected boolean mIsInitialized;
    protected FloatBuffer mGLCubeBuffer;
    protected FloatBuffer mGLTextureBuffer;

    protected float[] mCurrentVertexMatrix;
    protected float[] mCurrentTextureMatrix;
    private boolean mHasListener = false;
    protected OnFilterListener mListener;
    private int mTextureTransformMatrixLocation = -1;
    private float[] mTextureTransformMatrix = null;    // Ext 纹理旋转矩阵

    protected int mFrameBuffer = -1;
    protected int mFrameBufferTexture = -1;

    protected boolean mHasFrameBuffer = false;//是否需要初始化FrameBuffer
    protected boolean mbNearestMode = false;
    protected boolean mbExtTextureModle = false;    // 是否是 Ext 纹理
    private String TAG = "GPUFilter";

    public GPUFilter() {
        this(NO_FILTER_VERTEX_SHADER, NO_FILTER_FRAGMENT_SHADER, false);
    }

    public GPUFilter(final String vertexShader, final String fragmentShader){
        this(vertexShader, fragmentShader, false);
    }

    public GPUFilter(final String vertexShader, final String fragmentShader, boolean bOesModel) {
        mRunOnDraw = new LinkedList<Runnable>();
        mVertexShader = vertexShader;
        mFragmentShader = fragmentShader;
        mbExtTextureModle = bOesModel;
        if (true == bOesModel){
            Log.i(TAG, "set Oes fileter");
        }

        mGLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mCurrentVertexMatrix = TextureRotationUtil.CUBE;
        mGLCubeBuffer.put(mCurrentVertexMatrix).position(0);

        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mCurrentTextureMatrix = TextureRotationUtil.getRotation(Rotation.NORMAL, false, true);
        mGLTextureBuffer.put(mCurrentTextureMatrix).position(0);
    }

    public boolean init() {
        mGLProgId = OpenGlUtils.loadProgram(mVertexShader, mFragmentShader);
        if (mGLProgId != 0 && onInit())
            mIsInitialized = true;
        else mIsInitialized = false;
        onInitialized();
        return mIsInitialized;
    }

    public void setHasFrameBuffer(boolean hasFrameBuffer){
        mHasFrameBuffer = hasFrameBuffer;
    }

    public void setNearestModel(boolean bNear){
        mbNearestMode = bNear;
        Log.i(TAG, "set Nearest model " + bNear);
    }

    public static Bitmap drawTextureToBitmap(int textureID, int w, int h){
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        ByteBuffer ib = ByteBuffer.allocate(w * h * 4).order(ByteOrder.nativeOrder());
        ib.position(0);
        GPUFilter filter = new GPUFilter();
        filter.init();
        GLES20.glViewport(0,0,w,h);
        filter.onDrawFrame(textureID);
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, ib);
        bmp.copyPixelsFromBuffer(ib);
        filter.destroy();
        return bmp;
    }


    public void setListener(OnFilterListener listener){
        mHasListener = (listener != null);
        mListener = listener;
    }

    public boolean getHasListener(){
        return mHasListener;
    }

    public boolean onInit() {
        mGLAttribPosition = GLES20.glGetAttribLocation(mGLProgId, "position");
        mGLUniformTexture = GLES20.glGetUniformLocation(mGLProgId, "inputImageTexture");
        mTextureTransformMatrixLocation = GLES20.glGetUniformLocation(mGLProgId, "textureTransform");
        mGLAttribTextureCoordinate = GLES20.glGetAttribLocation(mGLProgId,
                "inputTextureCoordinate");
//        int ret = GLES20.glGetError();
//        if (ret != 0){
//            if(DEBUG_MODE){
//                TXCLog.e("OpenGLError",ret + ":"+GLES20.glGetProgramInfoLog(mGLProgId));
//            }
//            return false;
//        } else
        return true;
    }

    public void onInitialized() {
    }

    public void destroy() {
        GLES20.glDeleteProgram(mGLProgId);
        onDestroy();
        mIsInitialized = false;
    }

    public void onDestroy() {
//        clearTextureBuffer();
        destroyFramebuffers();
        mOutputHeight = -1;
        mOutputWidth = -1;
    }

    private static float[] floatBuffer2Array(FloatBuffer buffer){
        if (buffer.limit() <= 0)
            return null;
        else{
            float[] ret = new float[buffer.limit()];
            for (int i =0;i< buffer.limit();i++){
                ret[i] = buffer.get(i);
            }
            return ret;
        }
    }

    public void destroyFramebuffers() {
        if (mFrameBuffer != -1) {
            if(DEBUG_MODE)
                Log.e("GPUFilter","check destroy"+mFrameBuffer+"\t"+mFrameBufferTexture);
            int[] frameBuf = new int[1];
            frameBuf[0] = mFrameBuffer;
            GLES20.glDeleteFramebuffers(1, frameBuf,0);
            mFrameBuffer = -1;
        }
        if (mFrameBufferTexture != -1) {
            int[] frameBuf = new int[1];
            frameBuf[0] = mFrameBufferTexture;
            GLES20.glDeleteTextures(1, frameBuf, 0);
            mFrameBufferTexture = -1;
        }
    }

    public void onOutputSizeChanged(final int width, final int height) {
        if (mOutputHeight == height && mOutputWidth == width)
            return;
        mOutputWidth = width;
        mOutputHeight = height;
        if (mHasFrameBuffer) {
            if (mFrameBuffer == -1) {

            } else {
                destroyFramebuffers();
            }
            int[] frameBuffers = new int[1];
            GLES20.glGenFramebuffers(1, frameBuffers, 0);
            mFrameBuffer = frameBuffers[0];
            mFrameBufferTexture = OpenGlUtils.createTexture(width, height, GLES20.GL_RGBA, GLES20.GL_RGBA);
            if (DEBUG_MODE)
                Log.e("GPUFilter", "check" + mFrameBuffer + "\t" + mFrameBufferTexture);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                    GLES20.GL_TEXTURE_2D, mFrameBufferTexture, 0);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        }
    }

    public void onDraw(final int textureId, final FloatBuffer cubeBuffer,
                       final FloatBuffer textureBuffer) {
        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();
        if (!mIsInitialized) {
            return;
        }

        cubeBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, cubeBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        textureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0,
                textureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        if (mTextureTransformMatrixLocation >= 0 && null != mTextureTransformMatrix){
            GLES20.glUniformMatrix4fv(mTextureTransformMatrixLocation, 1, false, mTextureTransformMatrix, 0);
        }


        if (textureId != OpenGlUtils.NO_TEXTURE) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            if (true == mbExtTextureModle){
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            }else{
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
            }
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }
        onDrawArraysPre();
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        onDrawArraysAfter();
        if (true == mbExtTextureModle) {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        }else{
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        }
    }
    public void setTextureTransformMatrix(float[] mtx){
        mTextureTransformMatrix = mtx;
    }

    public void flipX(){
        if(mCurrentTextureMatrix != null){
            for (int i = 0; i < 8; i += 2){
                mCurrentTextureMatrix[i] = 1.f - mCurrentTextureMatrix[i];
            }
        }
        else return;
        setAttribPointer(mCurrentVertexMatrix, mCurrentTextureMatrix);
    }

    public void flipY(){
        if(mCurrentTextureMatrix != null){
            for (int i = 1; i < 8; i += 2){
                mCurrentTextureMatrix[i] = 1.f - mCurrentTextureMatrix[i];
            }
        }
        else return;
        setAttribPointer(mCurrentVertexMatrix, mCurrentTextureMatrix);
    }

    public int onDrawFrame(final int textureId, final FloatBuffer cubeBuffer,
                           final FloatBuffer textureBuffer) {
        if (!mIsInitialized) {
            return OpenGlUtils.NOT_INIT;
        }

        onDraw(textureId, cubeBuffer, textureBuffer);

        if (mListener instanceof OnFilterListener){
            mListener.onFilterListener(textureId);
        }
        return OpenGlUtils.ON_DRAWN;
    }

    public void clearTextureBuffer() {
        GLES20.glUseProgram(mGLProgId);
        if (mFrameBuffer != -1){
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFrameBuffer);
            GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
            GLES20.glClearColor(0.0F, 0.0F, 0.0F, 0.0F);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        } else {
            GLES20.glViewport(0, 0, mOutputWidth, mOutputHeight);
            GLES20.glClearColor(0.0F, 0.0F, 0.0F, 1.0F);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        }
        return;
    }

    public int onDrawFrame(final int textureId) {
        return onDrawFrame(textureId, mGLCubeBuffer, mGLTextureBuffer);
    }

    protected void onDrawArraysPre() {
    }

    protected void onDrawArraysAfter() {
    }

    protected void runPendingOnDrawTasks() {
        while (!mRunOnDraw.isEmpty()) {
            mRunOnDraw.removeFirst().run();
        }
    }

//    public int onDrawToTexture(final int textureId, int []frameBuffers,int []frameBufferTextures) {
//        if(frameBuffers == null)
//            return OpenGlUtils.NO_TEXTURE;
//        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[0]);
//        int ret = onDrawFrame(textureId);
//        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
//
//        if(ret == OpenGlUtils.ON_DRAWN)
//        {
//            ret = frameBufferTextures[0];
//        }
//        else {
//            ret = 0;
//        }
//
//        return ret;
//
//    }

    public int onDrawToTexture(final int textureId, int frameBuffer,int frameBufferTexture) {
        if(!mIsInitialized)
            return OpenGlUtils.NO_TEXTURE;
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffer);
        onDraw(textureId, mGLCubeBuffer, mGLTextureBuffer);
        if (mListener instanceof OnFilterListener){
            mListener.onFilterListener(frameBufferTexture);
        }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        return frameBufferTexture;
    }

    public int onDrawToTexture(final int textureId) {
        return onDrawToTexture(textureId,mFrameBuffer,mFrameBufferTexture);
    }

    public int getOutputTexture(){
        return mFrameBufferTexture;
    }

    public int getOutputFrameBuffer(){
        return mFrameBuffer;
    }

    public void onDisplaySizeChanged(final int width, final int height) {
        mOutputWidth = width;
        mOutputHeight = height;
    }

    public void onInputSizeChanged(final int width, final int height) {
        mIntputWidth = width;
        mIntputHeight = height;
    }

    public void setAttribPointer(float[] vertexPointer, float[] fragmentPointer){
        mCurrentVertexMatrix = vertexPointer;
        mGLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLCubeBuffer.put(vertexPointer).position(0);

        mCurrentTextureMatrix = fragmentPointer;
        mGLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        mGLTextureBuffer.put(fragmentPointer).position(0);
    }

    public void setAttribPointer(FloatBuffer vertexPointer, FloatBuffer fragmentPointer){
        mGLCubeBuffer = vertexPointer;
        mGLTextureBuffer = fragmentPointer;
    }


    /**
     * 旋转和裁剪，这里不是严格裁剪出目标宽高，而是按照16:9的比例尽可能保存多的像素
     * @param oriWidth：采集宽度
     * @param oriHeight：采集高度
     * @param orientaion：旋转角度，逆时针方向
     * @param fragmentPointer：null，相对于标准位置
     */
    public void scaleClipAndRotate(int oriWidth, int oriHeight, int orientaion, FloatBuffer fragmentPointer, float ratio){
        float[] oriFragmentPointer = null;
        if (fragmentPointer == null) {
            oriFragmentPointer = TextureRotationUtil.getRotation(Rotation.NORMAL, false, true);
        } else {
            oriFragmentPointer = floatBuffer2Array(fragmentPointer);
        }
        //裁剪：
        //float ratio = 16 / (float)9;
        int clipWidth = oriWidth;
        int clipHeight = oriHeight;
        if (oriWidth / (float)oriHeight > ratio){
            clipHeight = oriHeight;
            clipWidth = (int) (clipHeight * ratio);
        } else if (oriWidth / (float) oriHeight < ratio) {
            clipWidth = oriWidth;
            clipHeight = (int)(clipWidth / ratio);
        }

        float xClip = (float) clipWidth/oriWidth;
        float yClip = (float) clipHeight/oriHeight;
        xClip = (1 - xClip)/2;
        yClip = (1 - yClip)/2;
        for (int i =0;i<oriFragmentPointer.length/2;i++){
            if (oriFragmentPointer[2*i] < 0.5f)
                oriFragmentPointer[2*i] += xClip;
            else oriFragmentPointer[2*i] -= xClip;
            if (oriFragmentPointer[2*i+1] < 0.5f)
                oriFragmentPointer[2*i+1]+=yClip;
            else oriFragmentPointer[2*i+1]-=yClip;
        }
        //旋转：
        int k = orientaion/90;
        for (int i =0;i<k;i++){
            float tX = oriFragmentPointer[0];
            float tY = oriFragmentPointer[1];
            oriFragmentPointer[0] = oriFragmentPointer[2];
            oriFragmentPointer[1] = oriFragmentPointer[3];
            oriFragmentPointer[2] = oriFragmentPointer[6];
            oriFragmentPointer[3] = oriFragmentPointer[7];
            oriFragmentPointer[6] = oriFragmentPointer[4];
            oriFragmentPointer[7] = oriFragmentPointer[5];
            oriFragmentPointer[4] = tX;
            oriFragmentPointer[5] = tY;
        }
        setAttribPointer(TextureRotationUtil.CUBE.clone(),oriFragmentPointer);
    }

    /**
     * 旋转和裁剪，这里不是严格裁剪出目标宽高，而是按照ratio的比例尽可能保存多的像素
     * @param oriWidth：采集宽度
     * @param oriHeight：采集高度
     * @param orientaion：旋转角度，逆时针方向
     * @param fragmentPointer：null，相对于标准位置
     */
    public void scaleClipAndRotate(int oriWidth, int oriHeight, int orientaion, float[] fragmentPointer, float ratio, boolean xMirror, boolean yMirror){
        float[] oriFragmentPointer = null;
        if (fragmentPointer == null){
            oriFragmentPointer = TextureRotationUtil.getRotation(Rotation.NORMAL,false,true);
        }
        else{
            oriFragmentPointer = fragmentPointer;
        }
        //裁剪：
        //float ratio = 16 / (float)9;
        int clipWidth = oriWidth;
        int clipHeight = oriHeight;
        if (oriWidth / (float)oriHeight > ratio){
            clipHeight = oriHeight;
            clipWidth = (int)(clipHeight * ratio);
        }
        else if(oriWidth / (float)oriHeight < ratio){
            clipWidth = oriWidth;
            clipHeight = (int)(clipWidth / ratio);
        }

        float xClip = (float) clipWidth/oriWidth;
        float yClip = (float) clipHeight/oriHeight;
        xClip = (1 - xClip)/2;
        yClip = (1 - yClip)/2;
        for (int i =0;i<oriFragmentPointer.length/2;i++){
            if (oriFragmentPointer[2*i] < 0.5f)
                oriFragmentPointer[2*i] += xClip;
            else oriFragmentPointer[2*i] -= xClip;
            if (oriFragmentPointer[2*i+1] < 0.5f)
                oriFragmentPointer[2*i+1]+=yClip;
            else oriFragmentPointer[2*i+1]-=yClip;
        }
        //旋转：
        int k = orientaion/90;
        for (int i =0;i<k;i++){
            float tX = oriFragmentPointer[0];
            float tY = oriFragmentPointer[1];
            oriFragmentPointer[0] = oriFragmentPointer[2];
            oriFragmentPointer[1] = oriFragmentPointer[3];
            oriFragmentPointer[2] = oriFragmentPointer[6];
            oriFragmentPointer[3] = oriFragmentPointer[7];
            oriFragmentPointer[6] = oriFragmentPointer[4];
            oriFragmentPointer[7] = oriFragmentPointer[5];
            oriFragmentPointer[4] = tX;
            oriFragmentPointer[5] = tY;
        }
        if(k == 0||k==2){
            if(xMirror){
                oriFragmentPointer[0] = 1.0f - oriFragmentPointer[0];
                oriFragmentPointer[2] = 1.0f - oriFragmentPointer[2];
                oriFragmentPointer[4] = 1.0f - oriFragmentPointer[4];
                oriFragmentPointer[6] = 1.0f - oriFragmentPointer[6];
            }
            if(yMirror){
                oriFragmentPointer[1] = 1.0f - oriFragmentPointer[1];
                oriFragmentPointer[3] = 1.0f - oriFragmentPointer[3];
                oriFragmentPointer[5] = 1.0f - oriFragmentPointer[5];
                oriFragmentPointer[7] = 1.0f - oriFragmentPointer[7];
            }
        }
        else{
            if(yMirror){
                oriFragmentPointer[0] = 1.0f - oriFragmentPointer[0];
                oriFragmentPointer[2] = 1.0f - oriFragmentPointer[2];
                oriFragmentPointer[4] = 1.0f - oriFragmentPointer[4];
                oriFragmentPointer[6] = 1.0f - oriFragmentPointer[6];
            }
            if(xMirror){
                oriFragmentPointer[1] = 1.0f - oriFragmentPointer[1];
                oriFragmentPointer[3] = 1.0f - oriFragmentPointer[3];
                oriFragmentPointer[5] = 1.0f - oriFragmentPointer[5];
                oriFragmentPointer[7] = 1.0f - oriFragmentPointer[7];
            }
        }
        setAttribPointer(TextureRotationUtil.CUBE.clone(),oriFragmentPointer);
    }

    public void setZoomAndRotate(int orientaion, FloatBuffer fragmentPointer){
        float[] oriFragmentPointer = null;
        if (fragmentPointer == null) {
            oriFragmentPointer = TextureRotationUtil.getRotation(Rotation.NORMAL, false, true);
        } else {
            oriFragmentPointer = floatBuffer2Array(fragmentPointer);
        }

        //旋转：
        int k = orientaion/90;
        for (int i =0;i<k;i++){
            float tX = oriFragmentPointer[0];
            float tY = oriFragmentPointer[1];
            oriFragmentPointer[0] = oriFragmentPointer[2];
            oriFragmentPointer[1] = oriFragmentPointer[3];
            oriFragmentPointer[2] = oriFragmentPointer[6];
            oriFragmentPointer[3] = oriFragmentPointer[7];
            oriFragmentPointer[6] = oriFragmentPointer[4];
            oriFragmentPointer[7] = oriFragmentPointer[5];
            oriFragmentPointer[4] = tX;
            oriFragmentPointer[5] = tY;
        }
        setAttribPointer(TextureRotationUtil.CUBE.clone(),oriFragmentPointer);
    }


    public static void rotate(int orientaion, FloatBuffer fragmentPointer){
        float[] oriFragmentPointer = null;
        if (fragmentPointer == null) {
            oriFragmentPointer = TextureRotationUtil.getRotation(Rotation.NORMAL, false, true);
        } else {
            oriFragmentPointer = floatBuffer2Array(fragmentPointer);
        }
        int k = orientaion/90;
        for (int i =0;i<k;i++){
            float tX = oriFragmentPointer[0];
            float tY = oriFragmentPointer[1];
            oriFragmentPointer[0] = oriFragmentPointer[2];
            oriFragmentPointer[1] = oriFragmentPointer[3];
            oriFragmentPointer[2] = oriFragmentPointer[6];
            oriFragmentPointer[3] = oriFragmentPointer[7];
            oriFragmentPointer[6] = oriFragmentPointer[4];
            oriFragmentPointer[7] = oriFragmentPointer[5];
            oriFragmentPointer[4] = tX;
            oriFragmentPointer[5] = tY;
        }
        if (fragmentPointer != null){
            fragmentPointer.put(oriFragmentPointer);
        }
    }

    /**
     * 根据输入的宽高比，对输入裁剪后，放入输出区域
     * 该接口仅仅用来根据输出窗口的大小动态裁剪输入
     * @param inputWidth：输入宽度
     * @param inputHeight：输入高度
     * @param outputWidth：输出宽度
     * @param outputHeight：输出高度
     * @param textureBuffer：null，相对标准位置
     */
    public void adjustOutputWHRatio(int inputWidth, int inputHeight, int outputWidth, int outputHeight, FloatBuffer textureBuffer, boolean oritention){
        double iRatio = (double) inputHeight / inputWidth;
        double oRatio = (double)outputHeight / outputWidth;
        int clipX = 0;
        int clipY = 0;
        if (iRatio < oRatio){
            //Clip X
            clipX = inputHeight * outputWidth / outputHeight;
            clipY = inputHeight;
        } else if (iRatio > oRatio) {
            //Clip Y
            clipX = inputWidth;
            clipY = inputWidth * outputHeight / outputWidth;
        } else {
            clipX = inputWidth;
            clipY = inputHeight;
        }
        float[] oriFragmentPointer = floatBuffer2Array(textureBuffer);
        float xClip = (float) clipX/inputWidth;
        float yClip = (float) clipY/inputHeight;
        xClip = (1 - xClip)/2;
        yClip = (1 - yClip)/2;
        if (oritention){
            for (int i =0;i<oriFragmentPointer.length/2;i++){
                if (oriFragmentPointer[2*i] < 0.5f)
                    oriFragmentPointer[2*i] += yClip;
                else oriFragmentPointer[2*i] -= yClip;
                if (oriFragmentPointer[2*i+1] < 0.5f)
                    oriFragmentPointer[2*i+1]+=xClip;
                else oriFragmentPointer[2*i+1]-=xClip;
            }
        } else {
            for (int i = 0; i < oriFragmentPointer.length / 2; i++) {
                if (oriFragmentPointer[2 * i] < 0.5f)
                    oriFragmentPointer[2 * i] += xClip;
                else oriFragmentPointer[2 * i] -= xClip;
                if (oriFragmentPointer[2 * i + 1] < 0.5f)
                    oriFragmentPointer[2 * i + 1] += yClip;
                else oriFragmentPointer[2 * i + 1] -= yClip;
            }
        }
        setAttribPointer(TextureRotationUtil.CUBE.clone(),oriFragmentPointer);
    }

    /**
     * 根据输入的宽高比，对输入裁剪后，放入输出区域
     * 该接口仅仅用来根据输出窗口的大小动态裁剪输入
     * @param inputWidth：输入宽度
     * @param inputHeight：输入高度
     * @param outputWidth：输出宽度
     * @param outputHeight：输出高度
     * @param textureBuffer：null，相对标准位置
     */
    public void adjustOutputWHRatio(int inputWidth, int inputHeight, int outputWidth, int outputHeight, FloatBuffer textureBuffer, boolean oritention, boolean xMirror, boolean yMirror){
        double iRatio = (double) inputHeight / inputWidth;
        double oRatio = (double)outputHeight / outputWidth;
        int clipX = 0;
        int clipY = 0;
        if (iRatio < oRatio){
            //Clip X
            clipX = inputHeight * outputWidth / outputHeight;
            clipY = inputHeight;
        }
        else if(iRatio > oRatio){
            //Clip Y
            clipX = inputWidth;
            clipY = inputWidth * outputHeight / outputWidth;
        }
        else{
            clipX = inputWidth;
            clipY = inputHeight;
        }
        float[] oriFragmentPointer = floatBuffer2Array(textureBuffer);
        float xClip = (float) clipX/inputWidth;
        float yClip = (float) clipY/inputHeight;
        xClip = (1 - xClip)/2;
        yClip = (1 - yClip)/2;
        if (oritention){
            for (int i =0;i<oriFragmentPointer.length/2;i++){
                if (oriFragmentPointer[2*i] < 0.5f)
                    oriFragmentPointer[2*i] += yClip;
                else oriFragmentPointer[2*i] -= yClip;
                if (oriFragmentPointer[2*i+1] < 0.5f)
                    oriFragmentPointer[2*i+1]+=xClip;
                else oriFragmentPointer[2*i+1]-=xClip;
            }
        }
        else{
            for (int i =0;i<oriFragmentPointer.length/2;i++){
                if (oriFragmentPointer[2*i] < 0.5f)
                    oriFragmentPointer[2*i] += xClip;
                else oriFragmentPointer[2*i] -= xClip;
                if (oriFragmentPointer[2*i+1] < 0.5f)
                    oriFragmentPointer[2*i+1]+=yClip;
                else oriFragmentPointer[2*i+1]-=yClip;
            }
        }
        if(xMirror){
            oriFragmentPointer[0] = 1.0f - oriFragmentPointer[0];
            oriFragmentPointer[2] = 1.0f - oriFragmentPointer[2];
            oriFragmentPointer[4] = 1.0f - oriFragmentPointer[4];
            oriFragmentPointer[6] = 1.0f - oriFragmentPointer[6];
        }
        if(yMirror){
            oriFragmentPointer[1] = 1.0f - oriFragmentPointer[1];
            oriFragmentPointer[3] = 1.0f - oriFragmentPointer[3];
            oriFragmentPointer[5] = 1.0f - oriFragmentPointer[5];
            oriFragmentPointer[7] = 1.0f - oriFragmentPointer[7];
        }
        setAttribPointer(TextureRotationUtil.CUBE.clone(),oriFragmentPointer);
    }

    public boolean isInitialized() {
        return mIsInitialized;
    }

    public int getOutputWidth() {
        return mOutputWidth;
    }

    public int getOutputHeight() {
        return mOutputHeight;
    }

    public int getProgram() {
        return mGLProgId;
    }

    public int getAttribPosition() {
        return mGLAttribPosition;
    }

    public int getAttribTextureCoordinate() {
        return mGLAttribTextureCoordinate;
    }

    public int getUniformTexture() {
        return mGLUniformTexture;
    }

    public void setInteger(final int location, final int intValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform1i(location, intValue);
            }
        });
    }

    public void setFloat(final int location, final float floatValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform1f(location, floatValue);
            }
        });
    }

    public void setFloatVec2(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform2fv(location, 1, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    public void setFloatVec3(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform3fv(location, 1, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    public void setFloatVec4(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform4fv(location, 1, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    public void setFloatArray(final int location, final float[] arrayValue) {
        runOnDraw(new Runnable() {
            @Override
            public void run() {
                GLES20.glUniform1fv(location, arrayValue.length, FloatBuffer.wrap(arrayValue));
            }
        });
    }

    public void setPoint(final int location, final PointF point) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                float[] vec2 = new float[2];
                vec2[0] = point.x;
                vec2[1] = point.y;
                GLES20.glUniform2fv(location, 1, vec2, 0);
            }
        });
    }

    public void setUniformMatrix3f(final int location, final float[] matrix) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                GLES20.glUniformMatrix3fv(location, 1, false, matrix, 0);
            }
        });
    }

    public void setUniformMatrix4f(final int location, final float[] matrix) {
        runOnDraw(new Runnable() {

            @Override
            public void run() {
                GLES20.glUniformMatrix4fv(location, 1, false, matrix, 0);
            }
        });
    }

    public void runOnDraw(final Runnable runnable) {
        synchronized (mRunOnDraw) {
            mRunOnDraw.addLast(runnable);
        }
    }

    public static String loadShader(String file, Context context) {
        try {
            AssetManager assetManager = context.getAssets();
            InputStream ims = assetManager.open(file);

            String re = convertStreamToString(ims);
            ims.close();
            return re;
        } catch (Exception e) {
            e.printStackTrace();
        }

        return "";
    }

    public static String convertStreamToString(InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    public void checkGlError(String op) {
        int error = GLES20.glGetError();
        if (error != 0) {
            throw new RuntimeException(op + ": glError " + error);
        }
    }
}