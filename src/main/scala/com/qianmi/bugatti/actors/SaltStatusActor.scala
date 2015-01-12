package com.qianmi.bugatti.actors

import java.net.InetAddress

import akka.actor.{Actor, ActorRef, FSM, LoggingFSM}
import com.qianmi.bugatti.actors.SaltStatusActor._

import scala.concurrent.duration._
import scala.language.postfixOps
/**
 * Created by mind on 12/26/14.
 */

object SaltStatusActor {

  sealed trait State

  case object S_Init extends State
  case object S_Ping extends State
  case object S_Grains extends State
  case object S_Finish extends State

  case class CanPing(ping: Boolean)
}

class SaltStatusActor(remoteActor: ActorRef) extends LoggingFSM[State, SaltStatusResult] {
  startWith(S_Init, SaltStatusResult(null, null, false, false, ""))

  when(S_Init) {
    case Event(SaltStatus(hostName, hostIp, needMInfo), data: SaltStatusResult) =>
      goto(S_Ping) using data.copy(hostName = hostName, hostIp = hostIp, needMInfo = needMInfo)
  }

  when(S_Ping, stateTimeout = 5 second) {
    case Event(CanPing(ping), data: SaltStatusResult) =>
      stay using data.copy(canPing = ping)
    case Event(sb: SaltJobBegin, _) =>
      stay
    case Event(sb: SaltJobOk, data: SaltStatusResult) =>
      (if (data.needMInfo) { goto(S_Grains) } else { goto(S_Finish) }) using data.copy(canSPing = true)

    case Event(StateTimeout, data: SaltStatusResult) =>
      goto(S_Finish) using data.copy(canSPing = false)
  }

  when(S_Grains, stateTimeout = 5 second) {
    case Event(sb: SaltJobBegin, _) =>
      stay
    case Event(SaltJobOk(result, _), data: SaltStatusResult) =>
      goto(S_Finish) using data.copy(mmInfo = result)
    case Event(StateTimeout, data: SaltStatusResult) =>
      goto(S_Finish)
  }

  when(S_Finish)(FSM.NullFunction)

  whenUnhandled {
    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

  onTransition {
    case S_Init -> S_Ping => {
      if (nextStateData.hostIp == null || nextStateData.hostIp.isEmpty) {
        self ! CanPing(false)
      } else {
        val addr = InetAddress.getByName(nextStateData.hostIp)
        self ! CanPing(addr.isReachable(3))
      }
      context.parent ! SaltCommand(Seq("salt", nextStateData.hostName, "test.ping"))
    }

    case S_Ping -> S_Grains => {
      context.parent ! SaltCommand(Seq("salt", nextStateData.hostName, "grains.items"))
    }

    case (S_Ping | S_Grains) -> S_Finish => {
      remoteActor ! nextStateData

      context.stop(self)
    }
  }

  initialize()
}
