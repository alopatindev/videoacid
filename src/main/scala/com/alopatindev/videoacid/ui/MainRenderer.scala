package com.alopatindev.videoacid.ui

import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.hardware.Camera
import android.opengl.GLSurfaceView
import android.opengl.GLES11Ext
import android.opengl.GLES20

import com.alopatindev.videoacid.{ApproxRandomizer, Utils}
import com.alopatindev.videoacid.Logs.{logd, loge, logi}

import language.postfixOps

import scala.concurrent.duration._  // scalastyle:ignore
import scala.util.Try

import java.nio.{ByteBuffer, ByteOrder, FloatBuffer, IntBuffer}
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainRenderer(val view: MainView) extends Object
                                       with GLSurfaceView.Renderer
                                       with SurfaceTexture.OnFrameAvailableListener {

  import android.content.Context

  import com.alopatindev.videoacid.ConcurrencyUtils
  import com.alopatindev.videoacid.Utils

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
    val alpha: String = "fAlpha"
  }

  private lazy val vertMainShader: Option[String] = Utils.loadAsset("main.vert")
  private lazy val vertFbTextureShader: Option[String] = Utils.loadAsset("fbtexture.vert")
  private lazy val fragMainShader: Option[String] = Utils.loadAsset("main.frag")
  private lazy val fragMonochromeShader: Option[String] = Utils.loadAsset("monochrome.frag")
  private lazy val fragFbTextureShader: Option[String] = Utils.loadAsset("fbtexture.frag")
  private var mainShaderProgram: Int = 0
  private var monochromeShaderProgram: Int = 0
  private var fbTextureShaderProgram: Int = 0

  private var fbInitialized = false
  private val fbTexture: Array[Int] = Array(0)
  private val fb: Array[Int] = Array(0)

  private val cameraTexture: Array[Int] = Array(0)

  @volatile private var camera: Option[Camera] = None
  private val cameraAngle: Float = -0.5f * Math.PI.toFloat

  private var surfaceTexture: Option[SurfaceTexture] = None

  private val lightMonochromeColorRange: (Float, Float) = (0.51f, 1.0f)
  private val darkMonochromeColorRange: (Float, Float) = (0.0f, 0.5f)

  private val FB_RECTS = 1

  private val screenRandFactor = 1.2f
  private val fbVertsInitial: Array[Float] = MainRenderer.vertsGen(rects=FB_RECTS).toArray
  private val fbVertsInitialVector: Vector[Float] = fbVertsInitial.toVector
  private lazy val fbVertsApproxRandomizer = new ApproxRandomizer(
    minVector = fbVertsInitialVector map { x => if (x < 0.0f) x * screenRandFactor else x },
    // minVector = fbVertsInitialVector map { x => x * screenRandFactor },
    maxVector = fbVertsInitialVector,
    speed = 2.3f,
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

  private val BYTES_PER_INT = 4
  private val BYTES_PER_FLOAT = BYTES_PER_INT

  private def newBuffer(size: Int): ByteBuffer = ByteBuffer
    .allocateDirect(size)
    .order(ByteOrder.nativeOrder)

  private def newFloatBuffer(length: Int): FloatBuffer = newBuffer(length * BYTES_PER_FLOAT).asFloatBuffer()
  private def newIntBuffer(length: Int): IntBuffer = newBuffer(length * BYTES_PER_INT).asIntBuffer()

  private val vertsInitial: Array[Float] = MainRenderer.vertsGen(rects=1).toArray
  private val verts: FloatBuffer = newFloatBuffer(vertsInitial.length)
  verts.put(vertsInitial)
  verts.position(0)

  private val fbVerts: FloatBuffer = newFloatBuffer(fbVertsInitial.length)
  updateFbVerts()

  private val uvCoordsInitial: Array[Float] = MainRenderer.uvGen(rects=1).toArray
  private val uvCoords: FloatBuffer = newFloatBuffer(uvCoordsInitial.length)
  uvCoords.put(uvCoordsInitial)
  uvCoords.position(0)

  private val fbUvInitial: Array[Float] = MainRenderer.uvGen(rects=FB_RECTS).toArray
  private val fbUvCoords: FloatBuffer = newFloatBuffer(fbUvInitial.length)
  fbUvCoords.put(fbUvInitial)
  fbUvCoords.position(0)

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
    releaseGLResources()
  }

  override def onSurfaceCreated(unused: GL10, config: EGLConfig): Unit = {
    logi(s"MainRenderer.onSurfaceCreated thread=${ConcurrencyUtils.currentThreadId()}")

    initCameraTexture()
    initShaders()
  }

  private def setOutputToScreen(): Unit = {
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
  }

  private def setOutputToFrameBuffer(): Unit = {
    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fb(0))
    GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fbTexture(0), 0)
    val status: Int = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
    if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
      loge("setOutputToFrameBuffer: something went wrong")
    }
  }

  override def onDrawFrame(unused: GL10): Unit = {
    setOutputToScreen()
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT)

    if (frameAvailable) {
      frameAvailable = false

      updateFbVerts()
      renderFbTexture()  // rendering previous fb texture to screen

      setOutputToFrameBuffer()
      // GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT)
      surfaceTexture foreach { _.updateTexImage() }  // fetch a new frame from camera
      withBlend {
        val madnessLocal = ApproxRandomizer.madness
        val alpha = Utils.clamp(1.0f - madnessLocal * 4.0f, 0.2f, 1.0f)
        renderNormal(alpha)
        renderLightMonochrome(madnessLocal)
        renderDarkMonochrome(madnessLocal)
      }

      // GLES20.glFlush()
      // GLES20.glFinish()
    }
  }

  override def onSurfaceChanged(unused: GL10, width: Int, height: Int): Unit = {
    logd(s"onSurfaceChanged")
    logd(s"renderer: '${GLES20.glGetString(GLES20.GL_RENDERER)}'")
    logd(s"extensions: '${GLES20.glGetString(GLES20.GL_EXTENSIONS)}'")

    if (!fbInitialized) {
      initFrameBufferTexture(width, height)
    }

    startCamera(width, height)

    GLES20.glViewport(0, 0, width, height)
  }

  override def onFrameAvailable(st: SurfaceTexture): Unit = {
    frameAvailable = true
    view.requestRender()
  }

  private def renderNormal(alpha: Float = 1.0f): Unit =
    applyShader(mainShaderProgram, List(
      (ShaderInputs.angle, cameraAngle),
      (ShaderInputs.texture, cameraTexture),
      (ShaderInputs.alpha, alpha)
    ))

  private def renderFbTexture(): Unit =
    applyShader(fbTextureShaderProgram, List(
      (ShaderInputs.texture, fbTexture)
    ))

  private def renderLightMonochrome(madnessLocal: Float): Unit =
    applyShader(monochromeShaderProgram, List(
      (ShaderInputs.angle, cameraAngle),
      (ShaderInputs.texture, cameraTexture),
      (ShaderInputs.otherColor, lightColorChangeApproxRandomizer.getCurrentVector()),
      (ShaderInputs.low, lightMonochromeColorRange._1),
      (ShaderInputs.high, lightMonochromeColorRange._2),
      (ShaderInputs.madness, madnessLocal)
    ))

  private def renderDarkMonochrome(madnessLocal: Float): Unit =
    applyShader(monochromeShaderProgram, List(
      (ShaderInputs.angle, cameraAngle),
      (ShaderInputs.texture, cameraTexture),
      (ShaderInputs.otherColor, darkColorChangeApproxRandomizer.getCurrentVector()),
      (ShaderInputs.low, darkMonochromeColorRange._1),
      (ShaderInputs.high, darkMonochromeColorRange._2),
      (ShaderInputs.madness, madnessLocal)
    ))

  private def applyShader(shaderProgram: Int, args: List[(String, Any)]): Unit = {
    GLES20.glUseProgram(shaderProgram)
    args foreach { case (argName: String, argValue: Any) =>
      val argId: Int = GLES20.glGetUniformLocation(shaderProgram, argName)
      argValue match {
        case tex: Array[Int @unchecked] if argName == ShaderInputs.texture =>
          GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
          val textureType = if (shaderProgram == fbTextureShaderProgram) GLES20.GL_TEXTURE_2D
                            else GLES11Ext.GL_TEXTURE_EXTERNAL_OES
          GLES20.glBindTexture(textureType, tex(0))
          GLES20.glUniform1i(argId, 0)
        case num: Float => GLES20.glUniform1f(argId, num)
        case vec: Vector[Float @unchecked] => vec.length match {
          case 2 => GLES20.glUniform2f(argId, vec(0), vec(1))
          case 3 => GLES20.glUniform3f(argId, vec(0), vec(1), vec(2))
          case _ => throw new IllegalArgumentException
        }
        case _ => throw new IllegalArgumentException
      }
    }

    val vertsNumber = if (shaderProgram == fbTextureShaderProgram) fbVertsInitial.length / 2
                      else vertsInitial.length / 2

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, vertsNumber)
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

  private def updateFbVerts(): Unit = {
    fbVerts.put(fbVertsApproxRandomizer.getCurrentArray())
    fbVerts.position(0)
  }

  private def initCameraTexture(): Unit = {
    logd("initCameraTexture")

    GLES20.glGenTextures(1, cameraTexture, 0)
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, cameraTexture(0))

    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

    surfaceTexture = Some(new SurfaceTexture(cameraTexture(0)))
    surfaceTexture foreach { _.setOnFrameAvailableListener(this) }

    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
  }

  private def initFrameBufferTexture(width: Int, height: Int): Unit = {
    GLES20.glGenFramebuffers(1, fb, 0)
    GLES20.glGenTextures(1, fbTexture, 0)

    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, fbTexture(0))

    val bufferLength = width * height * BYTES_PER_INT
    val texBuffer: IntBuffer = newIntBuffer(bufferLength)

    GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGB, width, height, 0, GLES20.GL_RGB, GLES20.GL_UNSIGNED_SHORT_5_6_5, texBuffer)

    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)

    GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

    fbInitialized = true
  }

  private def releaseGLResources(): Unit = {
    GLES20.glDeleteTextures(1, cameraTexture, 0)
    if (fbInitialized) {
      GLES20.glDeleteTextures(1, fbTexture, 0)
      GLES20.glDeleteFramebuffers(1, fb, 0)
      fbInitialized = false
    }
  }

  private def loadShader(vss: Option[String], fss: Option[String]): Int = {
    val vshader: Int = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
    vss foreach { vss => GLES20.glShaderSource(vshader, vss) }
    GLES20.glCompileShader(vshader)

    val status = Array(0)
    GLES20.glGetShaderiv(vshader, GLES20.GL_COMPILE_STATUS, status, 0)
    if (status(0) != GLES20.GL_TRUE) {
      loge(s"Could not compile vshader: ${GLES20.glGetShaderInfoLog(vshader)}")
      GLES20.glDeleteShader(vshader)
    }

    val fshader: Int = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
    fss foreach { fss => GLES20.glShaderSource(fshader, fss) }
    GLES20.glCompileShader(fshader)
    GLES20.glGetShaderiv(fshader, GLES20.GL_COMPILE_STATUS, status, 0)
    if (status(0) != GLES20.GL_TRUE) {
      loge(s"Could not compile fshader: ${GLES20.glGetShaderInfoLog(fshader)}")
      GLES20.glDeleteShader(fshader)
    }

    val program: Int = GLES20.glCreateProgram()
    GLES20.glAttachShader(program, vshader)
    GLES20.glAttachShader(program, fshader)
    GLES20.glLinkProgram(program)

    GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0);
    if (status(0) != GLES20.GL_TRUE) {
      loge(s"Could not link program: ${GLES20.glGetProgramInfoLog(program)}")
      GLES20.glDeleteProgram(program)
    }

    program
  }

  private def initShaders(): Unit = {
    mainShaderProgram = loadShader(vertMainShader, fragMainShader)
    monochromeShaderProgram = loadShader(vertMainShader, fragMonochromeShader)
    fbTextureShaderProgram = loadShader(vertFbTextureShader, fragFbTextureShader)

    for (shaderProgram <- List(mainShaderProgram, monochromeShaderProgram, fbTextureShaderProgram)) yield {
      val vPosition: Int = GLES20.glGetAttribLocation(shaderProgram, ShaderInputs.position)
      val vTexCoord: Int = GLES20.glGetAttribLocation(shaderProgram, ShaderInputs.texCoord)
      val v = if (shaderProgram == fbTextureShaderProgram) fbVerts
              else verts
      val uv = if (shaderProgram == fbTextureShaderProgram) fbUvCoords
               else uvCoords
      GLES20.glVertexAttribPointer(vPosition, 2, GLES20.GL_FLOAT, false, 0, v)
      GLES20.glVertexAttribPointer(vTexCoord, 2, GLES20.GL_FLOAT, false, 0, uv)
      GLES20.glEnableVertexAttribArray(vPosition)
      GLES20.glEnableVertexAttribArray(vTexCoord)
    }
  }

}

object MainRenderer {

  val rectVerts = 4
  val rectVertComponents = 2
  val rectItems = rectVerts * rectVertComponents

  private def rect(x: Float, y: Float, width: Float): List[Float] = List(
    x, y,         x + width, y,
    x, y + width, x + width, y + width
  )

  private def gen(rects: Int, low: Float, high: Float): List[Float] = {
    val cols: Int = Math.sqrt(rects).toInt
    val rows = cols

    val rectWidth: Float = Math.abs(low) + Math.abs(high)

    val width: Float = rectWidth / cols.toFloat
    val coords: List[Float] = {
      for {
        col <- low until high by width
        row <- low until high by width
        coord <- rect(col, row, width)
      } yield (coord)
    }.toList

    assert(coords.length == rectItems * cols * rows)

    coords
  }

  def uvGen(rects: Int): List[Float] = gen(rects, low = 0.0f, high = 1.0f)

  def vertsGen(rects: Int): List[Float] = gen(rects, low = -1.0f, high = 1.0f)

}
