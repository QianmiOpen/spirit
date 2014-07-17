package com.qianmi.bugatti.actors

import java.io.File
import java.util.Date

import akka.actor.{ActorRef, Props, Actor, ActorLogging}

import scala.sys.process.Process

/**
 * Created by mind on 7/16/14.
 */

// salt执行命令
case class SaltCommand(commandId: String, command: Seq[String], delayTime: Int = 3, workDir: String = ".")

// salt执行结果
case class SaltResult(commandId: String, lines: Seq[String], excuteTime: Long)

// 执行超时
case class TimeOut(commandId: String)


class CommandsActor extends Actor with ActorLogging {

  override def receive = {
    case cmd: SaltCommand => {
      val saltCmd = context.actorOf(Props(classOf[SaltCommandActor], cmd, sender))

      saltCmd ! Run
    }

    case CheckResults => {
      context.children.foreach {
        child => child ! CheckJID
      }

      log.debug(s"Children count: ${context.children.size}")
    }

    case x => log.info(s"Unknown commands message: ${x}")
  }
}


private class SaltCommandActor(cmd: SaltCommand, remoteSender: ActorRef) extends Actor with ActorLogging {
  val TimeOutSeconds = 600

  val beginDate = new Date

  var jid = ""
  var reRun = false
  var checkJidCount = 0
  var delayTimes = 0

  override def receive = {
    case Run => {
      try {
        val ret = Process(cmd.command :+ "--async", new File(cmd.workDir)).lines.mkString(",")
        if (ret.size > 0 && ret.contains("Executed command with job ID")) {
          jid = ret.replaceAll("Executed command with job ID: ", "")
        }

        delayTimes = cmd.delayTime
        log.debug( s"""Execute "${cmd.command.mkString(" ")}"; jobId: ${jid}; cmdId: ${cmd.commandId}; ret: ${ret}""")
      } catch {
        case x: Exception => log.warning(s"Run exception: ${x}")
      }
    }

    case CheckJID => {
      // 一次查询需要0.3-0.4秒，比较消耗线程池，这里空余一段时间查询，减少查询次数，提高吞吐量
      if (delayTimes > 0) {
        delayTimes -= 1
      } else {
        if (checkJidCount > TimeOutSeconds) {
          remoteSender ! TimeOut(cmd.commandId)

          log.warning(s"Command timeout: ${cmd.toString}")

          context.stop(self)
        }

        try {
          if (jid.size > 0) {
            val jobRet = Process(Seq("salt-run", "jobs.lookup_jid", jid), new File(cmd.workDir)).lines
            if (jobRet.size > 0) {
              // 有其他任务正在执行，需要纪录正在执行的jid，并且重新执行本任务
              if (jobRet.last.contains("is running as")) {
                reRun = true
                jid = jobRet.last.replaceAll("^.* with jid ", "")
              } // 返回正确的结果
              else if (jobRet.last.contains("Total")) {
                if (reRun) {
                  reRun = false
                  jid = ""
                  self ! Run
                } else {
                  val endTime = new Date
                  remoteSender ! SaltResult(cmd.commandId, jobRet, (endTime.getTime - beginDate.getTime) / 1000)

                  log.debug(s"job: ${cmd.commandId}; jid: ${jid}; complete.")
                  context.stop(self)
                }
              }
              else {
                log.warning(s"Unexpected ret: ${jobRet.toString}")
              }
            }

            checkJidCount += 1
          }
        } catch {
          case x: Exception => log.warning(s"CheckJid exception: ${x}")
        }
      }
    }

    case x => log.info(s"Unknown salt command message: ${x}")
  }
}

// 定时器，定时检查结果
private case class CheckResults()

// 运行命令
private case class Run()

// 任务中检查结果
private case class CheckJID()
