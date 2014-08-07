package com.qianmi.bugatti.actors

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef, Cancellable}
import akka.event.LoggingReceive

import scala.concurrent.duration._
import scala.sys.process.Process

import scala.language.postfixOps

/**
 * Created by mind on 8/6/14.
 */
private class SaltCommandActor(cmd: SaltCommand, remoteSender: ActorRef) extends Actor with ActorLogging {

  import context._

  var timeOutSchedule: Cancellable = _

  val TimeOutSeconds = 600 seconds

  val beginTime = System.currentTimeMillis()

  var jid = ""

  var reRunTimesWhenException = 1

  val AllHost = "*"  // 命令执行，标示所有主机

  val AllHostDelaySeconds = 3 // 针对所有主机执行命令时，延时返回的秒数

  val NoDelay = 0   // 针对单一主机执行命令时，不等待

  override def preStart(): Unit = {
    timeOutSchedule = context.system.scheduler.scheduleOnce(TimeOutSeconds, self, TimeOut)
  }

  override def postStop(): Unit = {
    if (timeOutSchedule != null) {
      timeOutSchedule.cancel()
    }
  }

  override def receive = LoggingReceive {
    case Run => {
      try {
        cmd.command match {
          case Seq("salt", xs@_*) => {
            val ret = Process(cmd.command ++ Seq("--return", "spirit", "--async"), new File(cmd.workDir)).lines.mkString(",")
            if (ret.size > 0 && ret.contains("Executed command with job ID")) {
              jid = ret.replaceAll("Executed command with job ID: ", "")
              log.info(s"SaltCommandActor ==> ${context.parent}")
              context.parent ! JobNotify(jid, self)

              log.debug( s"""Execute "${cmd.command.mkString(" ")}"; path: ${self.path}; ret: ${ret}""")
            }
          }
          case Seq("salt-run", xs@_*) => {
            val ret = Process(cmd.command, new File(cmd.workDir)).lines.mkString(",")

            remoteSender ! SaltResult(ret, System.currentTimeMillis() - beginTime)

            log.debug( s"""Execute saltrun "${cmd.command.mkString(" ")}"; path: ${self.path}; ret: ${ret}""")
          }
          case x => {
            log.warning(s"Salt Command receive unknown message: ${x}")
          }
        }
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
      log.debug(s"SaltCommandActor JobFinish: ${jobRet}")
      remoteSender ! SaltResult(jobRet.result, System.currentTimeMillis() - beginTime)

      context.stop(self)
    }

    case TimeOut => {
      remoteSender ! TimeOut()
      context.stop(self)
    }

    case x => log.info(s"Unknown salt command message: ${x}")
  }
}

// 运行命令
private case class Run()

private case class Status()

private case class Stop()

