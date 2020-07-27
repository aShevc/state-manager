package org.ashevc.statemanager.config

import com.typesafe.config.{Config, ConfigFactory}

import scala.jdk.CollectionConverters._

case class StateManagerConfig() {
  protected final val configPrefix = "state-manager"

  protected final val HttpPort = s"$configPrefix.http.port"

  lazy val config: Config = ConfigFactory.load

  def getHttpPort: Int = getInt(HttpPort).getOrElse(8080)

  // config helpers
  protected def getString(path: String): Option[String] = get(path, _.getString)

  protected def getStringList(path: String): Option[List[String]] = get(path, _.getStringList).map(x => x.asScala.toList)

  protected def getInt(path: String): Option[Int] = get(path, _.getInt)

  protected def getLong(path: String): Option[Long] = get(path, _.getLong)

  protected def getDouble(path: String): Option[Double] = get(path, _.getDouble)

  protected def getBoolean(path: String): Option[Boolean] = get(path, _.getBoolean)

  protected def get[T](path: String, getter: Config => String => T): Option[T] = {
    if (config.hasPath(path)) Some(getter(config)(path))
    else None
  }

  override def toString: String = {
    s"""
       |$HttpPort -> $getHttpPort
       |""".stripMargin
  }
}
