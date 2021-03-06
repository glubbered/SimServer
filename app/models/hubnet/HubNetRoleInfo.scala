package models.hubnet

import
  play.api.{ data, Logger },
    data.{ Form, Forms },
      Forms._

import
  models.util.ModelMapper

import Converter.str2Option

/**
 * Created by IntelliJ IDEA.
 * User: Jason
 * Date: 6/7/12
 * Time: 1:28 PM
 */

private object Converter {
  implicit def str2Option(str: String) = Option(str) // High potential for danger, but... I think it's safe here
}

sealed class HubNetRoleInfo(val modelName: Option[String] = None, val username: String, val isHeadless: Option[String] = None,
                            val teacherName: String, val portNum: Option[String] = None, val isLogging: Option[String] = None)

sealed trait InfoCompanion[T] {

  protected val RequiredText = text.verifying("Required Text", !(_: String).isEmpty)

   /*none*/ val YesNoChoices = Seq("No", "Yes")
  protected val YesNo        = text.verifying(s"Invalid value; choose ${quoteAndOrDelim(YesNoChoices)}", YesNoChoices.contains(_: String))

  protected def numerical(min: Int, max: Int) =
    text.verifying(
      s"Not a number within the range [$min, $max] (inclusive)",
      number =>
        try   { val x = number.toInt; x >= min && x <= max }
        catch {
          case ex: NumberFormatException =>
            false
          case ex: Exception =>
            Logger.warn("Unexpected error on string => number conversion", ex)
            false
        }
    )

  def form: Form[T]

  private def quoteAndOrDelim(choices: Seq[String]): String = choices map (x => s"'$x'") mkString " or "

}

case class StudentInfo(private val uname: String, private val tname: String) extends HubNetRoleInfo(username = uname, teacherName = tname)

object StudentInfo extends InfoCompanion[StudentInfo] {
  override def form: Form[StudentInfo] = {
    Form(
      mapping(
        "User Name"    -> RequiredText,
        "Teacher Name" -> RequiredText.verifying("No registry entry found for this teacher name", HubNetServerRegistry.getPortByTeacherName(_).isSuccess)
      )(StudentInfo.apply)(StudentInfo.unapply)
    )
  }
}

case class TeacherInfo(private val mname: String, private val uname: String, private val headless: String,
                       private val tname: String, private val port: String,  private val logging: String)
                       extends HubNetRoleInfo(mname, uname, headless, tname, port, logging) {
  def generateWithIP(ipAddr: String): TeacherInfo = new TeacherInfo(mname, uname, headless, tname, port, logging)
}

// Crappy validation....  Could use built-in validators, but they carry annoying messages that I don't know how to get rid of
object TeacherInfo extends InfoCompanion[TeacherInfo] {
  override def form: Form[TeacherInfo] = {
    Form(
      mapping(
        "Model Name"   -> text.verifying("Unknown model: Must come from the list of " + ModelMapper.modelNames.mkString("{ ", "; ", " }"), ModelMapper.contains _),
        "User Name"    -> RequiredText,
        "Is Headless"  -> YesNo,
        "Teacher Name" -> RequiredText,
        "Port Number"  -> numerical(0, 65535),
        "Is Logging"   -> YesNo
      )(TeacherInfo.apply)(TeacherInfo.unapply)
    )
  }
}
