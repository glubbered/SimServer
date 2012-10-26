package models.submission

import models.submission, submission.{ UserWorkComment => Comment }, submission.{ UserWorkSupplement => Supplement }

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 10/26/12
 * Time: 12:14 PM
 */

case class UserWork(override val id:          Option[Long] = None,
                                 timestamp:   Long = System.currentTimeMillis(),
                                 periodID:    String,
                                 runID:       String,
                                 userID:      String,
                                 data:        String,
                                 metadata:    String,
                                 description: String,
                                 supplements: Seq[Supplement],
                                 comments:    Seq[Comment]) extends Entry {

  def addComments   (newComments:    UserWorkComment*)    = this.cloneWith(comments = this.comments ++ newComments)
  def addSupplements(newSupplements: UserWorkSupplement*) = this.cloneWith(supplements = this.supplements ++ newSupplements)

  def cloneWith(id:          Option[Long]    = this.id,
                timestamp:   Long            = this.timestamp,
                periodID:    String          = this.periodID,
                runID:       String          = this.runID,
                userID:      String          = this.userID,
                data:        String          = this.data,
                metadata:    String          = this.metadata,
                description: String          = this.description,
                supplements: Seq[Supplement] = this.supplements,
                comments:    Seq[Comment]    = this.comments) =
    UserWork(id, timestamp, periodID, runID, userID, data, metadata, description, supplements, comments)

}

object UserWork extends FromMapParser {

  import models.datastructure.FullFailValidationList.vsl2Enhanced
  import scalaz.{ Failure, Success, Validation }

  override protected type Target    = UserWork
  override protected type ConsTuple = (Option[Long], Long, String, String, String, String, String, String, Seq[Supplement], Seq[Comment])

  override def fromMap(params: MapInput) : Output = {

    val PeriodIDKey    = "period_id"
    val RunIDKey       = "run_id"
    val UserIDKey      = "user_id"
    val DataKey        = "data"
    val Keys           = List(PeriodIDKey, RunIDKey, UserIDKey, DataKey)

    val MetadataKey    = "metadata"
    val DescriptionKey = "description"

    val valueMaybes = Keys map {
      key => params.get(key) map (Success(_)) getOrElse (Failure("No item with key '%s' passed in\n".format(key))) map (List(_))
    } // We `map` the `Success`es into lists so that `append` (called below) will give me something pattern-matchable --JAB

    val valueTupleMaybe = valueMaybes reduce (_ fullFailAppend _) map {
      case periodID :: runID :: userID :: data :: Nil =>
        (System.currentTimeMillis(), periodID, runID, userID, data, params.getOrElse(MetadataKey, ""), params.getOrElse(DescriptionKey, ""))
      case _ =>
        throw new IllegalArgumentException("Broken Work validation format!")
    }

    valueTupleMaybe flatMap (validate _).tupled map (UserWork.apply _).tupled

  }

  protected def validate(timestamp: Long, periodID: String, runID: String, userID: String,
                         data: String, metadata: String, description: String) : Validation[String, ConsTuple] = {

    val timestampMaybe   = Validator.validateTimestamp(timestamp)
    val periodIDMaybe    = Validator.validatePeriodID(periodID)
    val runIDMaybe       = Validator.validateRunID(runID)
    val userIDMaybe      = Validator.validateUserID(userID)
    val dataMaybe        = Success(data)
    val metadataMaybe    = Success(metadata)
    val descriptionMaybe = Success(description)
    val maybes           = List(timestampMaybe, periodIDMaybe, runIDMaybe, userIDMaybe,
      dataMaybe, metadataMaybe, descriptionMaybe) map (_ map (List(_)))

    maybes reduce (_ fullFailAppend _) map {
      case (timestamp: Long) :: (periodID: String) :: (runID: String) :: (userID: String) ::
        (data: String) :: (metadata: String) :: (description: String) :: Nil =>
        (None, timestamp, periodID, runID, userID, data, metadata, description, Seq(), Seq())
      case _ =>
        throw new IllegalArgumentException("Broken Work constructor validation format!")
    }

  }

}