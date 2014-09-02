package uk.ac.warwick.tabula.groups.pages

import org.openqa.selenium.{WebElement, WebDriver, By}
import uk.ac.warwick.tabula.{FunctionalTestProperties, BreadcrumbsMatcher}
import org.scalatest.selenium.WebBrowser.Page
import org.scalatest.selenium.WebBrowser
import scala.collection.JavaConverters._
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.ShouldMatchers
import uk.ac.warwick.tabula.EventuallyAjax


class SmallGroupTeachingPage(val departmentCode:String)(implicit val webDriver:WebDriver) extends Page with WebBrowser with	BreadcrumbsMatcher with EventuallyAjax with ShouldMatchers  with GroupSetList {

	val url = FunctionalTestProperties.SiteRoot + "/groups/admin/department/" + departmentCode

	def isCurrentPage(): Boolean = {
		currentUrl should include ("/groups/admin/department/" + departmentCode)
		find(cssSelector("h1")).get.text == ("Tabula » Small Group Teaching")
	}

	def getBatchOpenButton() = {
		val manageButton = find(linkText("Manage")).get.underlying
		manageButton.click()
		val manageDropdownContainer = find(cssSelector("div.dept-toolbar")).get.underlying
		val openButton = manageDropdownContainer.findElement(By.partialLinkText("Open"))
		eventually {
			openButton.isDisplayed should be (true)
		}
		openButton
	}
}

class GroupSetInfoSummarySection(val underlying: WebElement, val moduleCode: String)(implicit webDriver: WebDriver) extends Eventually with ShouldMatchers {

	val groupsetId = {
		underlying.getAttribute("id").replaceFirst("set-","")
	}

	def goToEditProperties: EditSmallGroupSetPropertiesPage = {
		underlying.findElement(By.partialLinkText("Actions")).click()
		val editGroupset = underlying.findElement(By.partialLinkText("Edit"))
		eventually {
			editGroupset.isDisplayed should be (true)
		}
		editGroupset.click()
		val propsPage = new EditSmallGroupSetPropertiesPage()
		// HACK: the module name is the module code in uppercase in the test data. Should really pass it around separately
		propsPage.isCurrentPage(moduleCode.toUpperCase)
		propsPage
	}

	def goToEditGroups: EditSmallGroupSetGroupsPage = {

		val propsPage = goToEditProperties
		propsPage.submitAndAddGroups()

		val groupsPage = new EditSmallGroupSetGroupsPage()
		// HACK: the module name is the module code in uppercase in the test data. Should really pass it around separately
		groupsPage.isCurrentPage(moduleCode.toUpperCase)
		groupsPage
	}

	def goToAllocate = {
		underlying.findElement(By.partialLinkText("Actions")).click()
		val allocate = underlying.findElement(By.partialLinkText("Allocate students"))
		eventually {
			allocate.isDisplayed should be(true)
		}
		allocate.click()
		val allocatePage = new AllocateStudentsToGroupsPage()
		allocatePage.isCurrentPage(moduleCode.toUpperCase())
		allocatePage
	}

	def isShowingOpenButton() = {
		underlying.findElement(By.partialLinkText("Actions")).click()
		!underlying.findElements(By.partialLinkText("Open")).isEmpty
	}

	def getOpenButton() = {
		underlying.findElement(By.partialLinkText("Actions")).click()
		underlying.findElement(By.partialLinkText("Open"))
	}
}

class ModuleGroupSetInfoSummarySection(val underlying: WebElement, val moduleCode: String)(implicit webDriver: WebDriver) extends Eventually with ShouldMatchers {

	val groupsetId = {
		val classes = underlying.getAttribute("class").split(" ")
		classes.find(_.startsWith("groupset-")).get.replaceFirst("groupset-","")
	}

	def getSignupButton() = {
		underlying.findElement(By.className("sign-up-button"))
	}

