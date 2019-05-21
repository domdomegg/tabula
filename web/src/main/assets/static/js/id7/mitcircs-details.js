/* eslint-env browser */
/* eslint-disable no-use-before-define */
import $ from 'jquery';
import _ from 'lodash-es';

class MitCircsDetails {
  constructor(details) {
    this.details = details;
    this.init();
  }

  init() {
    const { details } = this;
    const $details = $(details);

    // scroll to the end of the message container
    function messageScroll() {
      const $body = $details.find('.message-thread__body');
      $body.scrollTop($body.prop('scrollHeight'));
    }

    // Disable send button when the textarea is empty
    function checkAndUpdateSendButton(e) {
      const $thread = (e !== undefined) ? $(e.target).closest('.message-thread') : $details;
      const $textarea = $thread.find('.message-thread__footer__fields textarea');
      const $button = $thread.find('.message-thread__footer__fields button[type=submit]');
      if (_.trim($textarea.val()).length === 0) {
        $button.prop('disabled', true);
      } else {
        $button.prop('disabled', false);
      }
    }
    $details.on('keyup', '#messages', checkAndUpdateSendButton);

    function showErrorsInModals(e) {
      const $thread = (e !== undefined) ? $(e.target).closest('.message-thread') : $details;
      const $modalsWithErrors = $thread.find('.form-group.has-error').closest('.modal');
      $modalsWithErrors.modal().modal('show');
    }

    function bind() {
      checkAndUpdateSendButton();
      showErrorsInModals();
      messageScroll();
      $('.use-tooltip').tooltip();
    }

    $details
      .find('.mitcircs-details__section.async')
      .each((i, el) => {
        const $asyncSection = $(el);
        const $content = $asyncSection.find('.content');
        $content.load($asyncSection.data('href'), (text, status, xhr) => {
          if (status === 'error') {
            $content.text(`Unable to load content: ${xhr.statusText || xhr.status || 'error'}`);
          } else {
            bind();
          }
        });
      });

    // Grow textarea when typing
    $details.on('keydown', '#messages textarea', (e) => {
      const $target = $(e.target);
      // For some reason carriage returns don't update the height until the methods returns,
      // so just wait a tick before checking the height
      setTimeout(() => {
        if ($target.get(0).scrollHeight > $target.get(0).offsetHeight && $target.prop('rows') < 10) {
          $target.prop('rows', $target.prop('rows') + 1);
        }
      }, 1);
    });

    // Add file number badge and update tooltip when files are selected
    $details.on('change', '#messages input[type=file]', (e) => {
      const $input = $(e.target);
      const $label = $input.closest('label');
      const $tooltip = $label.find('.use-tooltip');
      const originalText = $tooltip.attr('title') || $tooltip.data('original-title');
      const fileCount = ($input.prop('files') && $input.prop('files').length > 1) ? $input.prop('files').length : 1;
      const newText = (fileCount > 1) ? `${$input.prop('files').length} files selected` : $input.val().split('\\').pop();
      if (newText) {
        $tooltip.attr('data-original-title', newText);
        $label.append($('<span/>').addClass('badge').text(fileCount));
      } else {
        $tooltip.attr('data-original-title', originalText);
        $label.find('.badge').remove();
      }
    });

    $details.on('click', '#messages .message-thread__footer__message-templates a', (e) => {
      const $thread = (e !== undefined) ? $(e.target).closest('.message-thread') : $details;
      const $textarea = $thread.find('.message-thread__footer__fields textarea');
      const $modal = $('.message-thread__footer__message-templates');
      const $templateName = $(e.target).closest('dt');
      const templateText = $templateName.next('dd').text();
      $textarea.val(templateText);
      $modal.modal('toggle');
      $textarea.height($textarea.get(0).scrollHeight);
      checkAndUpdateSendButton();
    });

    // Add badge and update tooltip when respond by date
    $details.on('change', '#replyByDateModal :input', (e) => {
      const $input = $(e.target);
      const $label = $input.closest('.modal').prev('label');
      const $tooltip = $label.find('.use-tooltip');
      const originalText = $tooltip.attr('title') || $tooltip.data('original-title');
      const date = $input.val();
      const newText = (date) ? `Respond by ${date}` : $input.val().split('\\').pop();
      if (newText) {
        $tooltip.attr('data-original-title', newText);
        $label.append($('<span/>').addClass('badge').html('<i class="fal fa-check"></i>'));
      } else {
        $tooltip.attr('data-original-title', originalText);
        $label.find('.badge').remove();
      }
    });

    $details.on('submit', '#messages', (e) => {
      e.preventDefault();
      const $form = $(e.target);
      const $thread = $form.closest('.message-thread');
      const $content = $thread.closest('.mitcircs-details__section .content');
      $form.find(':input').prop('readonly', true);
      $form.find('button').prop('disabled', true);

      const formData = new FormData(e.target);
      $.ajax({
        url: $form.attr('action'),
        type: 'POST',
        data: formData,
        success: (data) => {
          $content.html(data);
          bind();
        },
        cache: false,
        contentType: false,
        processData: false,
      });
    });

    // Open attachments in a modal if they are serve-inline
    const injectModal = () => {
      // Inject a modal into the body that will hold the content
      const $modalTitle = $('<h4 />').addClass('modal-title').attr('id', 'mitcircs-details-attachment-modal-label');
      const $modalBody = $('<div />').addClass('modal-body');
      const $newTabLink = $('<a />').addClass('btn btn-default').attr('target', '_blank').text('Open in new tab');

      const $modal = $('<div />').addClass('modal fade').attr({
        id: 'mitcircs-details-attachment-modal',
        tabindex: -1,
        role: 'dialog',
        'aria-labelledby': 'mitcircs-details-attachment-modal-label',
      }).append(
        $('<div />').addClass('modal-dialog modal-lg').attr('role', 'document')
          .append(
            $('<div />').addClass('modal-content')
              .append(
                $('<div />').addClass('modal-header')
                  .append(
                    $('<button />').addClass('close').attr({
                      type: 'button',
                      'data-dismiss': 'modal',
                      'aria-label': 'Close',
                    }).html('<span aria-hidden="true">&times;</span>'),
                    $modalTitle,
                  ),
                $modalBody,
                $('<div />').addClass('modal-footer')
                  .append(
                    $newTabLink,
                    $('<button />').addClass('btn btn-default').attr({
                      type: 'button',
                      'data-dismiss': 'modal',
                    }).text('Close'),
                  ),
              ),
          ),
      );

      $modal.appendTo($('body'));
      this.$attachmentsModal = $modal;
      this.$attachmentsModalTitle = $modalTitle;
      this.$attachmentsModalBody = $modalBody;
      this.$attachmentsModalNewTabLink = $newTabLink;
      return $modal;
    };

    $details.on('click', '.attachment a[data-inline="true"]', (e) => {
      e.stopPropagation();
      e.preventDefault();

      const $modal = this.$attachmentsModal || injectModal();
      const $modalTitle = this.$attachmentsModalTitle;
      const $modalBody = this.$attachmentsModalBody;
      const $newTabLink = this.$attachmentsModalNewTabLink;

      const $a = $(e.target);

      $modalTitle.text($a.text());
      $modalBody.empty().append(
        $('<iframe />').attr({
          src: $a.attr('href'),
          scrolling: 'auto',
          frameborder: 0,
          allowtransparency: true,
          seamless: 'seamless',
        }),
      );
      $newTabLink.attr('href', $a.attr('href'));

      $modal.modal().modal('show');
    });

    $details.on('submit', '#readyModal', (e) => {
      e.stopPropagation();
      e.preventDefault();
      const $form = $(e.target);
      const formData = new FormData(e.target);
      const $container = $form.closest('.modal-content');
      $.ajax({
        url: $form.attr('action'),
        type: 'POST',
        data: formData,
        success: (data) => {
          if (data.success) {
            document.location.reload();
          } else {
            $container.html(data);
          }
        },
        cache: false,
        contentType: false,
        processData: false,
      });
    });
  }
}

function init() {
  $('.mitcircs-details').each((i, el) => {
    $(el).data('tabula.mitCircsAsyncSection', new MitCircsDetails(el));
  });
}

$(() => init());
