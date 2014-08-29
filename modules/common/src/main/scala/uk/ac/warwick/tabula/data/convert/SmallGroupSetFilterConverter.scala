package uk.ac.warwick.tabula.data.convert

import org.springframework.beans.factory.annotation.Autowired
import uk.ac.warwick.tabula.data.model.groups.{SmallGroupSetFilters, SmallGroupSetFilter}
import uk.ac.warwick.tabula.services.{SmallGroupService, ModuleAndDepartmentService}
import uk.ac.warwick.tabula.system.TwoWayConverter
import uk.ac.warwick.tabula.helpers.StringUtils._

class SmallGroupSetFilterConverter extends TwoWayConverter[String, SmallGroupSetFilter] {

	@Autowired var moduleService: ModuleAndDepartmentService = _
	@Autowired var smallGroupService: SmallGroupService = _

	override def convertRight(source: String) = source match {
		case r"Module\(([^\)]+)${moduleCode}\)" =>
			SmallGroupSetFilters.Module(moduleService.getModuleByCode(sanitise(moduleCode)).getOrElse { moduleService.getModuleById(moduleCode).orNull })
		case r"AllocationMethod\.Linked\(([^\)]+)${id}\)" =>
			SmallGroupSetFilters.AllocationMethod.Linked(smallGroupService.getDepartmentSmallGroupSetById(id).getOrElse { throw new IllegalArgumentException })
		case _ => SmallGroupSetFilters.of(source)
	}

	override def convertLeft(source: SmallGroupSetFilter) = Option(source).map { _.getName }.orNull

	def sanitise(code: String) = {
		if (code == null) throw new IllegalArgumentException
		else code.toLowerCase
	}

}
