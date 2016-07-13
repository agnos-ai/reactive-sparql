/**
 * Helper functions / values / variables for functional tests.
 */

package com.modelfabric.test

import _root_.akka.actor.{ActorRef, ActorSystem}

import scala.language.postfixOps

/**
 * Provides helper and utility functions for tests.
 */
// JC: According to me, 'Helpers' is not a valid class/object name.
// the only place this object is used is in HttpEndpointSuiteTestRunner. I'm sure whatever defined here can be moved over.
object Helpers {

  val webActorName = "service-web"

  var server: ActorRef = null

  /**
   * Shutdown the system if it hasn't been done so already.
   *
   * @param system The ActorSystem currently in use.
   */
  def shutdownSystem(implicit system: ActorSystem) {
    //if (!system.is) {
      system.terminate()
    //}

    // Give the OS some time to clean up the current port. This sucks.
    Thread.sleep(250)
  }
}
