package com.qianmi.bugatti.actors

import akka.actor._
import akka.event.LoggingReceive
import akka.pattern.ask
import akka.util.Timeout
import spray.can.Http
import spray.http.HttpMethods._
import spray.http._

import scala.concurrent.Await
import scala.concurrent.duration._
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

      val jobCount = result.count(_.contains("Job_"))
      val cmdCount = result.length - jobCount

      sender ! HttpResponse(entity = s"""cmdCount: ${cmdCount}, jobCount: ${jobCount}""")
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

    case x => log.debug(s"Unknown message $x")

    case Timedout(HttpRequest(method, uri, _, _, _)) => {
      sender ! HttpResponse(
        status = 500,
        entity = s"The ${method} request to '${uri}' has timed out..."
      )
    }
  }
}
