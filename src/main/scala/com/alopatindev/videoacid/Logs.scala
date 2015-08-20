package com.alopatindev.videoacid

object Logs {

  import android.util.Log

  private val LOG_TAG = "VideoAcid"

  def loge(text: String): Unit = Log.e(LOG_TAG, text)
  def logw(text: String): Unit = Log.w(LOG_TAG, text)
  def logi(text: String): Unit = Log.i(LOG_TAG, text)
  def logd(text: String): Unit = Log.d(LOG_TAG, text)

}
