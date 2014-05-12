package uk.ac.warwick.tabula

import org.scalatest.selenium.WebBrowser
import org.scalatest.Assertions
import org.scalatest.matchers.ShouldMatchers
import org.openqa.selenium.WebDriver
import org.scalatest.concurrent.Eventually
import org.scalatest.time.SpanSugar._



trait WebsignonMethods extends ShouldMatchers  with Eventually{
	import WebBrowser._ // include methods like "go to"
	implicit val webDriver: WebDriver // let the trait know this will be implemented
	
	// nested objects so we can say "signIn.as(user) to (url)".
	// with Java punctuation it would be "(signIn.as(user)).to(url)". 
	//
	// (currently requires that the user's first name is the usercode, to check signed-in-ness)
	object signIn {
		def as(details: LoginDetails) = {
			SigningInPhase(details)
		}

    case class SigningInPhase(details: LoginDetails) {
      def to(url: String) {
        go to (url)
        // FIXME doesn't handle being signed in as another user
        // TODO doesn't check that SSO sends you back to the right page
        // FIXME requires firstName==usercode
        if (pageSource contains ("Signed in as " + details.usercode)) {
          // we're done
        } else {

          if (pageSource contains ("Signed in as ")) {
						// signed in as someone else; sign out first
						click on linkText("Sign out")
					}else if (pageTitle startsWith("Sign in - Access refused")){
						//signed in as someone else who doesn't have permissions to view the page
						//  - follow the "sign in as another user" link
						click on linkText("Sign in with a different username.")
					}

          // sign in if we've not already been taken to that page
          if (!pageTitle.contains("Sign in")) {
						if (linkText("Sign in").findElement.isDefined) {
							click on linkText("Sign in")
						} else if (linkText("Sign out").findElement.isDefined) {
							val SignedInAs = "(Signed in as [a-zA-Z0-9\\s]+)".r
							val detail = pageSource match {
								case SignedInAs(line) => line
								case _ => "couldn't parse anything useful from the HTML"
							}
							fail(s"Tried to sign out to sign in as ${details.usercode}, but still appear to be signed in! (${detail})")
						} else {
							fail("No Sign in or out links! URL:"+currentUrl)
						}
          }
					// wait for the page to load
					eventually(timeout(10.seconds), interval(200.millis))(
						pageTitle should include("Sign in")
					)
					textField("userName").value = details.usercode
          // FIXME lame way of setting password field - fixed in scalatest but not
          // yet released. fix it when it's released. https://groups.google.com/forum/?fromgroups=#!topic/scalatest-users/ojW9g4-2fmI
          id("password").webElement.sendKeys(details.password)
          submit()
					// Sign-out operations redirect you to the context root, so we may now be on the wrong page...
					if (currentUrl != url){
						go to url
					}
          if (pageSource contains ("Signed in as " + details.usercode)) {
            // NOW we're done
          } else if (pageSource contains ("Access refused")) {
            Assertions.fail("Signed in as " + details.description + " but access refused to " + url)
          } else {
            Assertions.fail("Tried to sign in as " + details.description + " but failed.")
          }
        }
      }
    }
	}


	
}