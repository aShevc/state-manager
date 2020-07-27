package org.ashevc.statemanager.history

import akka.actor.Actor
import HistoryRegistry.{GetHistory, RecordTransition}
import org.ashevc.statemanager.state.State
import org.joda.time.LocalDateTime

object HistoryRegistry {

  sealed trait Commands

  final case class RecordTransition(entityId: Long, from: State, to: State)

  final case object GetHistory

  final case class GetHistory(entityId: Long)

}

class HistoryRegistry extends Actor {

  var registry: List[EntityTransition] = List[EntityTransition]()

  override def receive: Receive = {
    case RecordTransition(entityId, from, to) =>
      val transition = EntityTransition(entityId, from, to, LocalDateTime.now())
      registry = registry :+ transition
    case GetHistory(entityId) =>
      sender() ! Right(registry.filter(_.entityId == entityId))
    case GetHistory =>
      sender() ! Right(registry)
  }
}