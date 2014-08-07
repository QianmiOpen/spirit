package com.qianmi.bugatti.actors

import akka.actor._
import akka.event.LoggingReceive

import scala.language.postfixOps

/**
 * Created by mind on 7/16/14.
 */

trait SpiritCommand;

trait SpiritResult;

// salt执行命令
case class SaltCommand(command: Seq[String], workDir: String = ".") extends SpiritCommand

// salt执行结果
case class SaltResult(result: String, excuteMicroseconds: Long) extends SpiritResult

// 执行超时
case class TimeOut() extends SpiritResult

class CommandsActor extends Actor with ActorLogging {
  val JobNameFormat = "Job_%s"

  def jobName(jid: String) = JobNameFormat.format(jid)

  val DelayStopJobResult = 3

  override def receive = LoggingReceive {
    case cmd: SaltCommand => {
      log.info(s"cmd: ${cmd}; remoteSender: ${sender}")

      val saltCmd = context.actorOf(Props(classOf[SaltCommandActor], cmd, sender).withDispatcher("execute-dispatcher"))
      saltCmd ! Run
    }

    // 从cmd触发过来
    case CheckJob(jid) => {
      val jn = jobName(jid)
      context.child(jn).getOrElse {
        context.actorOf(Props(classOf[SaltResultActor], jid), name = jn)
      } ! NotifyMe(sender)
    }

    // 从jobresult触发过来，通知另一个job
    case reRun: ReRunNotify => {
      val jn = jobName(reRun.jid)
      context.child(jn).getOrElse {
        context.actorOf(Props(classOf[SaltResultActor], reRun.jid), name = jn)
      } ! reRun
    }

    // 从logUdpActor触发过来
    case jobBegin: JobBegin => {
      val jn = jobName(jobBegin.jid)
      context.child(jn).getOrElse {
        context.actorOf(Props(classOf[SaltResultActor], jobBegin.jid), name = jn)
      } ! JobBegin
    }

    // 从httpserverActor触发过来
    case jobRet: JobFinish => {
      val jn = jobName(jobRet.jid)
      context.child(jn).getOrElse {
        context.actorOf(Props(classOf[SaltResultActor], jobRet.jid), name = jn)
      } ! jobRet
    }

    case Status => {
      val ret = context.children.map { child =>
        child.toString()
      }

      sender ! ret
    }

    case x => log.info(s"Unknown commands message: ${x}")
  }
}
