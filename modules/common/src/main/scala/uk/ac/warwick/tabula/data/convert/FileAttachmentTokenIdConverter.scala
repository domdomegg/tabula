package uk.ac.warwick.tabula.data.convert


import uk.ac.warwick.tabula.data.model.FileAttachmentToken
import uk.ac.warwick.tabula.system.TwoWayConverter
import uk.ac.warwick.tabula.data.Daoisms

class FileAttachmentTokenIdConverter extends TwoWayConverter[String, FileAttachmentToken] with Daoisms {

	override def convertRight(id: String) = getById[FileAttachmentToken](id).orNull
	override def convertLeft(token: FileAttachmentToken) = (Option(token) map {_.id}).orNull}