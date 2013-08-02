package uk.ac.warwick.tabula.data.model.forms

import java.io.StringReader
import scala.annotation.target.field
import collection.JavaConversions._
import com.fasterxml.jackson.databind.ObjectMapper
import org.hibernate.annotations.Type
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Configurable
import org.springframework.validation.Errors
import org.springframework.web.util.HtmlUtils._
import javax.persistence._
import uk.ac.warwick.tabula.JavaImports._
import uk.ac.warwick.tabula.commands.UploadedFile
import uk.ac.warwick.tabula.data.model.Assignment
import uk.ac.warwick.tabula.data.model.SavedSubmissionValue
import uk.ac.warwick.tabula.data.FileDao
import org.springframework.beans.factory.annotation.Configurable
import scala.xml.NodeSeq
import org.springframework.web.multipart.commons.CommonsMultipartFile
import org.springframework.web.multipart.MultipartFile
import uk.ac.warwick.tabula.data.model.MarkingWorkflow
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.data.model.GeneratedId
import uk.ac.warwick.userlookup.User
import uk.ac.warwick.tabula.services.UserLookupService
import scala.reflect._
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.tabula.JsonObjectMapperFactory

/**
 * A FormField defines a field to be displayed on an Assignment
 * when a student is making a submission.
 *
 * Submissions are bound in the command as SubmissionValue items,
 * and if validation passes a Submission object is saved with a
 * collection of SavedSubmissionValue objects.
 *
 * Although there can be many types of FormField, many of them
 * can use the same SubmissionValue class if they contain the
 * same sort of data (e.g. a string), and there is only one
 * SavedSubmissionValue class.
 *
 */
@Entity @Access(AccessType.FIELD)
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "fieldtype")
abstract class FormField extends GeneratedId with Logging {

	@transient var json = JsonObjectMapperFactory.instance
	@transient var userLookup = Wire.auto[UserLookupService]

	@BeanProperty
	@ManyToOne
	@(JoinColumn @field)(name = "assignment_id", updatable = false, nullable = false)
	var assignment: Assignment = _

	var name: String = _
	var label: String = _
	var instructions: String = _
	var required: Boolean = _

	@Basic(optional = false)
	@Access(AccessType.PROPERTY)
	def getProperties() = {
		// TODO cache the string value.
		json.writeValueAsString(propertiesMap)
	}
	def properties = getProperties

	def setProperties(props: String) {
		propertiesMap = json.readValue(new StringReader(props), classOf[Map[String, Any]])
	}

	@transient var propertiesMap: collection.Map[String, Any] = Map()

	protected def setProperty(name: String, value: Any) = {
		propertiesMap += name -> value
	}
	/**
	 * Fetch a property out of the property map if it matches the type.
	 * Careful with types as they are generally the ones that the JSON library
	 * has decided on, so integers come out as JInteger, and Int
	 * won't match.
	 */
	protected def getProperty[A : ClassTag](name: String, default: A) =
		propertiesMap.get(name) match {
			case Some(null) => default
			case Some(obj) if classTag[A].runtimeClass.isInstance(obj) => obj.asInstanceOf[A]
			case Some(obj) => {
				// TAB-705 warn when we return an unexpected type
				logger.warn("Expected property %s of type %s, but was %s".format(name, classTag[A].runtimeClass.getName, obj.getClass.getName))
				default
			}
			case _ => default
		}

	def isReadOnly = false
	final def readOnly = isReadOnly

	@Type(`type` = "int")
	var position: JInteger = 0

	/** Determines which Freemarker template is used to render it. */
	@transient lazy val template = getClass.getAnnotation(classOf[DiscriminatorValue]).value

	/**
	 * Return a blank SubmissionValue that can be used to bind a submission
	 * of the same type as this FormField.
	 */
	def blankSubmissionValue: SubmissionValue

	def validate(value: SubmissionValue, errors: Errors)

}

trait SimpleValue[A] { self: FormField =>
	def value_=(value: A) { propertiesMap += "value" -> value }
	def setValue(value: A) = value_=(value)

	def value: A = propertiesMap.getOrElse("value", null).asInstanceOf[A]
	def getValue() = value

	def blankSubmissionValue = new StringSubmissionValue(this)
}

@Entity
@DiscriminatorValue("comment")
class CommentField extends FormField with SimpleValue[String] with FormattedHtml {
	override def isReadOnly = true

	def formattedHtml: String = formattedHtml(Option(value))

