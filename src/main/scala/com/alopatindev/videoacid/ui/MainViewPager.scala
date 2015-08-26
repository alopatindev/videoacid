package com.alopatindev.videoacid.ui

import android.content.Context
import android.support.v4.view.ViewPager
import android.util.AttributeSet
import android.view.MotionEvent

class MainViewPager(context: Context, attrs: AttributeSet) extends ViewPager(context, attrs) {

  private val pagingEnabled = false

  override def onTouchEvent(event: MotionEvent): Boolean = pagingEnabled && super.onTouchEvent(event)

  override def onInterceptTouchEvent(event: MotionEvent): Boolean = pagingEnabled && super.onInterceptTouchEvent(event)

}
