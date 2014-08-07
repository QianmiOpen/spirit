package com.qianmi.bugatti.actors

import akka.actor._
import akka.event.LoggingReceive
import akka.util.Timeout
import spray.can.Http
import spray.http.HttpMethods._
import spray.http._

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

import scala.language.postfixOps

/**
 * Created by mind on 7/17/14.
 */

class HttpServiceActor(commandsActor: ActorRef) extends Actor with ActorLogging {

  implicit val timeout: Timeout = 3 seconds // for the actor 'asks'

  def receive = LoggingReceive {
    // when a new connection comes in we register ourselves as the connection handler
    case _: Http.Connected => sender ! Http.Register(self)

    case HttpRequest(GET, Uri.Path("/job"), _, _, _) => {
      val result = Await.result((commandsActor ? Status).mapTo[Seq[String]], 3 seconds)

      sender ! HttpResponse(entity = if (result.isEmpty) "Empty" else result.mkString("\n"))
    }

    case HttpRequest(GET, Uri.Path(path), _, _, _) if path startsWith "/test" => {

      val number = path.stripPrefix("/test").stripPrefix("/")

      val doCount = if (number.length <= 0) 1 else number.toInt

      for (i <- 1 to doCount) {
        //      inbox.send(commandsActor, SaltCommand(Seq("salt", "minion0", "state.sls", "java.install")))
        //      inbox.send(commandsActor, SaltCommand(Seq("salt", "*", "test.ping"), 3))
        //      inbox.send(commandsActor, SaltCommand(Seq("salt", "8e6499e6412a", "test.ping")))
        commandsActor ! SaltCommand(Seq("salt", "minion0", "test.ping"))
      }

      sender ! HttpResponse(entity = "test ok")
    }

    case HttpRequest(POST, Uri.Path(path), _, entity, _) if path startsWith "/job" => {

      val args = path.stripPrefix("/job/").split("/")
      if (args.size == 2) {
        val Array(jid, hostName) = args
        val result = entity.data.asString

        commandsActor ! JobFinish(jid, result)
        sender ! HttpResponse(entity = "OK")
      } else {
        log.warning(s"Request Error: \npath: ${path}\nentity:${entity}")
        sender ! HttpResponse(entity = "ERROR")
      }
    }

    case _: HttpRequest => sender ! HttpResponse(status = 404, entity = "Unknown resource!")

    case Timedout(HttpRequest(method, uri, _, _, _)) => {
      sender ! HttpResponse(
        status = 500,
        entity = s"The ${method} request to '${uri}' has timed out..."
      )
    }

    case SaltResult(lines, time) => log.debug(s"execute ${time}ms: result: ${lines}")

    //    case HttpRequest(GET, Uri.Path("/stop"), _, _, _) =>
    //      sender ! HttpResponse(entity = "Shutting down in 1 second ...")
    //      sender ! Http.Close
    //      context.system.scheduler.scheduleOnce(1.second) {
    //        context.system.shutdown()
    //      }
  }
}
