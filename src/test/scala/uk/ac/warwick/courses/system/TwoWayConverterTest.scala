package uk.ac.warwick.courses.system

import uk.ac.warwick.courses.TestBase
import org.joda.time.Days
import org.springframework.core.convert.TypeDescriptor


class TwoWayConverterTest extends TestBase {

	@Test def converting {
		val converter = new DaysConverter
		converter.convert("3", descriptor[String], descriptor[Days]) should be (Days.THREE)
		converter.convert(Days.FIVE, descriptor[Days], descriptor[String]) should be ("5")
	}

	private def descriptor[T](implicit m:Manifest[T]) = TypeDescriptor.valueOf(m.erasure)
}


/*
 * Test converter which converts from a string of a number into a Days instance,
 * or null if invalid string.
 *
 * "4" -> Days.FOUR
 * Days.NINE -> "9"
 *
 * etc.
 */
class DaysConverter extends TwoWayConverter[String, Days] {
	def convertRight(source: String) =
		try {
			Days.days(source.toInt)
		} catch {
			case _:NumberFormatException => null
		}

	def convertLeft(source: Days) = source.getDays.toString
}
