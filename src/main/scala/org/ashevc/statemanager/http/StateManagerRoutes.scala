package org.ashevc.statemanager.http

import akka.actor.ActorRef
import akka.dispatch.ExecutionContexts.global
import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.{`Access-Control-Allow-Credentials`, `Access-Control-Allow-Headers`, `Access-Control-Allow-Methods`, `Access-Control-Allow-Origin`}
import akka.http.scaladsl.server.{Directive0, Directives, Route}
import akka.util.Timeout
import akka.pattern.ask
import org.ashevc.statemanager.entity.UpdateState
import org.ashevc.statemanager.entity.EntityRegistry.{CreateEntity, GetEntities, GetEntity, UpdateStateOfEntity}
import org.ashevc.statemanager.history.HistoryRegistry.GetHistory
import org.ashevc.statemanager.transition.TransitionRegistry.GetTransitions
import akka.http.scaladsl.model.HttpMethods._
import org.ashevc.statemanager.entity.{Entity, UpdateState}
import org.ashevc.statemanager.entity.EntityRegistry.{CreateEntity, GetEntities}
import org.ashevc.statemanager.history.EntityTransition
import org.ashevc.statemanager.history.HistoryRegistry.GetHistory
import org.ashevc.statemanager.json.JsonFormats
import org.ashevc.statemanager.response.APIError
import org.ashevc.statemanager.state.State
import org.ashevc.statemanager.transition.CreateTransition
import org.ashevc.statemanager.transition.TransitionRegistry.GetTransitions

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

case class StateManagerRoutes(entityRegistry: ActorRef,
                              historyRegistry: ActorRef,
                              transitionRegistry: ActorRef) extends Directives with JsonFormats with CORSHandler {

  implicit val timeout: Timeout = Timeout(25.seconds)

  implicit val ec: ExecutionContextExecutor = global

  val stateRoutes: Route =
    corsHandler {
      path("entities") {

        post {
          entity(as[CreateEntity]) { createEntity =>
            handleAsyncRequest(
              (entityRegistry ? createEntity).mapTo[Either[APIError, Entity]]
            )
          }
        } ~
          get {
            handleAsyncRequest(
              (entityRegistry ? GetEntities).mapTo[Either[APIError, List[Entity]]]
            )
          }
      } ~
        path("entities" / LongNumber) { entityId =>
          get {
            handleAsyncRequest(
              (entityRegistry ? GetEntity(entityId)).mapTo[Either[APIError, Entity]]
            )
          }
        } ~
        path("entities" / LongNumber / "state") { entityId =>
          put {
            entity(as[UpdateState]) { updateState =>
              handleAsyncRequest(
                (entityRegistry ? UpdateStateOfEntity(entityId, updateState.state))
                  .mapTo[Either[APIError, Entity]]
              )
            }
          }
        } ~
        path("entities" / LongNumber / "history") { entityId =>
          get {
            handleAsyncRequest(
              (historyRegistry ? GetHistory(entityId)).mapTo[Either[APIError, List[EntityTransition]]]
            )
          }
        } ~
        path("history") {
          get {
            handleAsyncRequest(
              (historyRegistry ? GetHistory).mapTo[Either[APIError, List[EntityTransition]]]
            )
          }
        } ~
        path("transitions") {
          get {
            handleAsyncRequest(
              (transitionRegistry ? GetTransitions).mapTo[Either[APIError, Map[State, Set[State]]]]
            )
          } ~
            post {
              entity(as[CreateTransition]) { createTransition =>
                handleAsyncRequest(
                  (transitionRegistry ? createTransition).mapTo[Either[APIError, Map[State, Set[State]]]]
                )
              }
            }
        }
    }


  def handleAsyncRequest[A: ToResponseMarshaller](f: => Future[Either[APIError, A]]): Route = {
    onComplete(f) {
      case Success(res) =>
        res match {
          case Right(entity) => complete(entity)
          case Left(error) =>
            complete(error.statusCode, error)
        }
      case Failure(error) =>
        complete(InternalServerError, error.getMessage)
    }
  }
}

trait CORSHandler {
  this: Directives =>

  val corsResponseHeaders = List(

    `Access-Control-Allow-Origin`.*,

    `Access-Control-Allow-Credentials`(true),

    `Access-Control-Allow-Headers`("Authorization", "Content-Type", "X-Requested-With")

  )

  //this directive adds access control headers to normal responses

  private def addAccessControlHeaders: Directive0 = {

    respondWithHeaders(corsResponseHeaders)

  }

  //this handles preflight OPTIONS requests.

  private def preflightRequestHandler: Route = options {

    complete(HttpResponse(StatusCodes.OK).

      withHeaders(`Access-Control-Allow-Methods`(OPTIONS, POST, PUT, GET, DELETE)))

  }

  // Wrap the Route with this method to enable adding of CORS headers

  def corsHandler(r: Route): Route = addAccessControlHeaders {

    preflightRequestHandler ~ r

    }

  // Helper method to add CORS headers to HttpResponse

  // preventing duplication of CORS headers across code

  private def addCORSHeaders(response: HttpResponse): HttpResponse =

    response.withHeaders(corsResponseHeaders)

}
