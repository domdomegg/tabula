package uk.ac.warwick.tabula.mitcircs.web

import uk.ac.warwick.tabula.AcademicYear
import uk.ac.warwick.tabula.data.model.mitcircs.{MitigatingCircumstancesNote, MitigatingCircumstancesSubmission}
import uk.ac.warwick.tabula.data.model.Department
import uk.ac.warwick.tabula.web.RoutesUtils

/**
  * Generates URLs to various locations, to reduce the number of places where URLs
  * are hardcoded and repeated.
  *
  * For methods called "apply", you can leave out the "apply" and treat the object like a function.
  */
object Routes {

  import RoutesUtils._

  private val context = "/mitcircs"

  def home: String = context + "/"

  object Admin {
    def home(department: Department): String = s"$context/admin/${encoded(department.code)}"
    def home(department: Department, academicYear: AcademicYear): String = s"$context/admin/${encoded(department.code)}/${encoded(academicYear.startYear.toString)}"
    def review(submission: MitigatingCircumstancesSubmission): String = s"$context/submission/${encoded(submission.key.toString)}"
    def sensitiveEvidence(submission: MitigatingCircumstancesSubmission): String = s"$context/submission/${encoded(submission.key.toString)}/sensitiveevidence"
    def readyForPanel(submission: MitigatingCircumstancesSubmission): String = s"$context/submission/${encoded(submission.key.toString)}/ready"
  }

  object Messages {
    def apply(submission: MitigatingCircumstancesSubmission): String = s"$context/submission/${encoded(submission.key.toString)}/messages"
  }

  object Notes {
    def apply(submission: MitigatingCircumstancesSubmission): String = s"$context/submission/${encoded(submission.key.toString)}/notes"
    def delete(note: MitigatingCircumstancesNote): String = s"$context/submission/${encoded(note.submission.key.toString)}/notes/${encoded(note.id)}/delete"
  }

}
