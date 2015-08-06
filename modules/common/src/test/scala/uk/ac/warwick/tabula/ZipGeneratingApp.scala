package uk.ac.warwick.tabula

import java.io.File
import uk.ac.warwick.tabula.services._
import java.io.ByteArrayInputStream
import java.io.InputStream
import language.implicitConversions

object ZipGeneratingApp extends App with ZipCreator {
	def zipDir = new File(System.getProperty("java.io.tmpdir"))

	implicit def stringStream(text:String): InputStream = new ByteArrayInputStream(text.getBytes("UTF-8"))

	val zip = getZip("euros-uncompatibles", Seq(
		ZipFileItem("README.txt", "Don't feed them after midnight", 30),
		ZipFolderItem("Languages", Seq(
			ZipFileItem("Greek - \u03B1\u03B2\u03B3\u03B4\u03B5\u03B6\u03B7\u03B8.txt", "It's all Greek to me.", 20),
			ZipFileItem("English - abcdefghji.txt", "No problems here, hopefully.", 30)
		)),
		ZipFileItem("PRELUDE \u0E01\u0E02\u0E03\u0E04\u0E05\u0E06.txt", "This concludes our presentation.", 30)
	))
}