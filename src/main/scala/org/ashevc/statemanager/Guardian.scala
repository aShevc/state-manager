package org.ashevc.statemanager

import akka.actor.{Actor, ActorSystem, Props, Terminated}
import akka.stream.Materializer
import org.ashevc.statemanager.http.StateManagerRoutes
import org.ashevc.statemanager.config.StateManagerConfig
import org.ashevc.statemanager.entity.EntityRegistry
import org.ashevc.statemanager.history.HistoryRegistry
import org.ashevc.statemanager.http.{HttpApp, StateManagerRoutes}
import org.ashevc.statemanager.transition.TransitionRegistry
import org.slf4j.LoggerFactory

class Guardian(config: StateManagerConfig)
              (implicit materializer: Materializer, system: ActorSystem) extends Actor {

  private val log = LoggerFactory.getLogger(this.getClass)

  override def preStart(): Unit = {
    val historyRegistry = system.actorOf(Props(new HistoryRegistry), "history-registry")
    val entityRegistry = system.actorOf(Props(new EntityRegistry(historyRegistry)), "entity-registry")
    val transitionRegistry = system.actorOf(Props(new TransitionRegistry(entityRegistry)), "transition-registry")
    context.watch(historyRegistry)
    context.watch(entityRegistry)
    context.watch(transitionRegistry)

    val httpApp = system.actorOf(Props(new HttpApp(config,
      StateManagerRoutes(entityRegistry, historyRegistry, transitionRegistry).stateRoutes)))

    context.watch(httpApp)

    super.preStart()
  }

  override def receive: Receive = {
    case Terminated(actor) =>
      log.error("Terminating the system because {} terminated!", actor.path)
      context.system.terminate()
  }
}
