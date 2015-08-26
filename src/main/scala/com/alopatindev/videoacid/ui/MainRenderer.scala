package com.alopatindev.videoacid.ui

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.hardware.Camera
import android.opengl.GLSurfaceView
import android.opengl.GLES11Ext
import android.opengl.GLES20

import com.alopatindev.videoacid.{ApproxRandomizer, Utils}

//import rx.lang.scala._

//import language.postfixOps

//import scala.concurrent.duration._
import scala.util.Try

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainRenderer(val view: MainView) extends Object
                                       with GLSurfaceView.Renderer
                                       with SurfaceTexture.OnFrameAvailableListener {

  import android.content.Context

  import com.alopatindev.videoacid.Logs._

  implicit val ctx: Context = view.getContext()

  private lazy val vertMainShader: Option[String] = Utils.loadAsset("main.vert")
  private lazy val fragMainShader: Option[String] = Utils.loadAsset("main.frag")
  private lazy val fragMonochromeShader: Option[String] = Utils.loadAsset("monochrome.frag")
  private var mainShaderProgram: Int = 0
  private var monochromeShaderProgram: Int = 0

  private val texture: Array[Int] = Array(0)
 
  @volatile private var camera: Option[Camera] = None
  private val cameraAngle: Float = -0.5f * Math.PI.toFloat

  private var surfaceTexture: Option[SurfaceTexture] = None
  //private lazy val screenRatio: Float = view.getHeight().toFloat / view.getWidth().toFloat
  private lazy val screenRatio = 1.0f

  private lazy val vertsApproxRandomizer = new ApproxRandomizer(
    originalVector = Vector(1.0f,-1.0f, -1.0f,-1.0f, 1.0f,1.0f, -1.0f,1.0f),
    factor = 1.5f,
    speed = 2.0f,
    updateInterval = 30L,
    randUpdateInterval = 2000L
  )

  private lazy val lightColorChangeApproxRandomizer = new ApproxRandomizer(
    originalVector = Vector(0.6f, 0.4f, 0.2f),
    factor = 55.0f,
    speed = 555.0f,
    updateInterval = 30L,
    randUpdateInterval = 2000L
  )

  private lazy val darkColorChangeApproxRandomizer = new ApproxRandomizer(
    originalVector = Vector(0.8f, 0.4f, 0.4f),
    factor = 6.7f,
    speed = 50.0f,
    updateInterval = 30L,
    randUpdateInterval = 4000L
  )

  private val verts: FloatBuffer = ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
  updateVerts()

  private val uvCoords: FloatBuffer = ByteBuffer.allocateDirect(8*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
  uvCoords.put(Array(1.0f,1.0f, 0.0f,1.0f, 1.0f,0.0f, 0.0f,0.0f))
  uvCoords.position(0)

  private def startCamera(surfaceWidth: Int, surfaceHeight: Int): Unit = {
    logi("startCamera thread=" + Thread.currentThread.getId())
    logi(s"startCamera ${surfaceWidth} ${surfaceHeight}")

    Try {
      camera = Some(Camera.open())
      camera foreach { cam => {
        cam.lock()
        surfaceTexture foreach { cam.setPreviewTexture(_) }
      }}
    }

    camera foreach { cam => {
      import scala.collection.JavaConversions._
      val param = cam.getParameters()
      val bestCameraSize = param.getSupportedPreviewSizes().filter(item => item.width >= surfaceWidth || item.height >= surfaceHeight).last
      //val bestCameraSize = param.getSupportedPreviewSizes().sortBy(_.width).reverse.head
      //val bestMinCameraSize = Math.min(bestCameraSize.width, bestCameraSize.height)
      //logi(s"using width=${bestMinCameraSize}")
      logi(s"using width=${bestCameraSize.width} height=${bestCameraSize.height}")
      param.set("orientation", "portrait")
      //param.setPreviewSize(bestMinCameraSize, bestMinCameraSize)
      param.setPreviewSize(bestCameraSize.width, bestCameraSize.height)
      param.setFocusMode("continuous-video")
      cam.setParameters(param)
      cam.startPreview()
    }}
  }

  def stopCamera(): Unit = {
    logi("stopCamera thread=" + Thread.currentThread.getId())
    camera foreach { cam => {
      cam.setPreviewTexture(null)
      cam.stopPreview()
      cam.release()
    }}
    camera = None
  }

  def release(): Unit = {
    logi("MainRenderer.release thread=" + Thread.currentThread.getId())
    stopCamera()
    surfaceTexture foreach { _.release() }
    deleteTex()
  }
 
  override def onSurfaceCreated(unused: GL10, config: EGLConfig): Unit = {
    logd("onSurfaceCreated thread=" + Thread.currentThread.getId())

    initTexture()
    surfaceTexture = Some(new SurfaceTexture(texture(0)))
    surfaceTexture foreach { _.setOnFrameAvailableListener(this) }

    //GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f)
    GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f)

    mainShaderProgram = loadShader(vertMainShader, fragMainShader)
    monochromeShaderProgram = loadShader(vertMainShader, fragMonochromeShader)

    val vPosition: Int = GLES20.glGetAttribLocation(mainShaderProgram, "vPosition")
    val vTexCoord: Int = GLES20.glGetAttribLocation(mainShaderProgram, "vTexCoord")

    GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, verts)
    GLES20.glVertexAttribPointer(vTexCoord, 2, GLES20.GL_FLOAT, false, 0, uvCoords)
    GLES20.glEnableVertexAttribArray(vPosition)
    GLES20.glEnableVertexAttribArray(vTexCoord)

    val sTexture: Int = GLES20.glGetUniformLocation(mainShaderProgram, "sTexture")
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture(0))
    GLES20.glUniform1i(sTexture, 0)
  }
 
  override def onDrawFrame(unused: GL10): Unit = {
    def drawNormal(): Unit = {
      GLES20.glUseProgram(mainShaderProgram)

      val fAngle: Int = GLES20.glGetUniformLocation(mainShaderProgram, "fAngle")
      GLES20.glUniform1f(fAngle, cameraAngle)

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    def drawLightMonochrome(): Unit = {
      GLES20.glUseProgram(monochromeShaderProgram)

      val vOtherColor: Int = GLES20.glGetUniformLocation(monochromeShaderProgram, "vOtherColor")
      val otherColor: Vector[Float] = lightColorChangeApproxRandomizer.getCurrentVector()
      GLES20.glUniform3f(vOtherColor, otherColor(0), otherColor(1), otherColor(2))

      val fLow: Int = GLES20.glGetUniformLocation(monochromeShaderProgram, "fLow")
      val fHigh: Int = GLES20.glGetUniformLocation(monochromeShaderProgram, "fHigh")
      GLES20.glUniform1f(fLow, 0.51f)
      GLES20.glUniform1f(fHigh, 1.0f)

      val fAngle: Int = GLES20.glGetUniformLocation(monochromeShaderProgram, "fAngle")
      GLES20.glUniform1f(fAngle, cameraAngle)

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    def drawDarkMonochrome(): Unit = {
      GLES20.glUseProgram(monochromeShaderProgram)

      val vOtherColor: Int = GLES20.glGetUniformLocation(monochromeShaderProgram, "vOtherColor")
      val otherColor: Vector[Float] = darkColorChangeApproxRandomizer.getCurrentVector()
      GLES20.glUniform3f(vOtherColor, otherColor(0), otherColor(1), otherColor(2))

      val fLow: Int = GLES20.glGetUniformLocation(monochromeShaderProgram, "fLow")
      val fHigh: Int = GLES20.glGetUniformLocation(monochromeShaderProgram, "fHigh")
      GLES20.glUniform1f(fLow, 0.0f)
      GLES20.glUniform1f(fHigh, 0.5f)

      val fAngle: Int = GLES20.glGetUniformLocation(monochromeShaderProgram, "fAngle")
      GLES20.glUniform1f(fAngle, cameraAngle)

      GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    def drawAll(): Unit = {
      GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

      surfaceTexture foreach { _.updateTexImage() }

      updateVerts()
      updateOtherColors()

      drawNormal()

      GLES20.glEnable(GLES20.GL_BLEND)
      configureBlending()

      drawLightMonochrome()
      drawDarkMonochrome()

      GLES20.glDisable(GLES20.GL_BLEND)

      //GLES20.glFlush()
      //GLES20.glFinish()
    }

    drawAll()
  }

  private def configureBlending(): Unit = {
    //GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    //GLES20.glDepthMask(false)
    //GLES20.glClearDepthf(1.0f)
    //GLES20.glHint(GLES20.GL_POLYGON_SMOOTH, GLES20.GL_NICEST)
    //GLES20.glCullFace(GLES20.GL_BACK)
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    GLES20.glDepthMask(false)
    GLES20.glClearDepthf(1.0f)
    GLES20.glCullFace(GLES20.GL_BACK)
  }

  private def updateVerts(): Unit = {
    vertsApproxRandomizer.update()
    verts.put(vertsApproxRandomizer.getCurrentArray())
    verts.position(0)
  }

  def updateOtherColors(): Unit = {
    lightColorChangeApproxRandomizer.update()
    darkColorChangeApproxRandomizer.update()
  }

  override def onSurfaceChanged(unused: GL10, width: Int, height: Int): Unit = {
    logd(s"onSurfaceChanged")

    startCamera(width, height)

    GLES20.glViewport(0, 0, width, height)
  }
 
  private def initTexture(): Unit = {
    logd("initTexture")

    GLES20.glGenTextures(1, texture, 0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture(0))

    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

    //GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
    //GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
  }
 
  private def deleteTex(): Unit = {
    GLES20.glDeleteTextures(1, texture, 0)
  }
 
  override def onFrameAvailable(st: SurfaceTexture): Unit = {
    view.requestRender()
  }
 
  private def loadShader(vss: Option[String], fss: Option[String]): Int = {
    var vshader: Int = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
    vss foreach { vss => GLES20.glShaderSource(vshader, vss) }
    GLES20.glCompileShader(vshader)

    var compiled = Array(0)
    GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, compiled, 0)
    if (compiled(0) == 0) {
      loge(s"Could not compile vshader: ${GLES20.glGetShaderInfoLog(vshader)}")
      GLES20.glDeleteShader(vshader)
      vshader = 0
    }
  
    var fshader: Int = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
    fss foreach { fss => GLES20.glShaderSource(fshader, fss) }
    GLES20.glCompileShader(fshader)
    GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0)
    if (compiled(0) == 0) {
      loge(s"Could not compile fshader: ${GLES20.glGetShaderInfoLog(fshader)}")
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
