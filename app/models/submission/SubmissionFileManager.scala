package models.submission

import akka.util.duration._

import models.filemanager.FileManager

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 11/1/12
 * Time: 1:52 PM
 */

object SubmissionFileManager extends FileManager {

  override           def MyFolderName = "uploads"
  override protected def LifeSpan     = 3650 days
  override protected def SystemName   = "SubmissionFiles"

  def formatFilePath(fileNameBasis: String, bundle: TypeBundle) : String =
    "%s/%s/%s.%s".format(MyFolderName, bundle.name, fileNameBasis, bundle.fileExtension)

  def registerFile(contents: String, fileNameBasis: String, bundle: TypeBundle) : String = {
    val filename = formatFilePath(fileNameBasis, bundle)
    saveFile(contents, filename, fileNameBasis) dropWhile (_ != '/') drop 1 // Toss out the "assets/"
  }

}
