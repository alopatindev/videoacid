package com.alopatindev.videoacid

import language.postfixOps

import rx.lang.scala.subjects.PublishSubject

import scala.concurrent.duration._

class ApproxRandomizer(val originalVector: Vector[Float],
                       val factor: Float = 1.5f,
                       val speed: Float = 2.0f,
                       val updateInterval: Duration = 30 millis,
                       val randUpdateInterval: Duration = 2 seconds,
                       val smooth: Float = 0.001f) {

  import ApproxRandomizer._

  import com.alopatindev.videoacid.ui.{MainActivity, VideoFragment}
  import com.alopatindev.videoacid.Logs._

  import rx.lang.scala.{Observable, Subscription}

  import scala.util.Random

  private val currentVectorObs = Observable
    .interval(updateInterval)
    .filter(_ => MainActivity.resumed)
  private val currentVectorSub: Subscription = currentVectorObs.filter { i => {
    val d = ((MAX_MADNESS_LEVEL * 60.0f) / (madnessLevel * 60.0f)).toInt
    i % d == 0
  }} subscribe { _ => calcCurrentVector() }

  private val nextRandVectorObs = Observable
    .interval(randUpdateInterval)
    .filter(_ => MainActivity.resumed)
  private val nextRandVectorSub: Subscription = nextRandVectorObs.filter { i => {
    val randAddition = rand.nextInt() % 1000
    //val randAddition = rand.nextInt(1000)
    val d = ((MAX_MADNESS_LEVEL * 60.0f) / (madnessLevel * 60.0f)).toInt
    (i + randAddition) % d == 0
  }} subscribe { i => { logd(s"calcNextRandVector $i") ; calcNextRandVector() } }

  private val madnessLevelSub: Subscription = ApproxRandomizer.madnessLevelChannel subscribe { ml => { madnessLevel = ml ; calcNextRandVector() ; calcCurrentVector() ;  logd(s"madnessLevel=$madnessLevel") } }

  @volatile private var currentVector = originalVector
  @volatile private var nextRandVector = originalVector
  @volatile private var madnessLevel = 1.0f

  def getCurrentVector(): Vector[Float] = currentVector
  def getCurrentArray(): Array[Float] = currentVector.toArray

  def suspend(): Unit = ???
  def resume(): Unit = ???

  private val rand = new Random

  private def calcNextRandVector(): Unit = {
    nextRandVector = originalVector map { x => {
      val scaledFactor = factor * madnessLevel
      val randNumber = rand.nextFloat()
      logd(s"rand=$randNumber")
      val newX = x + (randNumber - 0.5f) * scaledFactor
      if (Math.abs(newX) > 1.0f) newX
      else x
    }}
  }

  private def calcCurrentVector(): Unit = {
    currentVector = approxStep(current = currentVector, next = nextRandVector, step = smooth * speed)
    //logd(s"calcCurrentVector ${currentVector}")
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

/*
  def update(): Unit = {
    val currentTime = System.currentTimeMillis()
    val dirtyNextRandVector = (currentTime - nextRandUpdateTime) > 0L
    lazy val dirtyCurrentVector = (currentTime - nextUpdateTime) > 0L

    if (dirtyNextRandVector) {
      nextRandVector = originalVector map { x => {
        val newX = x + (rand.nextFloat() - 0.5f) * factor
        if (Math.abs(newX) > 1.0f) newX
        else x
      }}
      nextRandUpdateTime = currentTime + (randUpdateInterval / speed.toLong) + rand.nextLong() % 1000L
    } else if (dirtyCurrentVector) {
      currentVector = approxStep(current = currentVector, next = nextRandVector, step = smooth * speed)
      nextUpdateTime = currentTime + updateInterval
    }
  }
*/

}

object ApproxRandomizer {

  val madnessLevelChannel = PublishSubject[Float]()
  val MIN_MADNESS_LEVEL = 1.0f
  val MAX_MADNESS_LEVEL = 3.0f

}
