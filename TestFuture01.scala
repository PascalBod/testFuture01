/*
 *  Copyright (C) 2018 Pascal Bodin
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

import scala.concurrent.{Await, Future, Promise, blocking}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.Random

/**
  * This code sample demonstrates how to start several asynchronous tasks using Future,
  * and how to immediately return a Future that will be completed with one of two
  * possible results:
  * - the result from the first task to end with success (task duration)
  * - an error status if no task ends before a given timeout (-2)
  *
  * A task failure is signalled by an exception. The exception is intercepted and transformed
  * into an error status.
  *
  * Additionally, the code shows how to filter out a Future which does not satisfy
  * a condition: check the foreach applied to every created Future.
  *
  * In this exemple, the body of every task is a wait operation, for a random duration
  * between 0 and maxVal milliseconds. After the wait operation, the task checks the duration.
  * If it has been more than maxVal / 4, it triggers a RuntimeException, otherwise it
  * returns the duration value and task name.
  *
  * As the wait operation is blocking, we can easily reach the maximum number of concurrent
  * threads created by the default execution context. To prevent this, the blocking function
  * is used.
  *
  * How to play with this sample code:
  * - to get a timeout, set maxDuration to a small value
  * - to get a task failure, hard code a long duration for one of the tasks
  */
object TestFuture01 {

  def main(args: Array[String]): Unit = {

    val maxVal = 30000
    val maxDuration = 32000 // ms

    // Task names.
    val nameArray = List("task1", "task2", "task3", "task4")

    /**
      *
      * The piece of code that will be executed by each Future.
      *
      */
    def taskComputation(o: String): (Int, String) = {
      val d = Random.nextInt(maxVal)
      println(o + " about to sleep for " + d)
      Thread.sleep(d)
      println("end of sleep for " + o)
      if (d > maxVal / 4) {
        println(s"throwing exception for $o")
        throw new RuntimeException(s"value too large for $o")
      }
      else
        (d, o)
    }

    /**
      *
      * The transformation that converts the exception into an error status.
      *
      */
    def recovery: PartialFunction[Throwable, (Int, String)] = {
      case e: RuntimeException => (-1, "")
    }

    /**
      *
      * Start one Future per task, and the timeout Future, and returns a Future that
      * will point to an acceptable result.
      *
      */
    def getFirstSuccess: Future[(Int, String)] = {

      // The Promise used to get the first result before a possible timeout.
      val synchroP = Promise[(Int, String)]

      // One Future per task.
      for (name <- nameArray) {
        Future {
          blocking {
            taskComputation(name)
          }
        } // Transform possible exception into (-1, "")
          .recover(recovery)
          // Make the Promise succeed only if Future result is what we are waiting for.
          .foreach({ case (status, name) =>
          if (status > 0) synchroP.trySuccess((status, name))
        })
      }

      // Timeout.
      Future {
        blocking {
          println("timeout about to sleep for " + maxDuration)
          Thread.sleep(maxDuration)
          println("end of sleep for timeout")
        }
        (-2, "")
      } foreach synchroP.trySuccess

      val result = synchroP.future
      println("result Future returned")
      result

    }

    // Display number of available processors.
    val runtime = Runtime.getRuntime
    println("available processors: " + runtime.availableProcessors)

    // Let's go!
    println("waiting for result...")
    val r = Await.result(getFirstSuccess, Duration.Inf)
    println("result is: " + r._1 + " - " + r._2)

  }
}
