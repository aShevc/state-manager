package org.ashevc.statemanager.response

import akka.http.scaladsl.model.StatusCode
import akka.http.scaladsl.model.StatusCodes._

case class APIError(statusCode: StatusCode, message: String)

object APIError {

  def create(error: StateManagerError, message: String): APIError = error match {
    case InvalidState =>
      APIError(BadRequest, message)
    case InvalidTransition =>
      APIError(BadRequest, message)
    case EntityNotFound =>
      APIError(NotFound, message)
  }
}

sealed trait StateManagerError
case object InvalidState extends StateManagerError
case object InvalidTransition extends StateManagerError
case object EntityNotFound extends StateManagerError

