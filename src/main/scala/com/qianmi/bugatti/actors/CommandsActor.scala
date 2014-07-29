package com.qianmi.bugatti.actors

import java.io.File

import akka.actor._
import play.api.libs.json.{JsObject, Json}

import scala.collection.mutable
import scala.concurrent.duration._
import scala.sys.process.Process

/**
 * Created by mind on 7/16/14.
 */

trait SpiritCommand;
trait SpiritResult;

// salt执行命令
case class SaltCommand(command: Seq[String], delayTime: Int = 0, workDir: String = ".") extends SpiritCommand

// salt执行结果
case class SaltResult(result: String, excuteMicroseconds: Long) extends SpiritCommand

// 执行超时
case class TimeOut() extends SpiritResult

case class SaltRunCommand(command: Seq[String], delayTime: Int = 0, workDir: String = ".") extends SpiritResult

case class SaltRunResult(result: String, excuteMicroseconds: Long) extends SpiritResult

class CommandsActor extends Actor with ActorLogging {
  val JobNameFormat = "Job_%s"

  def jobName(jid: String) = JobNameFormat.format(jid)

  val DelayStopJobResult = 0

  override def receive = {
    case cmd: SaltCommand => {
      log.info(s"cmd: ${cmd}; remoteSender: ${sender}")

      val saltCmd = context.actorOf(Props(classOf[SaltCommandActor], cmd, sender).withDispatcher("execute-dispatcher"))
      saltCmd ! Run
    }

    case cmd: SaltRunCommand => {
      log.info(s"cmd: ${cmd}; remoteSender: ${sender}")

      val saltRunCmd = context.actorOf(Props(classOf[SaltRunCommand], cmd, sender).withDispatcher("execute-dispatcher"))
      saltRunCmd ! Run
    }

    // 从cmd触发过来
    case CheckJob(jid, delayTime) => {
      log.debug(s"CommandsActor ==>  ${context.children}")

      val jn = jobName(jid)
      context.child(jn).getOrElse {
        context.actorOf(Props(classOf[SaltResultActor], delayTime), name = jn)
      } ! NotifyMe(sender)
    }

    // 从jobresult触发过来，通知另一个job
    case reRun: ReRunNotify => {
      val jn = jobName(reRun.jid)
      context.child(jn).getOrElse {
        context.actorOf(Props(classOf[SaltResultActor], DelayStopJobResult), name = jn)
      } ! reRun
    }

    // 从httpserverActor触发过来
    case jobRet: JobFinish => {
      val jn = jobName(jobRet.jid)
      context.child(jn).getOrElse {
        context.actorOf(Props(classOf[SaltResultActor], DelayStopJobResult), name = jn)
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

private class SaltRunActor(cmd: SaltRunCommand, remoteSender: ActorRef) extends Actor with ActorLogging {
  val beginTime = System.currentTimeMillis()

  override def receive: Actor.Receive = {
    case Run => {
      try {
        val ret = Process(Seq("salt-run") ++ cmd.command, new File(cmd.workDir)).lines.mkString(",")

        remoteSender ! SaltRunResult(ret, System.currentTimeMillis() - beginTime)

        log.debug( s"""Execute saltrun "${cmd.command.mkString(" ")}"; path: ${self.path}; ret: ${ret}""")
      } catch {
        case x: Exception => {
          log.warning(s"Run exception: ${x}; path: ${self.path}")
        }
      }
    }
  }
}

private class SaltCommandActor(cmd: SaltCommand, remoteSender: ActorRef) extends Actor with ActorLogging {

  import context._

  var timeOutSchedule: Cancellable = _

  val TimeOutSeconds = 600 seconds

  val beginTime = System.currentTimeMillis()

  var jid = ""

  var reRunTimesWhenException = 1

  override def preStart(): Unit = {
    timeOutSchedule = context.system.scheduler.scheduleOnce(TimeOutSeconds) {
      remoteSender ! TimeOut
      context.stop(self)
    }
  }

  override def postStop(): Unit = {
    if (timeOutSchedule != null) {
      timeOutSchedule.cancel()
    }
  }

  override def receive = {
    case Run => {
      try {
        val ret = Process(cmd.command ++ Seq("--return", "spirit", "--async"), new File(cmd.workDir)).lines.mkString(",")
        if (ret.size > 0 && ret.contains("Executed command with job ID")) {
          jid = ret.replaceAll("Executed command with job ID: ", "")
          log.info(s"SaltCommandActor ==> ${context.parent}")
          context.parent ! CheckJob(jid, cmd.delayTime)
        }

        log.debug( s"""Execute "${cmd.command.mkString(" ")}"; path: ${self.path}; ret: ${ret}""")
      } catch {
        case x: Exception => {
          log.warning(s"Run exception: ${x}; path: ${self.path}")
          if (reRunTimesWhenException > 0) {
            reRunTimesWhenException -= 1
            self ! Run
          }
        }
      }
    }

    case jobRet: JobFinish => {
      remoteSender ! SaltResult(jobRet.result, System.currentTimeMillis() - beginTime)

      context.stop(self)
    }

    case x => log.info(s"Unknown salt command message: ${x}")
  }
}

private class SaltResultActor(delayTime: Int) extends Actor with ActorLogging {

  import context._

  val reRunNotifySet = mutable.Set[ActorRef]().empty

  var mergedJonsonRet = Json.arr()

  var scheduleOne: Cancellable = _

  var bReturn = false

  var m_cmdActor: ActorRef = _

  var m_jobRet: JobFinish = _

  var reRunJid = ""

  override def receive: Receive = {
    case ReRunNotify(jid, cmdActor) => {
      if (bReturn) {
        if (cmdActor != null) {
          cmdActor ! Run
        }
      }
      if (cmdActor != null) {
        reRunNotifySet += cmdActor
      }
    }

    case NotifyMe(cmdActor) => {
      log.debug(s" this is NotifyMe in SaltResultActor !")
      if (bReturn) {
        cmdActor ! m_jobRet
      }
      if (reRunJid.length > 0) {
        context.parent ! ReRunNotify(reRunJid, cmdActor)
      }
      m_cmdActor = cmdActor
    }

    case jobRet: JobFinish => {
      val retJson = Json.parse(jobRet.result)
      val resultLines = (retJson \ "result" \ "return").validate[Seq[String]].asOpt.getOrElse(Seq.empty)

      if (resultLines.nonEmpty && resultLines.last.contains("is running as PID")) {
        reRunJid = resultLines.last.replaceAll("^.* with jid ", "")

        if (m_cmdActor != null) {
          context.parent ! ReRunNotify(reRunJid, m_cmdActor)
        }
      } else {
        bReturn = true
        m_jobRet = jobRet

        reRunNotifySet.foreach { cmdActor =>
          cmdActor ! Run
        }

        if (delayTime <= 0) {
          if (m_cmdActor != null) {
            m_cmdActor ! jobRet
            log.debug(s"JobResult stop immediatly: ${jobRet}")
          }
        } else {
          mergedJonsonRet = mergedJonsonRet :+ retJson

          if (scheduleOne != null) scheduleOne.cancel

          scheduleOne = context.system.scheduler.scheduleOnce(delayTime seconds) {
            if (m_cmdActor != null) {
              m_cmdActor ! JobFinish(jobRet.jid, Json.stringify(mergedJonsonRet))
              log.debug(s"JobResult stop scheduler: ${jobRet}")
            }
          }
        }
      }

      context.system.scheduler.scheduleOnce(30 seconds) {
        context.stop(self)
      }
    }

    case x => log.info(s"Unknown salt result message: ${x}")
  }
}

// 运行命令
private case class Run()

private case class CheckJob(jid: String, delayTime: Int)

private case class JobFinish(jid: String, result: String)

private case class ReRunNotify(jid: String, cmdActor: ActorRef)

private case class Status()

private case class NotifyMe(cmdActor: ActorRef)