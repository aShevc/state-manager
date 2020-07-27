package org.ashevc.statemanager.state

import org.ashevc.statemanager.response.InvalidState
import org.ashevc.statemanager.response.{APIError, InvalidState}

case class State(name: String)

object States {

  private val InitName = "init"
  private val PendingName = "pending"
  private val FinishedName = "finished"
  private val ClosedName = "closed"
  private val NoStateName = "_"

  val Init: State = State(InitName)
  val Pending: State = State(PendingName)
  val Finished: State = State(FinishedName)
  val Closed: State = State(ClosedName)
  val NoState: State = State(NoStateName)

  def fromString(name: String): Either[APIError, State] = {
    name match {
      case InitName => Right(Init)
      case PendingName => Right(Pending)
      case FinishedName => Right(Finished)
      case ClosedName => Right(Closed)
      case NoStateName => Right(NoState)
      case x => Left(APIError.create(InvalidState, s"$x is not a valid state"))
    }
  }

  def stateSetFromList(name: List[String]): Either[APIError, Set[State]] = {
    var stateSet: Set[State] = Set()
    for (stateName: String <- name.toSet) {
      fromString(stateName) match {
        case Right(state) => stateSet = stateSet + state
        case Left(error) => return Left(error)
      }
    }

    Right(stateSet)
  }
}


