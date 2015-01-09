package com.qianmi.bugatti.actors


import akka.actor._
import akka.io.IO
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.can.Http
import akka.pattern.ask
import scala.concurrent.duration._

import scala.concurrent.Await
import scala.language.postfixOps

/**
 * Created by mind on 7/15/14.
 */
object Spirit {

  def init(actorSystem: ActorSystem): (ActorRef, ActorRef) = {
    implicit val timeout: Timeout = 5 second

    // get config
    val ipAddress = ConfigFactory.load.getString("akka.remote.netty.tcp.hostname")
    val httpPort = ConfigFactory.load.getInt("spray.can.server.port")

    // init commands actor
    val commandsActor = actorSystem.actorOf(Props[CommandsActor], name = "SpiritCommands")

    // init http actor
    val httpActor = actorSystem.actorOf(Props(classOf[HttpServiceActor], commandsActor), "SpiritHttpService")
    val fh = IO(Http)(actorSystem) ? Http.Bind(httpActor, interface = ipAddress, port = httpPort)
    Await.result(fh, timeout.duration)

    (commandsActor, httpActor)
  }

  def main(args: Array[String]) {
    init(ActorSystem("Spirit"))
  }
}
