package com.alopatindev.videoacid.ui

import android.support.v4.app.Fragment
import android.content.Context

import com.alopatindev.videoacid.R

//import rx.lang.scala._

//import language.postfixOps

//import scala.concurrent.duration._
import scala.util.Try

class VideoFragment extends Fragment with FragmentUtils {

  import android.os.Bundle
  import android.view.{LayoutInflater, View, ViewGroup}
  import android.widget.{LinearLayout, SeekBar}
  //import android.widget.SeekBar.OnSeekBarChangeListener

  import com.alopatindev.videoacid.Logs._

  private lazy val view = find[MainView](R.id.mainView)

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    logd("VideoFragment.onCreateView")

    val newView = inflater.inflate(R.layout.video, container, false)

    val madnessLevel = find[SeekBar](R.id.madnessLevel, newView)
    madnessLevel.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener {
      override def onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean): Unit = {
        val madnessLevel = progress.toFloat / 100.0f
        logi(s"madnessLevel=$madnessLevel")
      }

      override def onStartTrackingTouch(seekBar: SeekBar): Unit = ()
      override def onStopTrackingTouch(seekBar: SeekBar): Unit = ()
    })

    newView
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
