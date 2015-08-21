package com.alopatindev.videoacid

import scala.util.Random

class ApproxRandomizer(val originalVector: Vector[Float],
                       val factor: Float = 1.5f,
                       val speed: Float = 2.0f,
                       val updateInterval: Long = 30L,
                       val randUpdateInterval: Long = 2000L,
                       val smooth: Float = 0.001f) {

  private lazy val rand = new Random()
  private var nextRandUpdateTime = 0L
  private var nextUpdateTime = 0L
  private var currentVector = originalVector
  private var nextRandVector = originalVector

  def getCurrentVector(): Vector[Float] = currentVector

  def getCurrentArray(): Array[Float] = currentVector.toArray

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
