package controllers

import play.api.mvc._
import play.api.Logger

import java.net.URI

import scalaz.{ Scalaz, ValidationNEL }, Scalaz.ToValidationV

import models.hubnet.{ HubNetServerManager, StudentInfo, TeacherInfo }
import models.jnlp.{ HubNetJarManager, HubNetJNLP, Jar, NetLogoJNLP }
import models.filemanager.TempFileManager
import models.util.{ DecryptionUtil, EncryptionUtil, HubNetSettings, NetUtil, PBEWithMF5AndDES, ResourceManager, Util }

/**
 * Created by IntelliJ IDEA.
 * User: Jason
 * Date: 5/31/12
 * Time: 4:20 PM
 */

object HubNet extends Controller {

  val HubNetKy     = "hubnet_data"
  val ModelsSubDir = "assets/misc/models"
  val DepsSubDir   = "assets/misc/deps"

  TempFileManager.removeAll()  // Clear all temp gen files on startup

  def hubTest = Action {
    Ok(views.html.hubtest(StudentInfo.form))
  }

  def bindStudent = Action {
    implicit request => StudentInfo.form.bindFromRequest.fold(
      errors => Ok(views.html.hubtest(errors)),
      {
        case StudentInfo(userName, teacherName) =>
          import HubNetSettings._
          val vals  = Seq(userName,    teacherName)
          val keys  = Seq(UserNameKey, TeacherNameKey)
          val pairs = keys zip vals
          val encryptedMaybe = encryptHubNetInfoPairs(Map(pairs: _*))
          handleHubNet(encryptedMaybe, false)
      }
    )
  }

  def hubTeach = Action {
    Ok(views.html.hubteach(TeacherInfo.form))
  }

  def bindTeacher = Action {
    implicit request => TeacherInfo.form.bindFromRequest.fold(
      errors => Ok(views.html.hubteach(errors)),
      {
        case TeacherInfo(modelName, userName, isHeadless, teacherName, portNumber, isLogging) =>
          import HubNetSettings._
          val vals  = Seq(modelName,    userName,    teacherName,    portNumber, isHeadless,    isLogging)
          val keys  = Seq(ModelNameKey, UserNameKey, TeacherNameKey, PortNumKey, IsHeadlessKey, IsLoggingKey)
          val pairs = keys zip vals
          val encryptedMaybe = encryptHubNetInfoPairs(Map(pairs: _*))
          encryptedMaybe fold ((nel => ExpectationFailed(nel.list.mkString("\n"))), (str => Redirect(routes.HubNet.hubSnoop(NetUtil.encode(str)))))
          // Fail or redirect to snoop the IP
      }
    )
  }

  private def encryptHubNetInfoPairs(requiredInfo: Map[String, String], optionalPairs: Option[(String, String)]*) : ValidationNEL[String, String] = {
    try {
      val kvMap = requiredInfo ++ optionalPairs.flatten
      val delimed = kvMap.toSeq map { case (k, v) => "%s=%s".format(k, v) } mkString ResourceManager(ResourceManager.HubnetDelim)
      val encrypted = (new EncryptionUtil(ResourceManager(ResourceManager.HubNetKeyPass)) with PBEWithMF5AndDES) encrypt(delimed)
      encrypted.successNel[String]
    }
    catch {
      case ex: Exception =>
        val errorStr = "Failed to encrypt HubNet info"
        Logger.warn(errorStr, ex)
        "%s; %s".format(errorStr, ex.getMessage).failNel
    }
  }

  def hubSnoop(encryptedInfo: String) = Action {
    Ok(views.html.hubsnoop(NetUtil.encode(encryptedInfo)))
  }

  def handleTeacherProxy(encryptedStr: String, teacherIP: String) = Action {
    request => handleHubNet(encryptedStr.successNel[String], true, Option(teacherIP))(request)
  }

  def handleHubNet(encryptedStrMaybe: ValidationNEL[String, String], isTeacher: Boolean, teacherIP: Option[String] = None)
                  (implicit request: Request[AnyContent]) : Result = {

    val inputAndSettingsMaybe =
      for (
        encryptedStr <- encryptedStrMaybe;
        settings     <- DecryptionUtil.decodeForHubNet(encryptedStr, isTeacher)
      ) yield (encryptedStr, settings)

    inputAndSettingsMaybe flatMap {
      case (input, HubNetSettings(modelNameOpt, username, isHeadless, teacherName, preferredPortOpt, isLogging)) =>

        val ipPortMaybe = {
          import HubNetServerManager._
          if (isTeacher) registerTeacherIPAndPort(teacherName, teacherIP.get, preferredPortOpt)
          else           getPortByTeacherName(teacherName)
        }

        val JNLPConnectPath = "http://%s/logging".format(request.host)

        val codebaseURL = routes.Assets.at("").absoluteURL(false) dropRight 1  // URL of 'assets'/'public' folder (drop the '/' from the end)
        val programName = modelNameOpt getOrElse "NetLogo"
        val fileName = TempFileManager.formatFilePath(input, "jnlp")
        val clientOrServerStr = if (isTeacher) "Server" else "Client"

        val (mainClass, jvmArgs, argsMaybe) = {
          import HubNetJNLP._, NetLogoJNLP._, HubNetJarManager._
          if (isTeacher) {
            val args =
              modelNameOpt map {
                modelName => generateModelURLArgs(Models.getHubNetModelURL(modelName)) ++
                             ipPortMaybe.fold( {_ => Seq()}, { case (_, port) => generatePortArgs(port)} ) ++
                             generateLoggingArgs(isLogging)
              } map (_.successNel[String]) getOrElse "No model name supplied".failNel
            (ServerMainClass, ServerVMArgs, args)
          }
          else {
            val args = ipPortMaybe map { case (ip, port) => generateUserIDArgs(username) ++ generateIPArgs(ip) ++ generatePortArgs(port) }
            (ClientMainClass, ClientVMArgs, args)
          }
        }

        val properties = Util.ifFirstWrapSecond(isLogging, ("jnlp.connectpath", JNLPConnectPath)).toSeq
        val otherJars  = Util.ifFirstWrapSecond(isLogging, new Jar("logging.jar", true)).toSeq

        val propsMaybe = argsMaybe map {
          args =>
            new HubNetJNLP(
              codebaseURI       = new URI(codebaseURL),
              jnlpLoc           = fileName,
              mainClass         = mainClass,
              programName       = programName,
              roleStr           = clientOrServerStr,
              isOfflineAllowed  = false,
              vmArgs            = jvmArgs,
              otherJars         = otherJars,
              properties        = properties,
              args              = args
            )
        }

        propsMaybe map (jnlp => TempFileManager.registerFile(jnlp.toXMLStr, fileName).toString replaceAllLiterally("\\", "/"))

    } fold ((nel => ExpectationFailed(nel.list.mkString("\n"))), (url => Redirect("/" + url)))

  }

  def javascriptRoutes() = Action {
    implicit request =>
    Ok(
      play.api.Routes.javascriptRouter("jsRoutes")(
        routes.javascript.HubNet.handleTeacherProxy
      )
    ).as("text/javascript")
  }

}
