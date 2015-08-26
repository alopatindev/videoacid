package com.alopatindev.videoacid.ui

import android.support.v4.app.Fragment
import android.content.Context

import com.alopatindev.videoacid.R

import scala.util.Try

class VideoFragment extends Fragment with FragmentUtils {

  import android.os.Bundle
  import android.view.{LayoutInflater, View, ViewGroup}
  import android.widget.LinearLayout

  import com.alopatindev.videoacid.Logs._

  private lazy val view = find[MainView](R.id.mainView)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    logd("VideoFragment.onCreateView")
    inflater.inflate(R.layout.video, container, false)
  }

  override def onPause(): Unit = {
    logd("VideoFragment.onPause")
    Try {
      view.onPause()
    }
    super.onPause()
  }

  override def onResume(): Unit = {
    logd("VideoFragment.onResume")
    super.onResume()
    Try {
      //val width = view.getWidth()
      //view.setLayoutParams(new LinearLayout.LayoutParams(width, width))
      view.onResume()
    }
  }

  override def onDestroy(): Unit = {
    logd("VideoFragment.onDestory")
    Try {
      view.release()
    }
    super.onDestroy()
  }

}

object VideoFragment {

  val titleStringId: Int = R.string.video

}