	override def validate(value: SubmissionValue, errors: Errors) {}
}

@Entity
@DiscriminatorValue("text")
class TextField extends FormField with SimpleValue[String] {
	override def validate(value: SubmissionValue, errors: Errors) {}
}

@Entity
@DiscriminatorValue("wordcount")
class WordCountField extends FormField {
	def min: JInteger = getProperty[JInteger]("min", null)
	def min_=(limit: JInteger) = setProperty("min", limit)
	def max: JInteger = getProperty[JInteger]("max", null)
	def max_=(limit: JInteger) = setProperty("max", limit)
	def conventions: String = getProperty[String]("conventions", null)
	def conventions_=(conventions: String) = setProperty("conventions", conventions)
	
	def blankSubmissionValue = new IntegerSubmissionValue(this)

	override def validate(value: SubmissionValue, errors: Errors) {
		value match {
			case i:IntegerSubmissionValue => {
				 if (i.value == null) errors.rejectValue("value", "assignment.submit.wordCount.missing")
				 else if (i.value < min || i.value > max) errors.rejectValue("value", "assignment.submit.wordCount.outOfRange")
			}
			case _ => errors.rejectValue("value", "assignment.submit.wordCount.missing") // value was null or wrong type
		}
	}
}

@Entity
@DiscriminatorValue("textarea")
class TextareaField extends FormField with SimpleValue[String] {
	override def validate(value: SubmissionValue, errors: Errors) {}
}

@Entity
@DiscriminatorValue("checkbox")
class CheckboxField extends FormField {
	def blankSubmissionValue = new BooleanSubmissionValue(this)
	override def validate(value: SubmissionValue, errors: Errors) {}
}

@Entity
@DiscriminatorValue("marker")
class MarkerSelectField extends FormField with SimpleValue[String] {

	def markers:Seq[User] = {
		if (assignment.markingWorkflow == null) Seq()
		else assignment.markingWorkflow.firstMarkers.includeUsers.map(userLookup.getUserByUserId(_))
	}

	override def validate(value: SubmissionValue, errors: Errors) {
		value match {
			case v: StringSubmissionValue => {
				Option(v.value) match {
					case None => errors.rejectValue("value", "marker.missing")
					case Some(v) if v == "" => errors.rejectValue("value", "marker.missing")
					case Some(v) if !markers.exists { _.getUserId == v } => errors.rejectValue("value", "marker.invalid")
					case _ =>
				}
			}
		}
	}
}

@Entity
@DiscriminatorValue("file")
class FileField extends FormField {
	def blankSubmissionValue = new FileSubmissionValue(this)

	def attachmentLimit: Int = getProperty[JInteger]("attachmentLimit", 1)
	def attachmentLimit_=(limit: Int) = setProperty("attachmentLimit", limit)

	// List of extensions.
	def attachmentTypes: Seq[String] = getProperty[Seq[String]]("attachmentTypes", Seq())
	def attachmentTypes_=(types: Seq[String]) = setProperty("attachmentTypes", types: Seq[String])
	
	// This is after onBind is called, so any multipart files have been persisted as attachments
	override def validate(value: SubmissionValue, errors: Errors) {
		
		/** Are there any duplicate values (ignoring case)? */
		def hasDuplicates(names: Seq[String]) = names.size != names.map(_.toLowerCase()).distinct.size
		
		value match {
			case v: FileSubmissionValue => {
				if (v.file.isMissing) {
					errors.rejectValue("file", "file.missing")
				} else if (v.file.size > attachmentLimit) {
					if (attachmentLimit == 1) errors.rejectValue("file", "file.toomany.one")
					else errors.rejectValue("file", "file.toomany", Array(attachmentLimit: JInteger), "")
				} else if (hasDuplicates(v.file.fileNames)) {
					errors.rejectValue("file", "file.duplicate")
				} else if (!attachmentTypes.isEmpty) {
					val attachmentStrings = attachmentTypes.map(s => "." + s)
					val fileNames = v.file.fileNames map (_.toLowerCase)
					val invalidFiles = fileNames.filter(s => !attachmentStrings.exists(s.endsWith))
					if (invalidFiles.size > 0) {
						if (invalidFiles.size == 1) errors.rejectValue("file", "file.wrongtype.one", Array(invalidFiles.mkString("")), "")
						else errors.rejectValue("file", "file.wrongtype", Array(invalidFiles.mkString(", ")), "")
					}
				}
			}
		}
	}
}
