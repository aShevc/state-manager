package org.ashevc.statemanager.entity

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.StatusCodes
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import EntityRegistry.{CreateEntity, GetEntity, ReportTransitions, UpdateStateOfEntity}
import org.ashevc.statemanager.history.HistoryRegistry.RecordTransition
import org.ashevc.statemanager.state.States
import org.ashevc.statemanager.state.States.{Closed, Finished, Init, Pending}
import org.ashevc.statemanager.response.APIError
import org.ashevc.statemanager.state.{State, States}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

class EntityRegistrySpec extends TestKit(ActorSystem("EntityRegistrySpec"))
  with ImplicitSender
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val timeout: Timeout = Timeout(25.seconds)

  protected val defaultTimeout: FiniteDuration = 10.seconds

  override def afterAll: Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  case class SUT(historyRegistry: TestProbe = TestProbe(),
                 initialEntities: Map[Long, Entity] = Map[Long, Entity](),
                 initialTransitions: Map[State, Set[State]] = Map(Init -> Set(Pending),
                   Pending -> Set(Finished), Finished -> Set(Closed))) {

    val actor: ActorRef = system.actorOf(Props(new EntityRegistry(historyRegistry.ref, initialEntities)))

    actor ! ReportTransitions(initialTransitions)
  }

  val defaultTransitions = Map(Init -> Set(Pending),
    Pending -> Set(Finished), Finished -> Set(Closed))

  "EntityRegistry" should {
    "Create new entity" in {
      val sut = SUT()

      val entityName = "test"
      val entityState = States.Init

      val createEntity = CreateEntity(entityName, entityState.name)

      sut.actor ! createEntity

      var entityId: Option[Long] = None

      expectMsgPF(defaultTimeout) {
        case Right(Entity(id, name, state)) =>
          name shouldEqual entityName
          state shouldEqual entityState
          entityId = Some(id)
      }

      sut.actor ! GetEntity(entityId.get)
      expectMsgPF(defaultTimeout) {
        case Right(Entity(id, name, state)) =>
          name shouldEqual entityName
          state shouldEqual entityState
          id shouldEqual entityId.get
      }
    }
    "Update entities state to existing state through a valid transition and update the entity transition history" in {
      val entityName = "test"
      val entityState = States.Init
      val entityId = 1L
      val entity = Entity(entityId, entityName, entityState)
      val newState = States.Pending

      val sut = SUT(initialEntities = Map(entityId -> entity))

      sut.actor ! UpdateStateOfEntity(entityId, newState.name)

      expectMsgPF(defaultTimeout) {
        case Right(Entity(id, name, state)) =>
          id shouldEqual entityId
          name shouldEqual entityName
          state shouldEqual newState
      }

      sut.historyRegistry.expectMsgPF(defaultTimeout) {
        case RecordTransition(id, fromState, toState) =>
          id shouldEqual entityId
          fromState shouldEqual entityState
          toState shouldEqual newState
      }
    }
    "Not update entities state in case if the transition is invalid" in {
      val entityName = "test"
      val entityState = States.Init
      val entityId = 1L
      val entity = Entity(entityId, entityName, entityState)
      val newState = States.Closed

      val sut = SUT(initialEntities = Map(entityId -> entity))

      sut.actor ! UpdateStateOfEntity(entityId, newState.name)

      expectMsgPF(defaultTimeout) {
        case Left(APIError(statusCode, message)) =>
          statusCode shouldEqual StatusCodes.BadRequest
          message shouldEqual "Transition from state" +
            s" ${entity.state.name} to state ${newState.name} is invalid. Available states to transition to: Set(pending)"
      }
    }
  }
}
