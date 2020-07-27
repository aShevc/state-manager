package org.ashevc.statemanager.transition

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.http.scaladsl.model.StatusCodes
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.Timeout
import org.ashevc.statemanager.entity.EntityRegistry.ReportTransitions
import org.ashevc.statemanager.state.States
import org.ashevc.statemanager.state.States.{Closed, Finished, Init, Pending}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import akka.pattern.ask
import org.ashevc.statemanager.response.APIError
import org.ashevc.statemanager.state.State

import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration

class TransitionRegistrySpec extends TestKit(ActorSystem("TransitionRegistrySpec"))
  with ImplicitSender
  with AnyWordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  implicit val timeout: Timeout = Timeout(25.seconds)

  protected val defaultTimeout: FiniteDuration = 10.seconds

  protected implicit val ec: ExecutionContext = ExecutionContext.global

  override def afterAll: Unit = {
    super.afterAll()
    TestKit.shutdownActorSystem(system)
  }

  case class SUT(entityRegistry: TestProbe = TestProbe(),
                 defaultTransitions: Map[State, Set[State]] = Map(Init -> Set(Pending),
                   Pending -> Set(Finished), Finished -> Set(Closed))) {

    val actor: ActorRef = system.actorOf(Props(new TransitionRegistry(entityRegistry.ref, defaultTransitions)))
  }

  "TransitionRegistry" should {
    "Update transitions to valida states when needed and notify entity registry about the changes" in {
      //on start should send an update message with initial transitions
      val sut = SUT()

      val transitionUpdate = (Init -> Set(Pending, Closed))

      val updatedTransitions = sut.defaultTransitions + transitionUpdate

      sut.entityRegistry.expectMsgPF(defaultTimeout) {
        case ReportTransitions(transitions) =>
          transitions shouldEqual sut.defaultTransitions
      }

      //update is processed
      val res = (sut.actor ? CreateTransition(transitionUpdate._1.name, transitionUpdate._2.map(_.name).toList)).mapTo[Either[APIError, Map[State, Set[State]]]]

      Await.result(res.map {x => x.merge match {
        case map: Map[State, Set[State]] => map.get(transitionUpdate._1) match {
          case Some(toStates) => toStates shouldEqual transitionUpdate._2
          case _ => fail("Expected to update transitions")
        }
        case _ => fail("Expected to update transitions")
      }}, defaultTimeout)

      //entity is notified
      sut.entityRegistry.expectMsgPF(defaultTimeout) {
        case ReportTransitions(transitions) =>
          transitions shouldEqual updatedTransitions
      }
    }
    "Reject attempt to change transitions to invalid states" in {
      //on start should send an update message with initial transitions
      val sut = SUT()

      val invalidState = "invalid state"

      sut.entityRegistry.expectMsgPF(defaultTimeout) {
        case ReportTransitions(transitions) =>
          transitions shouldEqual sut.defaultTransitions
      }

      //update is processed
      sut.actor ! CreateTransition(Init.name, List(invalidState))

      expectMsgPF(defaultTimeout) {
        case Left(APIError(statusCode, message)) =>
          statusCode shouldEqual StatusCodes.BadRequest
          message shouldEqual s"$invalidState is not a valid state"
      }
    }
  }
}
