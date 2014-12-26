package com.qianmi.bugatti.actors

/**
 * Created by mind on 12/10/14.
 */
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class SaltKeysActorTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {

  def this() = this(ActorSystem("SaltKeysActorTestActorSystem"))

  val HOST_NAME = "ca9ceac56e8f"

  val (commandsActor, httpActor, keysActor) = Spirit.init(system)

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  "Salt key actor" must {

    "send ListHosts" in {
      keysActor ! ListHosts()
      expectMsg(ListHostsResult(Seq(HOST_NAME)))
    }

    "remove host" in {
      keysActor ! RemoveHost(HOST_NAME)
      expectMsg(RemoveHostResult(HOST_NAME, true))

      keysActor ! ListHosts()
      expectMsg(ListHostsResult(Seq.empty))

      keysActor ! RemoveHost(HOST_NAME)
      expectMsg(RemoveHostResult(HOST_NAME, false))
    }
  }
}