package com.alopatindev.videoacid

import language.postfixOps

import scala.concurrent.duration._

class ApproxRandomizer(val originalVector: Vector[Float],
                       val minVector: Vector[Float],
                       val maxVector: Vector[Float],
                       val absBoundsCheck: Boolean = false,
                       val minFactor: Float = 1.0f,
                       val maxFactor: Float = 55.5f,
                       val speed: Float = 2.0f,
                       val updateInterval: Duration = 30 millis,
                       val randUpdateInterval: Duration = 2 seconds,
                       val smooth: Float = 0.001f) {

  import ApproxRandomizer._

  import com.alopatindev.videoacid.ui.{MainActivity, VideoFragment}
  import com.alopatindev.videoacid.Logs._

  import rx.lang.scala.{Observable, Subscription}

  import scala.util.Random

  val secsInMinute = 60.0f

  private val currentVectorSubs: Subscription = Observable
    .interval(updateInterval)
    .filter(i => {
      val madnessLocal = ApproxRandomizer.madness
      def divider = (secsInMinute / (madnessLocal * secsInMinute)).toInt
      def tickMatch = i % divider == 0
      val safe = madnessLocal > 0.0f && divider > 0
      MainActivity.resumed && safe && tickMatch
    })
    .subscribe { _ => calcCurrentVector() }

  private val nextRandVectorSub: Subscription = Observable
    .interval(randUpdateInterval)
    .filter(i => {
      val randNum = rand.nextInt() % 1000
      val madnessLocal = ApproxRandomizer.madness
      def divider = (secsInMinute / (randNum * secsInMinute)).toInt
      def tickMatch = (i + randNum) % divider == 0
      val safe = madnessLocal > 0.0f && divider > 0
      MainActivity.resumed && safe && tickMatch
    })
    .subscribe { _ => calcNextRandVector() }

  @volatile private var currentVector = originalVector
  @volatile private var nextRandVector = originalVector

  def getCurrentVector(): Vector[Float] = currentVector
  def getCurrentArray(): Array[Float] = currentVector.toArray

  private val rand = new Random

  private def calcNextRandVector(): Unit = {
    nextRandVector = (minVector zip maxVector zip originalVector) map { case ((minX, maxX), x) => {
      import Math.abs
      val madnessLocal = ApproxRandomizer.madness
      val factor = (maxFactor - minFactor) * madnessLocal + minFactor
      val randNum = rand.nextFloat()
      val newX = x + (randNum - 0.5f) * factor
      val outOfBounds =
        if (absBoundsCheck) {
          val absNewX = abs(newX)
          absNewX < abs(minX) || absNewX > abs(maxX)
        } else {
          newX < minX || newX > maxX
        }
      if (outOfBounds) x
      else newX
    }}
  }

  private def calcCurrentVector(): Unit = {
    val currentVectorLocal = currentVector
    val newCurrentVector = approxStep(current = currentVectorLocal, next = nextRandVector, step = smooth * speed)
    val approxCompleted = currentVectorLocal == newCurrentVector
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

  def madness = madness_

  def setMadness(m: Float): Unit = {
    madness_ = m
  }

}
