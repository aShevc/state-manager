package org.ashevc.statemanager.http

import java.net.InetSocketAddress

import akka.actor.{Actor, ActorSystem, Status}
import akka.http.scaladsl.Http
import akka.pattern.pipe
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import org.ashevc.statemanager.config.StateManagerConfig
import org.slf4j.LoggerFactory

class HttpApp(config: StateManagerConfig, routes: Route)
             (implicit materializer: Materializer, system: ActorSystem) extends Actor {

  import context.dispatcher

  private val apiPort: Int = config.getHttpPort

  private val log = LoggerFactory.getLogger(this.getClass)

  override def preStart(): Unit = {
    Http(context.system)
      .bindAndHandle(routes, "0.0.0.0", apiPort)
      .pipeTo(self)
  }

  def receive: Receive = {
    case Http.ServerBinding(a) => handleBinding(a)
    case Status.Failure(c)     => handleBindFailure(c)
  }

  private def handleBinding(address: InetSocketAddress): Unit = {
    log.info("Listening on {}", address)
    context.become(Actor.emptyBehavior)
  }

  private def handleBindFailure(cause: Throwable): Unit = {
    log.error(s"Can't bind to 0.0.0.0:$apiPort!: $cause")
    context.stop(self)
  }
}