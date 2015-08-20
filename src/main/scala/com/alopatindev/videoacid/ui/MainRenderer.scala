package com.alopatindev.videoacid.ui

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.SurfaceTexture.OnFrameAvailableListener
import android.opengl.EGLConfig
import android.opengl.GLSurfaceView
import android.opengl.GLES20

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.opengles.GL10

class MainRenderer(val view: MainView) with GLSurfaceView.Renderer
                                       with SurfaceTexture.OnFrameAvailableListener {
  private lazy val vss =
      "attribute vec2 vPosition;\n" +
      "attribute vec2 vTexCoord;\n" +
      "varying vec2 texCoord;\n" +
      "void main() {\n" +
      "  texCoord = vTexCoord;\n" +
      "  gl_Position = vec4(vPosition.x, vPosition.y, 0.0, 1.0);\n" +
      "}"
 
  private lazy val fss =
      "#extension GL_OES_EGL_image_external : require\n" +
      "precision mediump float;\n" +
      "uniform samplerExternalOES sTexture;\n" +
      "varying vec2 texCoord;\n" +
      "void main() {\n" +
      "  gl_FragColor = texture2D(sTexture,texCoord);\n" +
      "}"

  private lazy val hTex: Array[Int] = initTex()
  private lazy val hProgram: Int = loadShader(vss, fss)
 
  private var camera: Option[Camera] = None
  private var surfaceTexture: Option[SurfaceTexture] = None
 
  private @volatile var surfaceDirty = false

  val pVertex: FloatBuffer = ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
  val pTexCoord: FloatBuffer = ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
  pVertex.put(Array(1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f))
  pVertex.position(0)
  pTexCoord.put(Array(1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f))
  pTexCoord.position(0)
 
  def close(): Unit = {
    surfaceDirty = false
    surfaceTexture foreach { _.release() }
    camera.stopPreview()
    camera = None
    deleteTex()
  }
 
  def onSurfaceCreated(unused: GL10, config: EGLConfig): Unit = {
    //String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS)
    //Log.i("mr", "Gl extensions: " + extensions)
    //Assert.assertTrue(extensions.contains("OES_EGL_image_external"))
        
    surfaceTexture = Some(new SurfaceTexture(hTex(0)))
    surfaceTexture foreach { _.setOnFrameAvailableListener(this) }

    camera = Some(Camera.open())
    try {
      camera foreach { cam => surfaceTexture foreach { cam.setPreviewTexture(_) } }
    } catch(IOException ioe) {
    }

    GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f)
  
    //hProgram = loadShader(vss, fss)
  }
 
  def onDrawFrame(unused: GL10): Unit = {
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
  
    if (surfaceDirty) {
      surfaceTexture foreach { _.updateTexImage() }
      surfaceDirty = false
    }

    GLES20.glUseProgram(hProgram)

    val ph: Int = GLES20.glGetAttribLocation(hProgram, "vPosition")
    val tch: Int = GLES20.glGetAttribLocation(hProgram, "vTexCoord")
    val th: Int = GLES20.glGetUniformLocation(hProgram, "sTexture")
  
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex(0))
    GLES20.glUniform1i(th, 0)

    GLES20.glVertexAttribPointer(ph, 2, GLES20.GL_FLOAT, false, 4*2, pVertex)
    GLES20.glVertexAttribPointer(tch, 2, GLES20.GL_FLOAT, false, 4*2, pTexCoord)
    GLES20.glEnableVertexAttribArray(ph)
    GLES20.glEnableVertexAttribArray(tch)
  
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    GLES20.glFlush()
  }
 
  def onSurfaceChanged(unused: GL10, width: Int, height: Int): Unit = {
    GLES20.glViewport(0, 0, width, height)
    Camera.Parameters param = camera.getParameters()
    val psize = param.getSupportedPreviewSizes()
    val sizes = for {
      i <- 0 until psize.size()
      item = psize.get(i)
      if (item.width < width || item.height < height)
    } yield item
    val size = sizes.last
    param.setPreviewSize(size.width, size.height)
    param.set("orientation", "landscape")
    camera.setParameters(param)
    camera.startPreview()
  }
 
  private def initTex(): Array[Int] = {
    val hTex = Array(0)
    GLES20.glGenTextures(1, hTex, 0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex(0))
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
    hTex
  }
 
  private def deleteTex(): Unit = {
    GLES20.glDeleteTextures(1, hTex, 0)
  }
 
  def onFrameAvailable(st: SurfaceTexture): Unit = {
    surfaceDirty = true
    view.requestRender()
  }
 
  private def loadShader(vss: String, fss: String): Int = {
    var vshader: Int = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
    GLES20.glShaderSource(vshader, vss)
    GLES20.glCompileShader(vshader)
    val compiled = Array(0)
    GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, compiled, 0)
    if(compiled(0) == 0) {
      Log.e("Shader", "Could not compile vshader")
      Log.v("Shader", "Could not compile vshader:"+GLES20.glGetShaderInfoLog(vshader))
      GLES20.glDeleteShader(vshader)
      vshader = 0
    }
  
    val fshader: Int = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
    GLES20.glShaderSource(fshader, fss)
    GLES20.glCompileShader(fshader)
    GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0)
    if(compiled(0) == 0) {
      Log.e("Shader", "Could not compile fshader")
      Log.v("Shader", "Could not compile fshader:"+GLES20.glGetShaderInfoLog(fshader))
      GLES20.glDeleteShader(fshader)
      fshader = 0
    }

    val program: Int = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vshader)
    GLES20.glAttachShader(program, fshader)
    GLES20.glLinkProgram(program)
        
    program
  }

}
