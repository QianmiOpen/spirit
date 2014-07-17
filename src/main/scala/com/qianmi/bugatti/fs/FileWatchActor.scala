package com.qianmi.bugatti.fs

import java.io.File
import java.nio.file.Path

import akka.actor.{Actor, ActorLogging}

/**
 * Created by mind on 7/16/14.
 */
class FileWatchActor extends Actor with ActorLogging {
  val watchServiceTask = new WatchServiceTask(self)
  val watchThread = new Thread(watchServiceTask, "WatchService")

  override def preStart(): Unit = {
    watchThread.setDaemon(true)
    watchThread.start()
  }

  override def postStop(): Unit = {
    watchThread.interrupt()
  }

  override def receive = {
    case MonitorDir(path) => {
      watchServiceTask.watchRecursively(path)
      log.debug(s"watch path: ${path}")
    }
    //    case Created(file) =>
    //    case Modifyed(file) =>
    //    case Deleted(file) =>
    case x => log.warning(x.toString)
  }
}

sealed trait FileSystemChange

case class Created(fileOrDir: File) extends FileSystemChange

case class Modifyed(fileOrDir: File) extends FileSystemChange

case class Deleted(fileOrDir: File) extends FileSystemChange

case class MonitorDir(path: Path)