	def findLeaveButtonFor(groupName:String) = {
		underlying.findElements(By.tagName("h4")).asScala.filter(e=>e.getText.trim.startsWith(groupName + " ")).headOption.flatMap(

			groupNameHeading=>{
				val parent = groupNameHeading.findElement(By.xpath(".."))
				//groupNameHeading.findElement(By.xpath("../form/input[@type='submit']"))}
				val maybeButton = parent.findElements(By.cssSelector("form input.btn")) // zero or 1-element java.util.List
				if (maybeButton.size()==0){
					None
				}else{
					Some(maybeButton.get(0))
				}
			}
		)
	}

	def showsGroupLockedIcon(): Boolean = {
		(underlying.findElements(By.className("icon-lock")).asScala.headOption).isDefined

	}

	def showsGroup(groupName:String) = {
		underlying.findElements(By.tagName("h4")).asScala.filter(e=>e.getText.trim.startsWith(groupName + " ")).headOption.isDefined
	}

	def findSelectGroupCheckboxFor(groupName:String ) = {
		val groupNameHeading = underlying.findElements(By.tagName("h4")).asScala.filter(e=>e.getText.trim.startsWith(groupName + " ")).head
		// ugh. Might be worth investigating ways of using JQuery selector/traversals in selenium instead of this horror:
		groupNameHeading.findElement(By.xpath("../../div[contains(@class,'pull-left')]/input"))
	}
}

class BatchOpenPage(val departmentCode: String)(implicit webDriver: WebDriver) extends Page with WebBrowser with Eventually with ShouldMatchers {
	val url = FunctionalTestProperties.SiteRoot + s"/groups/admin/department/${departmentCode}/groups/open"

	def isCurrentPage(): Boolean = {
		currentUrl should include (s"/groups/admin/department/${departmentCode}/groups/selfsignup/open")
		find(cssSelector("#main-content h1")).get.text.startsWith("Open groups in ")
	}

	def checkboxForGroupSet(groupset: GroupSetInfoSummarySection) = {
		findAll(tagName("input")).filter(_.underlying.getAttribute("value") == groupset.groupsetId).next.underlying
	}

	def submit() {
		findAll(tagName("input")).filter(_.underlying.getAttribute("type") == "submit").next.underlying.click()
	}
}

trait GroupSetList {
	this: WebBrowser with EventuallyAjax with ShouldMatchers =>

	def getGroupsetInfo(moduleCode: String, groupsetName: String)(implicit webdriver:WebDriver): Option[GroupSetInfoSummarySection] = {
		val setInfoElements = findAll(className("set-info")).filter(_.underlying.findElement(By.className("colour-h6")).getText.trim == groupsetName)
		if (setInfoElements.isEmpty) {
			None
		}	else {
			Some(setInfoElements.next().underlying).map { section =>
				if (section.getAttribute("class").indexOf("collapsible") != -1 && section.getAttribute("class").indexOf("expanded") == -1) {
					click on section.findElement(By.className("section-title"))

					eventuallyAjax {
						section.getAttribute("class") should include ("expanded")
					}
				}

				new GroupSetInfoSummarySection(section, moduleCode)
			}
		}
	}
}

trait ModuleAndGroupSetList {
	this: WebBrowser with EventuallyAjax with ShouldMatchers =>

	def getGroupsetInfo(moduleCode: String, groupsetName: String)(implicit webdriver: WebDriver): Option[ModuleGroupSetInfoSummarySection] = {
		getModuleInfo(moduleCode).flatMap { module =>
			if (module.getAttribute("class").indexOf("collapsible") != -1 && module.getAttribute("class").indexOf("expanded") == -1) {
				click on module.findElement(By.className("section-title"))
				eventuallyAjax {
					module.getAttribute("class") should include("expanded")
				}
			}

			val groupSet = module.findElements(By.className("item-info")).asScala.find(_.findElement(By.className("name")).getText.trim == groupsetName)
			groupSet.map { new ModuleGroupSetInfoSummarySection(_, moduleCode) }
		}
	}

	private def getModuleInfo(moduleCode: String)(implicit webdriver: WebDriver): Option[WebElement] = {
		val moduleInfoElements = findAll(className("module-info")).filter(_.underlying.findElement(By.className("mod-code")).getText == moduleCode.toUpperCase)
		if (moduleInfoElements.isEmpty) {
			None
		} else {
			Some(moduleInfoElements.next().underlying)
		}
	}
}