package com.qianmi.bugatti.actors

import java.net.InetSocketAddress

import akka.actor.{ActorLogging, Actor, ActorRef}
import akka.event.LoggingReceive
import akka.io.{Udp, IO}

/**
 * Created by mind on 8/6/14.
 */
class LogUdpActor(commandsActor: ActorRef, ipAddress: String, port: Int) extends Actor with ActorLogging {
  import context.system
  IO(Udp) ! Udp.Bind(self, new InetSocketAddress(ipAddress, port))

  def receive = LoggingReceive {
    case Udp.Bound(local) =>
      context.become(ready(sender))
  }

  def ready(socket: ActorRef): Receive = {
    case Udp.Received(data, remote) => {
      val logStr = data.decodeString("utf-8").dropRight(1) // 删除行尾的\0
      log.debug(s"udp receive: ${logStr}")
      if (logStr.contains("Executing command")){
        val jid = logStr.replaceAll("""^.* with jid +(\d{20})""", "$1")
        if (jid.size == 20) {
          commandsActor ! JobBegin(jid, remote.getHostName)
        }
      }
    }
    case Udp.Unbind  => socket ! Udp.Unbind
    case Udp.Unbound => context.stop(self)
  }
}