package uk.ac.warwick.tabula.events

import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.commands.{Command, GeneratesTriggers}
import uk.ac.warwick.tabula.services.TriggerService


trait TriggerHandling {

	var triggerService = Wire[TriggerService]

	def handleTriggers[A](cmd: Command[A])(f: => A): A = {
		val result = f

		cmd match {
			case gt: GeneratesTriggers[A @unchecked] =>
				triggerService.removeExistingTriggers(result)
				gt.generateTriggers(result).foreach(triggerService.push)
			case _ =>
		}

		result
	}

}
