/**
 * Scripts used only by the small group teaching admin section. 
 */
(function ($) { "use strict";

var exports = {};

exports.zebraStripeGroups = function($module) {
	$module.find('.group-info').filter(':visible:even').addClass('alt-row');
};

exports.wireModalButtons = function($container) {
	$container.find('.btn-group').on('click', 'a[data-toggle=modal]', function(e){
      e.preventDefault();
      var $this = $(this);
      var target = $this.attr('data-target');
      var url = $this.attr('href');
      $(target).load(url);
  });
};

// take anything we've attached to "exports" and add it to the global "Groups"
// we use extend() to add to any existing variable rather than clobber it
window.Groups = jQuery.extend(window.Groups, exports);

$(function(){
    
    // Zebra striping on lists of modules/groups
    $('.module-info').each(function(i, module) { 
        exports.zebraStripeGroups($(module));
    });
    
    $('.module-info.empty').css('opacity',0.66)
        .find('.module-info-contents').hide().end()
        .click(function(){
            $(this).css('opacity',1)
                .find('.module-info-contents').show().end();
        })
        .hide();
        
    $('.dept-show').click(function(event){
    	event.preventDefault();   	
    	var hideButton = $(this).find("a");
    	
        $('.striped-section.empty').toggle('fast', function() {
        	if($('.module-info.empty').is(":visible")) {
        		hideButton.html('<i class="icon-eye-close"></i> Hide');
        		hideButton.attr("data-original-title", hideButton.attr("data-title-hide"));
        		
        	} else { 
        		hideButton.html('<i class="icon-eye-open"></i> Show');
        		hideButton.attr("data-original-title", hideButton.attr("data-title-show"));
        	}
        });

    });
    
    $('.show-archived-small-groups').click(function(e){
        e.preventDefault();
        $(e.target).hide().closest('.striped-section').find('.item-info.archived').show();
    });

    // enable/disable the "sign up" buttons on the student groups homepage
    $('#student-groups-view .sign-up-button').addClass('disabled use-tooltip').prop('disabled',true).prop('title','Please select a group');
    $('#student-groups-view input.group-selection-radio').change(function(){
			$(this).closest('.item-info').find('.sign-up-button').removeClass('disabled use-tooltip').prop('disabled',false).prop('title','');
		});
});


// modals use ajax to retrieve their contents
$(function() {
	exports.wireModalButtons($('#container'));

    $("#modal-container").on("click","input[type='submit']", function(e){
        e.preventDefault();
        var $this = $(this);
        var $form = $this.closest("form");
        var updateTargetId = $this.data("update-target");

        var randomNumber = Math.floor(Math.random() * 10000000);

        jQuery.post($form.attr('action') + "?rand=" + randomNumber, $form.serialize(), function(data){
            $("#modal-container ").modal('hide');
            if (updateTargetId){
               $(updateTargetId).html(data);
            }else{
	            window.location.reload();
	        }
        });
    });
});

// Week selector and location picker
$(function() {
	$('table.week-selector').each(function() {
		var $table = $(this);

		var updateCell = function($cell, value) {
			var $icon = $cell.find('i');
			if (value) {
				$icon.addClass('icon-ok');
				$cell.addClass('checked');
			} else {
				$icon.removeClass('icon-ok');
				$cell.removeClass('checked');
			}
		};

		$table.find('input[type="checkbox"]').each(function() {
			var $checkbox = $(this);
			var $cell = $checkbox.closest('td');

			$checkbox.hide();

			var $icon = $('<i />').addClass('icon-fixed-width');
			$checkbox.after($icon);

			updateCell($cell, $checkbox.is(':checked'));

			$cell.on('click', function() {
				$checkbox.prop('checked', !$checkbox.prop('checked'));
				updateCell($cell, $checkbox.is(':checked'));
			});
		});
		$table.find('tbody tr th').each(function() {
			var $header = $(this);
			var $cells = $header.closest('tr').find('td');
			$header.on('click', function() {
				var allChecked = $cells.find('input[type="checkbox"]:not(:checked)').length == 0;
				if (allChecked) {
					$cells.each(function() {
						var $cell = $(this);
						$cell.find('input[type="checkbox"]').prop('checked', false);
						updateCell($cell, false);
					});
				} else {
					$cells.each(function() {
						var $cell = $(this);
						$cell.find('input[type="checkbox"]').prop('checked', true);
						updateCell($cell, true);
					});
				}
			});
		});

		$table.closest('.control-group').find('.show-vacations').each(function() {
			var $checkbox = $(this);

			if ($table.find('tr.vacation td.checked').length) {
				$checkbox.prop('checked', true);
			}

			var updateDisplay = function() {
				if ($checkbox.is(':checked')) {
					$table.find('tr.vacation').show();
				} else {
					$table.find('tr.vacation').hide();
				}
			};
			updateDisplay();

			$checkbox.on('change', updateDisplay);
		});
	});

	$('input#location, input#defaultLocation')
		.on('change', function() {
			var $this = $(this);
			if ($this.data('lid') === undefined || $this.data('lid').length === 0)
				return;

			$this.closest('.controls').find('input[type="hidden"]').val($this.data('lid'));
			$this.data('lid','');
		})
		.locationPicker();
});

// Re-usable small groups
$(function() {
	if ($('.add-student-to-set').length > 0) {
		$('.fix-area').fixHeaderFooter();
		$('details').on('open.details close.details', function() {
			setTimeout(function() {
				$(window).trigger('resize');
			}, 10);
		});

		$('.tablesorter').find('th.sortable').addClass('header').on('click', function() {
			var $th = $(this)
				, sortDescending = function() {
					$('#sortOrder').val('desc(' + $th.data('field') + ')');
					$th.closest('thead').find('th').removeClass('headerSortUp').removeClass('headerSortDown');
					$th.addClass('headerSortUp');
				}, sortAscending = function() {
					$('#sortOrder').val('asc(' + $th.data('field') + ')');
					$th.closest('thead').find('th').removeClass('headerSortUp').removeClass('headerSortDown');
					$th.addClass('headerSortDown');
				}, $form = $th.closest('form')
				, $details = $th.closest('details');

			if ($th.hasClass('headerSortUp')) {
				sortAscending();
			} else if ($th.hasClass('headerSortDown')) {
				sortDescending();
			} else {
				// not currently sorted on this column, default sort depends on column
				if ($th.hasClass('unrecorded-col') || $th.hasClass('missed-col')) {
					sortDescending();
				} else {
					sortAscending();
				}
			}

			if ($details.data('submitparam').length > 0) {
				$form.append($('<input/>').attr({
					'type': 'hidden',
					'name': $details.data('submitparam'),
					'value': true
				}));
			}
			$form.submit();
		});

		$('.pagination').on('click', 'a', function() {
			var $this = $(this), $form = $this.closest('form'), $details = $this.closest('details');
			if ($this.data('page').toString.length > 0) {
				$form.find('input[name="page"]').remove().end()
					.append($('<input/>').attr({
						'type': 'hidden',
						'name': 'page',
						'value': $this.data('page')
					})
				);
			}
			if ($details.data('submitparam').length > 0) {
				$form.find('input[name="' + $details.data('submitparam') + '"]').remove().end()
					.append($('<input/>').attr({
						'type': 'hidden',
						'name': $details.data('submitparam'),
						'value': true
					})
				);
			}
			$form.submit();
		});

		var prependClearLink = function($list) {
			if (!$list.find('input:checked').length) {
				$list.find('.clear-this-filter').remove();
			} else {
				if (!$list.find('.clear-this-filter').length) {
					$list.find('> ul').prepend(
						$('<li />').addClass('clear-this-filter')
							.append(
							$('<button />').attr('type', 'button')
								.addClass('btn btn-link')
								.html('<i class="icon-ban-circle"></i> Clear selected items')
								.on('click', function(e) {
									$list.find('input:checked').each(function() {
										var $checkbox = $(this);
										$checkbox.prop('checked', false);
										updateFilter($checkbox);
									});

									doRequest($list.closest('form'));
								})
						)
							.append($('<hr />'))
					);
				}
			}
		};

		var updateFilter = function($el) {
			// Add in route search
			// Update the filter content
			var $list = $el.closest('ul');
			var shortValues = $list.find(':checked').map(function() {
				return $(this).data('short-value');
			}).get();
			var $fsv = $el.closest('.btn-group').find('.filter-short-values');
			if (shortValues.length) {
				$el.closest('.btn-group').removeClass('empty-filter');
				$fsv.html($fsv.data("prefix") + shortValues.join(', '));
			} else {
				$el.closest('.btn-group').addClass('empty-filter');
				$fsv.html($fsv.data('placeholder'));
			}

			updateSearchButton($el);
			updateClearAllButton($el);
		};

		var updateClearAllButton = function($el) {
			var $filterList = $el.closest(".student-filter");

			if ($filterList.find(".empty-filter").length == $filterList.find(".btn-group").length) {
				$('.clear-all-filters').attr("disabled", "disabled");
			} else {
				$('.clear-all-filters').removeAttr("disabled");
			}
		};

		var updateSearchButton = function($el) {
			var $filter = $el.closest('.student-filter');
			if ($filter.find('input:checked').length > 0) {
				$filter.find('button.search').attr('disabled', false);
			} else {
				$filter.find('button.search').attr('disabled', true);
			}
		};

		$('.student-filter input').on('change', function() {
			// Load the new results
			var $checkbox = $(this);
			updateFilter($checkbox);
		});

		// Re-order elements inside the dropdown when opened
		$('.filter-list').closest('.btn-group').find('.dropdown-toggle').on('click.dropdown.data-api', function(e) {
			var $this = $(this);
			if (!$this.closest('.btn-group').hasClass('open')) {
				// Re-order before it's opened!
				var $list = $this.closest('.btn-group').find('.filter-list');
				var items = $list.find('li.check-list-item').get();

				items.sort(function(a, b) {
					var aChecked = $(a).find('input').is(':checked');
					var bChecked = $(b).find('input').is(':checked');

					if (aChecked && !bChecked) return -1;
					else if (!aChecked && bChecked) return 1;
					else return $(a).data('natural-sort') - $(b).data('natural-sort');
				});

				$.each(items, function(item, el) {
					$list.find('> ul').append(el);
				});

				prependClearLink($list);
			}
		});

		$('.clear-all-filters').on('click', function() {
			$('.filter-list').each(function() {
				var $list = $(this);

				$list.find('input:checked').each(function() {
					var $checkbox = $(this);
					$checkbox.prop('checked', false);
					updateFilter($checkbox);
				});

				prependClearLink($list);
			});
		});
	}
});

}(jQuery));