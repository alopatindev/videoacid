package com.alopatindev.videoacid.ui

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.hardware.Camera
import android.opengl.GLSurfaceView
import android.opengl.GLES11Ext
import android.opengl.GLES20

import com.alopatindev.videoacid.{ApproxRandomizer, Utils}

import language.postfixOps

import scala.concurrent.duration._  // scalastyle:ignore
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

  import com.alopatindev.videoacid.ConcurrencyUtils
  import com.alopatindev.videoacid.Logs.{logd, loge, logi}

  implicit val ctx: Context = view.getContext()

  @volatile var frameAvailable: Boolean = false

  object ShaderInputs {
    val position: String = "vPosition"
    val texCoord: String = "vTexCoord"
    val texture: String = "sTexture"
    val angle: String = "fAngle"
    val otherColor: String = "vOtherColor"
    val low: String = "fLow"
    val high: String = "fHigh"
    val madness: String = "fMadness"
  }

  private lazy val vertMainShader: Option[String] = Utils.loadAsset("main.vert")
  private lazy val fragMainShader: Option[String] = Utils.loadAsset("main.frag")
  private lazy val fragMonochromeShader: Option[String] = Utils.loadAsset("monochrome.frag")
  private var mainShaderProgram: Int = 0
  private var monochromeShaderProgram: Int = 0

  private val texture: Array[Int] = Array(0)

  @volatile private var camera: Option[Camera] = None
  private val cameraAngle: Float = -0.5f * Math.PI.toFloat

  private var surfaceTexture: Option[SurfaceTexture] = None

  private val lightMonochromeColorRange: (Float, Float) = (0.51f, 1.0f)
  private val darkMonochromeColorRange: (Float, Float) = (0.0f, 0.5f)

  private val screenRandFactor = 3.0f
  private val screenBounds = Vector(1.0f,-1.0f, -1.0f,-1.0f, 1.0f,1.0f, -1.0f,1.0f)
  private lazy val vertsApproxRandomizer = new ApproxRandomizer(
    minVector = screenBounds map { x => if (x < 0.0f) x * screenRandFactor else x },
    maxVector = screenBounds,
    speed = 20.0f,
    updateInterval = 30 millis,
    randUpdateInterval = 5 seconds
  )

  private lazy val lightColorChangeApproxRandomizer = new ApproxRandomizer(
    minVector = Vector(0.0f, 0.0f, 0.0f),
    maxVector = Vector(1.0f, 0.8f, 1.0f),
    speed = 10.0f,
    updateInterval = 30 millis,
    randUpdateInterval = 2 seconds
  )

  private lazy val darkColorChangeApproxRandomizer = new ApproxRandomizer(
    minVector = Vector(0.0f, 0.0f, 0.0f),
    maxVector = Vector(1.0f, 0.8f, 1.0f),
    speed = 10.5f,
    updateInterval = 30 millis,
    randUpdateInterval = 4 seconds
  )

  private val VERTS_NUMBER = 8
  private val BYTES_PER_FLOAT = 4
  private def newFloatBuffer(size: Int) = ByteBuffer
    .allocateDirect(size * BYTES_PER_FLOAT)
    .order(ByteOrder.nativeOrder)
    .asFloatBuffer()

  private val verts: FloatBuffer = newFloatBuffer(VERTS_NUMBER)
  updateVerts()

  private val uvCoords: FloatBuffer = newFloatBuffer(VERTS_NUMBER)
  uvCoords.put(Array(1.0f,1.0f, 0.0f,1.0f, 1.0f,0.0f, 0.0f,0.0f))
  uvCoords.position(0)

  private def startCamera(surfaceWidth: Int, surfaceHeight: Int): Unit = {
    logi(s"startCamera thread=${ConcurrencyUtils.currentThreadId()} (${surfaceWidth}x${surfaceHeight})")

    Try {
      val cameraOpt = Some(Camera.open())
      cameraOpt foreach { cam => {
        import scala.collection.JavaConversions._  // scalastyle:ignore

        cam.lock()
        surfaceTexture foreach { cam.setPreviewTexture(_) }

        val param = cam.getParameters()
        val bestCameraSize = param
          .getSupportedPreviewSizes()
          .filter(item => item.width >= surfaceWidth || item.height >= surfaceHeight)
          .last
        logi(s"using width=${bestCameraSize.width} height=${bestCameraSize.height}")

        param.set("orientation", "portrait")
        param.setPreviewSize(bestCameraSize.width, bestCameraSize.height)
        param.setFocusMode("continuous-video")

        cam.setParameters(param)
        cam.startPreview()

        camera = cameraOpt
      }}
    }

    ()
  }

  def stopCamera(): Unit = {
    logi(s"MainRenderer.stopCamera thread=${ConcurrencyUtils.currentThreadId()}")
    camera foreach { cam => {
      // cam.setPreviewTexture(null)  // scalastyle:ignore
      cam.stopPreview()
      cam.release()
    }}
    camera = None
  }

  def release(): Unit = {
    logd(s"MainRenderer.release thread=${ConcurrencyUtils.currentThreadId()}")
    stopCamera()
    surfaceTexture foreach { _.release() }
    deleteTex()
  }

  override def onSurfaceCreated(unused: GL10, config: EGLConfig): Unit = {
    logi(s"MainRenderer.onSurfaceCreated thread=${ConcurrencyUtils.currentThreadId()}")

    initTexture()
    surfaceTexture = Some(new SurfaceTexture(texture(0)))
    surfaceTexture foreach { _.setOnFrameAvailableListener(this) }

    // GLES20.glClearColor(0.5f, 0.5f, 0.5f, 1.0f)
    GLES20.glClearColor(1.0f, 1.0f, 0.0f, 1.0f)

    mainShaderProgram = loadShader(vertMainShader, fragMainShader)
    monochromeShaderProgram = loadShader(vertMainShader, fragMonochromeShader)

    val vPosition: Int = GLES20.glGetAttribLocation(mainShaderProgram, ShaderInputs.position)
    val vTexCoord: Int = GLES20.glGetAttribLocation(mainShaderProgram, ShaderInputs.texCoord)

    GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, verts)
    GLES20.glVertexAttribPointer(vTexCoord, 2, GLES20.GL_FLOAT, false, 0, uvCoords)
    GLES20.glEnableVertexAttribArray(vPosition)
    GLES20.glEnableVertexAttribArray(vTexCoord)

    val sTexture: Int = GLES20.glGetUniformLocation(mainShaderProgram, ShaderInputs.texture)
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture(0))
    GLES20.glUniform1i(sTexture, 0)
  }

  override def onDrawFrame(unused: GL10): Unit = {
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

    if (frameAvailable) {
      surfaceTexture foreach { _.updateTexImage() }
      frameAvailable = false

      updateVerts()
      drawNormal()
      withBlend {
        val madnessLocal = ApproxRandomizer.madness
        drawLightMonochrome(madnessLocal)
        drawDarkMonochrome(madnessLocal)
      }
      // GLES20.glFlush()
      // GLES20.glFinish()
    }
  }

  override def onSurfaceChanged(unused: GL10, width: Int, height: Int): Unit = {
    logd(s"onSurfaceChanged")

    startCamera(width, height)

    GLES20.glViewport(0, 0, width, height)
  }

  override def onFrameAvailable(st: SurfaceTexture): Unit = {
    frameAvailable = true
    view.requestRender()
  }

  private def drawNormal(): Unit = applyShader(mainShaderProgram, List((ShaderInputs.angle, cameraAngle)))

  private def drawLightMonochrome(madnessLocal: Float): Unit =
    applyShader(monochromeShaderProgram, List(
      (ShaderInputs.otherColor, lightColorChangeApproxRandomizer.getCurrentVector()),
      (ShaderInputs.low, lightMonochromeColorRange._1),
      (ShaderInputs.high, lightMonochromeColorRange._2),
      (ShaderInputs.madness, madnessLocal),
      (ShaderInputs.angle, cameraAngle)
    ))

  private def drawDarkMonochrome(madnessLocal: Float): Unit =
    applyShader(monochromeShaderProgram, List(
      (ShaderInputs.otherColor, darkColorChangeApproxRandomizer.getCurrentVector()),
      (ShaderInputs.low, darkMonochromeColorRange._1),
      (ShaderInputs.high, darkMonochromeColorRange._2),
      (ShaderInputs.madness, madnessLocal),
      (ShaderInputs.angle, cameraAngle)
    ))

  private def applyShader(shaderProgram: Int, args: List[(String, Any)]): Unit = {
    GLES20.glUseProgram(shaderProgram)
    args foreach { case (argName: String, argValue: Any) =>
      val argId: Int = GLES20.glGetUniformLocation(shaderProgram, argName)
      argValue match {
        case num: Float => GLES20.glUniform1f(argId, num)
        case vec: Vector[Float @unchecked] => vec.length match {
          case 2 => GLES20.glUniform2f(argId, vec(0), vec(1))
          case 3 => GLES20.glUniform3f(argId, vec(0), vec(1), vec(2))
          case _ => throw new IllegalArgumentException
        }
        case _ => throw new IllegalArgumentException
      }
    }
    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
  }

  private def withBlend(f: => Unit): Unit = {
    GLES20.glEnable(GLES20.GL_BLEND)
    // GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    // GLES20.glDepthMask(false)
    // GLES20.glClearDepthf(1.0f)
    // GLES20.glHint(GLES20.GL_POLYGON_SMOOTH, GLES20.GL_NICEST)
    // GLES20.glCullFace(GLES20.GL_BACK)
    GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
    GLES20.glDepthMask(false)
    GLES20.glClearDepthf(1.0f)
    GLES20.glCullFace(GLES20.GL_BACK)

    f

    GLES20.glDisable(GLES20.GL_BLEND)
  }

  private def updateVerts(): Unit = {
    verts.put(vertsApproxRandomizer.getCurrentArray())
    verts.position(0)
  }

  private def initTexture(): Unit = {
    logd("initTexture")

    GLES20.glGenTextures(1, texture, 0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texture(0))

    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

    // GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
    // GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
  }

  private def deleteTex(): Unit = GLES20.glDeleteTextures(1, texture, 0)

  private def loadShader(vss: Option[String], fss: Option[String]): Int = {
    val vshader: Int = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
    vss foreach { vss => GLES20.glShaderSource(vshader, vss) }
    GLES20.glCompileShader(vshader)

    val compiled = Array(0)
    GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, compiled, 0)
    if (compiled(0) == 0) {
      loge(s"Could not compile vshader: ${GLES20.glGetShaderInfoLog(vshader)}")
      GLES20.glDeleteShader(vshader)
    }

    val fshader: Int = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
    fss foreach { fss => GLES20.glShaderSource(fshader, fss) }
    GLES20.glCompileShader(fshader)
    GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, compiled, 0)
    if (compiled(0) == 0) {
      loge(s"Could not compile fshader: ${GLES20.glGetShaderInfoLog(fshader)}")
      GLES20.glDeleteShader(fshader)
    }

    val program: Int = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vshader)
    GLES20.glAttachShader(program, fshader)
    GLES20.glLinkProgram(program)

    program
  }

}
