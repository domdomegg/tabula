package uk.ac.warwick.tabula.helpers

import uk.ac.warwick.tabula.TestBase
import collection.JavaConverters._
import uk.ac.warwick.tabula.JavaImports

class LazyListsTest extends TestBase with JavaImports {
	
	@Test def lazyList {
		val list: JList[String] = LazyLists.simpleFactory()
		
		list.get(3) should be ("")
		list.get(20) should be ("")
		list.get(20) should be ("")
	}

}