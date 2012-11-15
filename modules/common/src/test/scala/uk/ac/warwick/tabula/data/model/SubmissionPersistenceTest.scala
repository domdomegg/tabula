package uk.ac.warwick.tabula.data.model

import uk.ac.warwick.tabula.PersistenceTestBase
import org.junit.runner.RunWith
import org.junit.Test
import uk.ac.warwick.tabula.JavaImports._
import javax.persistence.Entity
import org.hibernate.annotations.AccessType
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner
import uk.ac.warwick.tabula.Fixtures


class SubmissionPersistenceTest extends PersistenceTestBase {
	
	/**
	 * Test the relationship between Submission and OriginalityReport.
	 */
    @Test def submissionsAndReports {
    	transactional { t => 
    		
        	val submission = Fixtures.submission()
        	val assignment = newDeepAssignment()
        	submission.assignment = assignment
        	
        	val attachment = new FileAttachment()
        	
        	val value = SavedSubmissionValue.withAttachments(submission, "upload", JSet(attachment))
        	submission.values.add(value)
        	
        	session.save(assignment.module.department)
        	session.save(assignment.module)
        	session.save(assignment)
        	session.save(submission)
        	session.save(value)
        	session.save(attachment)
        	
        	val report1 = newReport
        	attachment.originalityReport = report1
        	report1.attachment = attachment

        	session.save(report1)
        	
        	session.flush
        	
        	val retrievedSubmission = session.get(classOf[Submission], submission.id).asInstanceOf[Submission]
        	val report2 = retrievedSubmission.allAttachments.find(_.originalityReport != null).get.originalityReport
        	report2.overlap should be (Some(1))
        	report2.webOverlap should be (None)
        	
        	// check that previous reports are removed, otherwise the @OneToOne will explode
        	attachment.originalityReport = null
        	session.delete(report2)
        	session.flush // hmm, need to flush to delete.
        	
        	val report3 = newReport
        	attachment.originalityReport = report3
        	report3.attachment = attachment
        	session.update(attachment)
        	session.save(report3)
            
            session.flush
            session.clear
            
            // expecting: no exception from having >1 matching OriginalityReport
//            val session2 = session.get(classOf[Submission], submission.id).asInstanceOf[Submission]
//        	session2.originalityReport should be ('completed)
    	}
    }
    
    def newReport = {
    	val r = new OriginalityReport
        r.overlap = Some(1)
        r
    }
	
}