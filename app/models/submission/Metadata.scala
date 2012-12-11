package models.submission

import play.api.libs.json.Json

import scalaz.Success

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 10/24/12
 * Time: 1:30 PM
 */

trait Metadata {
  def getType: String
}

object Metadata extends FromStringParser {
  override protected type Target    = Metadata
  override protected type ConsTuple = Nothing
  override def fromString(str: String) : Output = {
    Success(new Metadata { override def getType = (Json.parse(str) \ "type").as[String] })
  }
}

