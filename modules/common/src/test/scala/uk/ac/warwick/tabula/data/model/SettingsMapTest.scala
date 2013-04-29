package uk.ac.warwick.tabula.data.model

import uk.ac.warwick.tabula.TestBase

// scalastyle:off magic.number
class SettingsMapTest extends TestBase {
	
	class TestSettingsMap extends SettingsMap[TestSettingsMap]
	
	@Test def itWorks {
		val map = new TestSettingsMap {
			settingsSeq should be (Seq())
			getSetting("some setting") should be (None)
			getStringSetting("string setting", "default") should be ("default")
			getBooleanSetting("bool setting", true) should be (true)
			getIntSetting("int setting", 5) should be (5)
			
			++= (
					"string setting" -> "tease",
					"bool setting" -> false,
					"int setting" -> 7
			)
			
			getSetting("string setting") should be (Some("tease"))
			getStringSetting("string setting", "default") should be ("tease")
			getBooleanSetting("bool setting", true) should be (false)
			getIntSetting("int setting", 5) should be (7)
			
			settingsSeq should be (Seq(
					("string setting" -> "tease"),
					("bool setting" -> false),
					("int setting" -> 7)))
		}
	}

}