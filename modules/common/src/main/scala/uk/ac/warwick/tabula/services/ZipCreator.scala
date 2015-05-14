package uk.ac.warwick.tabula.services

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import org.springframework.http.HttpStatus
import uk.ac.warwick.tabula.system.exceptions.UserError

import scala.collection.mutable.ListBuffer
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream.UnicodeExtraFieldPolicy
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import uk.ac.warwick.tabula.helpers.Logging
import java.util.UUID
import java.util.zip.Deflater
import uk.ac.warwick.util.core.spring.FileUtils
import uk.ac.warwick.tabula.helpers.StringUtils._

/**
 * An item in a Zip file. Can be a file or a folder.
 */
sealed trait ZipItem {
	def name: String
	def length: Long
}
case class ZipFileItem(name: String, input: InputStream, length: Long) extends ZipItem
case class ZipFolderItem(name: String, startItems: Seq[ZipItem] = Nil) extends ZipItem {
	var items: ListBuffer[ZipItem] = ListBuffer()
	items.appendAll(startItems)

	def length = items.map { _.length }.sum
}

/**
 * Mixin trait for creating zip files based on a list of ZipItems.
 * With ZipItem it's easier to build hierarchical folders of items, and you
 * can reuse methods that create items to nest them further into folders,
 * which is difficult to do directly with ZipOutputStream.
 *
 * Requires a "zipDir" property to be set, relating to the base directory
 * where resulting files should be stored.
 *
 * If an error occurs while writing the zip, the target file is deleted
 * (this is because our zip files are generally created and left in place to
 * be re-used later, so it's better to delete and try recreating later
 * than to keep serving half a corrupt file).
 */
trait ZipCreator extends Logging {
	import ZipCreator._

	def zipDir: File

	/**
	 * General method for building a zip out of a list of ZipItems.
	 * A ZipItem can be a ZipFolderItem for defining hierarchies of files.
	 *
	 * name will be a path underneath the zipDir root, e.g.
	 * "feedback/ab/cd/ef/123". A zip extension will be added.
	 *
	 * If a zip of the given name already exists, it returns that file
	 * instead of regenerating the file. The app has to remember to call
	 * invalidateZip whenever the contents of the zip would change, otherwise
	 * it becomes stale.
	 */
	@throws[ZipRequestTooLargeError]("if the file doesn't exist and the items are too large to be zipped")
	def getZip(name: String, items: Seq[ZipItem]) = {
		val file = fileForName(name)
		if (!file.exists) writeToFile(file, items)
		file
	}

	/**
	 * Create a new Zip with a randomly generated name.
	 */
	@throws[ZipRequestTooLargeError]("if the items are too large to be zipped")
	def createUnnamedZip(items: Seq[ZipItem], progressCallback: (Int, Int) => Unit = {(_,_) => }) = {
		val file = unusedFile
		writeToFile(file, items, progressCallback)
		file
	}

	private def isOverSizeLimit(items: Seq[ZipItem]) =
		items.map { _.length }.sum > MaxZipItemsSizeInBytes
	
	private val CompressionLevel = Deflater.BEST_COMPRESSION

	@throws[ZipRequestTooLargeError]("if the items are too large to be zipped")
	private def writeToFile(file: File, items: Seq[ZipItem], progressCallback: (Int, Int) => Unit = {(_,_) => }) = {
		if (isOverSizeLimit(items)) throw new ZipRequestTooLargeError

		file.getParentFile.mkdirs
		openZipStream(file) { (zip) =>
			zip.setLevel(CompressionLevel)
			// HFC-70 Windows compatible, but fixes filenames in good apps like 7-zip 
			zip.setCreateUnicodeExtraFields(UnicodeExtraFieldPolicy.NOT_ENCODEABLE)
			writeItems(items, zip, progressCallback)
		}
	}
	
	private val UnusedFilenameAttempts = 100

	/** Try 100 times to get an unused filename */
	private def unusedFile = Stream.range(1, UnusedFilenameAttempts)
		.map(_ => fileForName(randomUUID))
		.find(!_.exists)
		.getOrElse(throw new IllegalStateException("Couldn't find unique filename"))

	private def randomUUID = UUID.randomUUID.toString.replace("-", "")

	/**
	 * Invalidates a previously created zip, by deleting its file.
	 *
	 * @param name The name as passed to getZip when creating the file.
	 */
	def invalidate(name: String) = fileForName(name).delete()

	private def fileForName(name: String) = new File(zipDir, name + ".zip")

	private def writeItems(items: Seq[ZipItem], zip: ZipArchiveOutputStream, progressCallback: (Int, Int) => Unit = {(_,_) => }) {
		def writeFolder(basePath: String, items: Seq[ZipItem]) {
			items.zipWithIndex.foreach{ case(item, index) => item match {
				case file: ZipFileItem =>
					zip.putArchiveEntry(new ZipArchiveEntry(basePath + trunc(file.name, MaxFileLength)))
					copy(file.input, zip)
					zip.closeArchiveEntry()
					progressCallback(index, items.size)
				case folder: ZipFolderItem => writeFolder(basePath + trunc(folder.name, MaxFolderLength) + "/", folder.items)
			}}
		}
		writeFolder("", items)
	}
	
	def trunc(name: String, limit: Int) =
		if (name.length() <= limit) name
		else {
			val ext = FileUtils.getLowerCaseExtension(name)
			if (ext.hasText) FileUtils.getFileNameWithoutExtension(name).safeSubstring(0, limit) + "." + ext 
			else name.substring(0, limit)
		}

	/**
	 * Opens a zip output stream from this file, and runs the given function.
	 * The output stream is always closed, and if anything bad happens the file
	 * is deleted.
	 */
	private def openZipStream(file: File)(fn: (ZipArchiveOutputStream) => Unit) {
		var zip: ZipArchiveOutputStream = null
		try {
			zip = new ZipArchiveOutputStream(file)
			fn(zip)
		} catch {
			case e: Exception =>
				logger.error("Exception creating zip file, deleting %s" format file)
				file.delete
				throw e
		} finally {
			if (zip != null) zip.close()
		}
	}
	
	private val BufferSizeInBytes = 4096 // 4kb

	// copies from is to os, but doesn't close os
	private def copy(is: InputStream, os: OutputStream) {
		try {
			// not sure how to create a byte[] directly, this seems reasonable.
			val buffer = ByteBuffer.allocate(BufferSizeInBytes).array
			// "continually" creates an endless iterator, "takeWhile" gives it an end
			val iterator = Iterator.continually { is.read(buffer) }.takeWhile { _ != -1 }
			for (read <- iterator) {
				os.write(buffer, 0, read)
			}
		} finally {
			is.close()
		}
	}

}

object ZipCreator {
	val MaxZipItemsSizeInBytes = 2L * 1024 * 1024 * 1024 // 2gb

	val MaxFolderLength = 20
	val MaxFileLength = 100
}

class ZipRequestTooLargeError extends java.lang.RuntimeException("Files too large to compress") with UserError {
	override val httpStatus = HttpStatus.PAYLOAD_TOO_LARGE
}