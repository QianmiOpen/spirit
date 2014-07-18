package com.qianmi.bugatti.actors


import akka.actor._
import akka.io.IO
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import spray.can.Http

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

/**
 * Created by mind on 7/15/14.
 */
object Spirit {
  lazy val ipAddress = ConfigFactory.load.getString("akka.remote.netty.tcp.hostname")

  lazy val httpPort = ConfigFactory.load.getInt("spray.can.server.port")

  implicit val system = ActorSystem("Spirit")

  def main(args: Array[String]) {

    implicit val timeout = Timeout(5.seconds)

    val commandsActor = system.actorOf(Props[CommandsActor], name = "SpiritCommands")
    val httpActor = system.actorOf(Props(classOf[HttpServiceActor], commandsActor), "SpiritHttpService")
    val bindResult = IO(Http) ? Http.Bind(httpActor, interface = ipAddress, port = httpPort)

    Await.ready(bindResult, 5 seconds)

    if (args.size > 0 && args(0) == "test") {
      test(commandsActor)
    }
  }


  def test(commandsActor: ActorRef, doCount: Int = 1) = {

    val inbox = Inbox.create(system)

    for (i <- 1 to doCount) {
//      inbox.send(commandsActor, SaltCommand(Seq("salt", "minion0", "state.sls", "java.install")))
//      inbox.send(commandsActor, SaltCommand(Seq("salt", "*", "test.ping"), 3))
//      inbox.send(commandsActor, SaltCommand(Seq("salt", "minion0", "test.ping")))
      inbox.send(commandsActor, SaltCommand(i, Seq("salt", "8e6499e6412a", "test.ping")))
    }

    for (j <- 1 to doCount) {
      val SaltResult(lines, time) = inbox.receive(30.seconds)
      println(s"execute ${time}ms: result: ${lines}")
    }
  }

}

