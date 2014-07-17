package com.qianmi.bugatti.actors

import java.util.concurrent.TimeUnit

import akka.actor._
import akka.io.IO
import akka.util.Timeout
import com.qianmi.bugatti.http.MyServiceActor
import spray.can.Http
import akka.pattern.ask

import scala.concurrent.duration._

/**
 * Created by mind on 7/15/14.
 */
object Spirit {
  def main(args: Array[String]) {
    implicit val system = ActorSystem("Spirit")
    implicit val timeout = Timeout(5.seconds)

    val commandsActor = system.actorOf(Props[CommandsActor], name = "Commands")

//    import system.dispatcher
//    system.scheduler.schedule(1.seconds, 1.seconds, commandsActor, CheckResults)
//    fileWatchActor ! MonitorDir(Paths.get("/Users/mind/var/cache/salt/master/jobs"))

    val service = system.actorOf(Props[MyServiceActor], "spirit-http-service")

    IO(Http) ? Http.Bind(service, interface = "0.0.0.0", port = 2551)

    if (args.size > 0 && args(0) == "test") {
      TimeUnit.SECONDS.sleep(3)
      test(commandsActor, system)
    }
  }


  def test(commandsActor: ActorRef, system: ActorSystem) = {

    val inbox = Inbox.create(system)

    val doCount = 1

    for (i <- 1 to doCount) {
      inbox.send(commandsActor, SaltCommand(i.toString, Seq("salt", "minion0", "state.sls", "java.install")))

    }

    for (j <- 1 to doCount) {
      val SaltResult(commandId, lines, time) = inbox.receive(30.seconds)
      println(s"commandId: ${commandId} execute ${time}s")
      lines.takeRight(4).foreach(println)
    }
  }

}

