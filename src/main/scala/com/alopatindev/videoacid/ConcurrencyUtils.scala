package com.alopatindev.videoacid

object ConcurrencyUtils {

  import android.os.{Handler, Looper}

  import com.alopatindev.videoacid.Logs.logd

  import java.util.concurrent.{Executors, ExecutorService}

  import rx.lang.scala.{Observable, Scheduler, Subscription}
  import rx.lang.scala.schedulers.ExecutionContextScheduler

  import language.postfixOps

  import scala.concurrent.{ExecutionContext, Future, Promise}
  import scala.concurrent.duration._  // scalastyle:ignore

  import scala.util.Try

  lazy val uiHandler = new Handler(Looper.getMainLooper)
  lazy val uiThread = Looper.getMainLooper.getThread

  // private val executorService: ExecutorService = Executors.newFixedThreadPool(2)
  private val executorService: ExecutorService = Executors.newSingleThreadExecutor()
  implicit val executor: ExecutionContext = ExecutionContext.fromExecutor(executorService)
  val scheduler: Scheduler = ExecutionContextScheduler(executor)

  def currentThreadId(): Long = Thread.currentThread().getId()
  def currentThreadName(): String = Thread.currentThread().getName()
  def printCurrentThreadDetails(): Unit = logd(s"current thread id=${currentThreadId()} name='${currentThreadName()}'")

  def newObservableInterval(interval: Duration): Observable[Long] = Observable
    .interval(interval)
    .observeOn(scheduler)
    .subscribeOn(scheduler)

  def runOnHandler(handler: Handler, f: => Unit, delay: Duration = 0.millis): Unit = {
    val runnable = new Runnable() {
      override def run() = f
    }
    if (delay > 0.millis) {
      handler postDelayed (runnable, delay.toMillis)
    } else {
      handler post runnable
    }
  }

  def runOnUIThread(f: => Unit, delay: Duration = 0.millis): Unit = runOnHandler(uiHandler, f, delay)

  def evalOnUIThread[T](f: => T, delay: Int = 0): Future[T] =
    if (uiThread == Thread.currentThread) {
      Future.fromTry(Try(f))
    } else {
      val p = Promise[T]()
      val runnable = new Runnable() {
        override def run() = p.complete(Try(f))
      }
      if (delay > 0) {
        uiHandler postDelayed (runnable, delay)
      } else {
        uiHandler post runnable
      }
      p.future
    }

}
