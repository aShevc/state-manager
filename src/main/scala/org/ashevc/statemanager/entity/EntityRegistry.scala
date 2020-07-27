package org.ashevc.statemanager.entity

import akka.actor.{Actor, ActorRef, Stash}
import akka.dispatch.ExecutionContexts.global
import EntityRegistry.{CreateEntity, GetEntities, GetEntity, ReportTransitions, UpdateStateOfEntity}
import org.ashevc.statemanager.history.HistoryRegistry.RecordTransition
import org.ashevc.statemanager.response.EntityNotFound

import scala.concurrent.duration._
import akka.util.Timeout
import org.ashevc.statemanager.state.States
import org.ashevc.statemanager.state.States._
import org.ashevc.statemanager.entity.EntityRegistry.GetEntities
import org.ashevc.statemanager.response.{APIError, EntityNotFound, InvalidTransition}
import org.ashevc.statemanager.state.{State, States}
import org.slf4j.LoggerFactory

import scala.concurrent.ExecutionContextExecutor

object EntityRegistry {

  sealed trait Command

  final case class GetEntities()

  final case class GetEntity(id: Long)

  final case class CreateEntity(name: String, state: String)

  final case class UpdateStateOfEntity(id: Long, state: String)

  final case class InitTransitionRegistry(actorRef: ActorRef)

  final case class ReportTransitions(transitions: Map[State, Set[State]])

}

case class EntityRegistry(historyRegistry: ActorRef,
                          initialEntities: Map[Long, Entity] = Map[Long, Entity]()) extends Actor with Stash {

  implicit val ec: ExecutionContextExecutor = global

  private val log = LoggerFactory.getLogger(this.getClass)

  implicit val timeout: Timeout = Timeout(5.seconds)

  private var idCounter: Long = 1

  private final var entities: Map[Long, Entity] = initialEntities

  private var transitions: Map[State, Set[State]] = Map()

  override def receive(): Receive = {
    case ReportTransitions(transitionsToSet) =>
      log.info(s"Received Initial Transitions: $transitionsToSet")
      transitions = transitionsToSet
      unstashAll()
      context.become(running())
    case _ =>
      stash()
  }

  def running(): Receive = {
    case CreateEntity(name, state) =>
      sender() ! States.fromString(state).map { initialState =>
        val id = idCounter
        log.info(s"Received create entity request: for name $name")
        val entity = Entity(idCounter, name, initialState)
        idCounter += 1
        entities = entities + (id -> Entity(id, name, initialState))
        historyRegistry ! RecordTransition(id, NoState, initialState)
        entity
      }
    case UpdateStateOfEntity(id, state) =>
      States.fromString(state) match {
        case Right(toState) =>
          entities.get(id) match {
            case Some(entity) => updateState(entity, toState)
            case None => sender() ! Left(APIError.create(EntityNotFound, s"Entity with id $id not found"))
          }
        case x => sender() ! x
      }
    case GetEntity(id) =>
      sender() ! entities.get(id).map(Right(_)).getOrElse(
        Left(APIError.create(EntityNotFound, s"Entity with id $id not found")))
    case GetEntities =>
      sender() ! Right(entities.values.toList)
    case ReportTransitions(transitionsToSet) =>
      log.info(s"Received updated transitions: $transitionsToSet")
      transitions = transitionsToSet
  }

  private def updateState(entity: Entity, toState: State): Unit = {
    val validTransitions = transitions.getOrElse(entity.state, Set())
    if (!validTransitions.contains(toState)) {
      sender() ! Left(APIError.create(InvalidTransition, s"Transition from state" +
        s" ${entity.state.name} to state ${toState.name} is invalid. Available states to transition to: ${validTransitions.map(_.name)}"))
    } else {
      val updatedEntity = entity.copy(state = toState)
      entities = entities + (entity.id -> updatedEntity)
      historyRegistry ! RecordTransition(entity.id, entity.state, toState)
      sender() ! Right(updatedEntity)
    }
  }
}