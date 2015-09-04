package com.alopatindev.videoacid.ui

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class MainView(context: Context, attrs: AttributeSet) extends GLSurfaceView(context, attrs) {

  import android.view.SurfaceHolder

  import com.alopatindev.videoacid.Logs.logd

  private val renderer = new MainRenderer(this)

  setEGLContextClientVersion(2)
  setPreserveEGLContextOnPause(true)
  setRenderer(renderer)
  setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY)

  override def surfaceCreated(holder: SurfaceHolder): Unit = {
    logd("MainView.surfaceCreated")
    super.surfaceCreated(holder)
  }

  override def surfaceDestroyed(holder: SurfaceHolder): Unit = {
    logd("MainView.surfaceDestroyed")
    renderer.stopCamera()
    super.surfaceDestroyed(holder)
  }

  override def surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int): Unit = {
    logd("MainView.surfaceChanged")
    super.surfaceChanged(holder, format, width, height)
  }

  override def onMeasure(width: Int, height: Int): Unit = {
    logd(s"MainView.onMeasure $width, $height")
    super.onMeasure(width, height)
    val size: Int = Math.min(getMeasuredWidth(), getMeasuredHeight())
    setMeasuredDimension(size, size)
  }

  def release(): Unit = {
    logd("MainView.release")
    renderer.release()
  }

}
