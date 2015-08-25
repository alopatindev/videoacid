package com.alopatindev.videoacid.ui

import android.content.Context
import android.support.v4.app.FragmentManager
import android.support.v4.app.FragmentPagerAdapter

class MainPagerAdapter(fm: FragmentManager, context: Context) extends FragmentPagerAdapter(fm) {

  import android.support.v4.app.Fragment

  import com.alopatindev.videoacid.{R, Utils}

  import scala.collection.immutable.Vector

  implicit val ctx: Context = context

  lazy val fragments = Vector(
    (new VideoFragment, VideoFragment.titleStringId),
    (new GalleryFragment, GalleryFragment.titleStringId)
  )

  override def getCount(): Int = fragments.length
  override def getItem(position: Int): Fragment = fragments(position)._1
  override def getPageTitle(position: Int): String = Utils.getString(fragments(position)._2)

}
