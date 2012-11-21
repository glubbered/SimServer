package controllers

import play.api.Logger
import play.api.mvc.{ Action, Controller }

import scalaz.{ Success, Failure }

import models.filemanager.PermFileManager
import models.util.{ PlayUtil, Util }

object Application extends Controller {

  /*
   "Wow!  WHAT IS THIS?!", you ask.  Well, let me tell you...
   First, look into the HTTP request type OPTIONS.  Then, understand that this simply replies to the OPTIONS request,
   saying that we'll accept pretty much any request--cross-domain, or not
   */
  def options(nonsense: String) = APIAction { Ok }

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def displayHttpRequest = APIAction {
    request =>
      val paramsOpt = PlayUtil.extractParamMapOpt(request)
      val text = "\nRequest Type: \n" + request.method +
                 "\n\nHeaders: \n" + (request.headers.toSimpleMap map { case (k, v) => "%s: %s".format(k, v) } mkString("\n")) +
                 "\n\nBody: \n" + (
                   paramsOpt flatMap {
                     params =>
                       Util.noneIfEmpty(params, ((_: Map[String, Seq[String]]) map { case (k, v) => "%s=%s".format(k, v(0)) } mkString ("\n")))
                   } getOrElse "[empty]"
                 ) + "\n\n"
      Ok(text)
  }

}

