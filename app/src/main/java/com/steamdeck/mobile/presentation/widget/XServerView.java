package com.steamdeck.mobile.presentation.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.steamdeck.mobile.presentation.renderer.GLRenderer;
import com.steamdeck.mobile.core.xserver.XServer;

@SuppressLint("ViewConstructor")
public class XServerView extends GLSurfaceView {
    private final GLRenderer renderer;

    public XServerView(Context context, XServer xServer) {
        super(context);
        android.util.Log.e("XServerView", "Constructor called");
        setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 0, 0);
        setPreserveEGLContextOnPause(true);
        renderer = new GLRenderer(this, xServer);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        android.util.Log.e("XServerView", "Renderer set, renderMode=CONTINUOUSLY");
    }

    @Override
    public void onResume() {
        android.util.Log.e("XServerView", "onResume() called");
        super.onResume();
    }

    @Override
    public void onPause() {
        android.util.Log.e("XServerView", "onPause() called");
        super.onPause();
    }

    public GLRenderer getRenderer() {
        return renderer;
    }
}
