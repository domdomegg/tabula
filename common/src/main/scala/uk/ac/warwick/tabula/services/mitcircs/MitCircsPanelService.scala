package uk.ac.warwick.tabula.services.mitcircs

import org.springframework.stereotype.Service
import uk.ac.warwick.spring.Wire
import uk.ac.warwick.tabula.data.Transactions._
import uk.ac.warwick.tabula.data.model.mitcircs.MitigatingCircumstancesPanel
import uk.ac.warwick.tabula.data.{AutowiringMitCircsPanelDaoComponent, MitCircsPanelDaoComponent}
import uk.ac.warwick.tabula.services.UserGroupMembershipHelper

trait MitCircsPanelService {
  def get(id: String): Option[MitigatingCircumstancesPanel]
  def saveOrUpdate(panel: MitigatingCircumstancesPanel): MitigatingCircumstancesPanel
}

abstract class AbstractMitCircsPanelService extends MitCircsPanelService {
  self: MitCircsPanelDaoComponent =>

  override def get(id: String): Option[MitigatingCircumstancesPanel] = transactional(readOnly = true) {
    mitCircsPanelDao.get(id)
  }

  override def saveOrUpdate(panel: MitigatingCircumstancesPanel): MitigatingCircumstancesPanel = transactional() {
    mitCircsPanelDao.saveOrUpdate(panel)
  }

}

@Service("mitCircsPanelService")
class AutowiredMitCircsPanelService
  extends AbstractMitCircsPanelService
    with AutowiringMitCircsPanelDaoComponent

trait MitCircsPanelServiceComponent {
  def mitCircsPanelService: MitCircsPanelService
}

trait AutowiringMitCircsPanelServiceComponent extends MitCircsPanelServiceComponent {
  var mitCircsPanelService: MitCircsPanelService = Wire[MitCircsPanelService]
}
