package com.qianmi.bugatti.actors

import akka.actor._
import akka.event.LoggingReceive

import scala.language.postfixOps

/**
 * Created by mind on 7/16/14.
 */

trait SpiritCommand

trait SpiritResult

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

  def getJob(jid: String) = {
  }

  override def receive = LoggingReceive {
    case cmd: SaltCommand => {
      log.info(s"remoteSender: ${sender}")

      val saltCmd = context.actorOf(Props(classOf[SaltCommandActor], cmd, sender).withDispatcher("execute-dispatcher"))
      saltCmd ! Run
    }

    case jobMsg: JobMsg => {
      val jn = jobName(jobMsg.jid)
      context.child(jn).getOrElse {
        context.actorOf(Props(classOf[SaltResultActor], jobMsg.jid), name = jn)
      } ! jobMsg
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
