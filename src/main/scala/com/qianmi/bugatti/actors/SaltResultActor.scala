package com.qianmi.bugatti.actors

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import akka.event.LoggingReceive
import play.api.libs.json.Json

import scala.collection.mutable
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

private case class JobNotify(jid: String, cmdActor: ActorRef) extends JobMsg

private case class JobReRunNotify(jid: String, cmdActor: ActorRef) extends JobMsg

private case class JobBegin(jid: String, ipAddr: String) extends JobMsg

private case class JobFinish(jid: String, result: String) extends JobMsg

private class SaltResultActor(jid: String) extends Actor with ActorLogging {
  import context._

  val DelayStopTime = 60

  val DelayFinishDelayTime = 3

  val SendCommandDelayTime = 1

  val CommandNotReceiveDelayTime = 3

  val reRunNotifySet = mutable.Set[ActorRef]().empty

  var mergedJonsonRet = Json.arr()

  var delayFinishSchedule: Cancellable = _

  var commandNotReceiveTimeoutSchedule: Cancellable = _ // 用于任务开始后的定时超时时间

  var sendCommandTimeoutSchedule: Cancellable = _ // 用于任务开始后的定时超时时间

  var m_cmdActor: ActorRef = _

  var m_reRunJid = ""

  var m_beginJobNum = 0

  var m_finishJobNum = 0

  var m_jobFinished = false

  var m_commandSended = false

  override def preStart(): Unit = {
    commandNotReceiveTimeoutSchedule = context.system.scheduler.scheduleOnce(CommandNotReceiveDelayTime seconds, self, TimeOut)
  }

  override def postStop(): Unit = {
    if (commandNotReceiveTimeoutSchedule != null) {
      commandNotReceiveTimeoutSchedule.cancel()
      commandNotReceiveTimeoutSchedule = null
    }
  }

  override def receive = LoggingReceive {
    case JobReRunNotify(_, cmdActor) => {
      if (m_jobFinished && cmdActor != null) {
        cmdActor ! Run
      }

      if (cmdActor != null) {
        reRunNotifySet += cmdActor
      }
    }

    case JobNotify(_, cmdActor) => {
      if (m_jobFinished) {
        cmdActor ! JobFinish(jid, Json.stringify(mergedJonsonRet))
      }

      // 本job返回结果是一个需要rerun的job，则发送rerunnotify
      if (m_reRunJid.length > 0) {
        context.parent ! JobReRunNotify(m_reRunJid, cmdActor)
      }

      m_cmdActor = cmdActor
    }

    case JobBegin(_, _) => {
      if (commandNotReceiveTimeoutSchedule != null) {
        commandNotReceiveTimeoutSchedule.cancel()
        commandNotReceiveTimeoutSchedule = null
      }

      m_beginJobNum += 1

      if (sendCommandTimeoutSchedule != null) sendCommandTimeoutSchedule.cancel
      sendCommandTimeoutSchedule = context.system.scheduler.scheduleOnce(SendCommandDelayTime seconds, self , SendCommandFinish)
    }

    case JobFinish(_, result) => {
      val retJson = Json.parse(result)
      val resultLines = (retJson \ "result" \ "return").validate[Seq[String]].asOpt.getOrElse(Seq.empty)
      log.debug(s"resultLines: ${resultLines}")

      if (resultLines.nonEmpty && resultLines.last.contains("is running as PID")) {
        log.debug(s"need rerun")

        m_reRunJid = resultLines.last.replaceAll("^.* with jid ", "")

        if (m_cmdActor != null) {
          context.parent ! JobReRunNotify(m_reRunJid, m_cmdActor)
        }
      } else {
        if (delayFinishSchedule != null) {
          delayFinishSchedule.cancel
          delayFinishSchedule = null
        }

        m_finishJobNum += 1
        mergedJonsonRet = mergedJonsonRet :+ retJson

        if (m_commandSended && m_beginJobNum == m_finishJobNum) {
          self ! AllJobFinish
        } else {
          delayFinishSchedule = context.system.scheduler.scheduleOnce(DelayFinishDelayTime seconds, self , AllJobFinish)
        }
      }
    }

    case SendCommandFinish => {
      m_commandSended = true

      if (m_beginJobNum == m_finishJobNum) {
        if (delayFinishSchedule != null) {
          delayFinishSchedule.cancel()
          delayFinishSchedule = null
        }

        self ! AllJobFinish
      }
    }

    case AllJobFinish => {
      m_jobFinished = true

      delayFinishSchedule = null

      reRunNotifySet.foreach { cmdActor =>
        cmdActor ! Run
      }

      if (m_cmdActor != null) {
        m_cmdActor ! JobFinish(jid, Json.stringify(mergedJonsonRet))
        log.debug(s"JobResult stop scheduler: ${jid}, ${mergedJonsonRet}")
      }

      context.system.scheduler.scheduleOnce(DelayStopTime seconds, self, Stop)
    }

    case TimeOut => {
      if (delayFinishSchedule != null) {
        delayFinishSchedule.cancel
        delayFinishSchedule = null
      }

      if (m_cmdActor != null) {
        m_cmdActor ! TimeOut
      }

      context.stop(self)
    }

    case Stop => context.stop(self)

    case x => log.info(s"Unknown salt result message: ${x}")
  }
}

private case class AllJobFinish()

private case class SendCommandFinish()