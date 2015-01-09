package com.qianmi.bugatti.actors

import java.io.File

import akka.actor.{Actor, ActorLogging}
import akka.event.LoggingReceive
import akka.util.Timeout

import scala.concurrent.duration._
import scala.language.postfixOps
/**
 * Created by mind on 12/10/14.
 */

trait HostCommand

case class ListHosts() extends HostCommand with SpiritCommand
case class ListHostsResult(hostNames: Seq[String]) extends SpiritResult

case class RemoveHost(hostName: String) extends HostCommand with SpiritCommand
case class RemoveHostResult(hostName: String, result: Boolean) extends SpiritResult

class SaltHostsActor(keyPath: String) extends Actor with ActorLogging {

  val keyPathFile = new File(keyPath)

  implicit val timeout:Timeout = 20 second

  override def preStart(): Unit = {
    if (!keyPathFile.exists()) {
      log.error(s"Path '$keyPathFile' not exists")
    }
  }

  override def receive: Receive = LoggingReceive {
    case lh: ListHosts => {
      sender ! ListHostsResult(keyPathFile.listFiles().map(_.getName))
    }

    case RemoveHost(hostName) => {
      val file = new File(s"${keyPathFile.getAbsolutePath}/$hostName")

      if (file.exists()) {
        val deleted = file.delete()
        if (!deleted) {
          log.warning(s"Delete $hostName error.")
        }
        sender ! RemoveHostResult(hostName, deleted)
      } else {
        sender ! RemoveHostResult(hostName, false)
      }
    }

    case x => {
      log.warning(s"Unknown message $x")
    }
  }
}
