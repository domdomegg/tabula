package uk.ac.warwick.tabula.events
import uk.ac.warwick.tabula.commands.Describable
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Configurable
import scala.reflect.BeanProperty
import uk.ac.warwick.tabula.commands.Unaudited
import uk.ac.warwick.tabula.helpers.Logging
import uk.ac.warwick.spring.Wire

object EventHandling {
	/**
	 * You'd only ever disable this in testing.
	 */
	var enabled = true
}

/**
 * Gives a class the ability to record events from a Describable object.
 */
trait EventHandling extends Logging {
	val listener = Wire.auto[EventListener]

	/**
	 * Records the various stages of an event: before, and either
	 * after or error. All of them should have the same eventid to
	 * join them together (though they also have unique primary keys).
	 */
	def recordEvent[A](d: Describable[A])(f: => A): A =
		d match {
			case _: Unaudited => f // don't audit unaudited events!
			case _ => {
				val event = Event.fromDescribable(d)
				try {
					listener.beforeCommand(event)
					val result = f
					val resultEvent = Event.resultFromDescribable(d, result, event.id)
					listener.afterCommand(resultEvent, result)
					return result
				} catch {
					case e => {
						// On exception, pass that on then rethrow.
						// If the exception handler throws an exception, just log that and rethrow the original
						try {
							listener.onException(event, e)
						} catch {
							case e1 => logger.error("Exception in EventHandling.onException", e1)
						} finally {
							throw e
						}
						throw e
					}
				}
			}
		}

}