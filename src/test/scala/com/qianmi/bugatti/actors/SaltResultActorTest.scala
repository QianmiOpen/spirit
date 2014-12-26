package com.qianmi.bugatti.actors

import akka.actor.{Props, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._
import scala.language.postfixOps

/**
 * Created by mind on 12/23/14.
 */
class SaltResultActorTest(_system: ActorSystem) extends TestKit(_system) with ImplicitSender
with WordSpecLike with Matchers with BeforeAndAfterAll {
  def this() = this(ActorSystem("SaltKeysActorTestActorSystem"))

  "salt result actor test" must {
    "notify -> finish" in {
      val resultActor = system.actorOf(Props(classOf[SaltResultActor]))
      resultActor ! JobNotify("20141219201338352165", System.currentTimeMillis(), "ttt", self)
      resultActor ! JobFinish("20141219201338352165", """{"pillar": null, "result": {"fun_args": ["20141219201337993955"], "jid": "20141219201338352165", "return": "Signal 9 sent to job 20141219201337993955 at pid 5123", "retcode": 0, "success": true, "fun": "saltutil.kill_job", "id": "6f388f2627d1"}, "grains": null}""")
      val msg = receiveOne(5 second).asInstanceOf[SaltJobOk]
      assert(msg.result == """{"pillar":null,"result":{"fun_args":["20141219201337993955"],"jid":"20141219201338352165","return":"Signal 9 sent to job 20141219201337993955 at pid 5123","retcode":0,"success":true,"fun":"saltutil.kill_job","id":"6f388f2627d1"},"grains":null}""")
    }

    "finish -> notify" in {
      val resultActor = system.actorOf(Props(classOf[SaltResultActor]))
      resultActor ! JobFinish("20141219201338352165", """{"pillar": null, "result": {"fun_args": ["20141219201337993955"], "jid": "20141219201338352165", "return": "Signal 9 sent to job 20141219201337993955 at pid 5123", "retcode": 0, "success": true, "fun": "saltutil.kill_job", "id": "6f388f2627d1"}, "grains": null}""")
      resultActor ! JobNotify("20141219201338352165", System.currentTimeMillis(), "ttt", self)
      val msg = receiveOne(5 second).asInstanceOf[SaltJobOk]
      assert(msg.result == """{"pillar":null,"result":{"fun_args":["20141219201337993955"],"jid":"20141219201338352165","return":"Signal 9 sent to job 20141219201337993955 at pid 5123","retcode":0,"success":true,"fun":"saltutil.kill_job","id":"6f388f2627d1"},"grains":null}""")
    }
  }
}