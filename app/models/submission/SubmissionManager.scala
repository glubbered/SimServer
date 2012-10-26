package models.submission

import anorm._
import anorm.SqlParser._
import play.api.db.DB

/**
 * Created with IntelliJ IDEA.
 * User: Jason
 * Date: 10/23/12
 * Time: 1:58 PM
 */

import play.api.Play.current

object SubmissionManager {

  import AnormExtras._

  def getUserWork(period: String, run: String, user: String) : Seq[UserWork] = {
    DB.withConnection { implicit connection =>
      SQL (
        """
          SELECT * FROM user_work
          WHERE period_id = {period} AND run_id = {run} AND user_id = {user};
        """
      ) on (
        "period"    -> period,
        "run"       -> run,
        "user"      -> user
      ) as {
        long("id") ~ timestamp("timestamp") ~ str("period_id") ~ str("run_id") ~ str("user_id") ~
          str("type") ~ str("data") ~ str("metadata") ~ str("description") map {
          case id ~ timestamp ~ session ~ run ~ user ~ typ ~ data ~ metadata ~ description =>
            UserWork(Option(id), timestamp, session, run, user, typ, data, metadata, description,
                     getWorkSupplementsByRefID(id), getWorkCommentsByRefID(id))
          case _ => throw new Exception("Bad format, newb!")
        } *
      }
    }
  }

  def getWorkCommentsByRefID(workRefID: Long) : Seq[UserWorkComment] = {
    DB.withConnection { implicit connection =>
      SQL (
        """
          SELECT * FROM user_work_comments
          WHERE ref_id = {refID};
        """
      ) on (
        "refID" -> workRefID
      ) as {
        long("id") ~ long("ref_id") ~ timestamp("timestamp") ~ str("user_id") ~ str("comment") map {
          case id ~ refID ~ timestamp ~ user ~ comment => UserWorkComment(Option(id), Option(refID), timestamp, user, comment)
          case _ => throw new Exception("Bad format, newb!")
        } *
      }
    }
  }

  def getWorkSupplementsByRefID(workRefID: Long) : Seq[UserWorkSupplement] = {
    DB.withConnection { implicit connection =>
      SQL (
        """
          SELECT * FROM user_work_supplements
          WHERE ref_id = {refID};
        """
      ) on (
        "refID" -> workRefID
      ) as {
        long("id") ~ long("ref_id") ~ str("type") ~ str("data") ~ str("metadata") map {
          case id ~ refID ~ typ ~ data ~ metadata => UserWorkSupplement(Option(id), Option(refID), typ, data, metadata)
          case _ => throw new Exception("Bad format, newb!")
        } *
      }
    }
  }

  def submit[T <% Submittable](submission: T) : Long = submission.submit

}

sealed trait Submittable {
  def submit : Long
}

private object Submittable {

  implicit def userWork2Submittable(userWork: UserWork) = new Submittable {
    override def submit : Long = DB.withConnection { implicit connection =>

        val sql = SQL (
          """
            INSERT INTO user_work
            (timestamp, period_id, run_id, user_id, type, data, metadata, description) VALUES
            ({timestamp}, {periodID}, {runID}, {userID}, {type}, {data}, {metadata}, {description});
          """
        ) on (
          "timestamp"   -> userWork.timestamp,
          "periodID"    -> userWork.periodID,
          "runID"       -> userWork.runID,
          "userID"      -> userWork.userID,
          "type"        -> userWork.typ,
          "data"        -> userWork.data,
          "metadata"    -> userWork.metadata,
          "description" -> userWork.description
          )

        sql.executeInsert().get

    }
  }

  implicit def workComment2Submittable(workComment: UserWorkComment) = new Submittable {
    override def submit : Long = DB.withConnection { implicit connection =>

        val sql = SQL (
          """
            INSERT INTO user_work_comments
            (ref_id, timestamp, user_id, comment) VALUES
            ({refID}, {timestamp}, {userID}, {comment});
          """
        ) on (
          "refID"     -> workComment.refID,
          "timestamp" -> workComment.timestamp,
          "userID"    -> workComment.userID,
          "comment"   -> workComment.comment
        )

        sql.executeInsert().get

    }
  }

  implicit def workSupplement2Submittable(workSupplement: UserWorkSupplement) = new Submittable {
    override def submit : Long = DB.withConnection { implicit connection =>

        val sql = SQL (
          """
            INSERT INTO user_work_supplements
            (ref_id, type, data, metadata) VALUES
            ({refID}, {type}, {data}, {metadata});
          """
        ) on (
          "refID"    -> workSupplement.refID,
          "type"     -> workSupplement.typ,
          "data"     -> workSupplement.data,
          "metadata" -> workSupplement.metadata
        )

        sql.executeInsert().get

    }
  }

}

object AnormExtras {
  import java.math.{ BigInteger => JBigInt }
  def timestamp(columnName: String) : RowParser[Long] = get[JBigInt](columnName)(implicitly[Column[JBigInt]]) map (new BigInt(_).toLong)
}