package com.alopatindev.videoacid.ui

class MainView(context: Context) extends GLSurfaceView(context) {

  private val renderer = new MainRenderer(this)

  setEGLContextClientVersion(2)
  setRenderer(renderer)
  setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)

  override def surfaceCreated(holder: SurfaceHolder): Unit = {
    super.surfaceCreated(holder)
  }
 
  override def surfaceDestroyed(holder: SurfaceHolder): Unit = {
    mRenderer.close()
    super.surfaceDestroyed(holder)
  }
 
  override def surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int): Unit = {
    super.surfaceChanged(holder, format, width, height)
  }

}
