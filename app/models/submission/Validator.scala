package models.submission

import
  scalaz.{ Scalaz, ValidationNel },
    Scalaz.ToValidationV

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 10/26/12
 * Time: 12:17 PM
 */

object Validator {

  protected type Fail = String
  protected type V[T] = ValidationNel[Fail, T]

  def accept[T](x: T) = x.successNel[Fail]
  def deny  [T](x: T) = x.failNel

  def validateRefID(refID: String): V[Long] =
    ensureNotEmpty(refID, "ref ID") flatMap {
      x =>
        try x.toLong match { case y => accept(y) }
        catch {
          case ex: NumberFormatException => deny(s"Cannot convert '$x' to Long; either not numerical or too many digits.")
        }
    }

  def validateUserID(userID: String)     = ensureWordyHyphen(userID,   "user ID")
  def validatePeriodID(periodID: String) = ensureWordyHyphen(periodID, "period ID")
  def validateRunID(runID: String)       = ensureWordyHyphen(runID,    "run ID")
  def validateType(`type`: String)       = ensureWordy      (`type`,   "type")

  protected val ErrorMessageTemplate = "Invalid value given for %s; %s".format(_: String, _: String)

  def ensure[T](data: T, dataName: String)(errorDesc: String)(errorCond: (T) => Boolean): V[T] = {
    lazy val errorMessage = ErrorMessageTemplate(dataName, errorDesc)
    failUnderCond(data, errorCond, errorMessage)
  }

  def ensureNotEmpty[T <% { def isEmpty: Boolean }](data: T, dataName: String): V[T] =
    ensure(data, dataName)("cannot be empty")(_.isEmpty)

  def ensureNonNegative[T <% AnyVal { def <=(x: Int): Boolean }](data: T, dataName: String): V[T] =
    ensure(data, dataName)("value is too small")(_ <= 0)

  def ensureWordyHyphen(data: String, dataName: String): V[String] =
    ensure(data, dataName)("may only contain word characters (alphanumerics and underscores) and hyphens")(x => !(x matches "[\\w-]+"))

  def ensureWordy(data: String, dataName: String): V[String] =
    ensure(data, dataName)("may only contain word characters (alphanumerics and underscores)")(x => !(x matches "\\w+"))

  protected def failUnderCond[T](param: T, failureCond: (T) => Boolean, errorStr: => String): V[T] = param match {
    case x if failureCond(x) => deny(errorStr)
    case x                   => accept(x)
  }

}

