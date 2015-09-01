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

  def getString(id: Int)(implicit ctx: Context): String = ctx.getString(id)

  def appVersion()(implicit ctx: Context): String =
    Try[String] {
      ctx
        .getPackageManager()
        .getPackageInfo(ctx.getPackageName(), 0)
        .versionName
    } getOrElse {
      "(unknown version)"
    }

  def isNetworkOn()(implicit ctx: Context): Boolean = Try {
    import android.net.ConnectivityManager
    import android.net.NetworkInfo

    val service = ctx.getSystemService(Context.CONNECTIVITY_SERVICE)
    service match {
      case cm: ConnectivityManager =>
        val info: NetworkInfo = cm.getActiveNetworkInfo()
        lazy val netType = info.getType()
        lazy val isWiFi = netType == ConnectivityManager.TYPE_WIFI || netType == ConnectivityManager.TYPE_WIMAX
        val onlyViaWifi = false // TODO
        val correctNetType = !onlyViaWifi || isWiFi
        correctNetType && info.isConnectedOrConnecting()
      case None => false
    }
  } getOrElse false

  def clamp(x: Float, low: Float, high: Float): Float = Math.min(Math.max(x, low), high)

}
