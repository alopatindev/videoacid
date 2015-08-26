package com.alopatindev.videoacid.ui

import android.content.Context
import android.support.v4.app.Fragment
import android.view.View

trait FragmentUtils <: Fragment {

  implicit val ctx: Context = getActivity()

  def find[V <: View](id: Int, view: View = getView()): V = view.findViewById(id).asInstanceOf[V]  // scalastyle:ignore

}
