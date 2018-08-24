package com.ws.videoview.videoview.opengl;


import android.annotation.TargetApi;
import android.opengl.*;
import android.util.Log;
import android.view.Surface;


@TargetApi(17)
public class EGL14Helper
{
    public static EGL14Helper createEGLSurface(EGLConfig config, EGLContext context, Surface surface, int width, int height ){
        EGL14Helper egl = new EGL14Helper();
        egl.mWidth = width;
        egl.mHeight = height;
        if (egl.initEGLSurface(config, context, surface)){
            return egl;
        }
        else{
            return null;
        }
    }

    public static EGL14Helper createFromCurrentContext(){
        EGL14Helper egl = new EGL14Helper();
        egl.mEGLDisplay = EGL14.eglGetCurrentDisplay();
        egl.mEGLContext = EGL14.eglGetCurrentContext();
        egl.mEGLSurface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW | EGL14.EGL_READ);
        egl.cpEGL();
        if(egl.mEGLDisplay == EGL14.EGL_NO_DISPLAY || egl.mEGLContext == EGL14.EGL_NO_CONTEXT || egl.mEGLSurface == EGL14.EGL_NO_SURFACE){
            return null;
        }
        else return egl;
    }

    public void cpEGL(){
        int ec = EGL14.eglGetError();
        if (ec != EGL14.EGL_SUCCESS){
            Log.e(TAG,"EGL error:" + ec);
            throw new RuntimeException(": EGL error: 0x" + Integer.toHexString(ec));
        }
    }

    public void makeCurrent() {
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            // called makeCurrent() before create?
            Log.d(TAG, "NOTE: makeCurrent w/o display");
        }
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface, mEGLContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }
    }

    public void release() {
        if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
            // Android is unusual in that it uses a reference-counted EGLDisplay.  So for
            // every eglInitialize() we need an eglTerminate().
            EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT);
            EGL14.eglDestroySurface(mEGLDisplay, mEGLSurface);
            EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
            mEGLContext = EGL14.EGL_NO_CONTEXT;
            EGL14.eglReleaseThread();
            // TODO: 2016/10/14 for Samsumg S4, EGL14.eglReleaseThread() cause CRASH!
            EGL14.eglTerminate(mEGLDisplay);
        }
        mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    }

    public boolean swap() {
        return EGL14.eglSwapBuffers(mEGLDisplay, mEGLSurface);
    }

    private boolean initEGLSurface(EGLConfig config, EGLContext context, Surface surface){
        mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new RuntimeException("unable to get EGL14 display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
            mEGLDisplay = null;
            throw new RuntimeException("unable to initialize EGL14");
        }
        if(config != null){
            mEGLConfig = config;
        }
        else{
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!EGL14.eglChooseConfig(mEGLDisplay, surface == null ? configSpecBuffer : configSpecWindow, 0, configs, 0, configs.length,
                    numConfigs, 0)) {
                return false;
            }
            mEGLConfig = configs[0];
        }
        if(context != null){
            mSharedContext = true;
        }
        else{
            context = EGL14.EGL_NO_CONTEXT;
        }
        int[] attrib_list = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, GLESVERSION,
                EGL14.EGL_NONE
        };
        mEGLContext = EGL14.eglCreateContext(mEGLDisplay, mEGLConfig, context,
                attrib_list, 0);
        int[] surfaceAttribs = {
                EGL14.EGL_NONE
        };

        if (mEGLContext == EGL14.EGL_NO_CONTEXT){
            cpEGL();
            return false;
        }
        if(surface == null){
            int attribListPbuffer[] = {
                    EGL14.EGL_WIDTH, mWidth,
                    EGL14.EGL_HEIGHT, mHeight,
                    EGL14.EGL_NONE
            };
            mEGLSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, mEGLConfig, attribListPbuffer, 0);
        }
        else mEGLSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, mEGLConfig, surface,surfaceAttribs, 0);
        cpEGL();
        if (!EGL14.eglMakeCurrent(mEGLDisplay, mEGLSurface, mEGLSurface,mEGLContext)){
            cpEGL();
            return false;
        }
        return true;
    }

    public void setPresentationTime(long nsecs) {
        EGLExt.eglPresentationTimeANDROID(mEGLDisplay, mEGLSurface, nsecs);
    }

    public EGLContext getContext(){
        return mEGLContext;
    }

    public EGLConfig getConfig(){
        return mEGLConfig;
    }

    private static final int EGL_RECORDABLE_ANDROID = 0x3142;
    private static int GLESVERSION = 2;
    private static final String TAG = EGL14Helper.class.getSimpleName();
    private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
    private EGLConfig mEGLConfig = null;
    private int                     mWidth = 0;
    private int                     mHeight = 0;
    private boolean                 mSharedContext;
    private EGLSurface mEGLSurface;
    private int                     mGlVersion = -1;


    private static int[] configSpecWindow ={
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_RENDERABLE_TYPE, GLESVERSION == 2 ? EGL14.EGL_OPENGL_ES2_BIT : EGL14.EGL_OPENGL_ES2_BIT| EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
    };

    private static int[] configSpecBuffer ={
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,//前台显示Surface这里EGL10.EGL_WINDOW_BIT
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_DEPTH_SIZE, 0,
            EGL14.EGL_STENCIL_SIZE, 0,
            EGL14.EGL_RENDERABLE_TYPE, GLESVERSION == 2 ? EGL14.EGL_OPENGL_ES2_BIT : EGL14.EGL_OPENGL_ES2_BIT| EGLExt.EGL_OPENGL_ES3_BIT_KHR,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
    };

}
