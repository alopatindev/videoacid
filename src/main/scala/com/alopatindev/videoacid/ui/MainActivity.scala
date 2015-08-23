package com.alopatindev.videoacid.ui

import android.app.Activity
import android.content.Context
import android.view.View

import com.alopatindev.videoacid.{R, TypedFindView}

import scala.util.Try

class MainActivity extends Activity with TypedFindView with ActivityUtils {

  import android.os.Bundle
  import android.os.PowerManager
  import android.view.Window
  import android.view.WindowManager
  import com.alopatindev.videoacid.Logs._

  private lazy val view = find[MainView](R.id.mainView)

  override def onCreate(bundle: Bundle): Unit = {
    logd("MainActivity.onCreate")
    super.onCreate(bundle)
    requestWindowFeature(Window.FEATURE_NO_TITLE)

    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

    setContentView(R.layout.main)
  }

  override def onDestroy(): Unit = {
    logd("MainActivity.onDestroy")
    super.onDestroy()
  }

  override def onPause(): Unit = {
    logd("MainActivity.onPause")
    Try {
      view.onPause()
    }
    super.onPause()
  }

  override def onResume(): Unit = {
    logd("MainActivity.onResume")
    super.onResume()
    Try {
      view.onResume()
    }
  }

}
