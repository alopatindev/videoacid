package com.alopatindev.videoacid

import language.postfixOps

import scala.concurrent.duration._

class ApproxRandomizer(val minVector: Vector[Float],
                       val maxVector: Vector[Float],
                       val absBoundsCheck: Boolean = false,
                       val speed: Float = 2.0f,
                       val updateInterval: Duration = 30 millis,
                       val randUpdateInterval: Duration = 2 seconds,
                       val smooth: Float = 0.001f,
                       val debug: Boolean = false) {

  import ApproxRandomizer._

  import com.alopatindev.videoacid.ui.{MainActivity, VideoFragment}
  import com.alopatindev.videoacid.Logs._

  import rx.lang.scala.{Observable, Subscription}

  import scala.util.Random

  private def log(text: String): Unit = if (debug) logd(text)
                                        else ()

  val secsInMinute = 60.0f

  private val currentVectorSubs: Subscription = Observable
    .interval(updateInterval)
    .filter { _ => MainActivity.resumed }
    .subscribe { _ => calcCurrentVector() }

  private val nextRandVectorSub: Subscription = Observable
    .interval(randUpdateInterval)
    .filter(i => {
      val randNum = rand.nextInt() % 4000
      val madnessLocal = ApproxRandomizer.madness
      def divider = (secsInMinute / (randNum * secsInMinute)).toInt
      def tickMatch = (i + randNum) % divider == 0
      val safe = madnessLocal > 0.0f && divider > 0
      MainActivity.resumed && safe && tickMatch
    })
    .subscribe { _ => calcNextRandVector() }

  @volatile private var currentVector = minVector
  @volatile private var nextRandVector = minVector

  def getCurrentVector(): Vector[Float] = currentVector
  def getCurrentArray(): Array[Float] = currentVector.toArray

  private val rand = new Random

  private def calcNextRandVector(): Unit = {
    log("calcNextRandVector")
    val madnessLocal = ApproxRandomizer.madness

    def randBetween(a: Float, b: Float): Float = {
      val delta = b - a
      val scaledDelta = delta * madnessLocal
      //val randNum = rand.nextFloat()
      val randGauss = (0.5 + rand.nextGaussian() / 8.0).toFloat
      val randNum = clamp(randGauss, 0.0f, 1.0f)
      val value = a + randNum * scaledDelta

      assert(randNum >= 0.0f && randNum <= 1.0f, s"$randNum is out of [0 .. 1]")
      assert(value >= a && value <= b, s"value $value is out of [$a .. $b]")

      value
    }

    nextRandVector = (minVector zip maxVector)
      .map { case (a, b) => randBetween(a, b) }
  }

  private def almostEqualVectors(a: Vector[Float], b: Vector[Float]): Boolean = {
    val madnessLocal = ApproxRandomizer.madness
    (a zip b)
      .map { case (a, b) => Math.abs(a - b) <= smooth * madnessLocal }
      .filter { small => small }
      .length == a.length
  }

  private def calcCurrentVector(): Unit = {
    //val madnessLocal = ApproxRandomizer.madness
    val currentVectorLocal = currentVector
    val newCurrentVector = approxStep(current = currentVectorLocal, next = nextRandVector, step = smooth * speed)
    //val approxCompleted = currentVectorLocal == newCurrentVector // FIXME: delta <= smooth?
    //val approxCompleted = madnessLocal > 0.5f && almostEqualVectors(currentVectorLocal, newCurrentVector)
    val approxCompleted = almostEqualVectors(currentVectorLocal, newCurrentVector)
    if (approxCompleted)
      calcNextRandVector()
    else {
      log(s"newCurrentVector=$newCurrentVector")
      currentVector = newCurrentVector
    }
  }

  private def approxStep(current: Vector[Float], next: Vector[Float], step: Float): Vector[Float] =
    (current zip next) map { case (c, n) => {
      val acc = if (c < n) step
                else -step
      val next = c + acc
      val found = Math.abs(next - n) <= step
      if (found) c
      else next
    }}

  private def clamp(x: Float, low: Float, high: Float): Float = Math.min(Math.max(x, low), high)

}

object ApproxRandomizer {

  @volatile private var madness_ = 0.0f

  def madness = madness_

  def setMadness(m: Float): Unit = {
    madness_ = m
  }

}
