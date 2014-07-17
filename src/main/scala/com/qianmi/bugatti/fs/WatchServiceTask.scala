package com.qianmi.bugatti.fs

import java.nio.file.LinkOption._
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file._
import java.nio.file.StandardWatchEventKinds._
import collection.JavaConversions._
import akka.actor.ActorRef

/**
 * Created by mind on 7/16/14.
 */
class WatchServiceTask(notifyActor: ActorRef) extends Runnable {
  private val watchService = FileSystems.getDefault.newWatchService()

  def watchRecursively(root: Path) = {
    watch(root)

    Files.walkFileTree(root, new SimpleFileVisitor[Path] {
      override def preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = {
        watch(dir)
        FileVisitResult.CONTINUE
      }
    })
  }

  private def watch(path: Path) = {
    path.register(watchService, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_MODIFY, ENTRY_DELETE)
  }

  override def run(): Unit = {
    while (!Thread.currentThread().isInterrupted) {
      val key = watchService.take()

      key.pollEvents().foreach { event =>
        val relativePath = event.context().asInstanceOf[Path]
        val path = key.watchable().asInstanceOf[Path].resolve(relativePath)

        event.kind() match {
          case ENTRY_CREATE => {
            if (Files.isDirectory(path, NOFOLLOW_LINKS)) {
              watchRecursively(path)
            }

            notifyActor ! Created(path.toFile)
          }

          case ENTRY_MODIFY => {
            notifyActor ! Modifyed(path.toFile)
          }

          case ENTRY_DELETE => {
            notifyActor ! Deleted(path.toFile)
          }

          case _ =>
        }
      }
    }
  }
}
