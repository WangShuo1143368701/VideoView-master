package com.ws.videoview.videoview.opengl;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Camera;
import android.opengl.*;
import android.util.Log;

import javax.microedition.khronos.opengles.GL10;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;


public class OpenGlUtils
{

    public interface OnDrawTaskHandler{
        void addOnDrawTask(Runnable task);
    }
    static final boolean DEBUG_MODE = false;
    public static final int PROGRAM_BEAUTY = 1;
    public static final int PROGRAM_FACE_VAR = 2;
    public static final int PROGRAM_EYE_SCALE = 3;
    public static final int PROGRAM_FACE_SLIM = 4;
    static public final int PROGRAM_BEAUTY2 = 5;
    static public final int PROGRAM_SKIN = 6;
    static public final int PROGRAM_I4202RGBA = 7;
    static public final int PROGRAM_RGBA2I420 = 8;
    static public final int PROGRAM_NV212RGBA = 9;
    static public final int PROGRAM_NV122RGBA = 10;
    static public final int PROGRAM_RGBA2NV21 = 11;
    static public final int PROGRAM_BEAUTYBLEND = 12;
    static public final int PROGRAM_SMOOTHHORIZONTAL = 13;
    static public final int PROGRAM_BEAUTY3_FILTER = 14;
    static public final int PROGRAM_BEAUTY2_SAMSUNG_S4 = 15;

    public static final int NO_TEXTURE = -1;
    public static final int NOT_INIT = -1;
    public static final int ON_DRAWN = 1;

    public static final int OPENGL_ES_3 = 3;  // OpenGL ES 3.0
    public static final int OPENGL_ES_2 = 2;  // OpenGL ES 2.0

    private static float[] FULL_RECTANGLE_COORDS = new float[]{-1.0F, -1.0F, 1.0F, -1.0F, -1.0F, 1.0F, 1.0F, 1.0F};
    private static float[] FULL_RECTANGLE_TEX_COORDS = new float[]{0.0F, 0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F};
    private static float[] FULL_RECTANGLE_TEX_COORDS_CLOCKWISE_90 = new float[]{0.0F, 1.0F, 0.0F, 0.0F, 1.0F, 1.0F, 1.0F, 0.0F};
    private static float[] FULL_RECTANGLE_TEX_COORDS_ANTICLOCKWISE_90 = new float[]{1.0F, 0.0F, 1.0F, 1.0F, 0.0F, 0.0F, 0.0F, 1.0F};
    private static float[] FULL_RECTANGLE_TEX_COORDS_MIRROR = new float[]{1.0F, 0.0F, 0.0F, 0.0F, 1.0F, 1.0F, 0.0F, 1.0F};
    public static FloatBuffer FULL_RECTANGLE_CUB_BUF = createFloatBuffer(FULL_RECTANGLE_COORDS);
    public static FloatBuffer FULL_RECTANGLE_TEX_BUF = createFloatBuffer(FULL_RECTANGLE_TEX_COORDS);
    public static FloatBuffer FULL_RECTANGLE_TEX_BUF_CLOCKWISE_90 = createFloatBuffer(FULL_RECTANGLE_TEX_COORDS_CLOCKWISE_90);
    public static FloatBuffer FULL_RECTANGLE_TEX_BUF_ANTICLOCKWISE_90 = createFloatBuffer(FULL_RECTANGLE_TEX_COORDS_ANTICLOCKWISE_90);
    public static FloatBuffer FULL_RECTANGLE_TEX_BUF_MIRROR = createFloatBuffer(FULL_RECTANGLE_TEX_COORDS_MIRROR);

    public static void setOpenGLVersion(int glVer){
        mOpenGlVERSION = glVer;
    }
    public static final int getOpenGLVersion(){
        return mOpenGlVERSION;
    }

    public static class FrameBufferTag{
        public FrameBufferTag(){
            frameBuffer = null;
            frameBufferTexture = null;
            width = -1;
            height = -1;
        }
        public int[]   frameBuffer;
        public int[]   frameBufferTexture;
        public int     width;
        public int     height;
    };

    public static void releaseFrameBufferList(OpenGlUtils.FrameBufferTag[] mFrameBufferTag){
        if (null != mFrameBufferTag){
            for (OpenGlUtils.FrameBufferTag frameBuf : mFrameBufferTag) {
                if (null != frameBuf){
                    releaseFrameBufferTag(frameBuf);
                    frameBuf = null;
                }
            }
        }
    }

