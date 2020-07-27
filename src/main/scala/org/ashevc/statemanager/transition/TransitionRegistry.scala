package org.ashevc.statemanager.transition

import akka.actor.{Actor, ActorRef}
import org.ashevc.statemanager.entity.EntityRegistry.ReportTransitions
import org.ashevc.statemanager.state.States
import org.ashevc.statemanager.state.States._
import TransitionRegistry.GetTransitions
import org.ashevc.statemanager.state.{State, States}
import org.ashevc.statemanager.transition.TransitionRegistry.GetTransitions

object TransitionRegistry {

  sealed trait Command

  final case class GetTransitions()
}

case class TransitionRegistry(entityRegistry: ActorRef,
                              defaultTransitions: Map[State, Set[State]] = Map(Init -> Set(Pending),
                                Pending -> Set(Finished), Finished -> Set(Closed))) extends Actor {

  private final var transitions = defaultTransitions

  override def preStart(): Unit = {
    entityRegistry ! ReportTransitions(transitions)

    super.preStart()
  }

  override def receive: Receive = {
    case CreateTransition(from, to) =>
      sender() ! (for {
        fromState <- States.fromString(from)
        toStates <- States.stateSetFromList(to)
      } yield {
        transitions = transitions + (fromState -> toStates)
        entityRegistry ! ReportTransitions(transitions)
        Map(fromState -> toStates)
      })

    case GetTransitions =>
      sender() ! Right(transitions)
  }
}
