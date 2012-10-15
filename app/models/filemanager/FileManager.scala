package models.filemanager

import java.io.File

import akka.util.Duration
import akka.actor.{PoisonPill, ActorSystem, Actor, Props}

import models.{Initialize, Write, Delete}
import models.util.FileUtil

/**
 * Created by IntelliJ IDEA.
 * User: Jason
 * Date: 8/22/12
 * Time: 1:02 PM
 */

trait FileManager extends Delayer {

  val PublicPath   = "public"
  val AssetPath    = "assets"
  def MyFolderName: String

  protected val CharEncoding = "UTF-8"
  protected def LifeSpan  : Duration
  protected def SystemName: String

  protected lazy val system      = ActorSystem(SystemName)
  protected lazy val fileFolder  = new File(PublicPath + File.separator + MyFolderName)

  if (!fileFolder.exists()) fileFolder.mkdir()

  def formatFilePath(fileNameBasis: String, fileExt: String) : String = {
    "%s/%s.%s".format(MyFolderName, fileNameBasis, fileExt)
  }

  def registerFile(contents: String, fileNameBasis: String, fileExt: String) : String =
    registerFile(contents, formatFilePath(fileNameBasis, fileExt))
  
  def registerFile(contents: String, fileName: String) : String = {

    // Create an actor with a handle to the file, write the contents to it
    val file = new File("%s%s%s".format(PublicPath, File.separator, fileName))
    val fileActor = system.actorOf(Props(new FileActor(file)))
    fileActor ! Initialize
    fileActor ! Write(contents)

    // The temp gen file is accessible for <LifeSpan> before being deleted
    system.scheduler.scheduleOnce(LifeSpan) { fileActor ! Delete }
    file.toString.replace(PublicPath, AssetPath)

  }

  // Could _easily_ be more efficient (at least for small numbers of files), but I want to stick to having actors manage the files
  def removeAll() {
    fileFolder.listFiles foreach { file => system.actorOf(Props(new FileActor(file))) ! Delete }
  }

}

// v--  DEFINITIONS BELOW ARE OPEN TO EXTRACTION/REFACTORING  --v

class FileActor(file: File) extends Actor {
  override protected def receive = {
    case Initialize      => file.delete(); file.createNewFile()
    case Write(contents) => FileUtil.printToFile(file)(_.write(contents))
    case Delete          => file.delete(); self ! PoisonPill // Terminate self after file is gone
  }
}

// This was created to seamlessly hide nitty-gritty detail that a class's body is delayed init (usually for superfluous reasons)
// Why does this not already exist in the Scala library to begin with? --JAB (8/29/12)
sealed trait Delayer extends DelayedInit {
  override def delayedInit(body: => Unit) {
    body
  }
}