    public static OpenGlUtils.FrameBufferTag[] createFrameBufferList(OpenGlUtils.FrameBufferTag[] frameBufferTag, int size, int width, int height){
        if (null == frameBufferTag){
            frameBufferTag = new OpenGlUtils.FrameBufferTag[size];
        }
        for (int i = 0; i < frameBufferTag.length; i++) {
            frameBufferTag[i] = createFrameBufferTag(frameBufferTag[i], width, height);
        }
        return frameBufferTag;
    }

    public static OpenGlUtils.FrameBufferTag createFrameBufferTag(OpenGlUtils.FrameBufferTag frameBufferTag, int width, int height){
        if (null == frameBufferTag){
            frameBufferTag = new OpenGlUtils.FrameBufferTag();
        }
        if (null == frameBufferTag.frameBuffer){
            frameBufferTag.frameBuffer = new int[1];
        }
        if (null == frameBufferTag.frameBufferTexture){
            frameBufferTag.frameBufferTexture = new int[1];
        }
        frameBufferTag.width = width;
        frameBufferTag.height = height;

        createDrawFrameBuffer(frameBufferTag.frameBuffer, frameBufferTag.frameBufferTexture, frameBufferTag.width, frameBufferTag.height);

        return frameBufferTag;
    }

    public static FrameBufferTag releaseFrameBufferTag(FrameBufferTag mDrawFrameBufTag){
        if (null != mDrawFrameBufTag){
            if (null != mDrawFrameBufTag.frameBuffer){
                GLES20.glDeleteFramebuffers(1, mDrawFrameBufTag.frameBuffer, 0);
                mDrawFrameBufTag.frameBuffer = null;
            }
            if (null != mDrawFrameBufTag.frameBufferTexture){
                GLES20.glDeleteTextures(1, mDrawFrameBufTag.frameBufferTexture, 0);
                mDrawFrameBufTag.frameBufferTexture = null;
            }
            mDrawFrameBufTag = null;
        }
        return mDrawFrameBufTag;
    }

