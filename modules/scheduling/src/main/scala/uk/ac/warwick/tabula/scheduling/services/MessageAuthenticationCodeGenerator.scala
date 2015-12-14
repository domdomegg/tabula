package uk.ac.warwick.tabula.scheduling.services

import org.springframework.stereotype.Service
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.helpers.StringUtils._
import org.apache.commons.codec.digest.DigestUtils

trait MessageAuthenticationCodeGenerator {

    /**
     * Generate the Message Authentication Code for this String.
     */
	def generateMessageAuthenticationCode(message: String): String

	def isValidSalt: Boolean

}

/**
 * Implementation of {@link MessageAuthenticationCodeGenerator} that hashes the url params
 * and a salt using the SHA-1 algorithm.
 */
@Service
class SHAMessageAuthenticationCodeGenerator extends MessageAuthenticationCodeGenerator {

	def this(salt: String) {
		this()
		this.salt = salt
	}

	var salt = Wire[String]("${tabula.sync.shared.secret}")

	override def generateMessageAuthenticationCode(message: String) =
		if (!isValidSalt) null
		else getSHAHash(message.concat(salt))

	// backed by Apache Commons-Codec
	private def getSHAHash(input: String) = DigestUtils.shaHex(input)

	override def isValidSalt = salt.hasText

}

trait MessageAuthenticationCodeGeneratorComponent {
	def messageAuthenticationCodeGenerator: MessageAuthenticationCodeGenerator
}

trait AutowiringMessageAuthenticationCodeGeneratorComponent extends MessageAuthenticationCodeGeneratorComponent {
	val messageAuthenticationCodeGenerator = Wire[MessageAuthenticationCodeGenerator]
}