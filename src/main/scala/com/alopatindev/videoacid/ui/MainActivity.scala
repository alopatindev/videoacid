package com.alopatindev.videoacid.ui

import android.app.Activity
import android.content.Context
import android.support.v4.app.FragmentActivity

import com.alopatindev.videoacid.{R, TypedFindView}

import scala.util.Try

class MainActivity extends FragmentActivity with TypedFindView with ActivityUtils {

  import android.os.Bundle
  import android.view.Window
  import android.view.WindowManager

  import com.alopatindev.videoacid.Logs.logd

  private lazy val pager = find[MainViewPager](R.id.pager)

  override def onCreate(bundle: Bundle): Unit = {
    logd("MainActivity.onCreate")
    super.onCreate(bundle)

    setupWindow()
    setContentView(R.layout.main)
    createTabs()
  }

  override def onResume(): Unit = {
    logd("MainActivity.onResume")
    super.onResume()
    MainActivity.setResumed(true)
  }

  override def onPause(): Unit = {
    logd("MainActivity.onPause")
    super.onPause()
    MainActivity.setResumed(false)
  }

  override def onStop(): Unit = {
    logd("MainActivity.onStop")
    super.onStop()
    MainActivity.setResumed(false)
  }

  override def onDestroy(): Unit = {
    logd("MainActivity.onDestroy")
    super.onDestroy()
    MainActivity.setResumed(false)
  }

  private def setupWindow(): Unit = {
    requestWindowFeature(Window.FEATURE_NO_TITLE)
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN)
    getWindow().setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON, WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  private def createTabs(): Unit = {
    val adapter = new MainPagerAdapter(getSupportFragmentManager(), this)
    pager setAdapter adapter
  }

}

object MainActivity {

  import com.alopatindev.videoacid.ConcurrencyUtils.{currentThreadId, executor}

  private lazy val threadId: Long = currentThreadId()
  private var resumed = false

  def isResumed(): Boolean = {
    assert(threadId == currentThreadId())
    resumed
  }

  def setResumed(value: Boolean): Unit = {
    executor.execute(new Runnable {
      override def run(): Unit = {
        assert(threadId == currentThreadId())
        resumed = value
      }
    })
  }

}
