package com.ws.videoview.videoview.opengl;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import java.nio.FloatBuffer;


public class GPUOESTextureFilter extends GPUFilter {
    private float[] mTextureTransformMatrix = new float[16];
    private int mTextureTransformMatrixLocation;

    public static final String CAMERA_VERTEX_SHADER = "" +
            "attribute vec4 position;\n" +
            "attribute vec4 inputTextureCoordinate;\n" +
            " \n" +
            "uniform mat4 textureTransform;\n"+
            "varying vec2 textureCoordinate;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "    gl_Position = position;\n" +
            "    textureCoordinate = (textureTransform * inputTextureCoordinate).xy;\n" +
            "}";

    public static final String CAMERA_FRAGMENT_SHADER = "" +
            "#extension GL_OES_EGL_image_external : require\n" +
            "\n"+
            "varying lowp vec2 textureCoordinate;\n" +
            " \n" +
            "uniform samplerExternalOES inputImageTexture;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "     gl_FragColor = texture2D(inputImageTexture, textureCoordinate);\n" +
            "}";

    public boolean mClearFlag = false;

    public GPUOESTextureFilter(){
        super(CAMERA_VERTEX_SHADER, CAMERA_FRAGMENT_SHADER);
        mHasFrameBuffer = true;
    }

    public boolean onInit() {
        boolean ret = super.onInit();
        mTextureTransformMatrixLocation = GLES20.glGetUniformLocation(mGLProgId, "textureTransform");
        if(ret && GLES20.glGetError() == 0)
            return true;
        else return false;
    }

    public void setTextureTransformMatrix(float[] mtx){
        mTextureTransformMatrix = mtx;
    }

    @Override
    public void onInputSizeChanged(final int width, final int height) {
        super.onInputSizeChanged(width, height);
    }

    @Override
    public void onDraw(int textureId, FloatBuffer vertexBuffer, FloatBuffer textureBuffer) {
        GLES20.glUseProgram(mGLProgId);
        runPendingOnDrawTasks();
        if(!isInitialized() || mTextureTransformMatrix == null) {
            return ;
        }
        vertexBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        textureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribTextureCoordinate, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glUniformMatrix4fv(mTextureTransformMatrixLocation, 1, false, mTextureTransformMatrix, 0);

        if(textureId != OpenGlUtils.NO_TEXTURE){
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(mGLUniformTexture, 0);
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGLAttribTextureCoordinate);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);

        return ;
    }
}
