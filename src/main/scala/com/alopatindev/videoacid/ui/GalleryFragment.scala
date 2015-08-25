package com.alopatindev.videoacid.ui

import android.support.v4.app.Fragment
import android.content.Context

import com.alopatindev.videoacid.R

class GalleryFragment extends Fragment with FragmentUtils {

  import android.os.Bundle
  import android.view.{LayoutInflater, View, ViewGroup}

  import com.alopatindev.videoacid.Logs._

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup, savedInstanceState: Bundle): View = {
    logd("GalleryFragment.onCreateView")
    inflater.inflate(R.layout.gallery, container, false)
  }

  override def onPause(): Unit = {
    logd("GalleryFragment.onPause")
    super.onPause()
  }

  override def onResume(): Unit = {
    logd("GalleryFragment.onResume")
    super.onResume()
  }

}

object GalleryFragment {

  val titleStringId: Int = R.string.gallery

}
