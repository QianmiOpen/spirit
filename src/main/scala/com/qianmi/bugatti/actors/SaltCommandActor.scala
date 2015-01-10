package com.qianmi.bugatti.actors

import java.io.File

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.event.LoggingReceive

import scala.language.postfixOps
import scala.sys.process.Process

/**
 * Created by mind on 8/6/14.
 */
private class SaltCommandActor(cmd: SaltCommand, remoteSender: ActorRef) extends Actor with ActorLogging {
  def currentTime = System.currentTimeMillis()

  val beginTime = currentTime

  final val AllHost = "*"  // 命令执行，标示所有主机

  override def receive = LoggingReceive {
    case Run => {
      try {
        cmd.command match {
          case Seq("salt", xs@_*) => {
            if (cmd.command(1).contains(AllHost) || cmd.command(1).contains("-L")) {
              remoteSender ! SaltJobError("Not support multi-host command", currentTime - beginTime)
            } else {
              val hostName = cmd.command(1)
              val ret = Process(cmd.command ++ Seq("--return", "spirit", "--async"), new File(cmd.workDir)).lines.mkString(",")
              if (ret.size > 0 && ret.contains("Executed command with job ID")) {
                val jid = ret.replaceAll("Executed command with job ID: ", "")
                context.parent ! JobNotify(jid, beginTime, hostName, remoteSender)
                remoteSender ! SaltJobBegin(jid, currentTime - beginTime)

                log.debug( s"""Execute "${cmd.command.mkString(" ")}"; path: ${self.path}; ret: ${ret}""")
              } else {
                remoteSender ! SaltJobError(ret, currentTime - beginTime)
              }
            }
          }
          case Seq("salt-run", xs@_*) => {
            val ret = Process(cmd.command, new File(cmd.workDir)).lines.mkString(",")

            remoteSender ! SaltJobOk(ret, currentTime - beginTime)

            log.debug( s"""Execute saltrun "${cmd.command.mkString(" ")}"; path: ${self.path}; ret: ${ret}""")
          }
          case x => {
            log.warning(s"Salt Command receive unknown message: ${x}")
            remoteSender ! SaltJobError(s"Unkown salt command: $x", currentTime - beginTime)
          }
        }
      } catch {
        case x: Exception => {
          log.warning(s"Run exception: ${x}; path: ${self.path}")
          remoteSender ! SaltJobError(x.getMessage, currentTime - beginTime)
        }
      } finally {
        context.stop(self)
      }
    }

    case x => log.info(s"Unknown salt command message: ${x}")
  }
}

// 运行命令
private case class Run()

private case class Status()
