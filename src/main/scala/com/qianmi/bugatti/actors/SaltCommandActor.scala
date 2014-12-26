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

  val beginTime = System.currentTimeMillis()

  var reRunTimesWhenException = 1

  final val AllHost = "*"  // 命令执行，标示所有主机

  override def receive = LoggingReceive {
    case Run => {
      try {
        cmd.command match {
          case Seq("salt", xs@_*) => {
            if (cmd.command(1).contains(AllHost) || cmd.command(1).contains("-L")) {
              remoteSender ! SaltJobError("Not support multi-host command", System.currentTimeMillis() - beginTime)
              context.stop(self)
            } else {
              val hostName = cmd.command(1)
              val ret = Process(cmd.command ++ Seq("--return", "spirit", "--async"), new File(cmd.workDir)).lines.mkString(",")
              if (ret.size > 0 && ret.contains("Executed command with job ID")) {
                val jid = ret.replaceAll("Executed command with job ID: ", "")
                context.parent ! JobNotify(jid, beginTime, hostName, remoteSender)
                remoteSender ! SaltJobBegin(jid, System.currentTimeMillis() - beginTime)

                log.debug( s"""Execute "${cmd.command.mkString(" ")}"; path: ${self.path}; ret: ${ret}""")
              }
            }
          }
          case Seq("salt-run", xs@_*) => {
            val ret = Process(cmd.command, new File(cmd.workDir)).lines.mkString(",")

            remoteSender ! SaltJobOk(ret, System.currentTimeMillis() - beginTime)

            log.debug( s"""Execute saltrun "${cmd.command.mkString(" ")}"; path: ${self.path}; ret: ${ret}""")
          }
          case x => {
            log.warning(s"Salt Command receive unknown message: ${x}")
          }
        }

        context.stop(self)
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

    case x => log.info(s"Unknown salt command message: ${x}")
  }
}

// 运行命令
private case class Run()

private case class Status()
