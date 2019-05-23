package uk.ac.warwick.tabula.data

import org.springframework.stereotype.Repository
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.commands.TaskBenchmarking
import uk.ac.warwick.tabula.data.model.mitcircs.MitigatingCircumstancesPanel
import uk.ac.warwick.tabula.services.AutowiringUserLookupComponent

trait MitCircsPanelDaoComponent {
  val mitCircsPanelDao: MitCircsPanelDao
}

trait AutowiringMitCircsPanelDaoComponent extends MitCircsPanelDaoComponent {
  val mitCircsPanelDao: MitCircsPanelDao = Wire[MitCircsPanelDao]
}

trait MitCircsPanelDao {
  def get(id: String): Option[MitigatingCircumstancesPanel]
  def saveOrUpdate(submission: MitigatingCircumstancesPanel): MitigatingCircumstancesPanel
}

@Repository
class MitCircsPanelDaoImpl extends MitCircsPanelDao
  with Daoisms with TaskBenchmarking with AutowiringUserLookupComponent {

  override def get(id: String): Option[MitigatingCircumstancesPanel] = getById[MitigatingCircumstancesPanel](id)

  override def saveOrUpdate(panel: MitigatingCircumstancesPanel): MitigatingCircumstancesPanel = {
    session.saveOrUpdate(panel)
    panel
  }
  
}

