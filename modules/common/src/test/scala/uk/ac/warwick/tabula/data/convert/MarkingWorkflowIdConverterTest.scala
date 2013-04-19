package uk.ac.warwick.tabula.data.convert
import org.hibernate.SessionFactory
import org.hibernate.classic.Session

import uk.ac.warwick.tabula.Mockito

import uk.ac.warwick.tabula.TestBase
import uk.ac.warwick.tabula.data.model.MarkingWorkflow

class MarkingWorkflowIdConverterTest extends TestBase with Mockito {
	
	val converter = new MarkingWorkflowIdConverter
	
	val sessionFactory = mock[SessionFactory]
	val session = mock[Session]
	
	sessionFactory.getCurrentSession() returns (session)
	
	converter.sessionFactory = sessionFactory
	
	@Test def validInput {
		val workflow = new MarkingWorkflow
		workflow.id = "steve"
			
		session.get(classOf[MarkingWorkflow].getName(), "steve") returns (workflow)
		
		converter.convertRight("steve") should be (workflow)
	}
	
	@Test def invalidInput {
		session.get(classOf[MarkingWorkflow].getName(), "20X6") returns (null)
		
		converter.convertRight("20X6") should be (null)
		converter.convertRight(null) should be (null)
	}
	
	@Test def formatting {
		val workflow = new MarkingWorkflow
		workflow.id = "steve"
		
		converter.convertLeft(workflow) should be ("steve")
		converter.convertLeft(null) should be (null)
	}

}