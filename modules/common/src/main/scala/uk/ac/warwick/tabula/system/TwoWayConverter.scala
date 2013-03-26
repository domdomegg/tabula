package uk.ac.warwick.tabula.system
import org.springframework.core.convert.TypeDescriptor
import org.springframework.core.convert.converter.GenericConverter
import org.springframework.core.convert.converter.GenericConverter.ConvertiblePair
import com.google.common.collect.Sets._
import java.{ util => j }
import org.springframework.format.Formatter
import java.util.Locale
import scala.reflect._

/**
 * A Spring GenericConverter that can convert both to and from two types.
 *
 * The regular Spring Converter interface is useful to convert from one type to another
 * and is used e.g. when binding a String parameter to another object, like a Module ID
 * to a Module. But it only works one way so you have to define two classes to get it to
 * also output the value in the view as the ID.
 *
 * There is a Formatter interface which can convert both ways, but the Parser part doesn't
 * allow null results which makes it very hard to handle invalid values cleanly.
 * (looking back, this isn't entirely accurate - the correct way to handle invalid values is
 * to throw IllegalArgumentException. The same is true with converters.)
 *
 * To use, just implement convertRight and convertLeft.
 * If a value is invalid, throw IllegalArgumentException. Spring will pick it up and correctly
 * treat it as a type mismatch.
 */
abstract class TwoWayConverter[A <: String: ClassTag, B <: AnyRef: ClassTag] extends GenericConverter with Formatter[B] {
	// JVM can't normally remember types at runtime, so store them as Manifests here
	val typeA = classTag[A]
	val typeB = classTag[B]

	val convertibleTypes: j.Set[ConvertiblePair] = newHashSet(
		new ConvertiblePair(typeA.runtimeClass, typeB.runtimeClass),
		new ConvertiblePair(typeB.runtimeClass, typeA.runtimeClass))

	def getConvertibleTypes = convertibleTypes

	// implement these. throw an IllegalArgumentException if input is invalid - don't just return null!
	def convertRight(source: A): B
	def convertLeft(source: B): A

	// Formatter used for generating textual value in template
	override def parse(string: String, locale: Locale): B = convertRight(string.asInstanceOf[A])
	override def print(value: B, locale: Locale): A = convertLeft(value)

	// Convert either left or right, depending on the two types.
	def convert(source: Any, sourceType: TypeDescriptor, targetType: TypeDescriptor) = {
		// normally a match statement is cleaner but they are almost as messy when manifests are involved.
		if (matching(sourceType, typeA) && matching(targetType, typeB)) {
			convertRight(source.asInstanceOf[A])
		} else if (matching(sourceType, typeB) && matching(targetType, typeA)) {
			convertLeft(source.asInstanceOf[B])
		} else {
			// ought never to happen as Spring checks getConvertibleTypes beforehand.
			throw new IllegalArgumentException("Unexpected source type " + sourceType.getType.getName)
		}
	}

	private def matching(descriptor: TypeDescriptor, manifest: ClassTag[_]) =
		descriptor.getType().isAssignableFrom(manifest.runtimeClass)
}
