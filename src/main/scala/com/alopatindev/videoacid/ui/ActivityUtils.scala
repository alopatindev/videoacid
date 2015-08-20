package com.alopatindev.videoacid.ui

import android.app.Activity

trait ActivityUtils <: Activity {

  import android.content.Context
  import android.os.Bundle
  import android.view.View
  import android.widget.Button

  implicit val ctx: Context = this

  def find[V <: View](id: Int): V = findViewById(id).asInstanceOf[V]  // scalastyle:ignore

  def setButtonHandler(button: Button, handler: () => Unit): Unit =
    button setOnClickListener {
      new View.OnClickListener() {
        override def onClick(v: View) = handler()
      }
    }

}
