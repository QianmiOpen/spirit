package com.qianmi.bugatti.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.Timeout
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by mind on 12/19/14.
 */
class CommandsActorTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("SaltKeysActorTestActorSystem"))

  val HOST_NAME = "ca9ceac56e8f"

  val timeout: Timeout = 15 second

  val (commandsActor, httpActor, keysActor) = Spirit.init(system)

  "Commands actor test" must {
    "test all host" in {
      commandsActor ! SaltCommand(Seq("salt", "*", "test.ping"))
      val joberr = receiveOne(timeout.duration).asInstanceOf[SaltJobError]
      assert(joberr.msg == "Not support multi-host command")
    }

    "test list host" in {
      commandsActor ! SaltCommand(Seq("salt", "-L", HOST_NAME, "test.ping"))
      val joberr = receiveOne(timeout.duration).asInstanceOf[SaltJobError]
      assert(joberr.msg == "Not support multi-host command")
    }

    "test one docker test.ping" in {
      commandsActor ! SaltCommand(Seq("salt", HOST_NAME, "test.ping"))
      val jobBegin = receiveOne(timeout.duration).asInstanceOf[SaltJobBegin]
      println(jobBegin)
      val jobRet = receiveOne(timeout.duration).asInstanceOf[SaltJobOk]
      println(jobRet)
    }

    "test one docker install java" in {
      commandsActor ! SaltCommand(Seq("salt", HOST_NAME, "state.sls", "java.install"))
      val jobBegin = receiveOne(timeout.duration).asInstanceOf[SaltJobBegin]
      println(jobBegin)
      val jobRet = receiveOne(timeout.duration * 10).asInstanceOf[SaltJobOk]
      println(jobRet)
    }

    "test one docker install java then stop ok" in {
      commandsActor ! SaltCommand(Seq("salt", HOST_NAME, "state.sls", "java.install"))
      val jobBegin = receiveOne(timeout.duration).asInstanceOf[SaltJobBegin]
      println(jobBegin)
      commandsActor ! SaltJobStop(jobBegin.jid)
      val jobStoped = receiveOne(timeout.duration).asInstanceOf[SaltJobStoped]
      println(jobStoped)
    }

  }
}