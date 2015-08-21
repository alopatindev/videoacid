package com.alopatindev.videoacid

object Utils {

  import android.content.Context
  import scala.io.Source
  import scala.util.Try
  import java.io.InputStream

  def loadAsset(path: String)(implicit ctx: Context): Option[String] = Try {
    val in: InputStream = ctx.getAssets().open(path)
    val result: String = Source.fromInputStream(in).mkString
    in.close()
    result
  }.toOption

}
