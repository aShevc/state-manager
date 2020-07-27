package org.ashevc.statemanager

import akka.actor.{ActorSystem, Props}
import org.ashevc.statemanager.config.StateManagerConfig

object StateManagerApp extends App {

  implicit val system: ActorSystem = ActorSystem("state-manager")

  system.actorOf(Props(new Guardian(StateManagerConfig())))
}
