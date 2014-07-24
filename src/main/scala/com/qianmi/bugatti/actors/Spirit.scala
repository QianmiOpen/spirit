package com.qianmi.bugatti.actors


import akka.actor._
import akka.io.IO
import com.typesafe.config.ConfigFactory
import spray.can.Http

/**
 * Created by mind on 7/15/14.
 */
object Spirit {
  lazy val ipAddress = ConfigFactory.load.getString("akka.remote.netty.tcp.hostname")

  lazy val httpPort = ConfigFactory.load.getInt("spray.can.server.port")

  implicit val system = ActorSystem("Spirit")

  def main(args: Array[String]) {

    val commandsActor = system.actorOf(Props[CommandsActor], name = "SpiritCommands")
    val httpActor = system.actorOf(Props(classOf[HttpServiceActor], commandsActor), "SpiritHttpService")
    IO(Http) ! Http.Bind(httpActor, interface = ipAddress, port = httpPort)
  }
}