    public static int loadTexture(final Bitmap img, final int usedTexId) {
        return loadTexture(img, usedTexId, true);
    }
    public static int createTexture(int width, int height, int internalformat, int format, int[] textureId)
    {
        //创建纹理
        GLES20.glGenTextures(1, textureId, 0);
        //绑定纹理
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId[0]);
        //设置纹理属性
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, internalformat, width, height, 0, format, GLES20.GL_UNSIGNED_BYTE, null);

        return textureId[0];
    }

    public static FloatBuffer createFloatBuffer(float[] coords) {
        ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(coords);
        fb.position(0);
        return fb;
    }

    public static int createTexture(final int width, final int height, final int internalformat, final int format, final IntBuffer data){
        int texture = getSimpleTextureID();
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, internalformat, width, height, 0, format, GLES20.GL_UNSIGNED_BYTE, data);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return texture;
    }

    public static int createTexture(final int width, final int height, final int internalformat, final int format){
        return createTexture(width, height, internalformat, format, (IntBuffer)null);
    }
	
    public static int loadTexture(final Bitmap img, final int usedTexId, final boolean recycle) {
        int textures[] = new int[1];
        if (usedTexId == NO_TEXTURE) {
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, img, 0);
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, usedTexId);
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, img);
            textures[0] = usedTexId;
        }
        if (recycle) {
            img.recycle();
        }
        return textures[0];
    }

    public static int loadTexture(final ByteBuffer data, int width, int height, final int usedTexId) {
        int textures[] = new int[1];
        if (usedTexId == NO_TEXTURE) {
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height,
                    0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data);
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, usedTexId);
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, width,
                    height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data);
            textures[0] = usedTexId;
        }
        return textures[0];
    }

    public static int createPBO(int width, int height, int[] pb){
        int PBOSIZE = width * height * 4;

        GLES30.glGenBuffers(1, pb, 0);
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pb[0]);
        GLES30.glBufferData(GLES30.GL_PIXEL_PACK_BUFFER, PBOSIZE, null, GLES30.GL_DYNAMIC_READ);

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);

        return pb[0];
    }

    public static int getExternalOESTextureID(){
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        if (DEBUG_MODE)
            Log.e("TXCOpenGLUtils", "check" + texture[0]);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        return texture[0];
    }

    public static void createDrawFrameBuffer(int[] framebuffer, int[] frameTexture, int widht, int height){
        GLES20.glGenFramebuffers(1, framebuffer, 0);
        frameTexture[0] = OpenGlUtils.createTexture(widht, height, GLES20.GL_RGBA, GLES20.GL_RGBA, frameTexture);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, frameTexture[0], 0);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
    }

    public static int getSimpleTextureID(){
        int[] texture = new int[1];
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        return texture[0];
    }

    public static int loadTexture(final IntBuffer data, final Camera.Size size, final int usedTexId) {
        int textures[] = new int[1];
        if (usedTexId == NO_TEXTURE) {
            GLES20.glGenTextures(1, textures, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, size.width, size.height,
                    0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data);
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, usedTexId);
            GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, size.width,
                    size.height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, data);
            textures[0] = usedTexId;
        }
        return textures[0];
    }

    public static int loadTexture(final Context context, final String name){
        final int[] textureHandle = new int[1];

        GLES20.glGenTextures(1, textureHandle, 0);

        if (textureHandle[0] != 0){

            // Read in the resource
            final Bitmap bitmap = getImageFromAssetsFile(context, name);
            if (bitmap == null)
                return textureHandle[0];

            // Bind to the texture in OpenGL
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle[0]);

            // Set filtering
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            // Load the bitmap into the bound texture.
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

            // Recycle the bitmap, since its data has been loaded into OpenGL.
            bitmap.recycle();
        }

        if (textureHandle[0] == 0){
            throw new RuntimeException("Error loading texture.");
        }

        return textureHandle[0];
    }

    public static Bitmap getImageFromAssetsFile(Context context, String fileName){
        Bitmap image = null;
        AssetManager am = context.getResources().getAssets();
        InputStream is = null;
        try{
            is = am.open(fileName);
            image = BitmapFactory.decodeStream(is);
            is.close();
        }catch (IOException e){
            e.printStackTrace();
            try{
                if(is != null)is.close();
            }
            catch (IOException e1){
                e1.printStackTrace();
            }
        }
        return image;
    }

    public static int loadTextureAsBitmap(final IntBuffer data, final Camera.Size size, final int usedTexId) {
        Bitmap bitmap = Bitmap
                .createBitmap(data.array(), size.width, size.height, Bitmap.Config.ARGB_8888);
        return loadTexture(bitmap, usedTexId);
    }

    public static int loadShader(final String strSource, final int iType) {
        int[] compiled = new int[1];
        int iShader = GLES20.glCreateShader(iType);
        GLES20.glShaderSource(iShader, strSource);
        GLES20.glCompileShader(iShader);
        GLES20.glGetShaderiv(iShader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.d("Load Shader Failed", "Compilation\n" + GLES20.glGetShaderInfoLog(iShader));
            return 0;
        }
        return iShader;
    }

    public static int loadProgram(final String strVSource, final String strFSource) {
        int iVShader;
        int iFShader;
        int iProgId;
        int[] link = new int[1];
        iVShader = loadShader(strVSource, GLES20.GL_VERTEX_SHADER);
        if (iVShader == 0) {
            Log.d("Load Program", "Vertex Shader Failed");
            return 0;
        }
        iFShader = loadShader(strFSource, GLES20.GL_FRAGMENT_SHADER);
        if (iFShader == 0) {
            Log.d("Load Program", "Fragment Shader Failed");
            return 0;
        }

        iProgId = GLES20.glCreateProgram();

        GLES20.glAttachShader(iProgId, iVShader);
        GLES20.glAttachShader(iProgId, iFShader);

        GLES20.glLinkProgram(iProgId);

        GLES20.glGetProgramiv(iProgId, GLES20.GL_LINK_STATUS, link, 0);
        if (link[0] <= 0) {
            Log.d("Load Program", "Linking Failed");
            return 0;
        }
        GLES20.glDeleteShader(iVShader);
        GLES20.glDeleteShader(iFShader);
        return iProgId;
    }

    public static float rnd(final float min, final float max) {
        float fRandNum = (float) Math.random();
        return min + (max - min) * fRandNum;
    }

    /**
     * Checks to see if a GLES error has been raised.
     */
    public static void checkGlError(String op) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String msg = op + ": glError 0x" + Integer.toHexString(error);
            Log.e("OpenGlUtils", msg);
            throw new RuntimeException(msg);
        }
    }

    public static String readShaderFromRawResource(Context context, final int resourceId){
        final InputStream inputStream = context.getResources().openRawResource(
                resourceId);
        final InputStreamReader inputStreamReader = new InputStreamReader(
                inputStream);
        final BufferedReader bufferedReader = new BufferedReader(
                inputStreamReader);

        String nextLine;
        final StringBuilder body = new StringBuilder();

        try{
            while ((nextLine = bufferedReader.readLine()) != null){
                body.append(nextLine);
                body.append('\n');
            }
        }
        catch (IOException e){
            return null;
        }
        return body.toString();
    }

    public static final android.opengl.EGLContext getEGL14Context(){
        return EGL14.eglGetCurrentContext();
    }

    private static int mOpenGlVERSION = OpenGlUtils.OPENGL_ES_2;
}