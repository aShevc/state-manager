package org.ashevc.statemanager.http

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.TestKit
import akka.util.Timeout
import org.ashevc.statemanager.entity.EntityRegistry
import org.ashevc.statemanager.entity.EntityRegistry.CreateEntity
import org.ashevc.statemanager.history.HistoryRegistry
import org.ashevc.statemanager.state.States.{Closed, Finished, Init, Pending}
import org.ashevc.statemanager.transition.CreateTransition
import org.scalatest.BeforeAndAfterEach
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration._
import akka.testkit._
import org.ashevc.statemanager.state.States
import org.ashevc.statemanager.entity.{Entity, EntityRegistry, UpdateState}
import org.ashevc.statemanager.history.{EntityTransition, HistoryRegistry}
import org.ashevc.statemanager.json.JsonFormats
import org.ashevc.statemanager.response.APIError
import org.ashevc.statemanager.state.{State, States}
import org.ashevc.statemanager.transition.{CreateTransition, TransitionRegistry}

class IStateManagerRoutesSpec extends AnyWordSpecLike
  with Matchers
  with ScalatestRouteTest
  with JsonFormats with BeforeAndAfterEach {

  case class SUT(entityRegistry: ActorRef,
                 historyRegistry: ActorRef,
                 transitionRegistry: ActorRef) {

    val routes: Route = StateManagerRoutes(entityRegistry, historyRegistry, transitionRegistry).stateRoutes

  }

  implicit def default(implicit system: ActorSystem): RouteTestTimeout = RouteTestTimeout(25.second.dilated)

  override def afterAll: Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  "StateManagerRoutes" should {
    "Get list of valid transitions" in {
      val validTransitions = Map(Init -> Set(Pending),
        Pending -> Set(Finished), Finished -> Set(Closed))

      val historyRegistry: ActorRef = system.actorOf(Props(new HistoryRegistry), "history-registry-1")
      val entityRegistry: ActorRef = system.actorOf(Props(new EntityRegistry(historyRegistry)), "entity-registry-1")
      val transitionRegistry: ActorRef = system.actorOf(Props(new TransitionRegistry(entityRegistry, validTransitions)),
        "transition-registry-1")

      val sut = SUT(historyRegistry, entityRegistry, transitionRegistry)

      Get("/transitions") ~> sut.routes ~> check {
        responseAs[Map[State, Set[State]]] shouldEqual validTransitions
        status shouldEqual StatusCodes.OK
      }
    }
    "Create an entity and view it" in {

      val historyRegistry: ActorRef = system.actorOf(Props(new HistoryRegistry), "history-registry-2")
      val entityRegistry: ActorRef = system.actorOf(Props(new EntityRegistry(historyRegistry)), "entity-registry-2")
      val transitionRegistry: ActorRef = system.actorOf(Props(new TransitionRegistry(entityRegistry)),
        "transition-registry-2")

      val sut = SUT(entityRegistry, historyRegistry, transitionRegistry)

      val entityName = "test"

      val entityInitState = "init"

      var entityId: Option[Long] = None

      val createEntity = CreateEntity(entityName, entityInitState)

      Post("/entities", createEntity) ~> sut.routes ~> check {
        val entity = responseAs[Entity]
        entity.name shouldEqual entityName
        entity.state shouldEqual States.fromString(entityInitState).merge
        entityId = Some(entity.id)
        status shouldEqual StatusCodes.OK
      }

      Get("/entities") ~> sut.routes ~> check {
        val entity = responseAs[List[Entity]]
        entity.size shouldEqual 1
        entity.headOption.map { e =>
          e shouldEqual Entity(entityId.get, entityName, States.fromString(entityInitState).merge.asInstanceOf[State])
        }
        status shouldEqual StatusCodes.OK
      }
    }
    "Update state of the entity only after transition to this state is made valid, and view entity transition history" in {

      val validTransitions = Map(Init -> Set(Pending),
        Pending -> Set(Finished), Finished -> Set(Closed))

      val initState = States.Init
      val transitionState = States.Closed

      var entityId: Option[Long] = None
      val entityName = "test"

      val historyRegistry: ActorRef = system.actorOf(Props(new HistoryRegistry), "history-registry-3")
      val entityRegistry: ActorRef = system.actorOf(Props(new EntityRegistry(historyRegistry)), "entity-registry-3")
      val transitionRegistry: ActorRef = system.actorOf(Props(new TransitionRegistry(entityRegistry, validTransitions)),
        "transition-registry-3")

      val sut = SUT(entityRegistry, historyRegistry, transitionRegistry)

      val updateState = UpdateState(transitionState.name)

      val createEntity = CreateEntity(entityName, initState.name)

      Post("/entities", createEntity) ~> sut.routes ~> check {
        val ent = responseAs[Entity]
        entityId = Some(ent.id)
        status shouldEqual StatusCodes.OK
      }

      Put(s"/entities/${entityId.get}/state", updateState) ~> sut.routes ~> check {
        val error = responseAs[APIError]
        error.message shouldEqual s"Transition from state ${initState.name} to state ${transitionState.name} is invalid." +
          s" Available states to transition to: ${validTransitions(initState).map(_.name)}"
        status shouldEqual StatusCodes.BadRequest
      }

      val newInitStates = CreateTransition(initState.name, List(transitionState.name, States.Pending.name))

      Post(s"/transitions", newInitStates) ~> sut.routes ~> check {
        val createdTransition = responseAs[Map[State, Set[State]]]
        createdTransition shouldEqual Map(initState -> Set(transitionState, States.Pending))
        status shouldEqual StatusCodes.OK
      }

      Put(s"/entities/${entityId.get}/state", updateState) ~> sut.routes ~> check {
        val entity = responseAs[Entity]
        entity shouldEqual entity.copy(state = transitionState)
        status shouldEqual StatusCodes.OK
      }

      Get(s"/entities/${entityId.get}/history") ~> sut.routes ~> check {
        val transitions = responseAs[List[EntityTransition]]
        transitions.size shouldEqual 2

        transitions match {
          case List(EntityTransition(entityId1, from1, to1, _), EntityTransition(entityId2, from2, to2, _))
            if entityId1 == entityId.get && entityId2 == entityId.get =>
              from1 shouldEqual States.NoState
              to1 shouldEqual initState
              from2 shouldEqual initState
              to2 shouldEqual transitionState
          case _ => fail()
        }

        status shouldEqual StatusCodes.OK
      }
    }
  }
}
