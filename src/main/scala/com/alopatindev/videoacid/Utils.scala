package com.alopatindev.videoacid

object Utils {

  import android.content.Context
  import android.os.{Handler, Looper}

  import scala.concurrent.{Future, Promise}
  import scala.io.Source
  import scala.util.Try

  import java.io.InputStream

  lazy val handler = new Handler(Looper.getMainLooper)
  lazy val uiThread = Looper.getMainLooper.getThread

  def loadAsset(path: String)(implicit ctx: Context): Option[String] = Try {
    val in: InputStream = ctx.getAssets().open(path)
    val result: String = Source.fromInputStream(in).mkString
    in.close()
    result
  }.toOption

  def getString(id: Int)(implicit ctx: Context): String = ctx.getString(id)

  def runOnUIThread(f: => Unit, delay: Int = 0): Unit =
    if (uiThread == Thread.currentThread) {
      f
    } else {
      val runnable = new Runnable() {
        override def run() = f
      }
      if (delay > 0) {
        handler postDelayed (runnable, delay)
      } else {
        handler post runnable
      }
    }

  def evalOnUIThread[T](f: => T, delay: Int = 0): Future[T] =
    if (uiThread == Thread.currentThread) {
      Future.fromTry(Try(f))
    } else {
      val p = Promise[T]()
      val runnable = new Runnable() {
        override def run() = p.complete(Try(f))
      }
      if (delay > 0) {
        handler postDelayed (runnable, delay)
      } else {
        handler post runnable
      }
      p.future
    }

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

}
