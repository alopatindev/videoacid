package com.alopatindev.videoacid.ui

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.hardware.Camera
import android.hardware.Camera._
import android.opengl.GLSurfaceView
import android.opengl.GLES11Ext
import android.opengl.GLES20

import rx.lang.scala._

import language.postfixOps

import scala.concurrent.duration._
import scala.util.{Random, Try}

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainRenderer(val view: MainView) extends Object
                                       with GLSurfaceView.Renderer
                                       with SurfaceTexture.OnFrameAvailableListener {

  import com.alopatindev.videoacid.Logs._

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
      "  gl_FragColor = texture2D(sTexture, texCoord);\n" +
      "}"

  private val hTex: Array[Int] = Array(0)
  private var hProgram: Int = 0
 
  private var camera: Option[Camera] = None
  private var surfaceTexture: Option[SurfaceTexture] = None
 
  @volatile private var surfaceDirty = false
  val RAND_VERTS_UPDATE_INTERVAL = 2000L * DISTORTION_SPEED.toLong
  val VERTS_UPDATE_INTERVAL = 30L
  var nextRandVertsUpdateTime = 0L
  var nextVertsUpdateTime = 0L

  lazy val rand = new Random()

  private val SMOOTH = 0.001f
  private val DISTORTION_FACTOR = 1.5f
  private val DISTORTION_SPEED = 5.0f
  private val pVertex: FloatBuffer = ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
  private val originalVerts = List(1.0f, -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f) map { DISTORTION_FACTOR / _ }
  private var nextRandVerts = originalVerts
  private var currentVerts = originalVerts
  updateVerts()

  def approxVertsStep(current: List[Float], next: List[Float], step: Float = SMOOTH * DISTORTION_SPEED): List[Float] =
    (current zip next) map { case (c, n) => {
      val acc = if (c < n) step else -step
      val next = c + acc
      val found = Math.abs(next - n) <= step
      if (found) c
      else next
    }}

  private val pTexCoord: FloatBuffer = ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
  pTexCoord.put(Array(1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 0.0f, 0.0f, 0.0f))
  pTexCoord.position(0)
 
  def close(): Unit = {
    surfaceDirty = false
    surfaceTexture foreach { _.release() }
    camera foreach { cam => {
      cam.stopPreview()
      cam.release()
    }}
    camera = None
    deleteTex()
  }
 
  override def onSurfaceCreated(unused: GL10, config: EGLConfig): Unit = {
    initTex()
    surfaceTexture = Some(new SurfaceTexture(hTex(0)))
    surfaceTexture foreach { _.setOnFrameAvailableListener(this) }

    Try {
      camera = Some(Camera.open())
      camera foreach { cam => surfaceTexture foreach { cam.setPreviewTexture(_) } }
    }

    GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f)
  
    hProgram = loadShader(vss, fss)
  }
 
  override def onDrawFrame(unused: GL10): Unit = {
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

    updateVerts()

    GLES20.glVertexAttribPointer(ph, 2, GLES20.GL_FLOAT, false, 4*2, pVertex)
    GLES20.glVertexAttribPointer(tch, 2, GLES20.GL_FLOAT, false, 4*2, pTexCoord)
    GLES20.glEnableVertexAttribArray(ph)
    GLES20.glEnableVertexAttribArray(tch)
  
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    GLES20.glFlush()
  }

  private def updateVerts(): Unit = {
    val currentTime = System.currentTimeMillis()
    val dirtyRandVerts = (currentTime - nextRandVertsUpdateTime) > 0L
    lazy val dirtyVerts = (currentTime - nextVertsUpdateTime) > 0L

    if (dirtyRandVerts) {
      nextRandVerts = originalVerts map { x => x + (rand.nextFloat() - 0.5f) * DISTORTION_FACTOR }
      nextRandVertsUpdateTime = currentTime + RAND_VERTS_UPDATE_INTERVAL + rand.nextLong() % 1000L
    } else if (dirtyVerts) {
      currentVerts = approxVertsStep(current = currentVerts, next = nextRandVerts)
      pVertex.put(currentVerts.toArray)
      pVertex.position(0)
      nextVertsUpdateTime = currentTime + VERTS_UPDATE_INTERVAL
    }
  }

  override def onSurfaceChanged(unused: GL10, width: Int, height: Int): Unit = {
    GLES20.glViewport(0, 0, width, height)

    camera foreach { cam => {
      val param = cam.getParameters()
      val psize = param.getSupportedPreviewSizes()
      val sizes = for {
        i <- 0 until psize.size()
        item = psize.get(i)
        if (item.width < width || item.height < height)
      } yield item
      val size = sizes.last
      param.setPreviewSize(size.width, size.height)
      param.set("orientation", "landscape")
      param.setFocusMode("continuous-video")
      cam.setParameters(param)
      cam.startPreview()
    }}
  }
 
  private def initTex(): Unit = {
    GLES20.glGenTextures(1, hTex, 0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, hTex(0))
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
  }
 
  private def deleteTex(): Unit = {
    GLES20.glDeleteTextures(1, hTex, 0)
  }
 
  override def onFrameAvailable(st: SurfaceTexture): Unit = {
    surfaceDirty = true
    view.requestRender()
  }
 
  private def loadShader(vss: String, fss: String): Int = {
    var vshader: Int = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
    GLES20.glShaderSource(vshader, vss)
    GLES20.glCompileShader(vshader)
    var compiled = Array(0)
    GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, compiled, 0)
    if(compiled(0) == 0) {
      loge("Could not compile vshader")
      logd("Could not compile vshader:"+GLES20.glGetShaderInfoLog(vshader))
      GLES20.glDeleteShader(vshader)
      vshader = 0
    }
  
    var fshader: Int = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
    GLES20.glShaderSource(fshader, fss)
    GLES20.glCompileShader(fshader)
    GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0)
    if (compiled(0) == 0) {
      loge("Could not compile fshader")
      logd("Could not compile fshader:"+GLES20.glGetShaderInfoLog(fshader))
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
