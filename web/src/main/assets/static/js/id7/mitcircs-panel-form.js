/* eslint-env browser */
import $ from 'jquery';

class MitCircsPanelForm {

  constructor(form) {
    this.form = form;
    this.init();
  }

  init() {
    const {form} = this;
    const $form = $(form);

    $form.on('click', '.mitcircs-panel-form__submissions button.remove', (e) => {
      const $target = $(e.target).closest('tr');
      const $table = $target.closest('table');
      $target.remove();

      if ($table.find('tbody tr').length === 0) {
        $table.replaceWith('<div class="form-control-static">No submissions</div>');
      }
    });
  }

}

function init() {
  $('.mitcircs-panel-form').each((i, el) => {
    $(el).data('tabula.mitCircsPanelForm', new MitCircsPanelForm(el));
  });
}

$(() => init());