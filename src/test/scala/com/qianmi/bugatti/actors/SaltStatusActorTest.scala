package com.qianmi.bugatti.actors

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by mind on 12/26/14.
 */
class SaltStatusActorTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("SaltKeysActorTestActorSystem"))

  val HOST_NAME = "ca9ceac56e8f"

  val (commandsActor, httpActor) = Spirit.init(system)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Salt status actor" must {
    "Query host status ok" in {
      commandsActor ! SaltStatus(HOST_NAME, "172.19.65.22")
      val msg = receiveOne(60 second).asInstanceOf[SaltStatusResult]
      assert(msg.hostName == HOST_NAME)
      assert(msg.canPing)
      assert(msg.canSPing)
      println(msg.mmInfo)
    }

    "Query host status error" in {
      commandsActor ! SaltStatus("ttt", "171.1.1.1")
      val msg = receiveOne(30 second).asInstanceOf[SaltStatusResult]
      assert(msg.hostName == "ttt")
      assert(!msg.canPing)
      assert(!msg.canSPing)
      println(msg.mmInfo)
    }
  }
}