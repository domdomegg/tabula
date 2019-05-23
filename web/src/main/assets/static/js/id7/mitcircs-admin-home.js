/* eslint-env browser */
import $ from 'jquery';

class MitCircsAdminHome {

  constructor(form) {
    this.form = form;
    this.init();
  }

  init() {
    const {form} = this;
    const $form = $(form);

    $form.on('change', '.check-all', (e) => {
      const $checkAll = $(e.target);
      $form.find('input[name=submissions]').prop('checked', $checkAll.is(':checked')).last().trigger('change');
    });

    $form.on('change', 'input[name=submissions]', () => {
      const $checkboxes = $form.find('input[name=submissions]');
      $form.find('.requires-selected').prop('disabled', !$checkboxes.is(":checked"));
    });
  }

}

function init() {
  $('.mitcircs-submission-actions').each((i, el) => {
    $(el).data('tabula.mitCircsAdminHome', new MitCircsAdminHome(el));
  });
}

$(() => init());