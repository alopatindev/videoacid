package com.alopatindev.videoacid.ui

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class MainView(context: Context, attrs: AttributeSet) extends GLSurfaceView(context, attrs) {

  import android.view.SurfaceHolder

  private val renderer = new MainRenderer(this)

  setEGLContextClientVersion(2)
  setPreserveEGLContextOnPause(true)
  setRenderer(renderer)
  setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)

  override def surfaceCreated(holder: SurfaceHolder): Unit = {
    super.surfaceCreated(holder)
  }
 
  override def surfaceDestroyed(holder: SurfaceHolder): Unit = {
    renderer.stopCamera()
    super.surfaceDestroyed(holder)
  }
 
  override def surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int): Unit = {
    super.surfaceChanged(holder, format, width, height)
  }

}
