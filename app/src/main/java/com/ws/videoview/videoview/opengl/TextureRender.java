package com.ws.videoview.videoview.opengl;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;


public class TextureRender
{
    private static final String TAG = "TextureRender";

    private static final int FLOAT_SIZE_BYTES = 4;
    private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
    private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
    private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
    private final float[] mTriangleVerticesDataOes = {
            // X, Y, Z, U, V
            -1.0f, -1.0f, 0, 0.f, 0.f,
             1.0f, -1.0f, 0, 1.f, 0.f,
            -1.0f,  1.0f, 0, 0.f, 1.f,
             1.0f,  1.0f, 0, 1.f, 1.f,
    };
    private final float[] mTriangleVerticesDataLocal = {
            // X, Y, Z, U, V
             1.0f, -1.0f, 0, 1.0f, 1.0f,
            -1.0f, -1.0f, 0, 0.0f, 1.0f,
             1.0f,  1.0f, 0, 1.0f, 0.0f,
            -1.0f,  1.0f, 0, 0.0f, 0.0f,
    };

    private FloatBuffer mTriangleVertices;

    private static final String VERTEX_SHADER =
            "uniform mat4 uMVPMatrix;\n" +
                    "uniform mat4 uSTMatrix;\n" +
                    "attribute vec4 aPosition;\n" +
                    "attribute vec4 aTextureCoord;\n" +
                    "varying vec2 vTextureCoord;\n" +
                    "void main() {\n" +
                    "  gl_Position = uMVPMatrix * aPosition;\n" +
                    "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                    "}\n";

    private static final String FRAGMENT_SHADER_OES =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;\n" +      // highp here doesn't seem to matter
                    "varying vec2 vTextureCoord;\n" +
                    "uniform samplerExternalOES sTexture;\n" +
                    "void main() {\n" +
                    "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                    "}\n";

    public static final String FRAGMENT_SHADER_LOCAL = "" +
            "varying highp vec2 vTextureCoord;\n" +
            " \n" +
            "uniform sampler2D sTexture;\n" +
            " \n" +
            "void main()\n" +
            "{\n" +
            "     gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
            "}";

    private float[] mMVPMatrix = new float[16];
    private float[] mSTMatrix = new float[16];

    private int mProgram;
    private int mTextureID = -12345;
    private int muMVPMatrixHandle;
    private int muSTMatrixHandle;
    private int maPositionHandle;
    private int maTextureHandle;

    private boolean mClearLastFrame = false;
    private boolean mIsOESTexture = true;
    private boolean  mMirror = false;
    private int     mOrientaion = -1;

    private int     mVideoWidth    = 0;
    private int     mVideoHeight   = 0;

    public TextureRender(boolean isOesTexture) {
        mIsOESTexture = isOesTexture;
        if (mIsOESTexture) {
            mTriangleVertices = ByteBuffer.allocateDirect(
                    mTriangleVerticesDataOes.length * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            mTriangleVertices.put(mTriangleVerticesDataOes).position(0);
        } else {
            mTriangleVertices = ByteBuffer.allocateDirect(
                    mTriangleVerticesDataLocal.length * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            mTriangleVertices.put(mTriangleVerticesDataLocal).position(0);
        }

        Matrix.setIdentityM(mSTMatrix, 0);

    }

    public int getTextureId() {
        return mTextureID;
    }

    public void clearLastFrame() {
        mClearLastFrame = true;
    }

    public void drawFrame(SurfaceTexture st) {
        if (st == null) {
            return;
        }
        checkGlError("onDrawFrame start");
        st.getTransformMatrix(mSTMatrix);

        drawFrame(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
    }

    public void setVideoSize(int width, int height) {
        mVideoWidth  = width;
        mVideoHeight = height;
    }

    public void drawFrame(int textureId, boolean mirror, int orientaion) {
        if (mMirror != mirror || mOrientaion != orientaion) {
            mMirror = mirror;
            mOrientaion = orientaion;
            float[] oriFragmentPointer = new float[20];
            for (int i =0;i< 20;i++){
                oriFragmentPointer[i] = mTriangleVerticesDataLocal[i];
            }
            if (mMirror) {
                oriFragmentPointer[0] = -oriFragmentPointer[0];
                oriFragmentPointer[5] = -oriFragmentPointer[5];
                oriFragmentPointer[10] = -oriFragmentPointer[10];
                oriFragmentPointer[15] = -oriFragmentPointer[15];
            }

            //旋转：
            int k = orientaion/90;
            for (int i =0;i<k;i++) {
                float tX = oriFragmentPointer[3];
                float tY = oriFragmentPointer[4];
                oriFragmentPointer[3] = oriFragmentPointer[8];
                oriFragmentPointer[4] = oriFragmentPointer[9];
                oriFragmentPointer[8] = oriFragmentPointer[18];
                oriFragmentPointer[9] = oriFragmentPointer[19];
                oriFragmentPointer[18] = oriFragmentPointer[13];
                oriFragmentPointer[19] = oriFragmentPointer[14];
                oriFragmentPointer[13] = tX;
                oriFragmentPointer[14] = tY;
            }
            mTriangleVertices.clear();
            mTriangleVertices.put(oriFragmentPointer).position(0);
        }


        drawFrame(GLES20.GL_TEXTURE_2D, textureId);
    }

    public void drawFrame(int textureId, boolean mirror, int orientaion, float ratio, int oriWidth, int oriHeight) {
        if (mMirror != mirror || mOrientaion != orientaion) {
            mMirror = mirror;
            mOrientaion = orientaion;
            float[] oriFragmentPointer = new float[20];
            for (int i =0;i< 20;i++){
                oriFragmentPointer[i] = mTriangleVerticesDataLocal[i];
            }
            if (mMirror) {
                oriFragmentPointer[0] = -oriFragmentPointer[0];
                oriFragmentPointer[5] = -oriFragmentPointer[5];
                oriFragmentPointer[10] = -oriFragmentPointer[10];
                oriFragmentPointer[15] = -oriFragmentPointer[15];
            }

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
            for (int i =0;i<oriFragmentPointer.length/5;i++){
                if (oriFragmentPointer[5*i+3] < 0.5f)
                    oriFragmentPointer[5*i+3] += xClip;
                else oriFragmentPointer[5*i+3] -= xClip;
                if (oriFragmentPointer[5*i+4] < 0.5f)
                    oriFragmentPointer[5*i+4]+=yClip;
                else oriFragmentPointer[5*i+4]-=yClip;
            }

            //旋转：
            int k = orientaion/90;
            for (int i =0;i<k;i++) {
                float tX = oriFragmentPointer[3];
                float tY = oriFragmentPointer[4];
                oriFragmentPointer[3] = oriFragmentPointer[8];
                oriFragmentPointer[4] = oriFragmentPointer[9];
                oriFragmentPointer[8] = oriFragmentPointer[18];
                oriFragmentPointer[9] = oriFragmentPointer[19];
                oriFragmentPointer[18] = oriFragmentPointer[13];
                oriFragmentPointer[19] = oriFragmentPointer[14];
                oriFragmentPointer[13] = tX;
                oriFragmentPointer[14] = tY;
            }
//            if(k == 0||k==2) {
//                if(mirror) {
//                    oriFragmentPointer[3] = 1.0f - oriFragmentPointer[3];
//                    oriFragmentPointer[8] = 1.0f - oriFragmentPointer[8];
//                    oriFragmentPointer[13] = 1.0f - oriFragmentPointer[13];
//                    oriFragmentPointer[19] = 1.0f - oriFragmentPointer[19];
//                }
//            } else {
//                if(mirror) {
//                    oriFragmentPointer[4] = 1.0f - oriFragmentPointer[4];
//                    oriFragmentPointer[9] = 1.0f - oriFragmentPointer[9];
//                    oriFragmentPointer[14] = 1.0f - oriFragmentPointer[14];
//                    oriFragmentPointer[19] = 1.0f - oriFragmentPointer[19];
//                }
//            }
            mTriangleVertices.clear();
            mTriangleVertices.put(oriFragmentPointer).position(0);
        }


        drawFrame(GLES20.GL_TEXTURE_2D, textureId);
    }

    private void drawFrame(int target, int textureId) {

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);
        if (mClearLastFrame) {
            mClearLastFrame = false;
            return;
        }

        GLES20.glUseProgram(mProgram);
        checkGlError("glUseProgram");

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(target, textureId);

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
        GLES20.glVertexAttribPointer(maPositionHandle, 3, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maPosition");
        GLES20.glEnableVertexAttribArray(maPositionHandle);
        checkGlError("glEnableVertexAttribArray maPositionHandle");

        mTriangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
        GLES20.glVertexAttribPointer(maTextureHandle, 2, GLES20.GL_FLOAT, false,
                TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mTriangleVertices);
        checkGlError("glVertexAttribPointer maTextureHandle");
        GLES20.glEnableVertexAttribArray(maTextureHandle);
        checkGlError("glEnableVertexAttribArray maTextureHandle");

        Matrix.setIdentityM(mMVPMatrix, 0);
        GLES20.glUniformMatrix4fv(muMVPMatrixHandle, 1, false, mMVPMatrix, 0);

        if (mVideoWidth % 8 != 0) {
            int width = (mVideoWidth + 7) / 8 * 8;
            Matrix.scaleM(mSTMatrix, 0, (mVideoWidth - 1) * 1.0f / width, 1f, 1f);
        }
        if (mVideoHeight % 8 != 0) {
            int height = (mVideoHeight + 7) / 8 * 8;
            Matrix.scaleM(mSTMatrix, 0, 1f, (mVideoHeight - 1) * 1.0f / height, 1f);
        }

        GLES20.glUniformMatrix4fv(muSTMatrixHandle, 1, false, mSTMatrix, 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays");
        GLES20.glFinish();
    }

    /**
     * Initializes GL state.  Call this after the EGL surface has been created and made current.
     */
    public void createTexture() {

        if (mIsOESTexture) {
            mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_OES);
        } else {
            mProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_LOCAL);
        }

        if (mProgram == 0) {
            throw new RuntimeException("failed creating program");
        }
        maPositionHandle = GLES20.glGetAttribLocation(mProgram, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (maPositionHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        maTextureHandle = GLES20.glGetAttribLocation(mProgram, "aTextureCoord");
        checkGlError("glGetAttribLocation aTextureCoord");
        if (maTextureHandle == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }

        muMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix");
        checkGlError("glGetUniformLocation uMVPMatrix");
        if (muMVPMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uMVPMatrix");
        }

        muSTMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uSTMatrix");
        checkGlError("glGetUniformLocation uSTMatrix");
        if (muSTMatrixHandle == -1) {
            throw new RuntimeException("Could not get attrib location for uSTMatrix");
        }

        if (mIsOESTexture) {
            createOESTexture();
        }

        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        checkGlError("glTexParameter");
    }

    public void destroy() {
        if (mProgram != 0) {
            GLES20.glDeleteProgram(mProgram);
        }
        int[] texture = new int[]{mTextureID};
        GLES20.glDeleteTextures(1, texture, 0);
        mTextureID = -1;
    }
    
    private void createOESTexture() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);

        mTextureID = textures[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureID);
        checkGlError("glBindTexture mTextureID");
    }


    /**
     * Replaces the fragment shader.
     */
    public void changeFragmentShader(String fragmentShader) {
        GLES20.glDeleteProgram(mProgram);
        mProgram = createProgram(VERTEX_SHADER, fragmentShader);
        if (mProgram == 0) {
            throw new RuntimeException("failed creating program");
        }
    }

    private int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        checkGlError("glCreateShader type=" + shaderType);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + shaderType + ":");
            Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) {
            return 0;
        }
        int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) {
            return 0;
        }

        int program = GLES20.glCreateProgram();
        checkGlError("glCreateProgram");
        if (program == 0) {
            Log.e(TAG, "Could not create program");
        }
        GLES20.glAttachShader(program, vertexShader);
        checkGlError("glAttachShader");
        GLES20.glAttachShader(program, pixelShader);
        checkGlError("glAttachShader");
        GLES20.glLinkProgram(program);
        int[] linkStatus = new int[1];
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
        if (linkStatus[0] != GLES20.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES20.glGetProgramInfoLog(program));
            GLES20.glDeleteProgram(program);
            program = 0;
        }
        return program;
    }

    public void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            return;
        }
    }

    /**
     * Saves the current frame to disk as a PNG image.  Frame starts from (0,0).
     * <p>
     * Useful for debugging.
     */
    public static void saveFrame(String filename, int width, int height) {
        // glReadPixels gives us a ByteBuffer filled with what is essentially big-endian RGBA
        // data (i.e. a byte of red, followed by a byte of green...).  We need an int[] filled
        // with native-order ARGB data to feed to Bitmap.
        //
        // If we implement this as a series of buf.get() calls, we can spend 2.5 seconds just
        // copying data around for a 720p frame.  It's better to do a bulk get() and then
        // rearrange the data in memory.  (For comparison, the PNG compress takes about 500ms
        // for a trivial frame.)
        //
        // So... we set the ByteBuffer to little-endian, which should turn the bulk IntBuffer
        // get() into a straight memcpy on most Android devices.  Our ints will hold ABGR data.
        // Swapping B and R gives us ARGB.  We need about 30ms for the bulk get(), and another
        // 270ms for the color swap.
        //
        // Making this even more interesting is the upside-down nature of GL, which means we
        // may want to flip the image vertically here.

        ByteBuffer buf = ByteBuffer.allocateDirect(width * height * 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        buf.rewind();

        int pixelCount = width * height;
        int[] colors = new int[pixelCount];
        buf.asIntBuffer().get(colors);
        for (int i = 0; i < pixelCount; i++) {
            int c = colors[i];
            colors[i] = (c & 0xff00ff00) | ((c & 0x00ff0000) >> 16) | ((c & 0x000000ff) << 16);
        }

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(filename);
            Bitmap bmp = Bitmap.createBitmap(colors, width, height, Bitmap.Config.ARGB_8888);
            bmp.compress(Bitmap.CompressFormat.PNG, 90, fos);
            bmp.recycle();
        } catch (IOException ioe) {
            throw new RuntimeException("Failed to write file " + filename, ioe);
        } finally {
            try {
                if (fos != null) fos.close();
            } catch (IOException ioe2) {
                throw new RuntimeException("Failed to close file " + filename, ioe2);
            }
        }
    }
}

