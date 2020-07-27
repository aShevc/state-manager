package org.ashevc.statemanager.json

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.StatusCodes
import org.ashevc.statemanager.entity.EntityRegistry.CreateEntity
import org.ashevc.statemanager.entity.UpdateState
import org.ashevc.statemanager.state.States
import org.ashevc.statemanager.entity.{Entity, UpdateState}
import org.ashevc.statemanager.entity.EntityRegistry.CreateEntity
import org.ashevc.statemanager.history.EntityTransition
import org.ashevc.statemanager.response.APIError
import org.ashevc.statemanager.state.{State, States}
import org.ashevc.statemanager.transition.CreateTransition
import org.joda.time.LocalDateTime
import org.joda.time.format.ISODateTimeFormat
import spray.json.{DefaultJsonProtocol, DeserializationException, JsString, JsValue, RootJsonFormat, _}

trait JsonFormats extends SprayJsonSupport with DefaultJsonProtocol {

  implicit object StateJsonFormat extends RootJsonFormat[State] {

    override def write(obj: State): JsValue = {
      JsString(obj.name)
    }

    override def read(json: JsValue): State = {
      json match {
        case JsString(name) => States.fromString(name).getOrElse(throw DeserializationException("Couldn't parse state"))
        case _ => throw DeserializationException("Couldn't parse state")
      }
    }
  }

  implicit object LocalDateTimeJsonFormat extends RootJsonFormat[LocalDateTime] {

    override def write(obj: LocalDateTime): JsValue = {
      JsString(obj.toString(ISODateTimeFormat.dateTime()))
    }

    override def read(json: JsValue): LocalDateTime = {
      json match {
        case JsString(value) => LocalDateTime.parse(value)
        case _ => throw DeserializationException("Couldn't parse date")
      }
    }
  }

  implicit val EntityJsonFormat: RootJsonFormat[Entity] = jsonFormat3(Entity)
  implicit val CreateEntityJsonFormat: RootJsonFormat[CreateEntity] = jsonFormat2(CreateEntity)
  implicit val CreateTransitionJsonFormat: RootJsonFormat[CreateTransition] = jsonFormat2(CreateTransition)
  implicit val UpdateStateJsonFormat: RootJsonFormat[UpdateState] = jsonFormat1(UpdateState)
  implicit val EntityTransitionJsonFormat: RootJsonFormat[EntityTransition] = jsonFormat4(EntityTransition)

  implicit object APIErrorJsonFormat extends RootJsonFormat[APIError] {

    override def write(obj: APIError): JsValue = {
      JsObject(
        "message" -> JsString(obj.message)
      )
    }

    override def read(json: JsValue): APIError =
      json.asJsObject.fields.get("message").map {
        case JsString(message) => APIError(StatusCodes.OK, message)
        case _ => throw DeserializationException("Couldn't parse API error")
      }.getOrElse(throw DeserializationException("Couldn't parse API error"))
  }

}
