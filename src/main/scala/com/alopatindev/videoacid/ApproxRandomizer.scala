package com.alopatindev.videoacid

import language.postfixOps

import scala.concurrent.duration._  // scalastyle:ignore

class ApproxRandomizer(val minVector: Vector[Float],
                       val maxVector: Vector[Float],
                       val absBoundsCheck: Boolean = false,
                       val speed: Float = 2.0f,
                       val updateInterval: Duration = 30 millis,
                       val randUpdateInterval: Duration = 2 seconds,
                       val smooth: Float = 0.001f,
                       val debug: Boolean = false) {

  import com.alopatindev.videoacid.ui.{MainActivity, VideoFragment}
  import com.alopatindev.videoacid.ConcurrencyUtils.newObservableInterval

  import rx.lang.scala.Subscription

  import scala.util.Random

  val secsInMinute = 60.0f

  private val currentVectorSubs: Subscription = newObservableInterval(updateInterval)
    .filter { _ => MainActivity.isResumed() }
    .subscribe { _ => calcCurrentVector() }

  private val nextRandVectorSub: Subscription = newObservableInterval(randUpdateInterval)
    .filter(i => {
      val randNum = rand.nextInt() % 4000
      val madnessLocal = ApproxRandomizer.madness
      def divider = (secsInMinute / (randNum * secsInMinute)).toInt
      def tickMatch = (i + randNum) % divider == 0
      val safe = madnessLocal > 0.0f && divider > 0
      MainActivity.isResumed() && safe && tickMatch
    })
    .subscribe { _ => calcNextRandVector() }

  @volatile private var currentVector = minVector
  @volatile private var nextRandVector = minVector

  def getCurrentVector(): Vector[Float] = currentVector
  def getCurrentArray(): Array[Float] = currentVector.toArray

  private val rand = new Random

  private def calcNextRandVector(): Unit = {
    val madnessLocal = ApproxRandomizer.madness

    def randBetween(a: Float, b: Float): Float = {
      val delta = b - a
      val scaledDelta = delta * madnessLocal
      // val randNum = rand.nextFloat()
      val randGauss = (0.5 + rand.nextGaussian() / 8.0).toFloat
      val randNum = Utils.clamp(randGauss, 0.0f, 1.0f)
      val value = a + randNum * scaledDelta

      assert(randNum >= 0.0f && randNum <= 1.0f, s"$randNum is out of [0 .. 1]")
      assert(value >= a && value <= b, s"value $value is out of [$a .. $b]")

      value
    }

    nextRandVector = (minVector zip maxVector)
      .map { case (a, b) => randBetween(a, b) }
  }

  private def almostEqualVectors(a: Vector[Float], b: Vector[Float]): Boolean =
    (a zip b)
      .map { case (a, b) => Math.abs(a - b) <= smooth }
      .filter(_ == true)
      .length == a.length

  private def calcCurrentVector(): Unit = {
    val madnessLocal = ApproxRandomizer.madness
    val currentVectorLocal = currentVector
    val newCurrentVector = approxStep(
      current = currentVectorLocal,
      next = nextRandVector,
      step = smooth * speed * madnessLocal
    )

    val approxCompleted = almostEqualVectors(currentVectorLocal, newCurrentVector)
    if (approxCompleted)
      calcNextRandVector()
    else
      currentVector = newCurrentVector
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

}

object ApproxRandomizer {

  @volatile private var madness_ = 0.0f

  def madness: Float = madness_

  def setMadness(m: Float): Unit = {
    madness_ = m
  }

}
