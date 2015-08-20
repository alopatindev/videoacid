package com.alopatindev.videoacid.ui

class MainView extends GLSurfaceView {

  private val renderer = new MainRenderer(this)

  def this(context: Context) = {
    super(context)
    setEGLContextClientVersion(2)
    setRenderer(renderer)
    setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)
  }

  def surfaceCreated(SurfaceHolder holder): Unit = {
    super.surfaceCreated(holder);
  }
 
  def surfaceDestroyed(SurfaceHolder holder): Unit = {
    mRenderer.close();
    super.surfaceDestroyed(holder);
  }
 
  def surfaceChanged(SurfaceHolder holder, int format, int w, int h): Unit = {
    super.surfaceChanged(holder, format, w, h);
  }

}
