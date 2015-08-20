package com.alopatindev.videoacid.ui

import android.app.Activity
import android.content.Context
import android.view.View

import com.alopatindev.videoacid.{R, TypedFindView}

import scala.util.Try

class MainActivity extends Activity with TypedFindView with ActivityUtils {

  import android.os.Bundle

  private val mainView = new MainView(this)

  override def onCreate(bundle: Bundle): Unit = {
    logd("MainActivity.onCreate")
    super.onCreate(bundle)
    requestWindowFeature(Window.FEATURE_NO_TITLE)
    setContentView(mainView)
  }

  override def onDestroy(): Unit = {
    logd("MainActivity.onDestroy")
    super.onDestroy()
  }

}
