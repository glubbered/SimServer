package models.filemanager

import
  scala.concurrent.{ Await, duration },
    duration._

import
  java.io.File

import
  akka.{ actor, pattern, util => util_akka },
    actor._,
    pattern.ask,
    util_akka.Timeout

import
  play.{ api, libs },
    api.Logger,
    libs.Akka


import
  models.{ FileActorMessage, util },
    FileActorMessage._,
    util.FileUtil

import play.api.libs.concurrent.Execution.Implicits.defaultContext

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
  protected def LifeSpan:   FiniteDuration
  protected def SystemName: String

  protected lazy val system     = ActorSystem(SystemName)
  protected lazy val fileFolder = new File(PublicPath + File.separator + MyFolderName)

  def formatFilePath(fileNameBasis: String, fileExt: String): String = {
    s"$MyFolderName/$fileNameBasis/$fileExt"
  }

  def registerFile(contents: Array[Byte], fileNameBasis: String, fileExt: String = ""): String = {
    val filename  = if (!fileExt.isEmpty) formatFilePath(fileNameBasis, fileExt) else fileNameBasis
    saveFile(contents, filename)
  }

  protected def saveFile(contents: Array[Byte], filename: String): String = {

    val file         = new File(s"${PublicPath}${File.separator}$filename")
    val fileActorOpt = {
      val actorName = idToActorName(filename)
      try Option(system.actorOf(Props(new FileActor(file)), name = actorName))
      catch {
        case ex: InvalidActorNameException =>
          Logger.warn("Actor name exception", ex)
          None
      }
    }

    fileActorOpt foreach {

      case actor =>

        actor ! Initialize
        actor ! Write(contents)

        // Kill the actor on termination so our scheduled "delete" task doesn't go off
        // The temp gen file is accessible for <LifeSpan> before being deleted
        Akka.system.registerOnTermination { actor ! PoisonPill }
        Akka.system.scheduler.scheduleOnce(LifeSpan) { actor ! Delete }

    }

    genPath(file)

  }

  def genPath(basis: String): String =
    genPath(new File(s"$PublicPath${File.separator}$basis"))

  def genPath(file: File): String =
    file.toString.replace(PublicPath, AssetPath)

  def retrieveFile(fileNameBasis: String): File = {
    implicit val timeout = Timeout(3 seconds)
    Await.result(system.actorSelection(s"/user/${idToActorName(fileNameBasis)}") ? Get, timeout.duration).asInstanceOf[File]
  }

  // Could _easily_ be more efficient (at least for small numbers of files), but I want to stick to having actors manage the files
  def removeAll(): Unit = {
    fileFolder.listFiles foreach { file => system.actorOf(Props(new FileActor(file))) ! Delete }
  }

  //@ Use work-type to determine this
  protected def idToActorName(id: String) = s"file-actor--${id.##.toString}"

}

// v--  DEFINITIONS BELOW ARE OPEN TO EXTRACTION/REFACTORING  --v

class FileActor(file: File) extends Actor {
  override def receive = {
    case Get             => sender ! file
    case Delete          => file.delete(); self ! PoisonPill // Terminate self after file is gone
    case Initialize      => file.getParentFile.mkdirs(); file.delete(); file.createNewFile()
    case Write(contents) => FileUtil.printBytesToFile(file.getAbsolutePath)(contents)
  }
}

// This was created to seamlessly hide nitty-gritty detail that a class's body is delayed init (usually for superfluous reasons)
// Why does this not already exist in the Scala library to begin with? --JAB (8/29/12)
sealed trait Delayer extends DelayedInit {
  override def delayedInit(body: => Unit): Unit = {
    body
  }
}
