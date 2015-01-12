package com.qianmi.bugatti.actors

import akka.actor._
import com.qianmi.bugatti.actors.SaltResultActor._
import play.api.libs.json.Json

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by mind on 8/6/14.
 *
 * preStart触发命令，如果没有收到任何终端执行的消息，延时3秒超时，
 * JobBegin触发命令发送等待schedule，延时1秒，等待命令下发完毕。
 * JobFinish触发延时finish的schedule，延时3秒。
 *
 * 整个result延时60秒关闭。
 */

trait JobMsg {
  val jid: String
}

case class JobNotify(jid: String, beginTime: Long, hostName: String, remoteActor: ActorRef) extends JobMsg
case class JobFinish(jid: String, jobResult: String) extends JobMsg

object SaltResultActor {
  sealed trait State
  case object S_Init extends State
  case object S_Notified extends State
  case object S_Stopping extends State
  case object S_Finished extends State
  case object S_TimeOut  extends State
}

case class StateData(remoteActor: ActorRef, hostName: String, jobResult: String, beginTime: Long, jid: String)

private class SaltResultActor extends LoggingFSM[State, StateData] {

  startWith(S_Init, StateData(null, null, null, System.currentTimeMillis(), null))

  when(S_Init) {
    case Event(JobNotify(_, beginTime, hostName, remoteActor), data: StateData) =>
      goto(S_Notified) using data.copy(beginTime = beginTime, remoteActor = remoteActor, hostName = hostName)

    case Event(JobFinish(_, result), data: StateData) =>
      goto(S_Notified) using data.copy(jobResult = result)
  }

  when(S_Notified, stateTimeout = 600 second) {
    case Event(JobNotify(_, beginTime, hostName, remoteActor), data: StateData) =>
      goto(S_Finished) using data.copy(beginTime = beginTime, remoteActor = remoteActor, hostName = hostName)

    case Event(JobFinish(_, result), data: StateData) =>
      goto(S_Finished) using data.copy(jobResult = result)

    case Event(SaltJobStop(jid), data: StateData) =>
      goto(S_Stopping) using data.copy(jid = jid)

    case Event(StateTimeout, _) =>
      goto(S_TimeOut)
  }

  when(S_Stopping){
    case Event(sb: SaltJobBegin, _) =>
      stay

    case Event(SaltJobOk(result, _), data: StateData) =>
      goto(S_Finished) using data.copy(jobResult = result)
  }

  when(S_TimeOut)(FSM.NullFunction)

  when(S_Finished, 10 second) {
    case Event(StateTimeout, _) => {
      context.stop(self)
      stay
    }
  }

  whenUnhandled {
    case Event(e, s) =>
      log.warning("received unhandled request {} in state {}/{}", e, stateName, s)
      stay
  }

  onTransition {
    case S_Notified -> S_Finished => {
      val retJson = Json.parse(nextStateData.jobResult)
      val resultLines = (retJson \ "result" \ "return").validate[Seq[String]].asOpt.getOrElse(Seq.empty)
      log.debug(s"resultLines: ${resultLines}")

      if (resultLines.nonEmpty && resultLines.last.contains("is running as PID")) {
        nextStateData.remoteActor ! SaltJobError(resultLines.mkString, System.currentTimeMillis() - nextStateData.beginTime)
      } else {
        nextStateData.remoteActor ! SaltJobOk(Json.stringify(retJson), System.currentTimeMillis() - nextStateData.beginTime)
        log.debug("Salt result:{}", retJson)
      }
    }

    case S_Stopping -> S_Finished => {
      nextStateData.remoteActor ! SaltJobStoped(nextStateData.jobResult, System.currentTimeMillis() - nextStateData.beginTime)
    }

    case S_Notified -> S_Stopping => {
      if (nextStateData.hostName != null) {
        context.parent ! SaltCommand(Seq("salt", nextStateData.hostName, "saltutil.kill_job", nextStateData.jid))
      } else {
        sender ! SaltJobStoped("hostName is null", System.currentTimeMillis() - nextStateData.beginTime)
      }
    }

    case S_Notified -> S_TimeOut => {
      log.debug("{}, {}", stateData, nextStateData)
      if (nextStateData.remoteActor != null) {
        nextStateData.remoteActor ! SaltTimeOut()
      } else {
        log.error("Job timeout without remoteActor")
      }

      goto(S_Finished)
    }
  }

  initialize()
}
