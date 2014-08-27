package com.qianmi.bugatti.actors

import java.net.InetSocketAddress

import akka.actor.{Props, Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive
import akka.io.{IO, Udp}

/**
 * Created by mind on 8/6/14.
 */
class LogUdpActor(commandsActor: ActorRef, ipAddress: String, port: Int) extends Actor with ActorLogging {
  import context.system
  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(ipAddress, port))

  val MaxValidPackageSize = 300

  def receive = LoggingReceive {
    case Udp.Bound(local) =>
      context.become(ready(sender))
  }

  def ready(socket: ActorRef): Receive = {
    case recievedPackage: Udp.Received => {
      if (recievedPackage.data.length <= MaxValidPackageSize) {
        val childName = recievedPackage.sender.getHostName
        context.child(childName).getOrElse{
          context.actorOf(Props(classOf[UdpHostActor], commandsActor), name = childName)
        } ! recievedPackage
      }
    }
    case Udp.Unbind  => {
      socket ! Udp.Unbind
      log.debug(s"Udp.Unbind")
    }
    case Udp.Unbound => {
      context.stop(self)
      log.debug(s"Udp.Unbound")
    }
  }
}

class UdpHostActor(commandsActor: ActorRef) extends Actor with ActorLogging {
  override def receive = {
    case Udp.Received(data, remote) => {
      val logStr = data.decodeString("utf-8").dropRight(1) // 删除行尾的\0
      log.debug(s"udp receive ${data.length}: ${logStr}")
      if (logStr.contains("Executing command")){
        val jid = logStr.replaceAll("""^.* with jid +(\d{20})""", "$1")
        if (jid.size == 20) {
          commandsActor ! JobBegin(jid, remote.getHostName)
        }
      }
    }
  }
}