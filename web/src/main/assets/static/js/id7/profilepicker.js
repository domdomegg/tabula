(function ($) { // I'm wrapping all day

  /**
   * RichResultField is a text field that can be overlaid with a similar-looking
   * box containing arbitrary text. Useful if you want to save a particular value
   * from a picker but display something more friendly from the user.
   *
   * Requires Bootstrap.
   */
  var RichResultField = function (input) {
    var self = this;
    this.$input = $(input);
    this.$uneditable = this.$input.parent().find('.uneditable-input.rich-result-field');
    if (this.$uneditable.length === 0) {
      this.canDelete = !!this.$input.data('can-delete');
      this.html = '<span class=val></span>';
      if (this.canDelete) {
        this.html = this.html + '<a href=# class="clear-field" title="Clear">&times;</a>';
      }
      this.$uneditable = $('<span>' + this.html + '</span>');
      this.$uneditable.attr({
        'class': 'uneditable-input rich-result-field ' + this.$input.attr('class'),
        'disabled': true
      });
      this.$input.after(this.$uneditable);
    }

    this.resetWidth = function () {
      // Attempt to match the original widths; defined width needed for text-overflow to work
      this.$input.css('width', this.$input.css('width'));
      this.$uneditable.css('width', this.$input.css('width'));
    };
    this.resetWidth();
    this.$uneditable.find('a').off('click.tabula.RichResultField').on('click.tabula.RichResultField', function () {
      self.edit();
      return false;
    });
    this.$uneditable.hide();
  };

  /** Clear field, focus for typing */
  RichResultField.prototype.edit = function () {
    this.resetWidth();
    this.$input.val('').show().trigger('change').focus();
    this.$uneditable.hide().find('.val').text('').attr('title', '');
  };

  /** Hide input field and show the rich `text` instead */
  RichResultField.prototype.storeText = function (text) {
    this.resetWidth();
    this.$input.hide();
    this.$uneditable.show().find('.val').text(text).attr('title', text);
  };

  /** Set value of input field, hide it and show the rich `text` instead */
  RichResultField.prototype.store = function (value, text) {
    this.resetWidth();
    this.$input.val(value).trigger('change').hide();
    this.$uneditable.show().find('.val').text(text).attr('title', text);
  };

  var TabulaTypeahead = function (options) {
    // Twitter typeahead doesn't work for Tabula, so the replaced Bootstrap typeahead is aliased as bootstrap3Typeahead
    var mergedOptions = $.extend({}, $.fn.typeahead.defaults, {
      source: options.source,
      item: options.item
    });
    var $typeahead = options.element.typeahead(mergedOptions).data('typeahead');

    // Overridden lookup() method uses this to delay the AJAX call
    $typeahead.delay = 100;

    // Provides delayed AJAX lookup - called by lookup() method.
    $typeahead._delaySource = function (query, source, process) {
      if (this.calledOnce === undefined) {
        this.calledOnce = true;
        var items = source(query, process);
        if (items) {
          this.delay = 0;
          return items;
        }
      } else {
        if (this.delay === 0) {
          return source(query, process);
        } else {
          if (this.timeout) {
            clearTimeout(this.timeout);
          }
          this.timeout = setTimeout(function () {
            return source(query, process);
          }, this.delay);
        }
      }
    };

    // Disable clientside sorting - server will sort where appropriate.
    $typeahead.sorter = function (items) {
      return items;
    };

    // The server will filter - don't do clientside filtering
    $typeahead.matcher = function () {
      return true;
    };

    // Mostly the same as the original but delays the lookup if a delay is set.
    $typeahead.lookup = function () {
      var items;
      this.query = this.$element.val();
      if (!this.query || this.query.length < this.options.minLength) {
        return this.shown ? this.hide() : this
      }
      items = ($.isFunction(this.source)) ? this._delaySource(this.query, this.source, $.proxy(this.process, this)) : this.source;
      return items ? this.process(items) : this
    };

    return $typeahead;

  };

// The jQuery plugin
  $.fn.tabulaTypeahead = function (options) {
    this.each(function () {
      var $this = $(this);
      if ($this.data('tabula-typeahead')) {
        throw new Error("TabulaTypeahead has already been added to this element.");
      }
      var allOptions = {
        element: $this
      };
      $.extend(allOptions, options || {});
      $this.data('tabula-typeahead', new TabulaTypeahead(allOptions));
    });
    return this;
  };

  /**
   * An AJAX autocomplete-style picker that can return a variety of different
   * result types, such as users, webgroups, and typed-in email addresses.
   *
   * The actual searching logic is handled by the corresponding controller -
   * this plugin just passes it option flags to tell it what to search.
   *
   * Requires Bootstrap with the Typeahead plugin.
   */
  var ProfilePicker = function (options) {
    var self = this;
    var $element = $(options.input);

    if (!$element.hasClass('profile-picker')) {
      $element.addClass('profile-picker');
    }

    // Disable browser autocomplete dropdowns, it gets in the way.
    $element.attr('autocomplete', 'off');

    var $spinner = $element.parent().find('.spinner-container');
    if ($spinner.length === 0) {
      $spinner = $('<div />').addClass('spinner-container');
      $element.before($spinner);
    }

    this.richResultField = new RichResultField($element[0]);

    this.iconMappings = {
      user: 'fa fa-user',
      group: 'fa fa-globe',
      email: 'fa fa-envelope'
    };

    var $typeahead = new TabulaTypeahead({
      element: $element,
      source: function (query, process) {
        $spinner.spin('small');
        self.search(query).done(function (results) {
          $spinner.spin(false);
          process(results);
        });
      },
      item: '<li class=profile-picker-result><a href="#"><i></i><span class=title></span><span class=type></span><div class=description></div></a></li>'
    });

    // Renders each result item with icon and description.
    $typeahead.render = function (items) {
      var that = this;
      var withIcons = $(items).filter(function (i, item) {
        return item != undefined && item.type != undefined;
      });
      var useIcons = withIcons.filter(function (i, item) {
        return item.type != withIcons.get(0).type;
      }).length > 0;
      items = $(items).map(function (i, item) {
        if (item != undefined) {
          i = $(that.options.item);
          i.attr('data-value', item.value);
          i.attr('data-type', item.type);
          i.attr('data-fullname', item.name);
          i.find('span.title').html(that.highlighter(item.title));
          i.find('span.type').html(item.type);
          if (useIcons) {
            i.find('i').addClass(self.iconMappings[item.type]);
          }
          var desc = i.find('.description');
          if (desc && desc != '') {
            desc.html(item.description).show();
          } else {
            desc.hide();
          }
          return i[0];
        } else {
          // no idea what's happened here. Return an empty item.
          return $(that.options.item)[0];
        }
      });

      items.first().addClass('active');
      this.$menu.html(items);
      return this;
    };

    // On selecting a value, we can transform it here before it's stored in the field.
    $typeahead.updater = function (value) {
      var type = this.$menu.find('.active').attr('data-type');
      return self.getValue(value, type);
    };

    // Override select item to place both the value in the field
    // and a more userfriendly text in the "rich result".
    var oldSelect = $typeahead.select;
    $typeahead.select = function () {
      var text = this.$menu.find('.active .title').text();
      var desc = this.$menu.find('.active .description').text();
      if (desc) {
        text = text + ' (' + desc + ')';
      }
      self.richResultField.storeText(text);
      this.$element.data('fullname', this.$menu.find('.active').data('fullname'));
      return oldSelect.call($typeahead);
    };

    // On load, look up the existing value and give it human-friendly text if possible
    // NOTE: this relies on the fact that the saved value is itself a valid search term
    // (excluding the prefix on webgroup, which is handled by getQuery() method).
    var currentValue = $element.val();
    if (currentValue && currentValue.trim().length > 0) {
      var searchQuery = this.getQuery(currentValue);
      this.search(searchQuery, {exact: true}).done(function (results) {
        if (results.length > 0) {
          self.richResultField.storeText(results[0].title + ' (' + results[0].description + ')');
        }
      });
    }

    // The Bootstrap Typeahead always appends the drop-down to directly after the input
    // Replace the show method so that the drop-down is added to the body
    $typeahead.show = function () {
      var pos = $.extend({}, this.$element.offset(), {
        height: this.$element[0].offsetHeight
      });

      this.$menu.appendTo($('body')).show().css({
        top: pos.top + pos.height, left: pos.left
      });

      this.shown = true;
      return this;
    };
  };

// Extract the value from a chosen value with type.
  ProfilePicker.prototype.getValue = function (value, type) {
    if (type == 'group') {
      value = this.prefixGroups + value;
    }
    return value;
  };

// Turn value into something we can query on.
  ProfilePicker.prototype.getQuery = function (value) {
    // If we prefixed something onto a group name, remove it first
    if (this.prefixGroups && value.indexOf(this.prefixGroups) == 0) {
      value = value.substring(this.prefixGroups.length);
    }
    return value;
  };

  ProfilePicker.prototype.transformItem = function (item) {
    // if (item.type == 'user') {
    //   item.title = item.name;
    //   item.description = item.value + ', ' + ((item.isStaff === 'true') ? 'Staff' : 'Student') + ', ' + item.department; // usercode, staff/student, department
    // } else if (item.type == 'group') {
    //   item.description = item.title;
    //   item.title = item.value;
    // } else if (item.type == 'email') {
    item.title = item.name + ' (' + item.userId + ')';
    item.value = item.userId;
    item.type = 'user';
    // }
  };

  /** Runs the search */
  ProfilePicker.prototype.search = function (query, options) {
    // We'll return a Deferred result, that will get resolved by the AJAX call
    options = options || {};
    var d = $.Deferred();
    var self = this;
    // Abort any existing search
    if (this.currentSearch) {
      this.currentSearch.abort();
      this.currentSearch = null;
    }
    this.currentSearch = $.ajax({
      url: '/profiles/relationships/agents/search.json',
      dataType: 'json',
      data: {
        query: query,
      },
      success: function (results) {
        // [{"name":"Kai Lan","id":"1574595","userId":"u1574595","description":"Web Developer, IT Services"}]
        if (results) {
          $.each(results, function (i, item) {
            self.transformItem(item);
          });
          // Resolve the deferred result, triggering any handlers
          // that may have been registered against it.
          d.resolve(results);
        }
      }
    });
    // unset the search when it's done
    this.currentSearch.always(function () {
      self.currentSearch = null;
    });
    return d;
  };

// The jQuery plugin
  $.fn.profilePicker = function (options) {
    this.each(function () {
      var $this = $(this);
      if ($this.data('profile-picker')) {
        throw new Error("Picker has already been added to this element.");
      }
      var allOptions = {
        input: this,
        includeGroups: $this.data('include-groups'),
        includeEmail: $this.data('include-email'),
        includeUsers: $this.data('include-users') !== false,
        tabulaMembersOnly: $this.data('members-only'),
        prefixGroups: $this.data('prefix-groups') || '',
        universityId: $this.data('universityid')
      };
      $.extend(allOptions, options || {});
      $this.data('profile-picker', new ProfilePicker(allOptions));
    });
    return this;
  };

  jQuery(function ($) {
    $('.profile-picker').profilePicker({});

    var emptyValue = function () {
      return (this.value || "").trim() == "";
    };

    var $collections = $('.profile-picker-collection');
    $collections.each(function (i, collection) {
      var $collection = $(collection),
        $blankInput = $collection.find('.profile-picker-container').first().clone()
          .find('input').val('').end();
      $blankInput.find('a.btn').remove();

      // check whenever field is changed or focused
      if (!!($collection.data('automatic'))) {
        $collection.on('change focus', 'input', function (ev) {
          // remove empty pickers
          var $inputs = $collection.find('input');
          if ($inputs.length > 1) {
            var toRemove = $inputs.not(':focus').not(':last').filter(emptyValue).closest('.profile-picker-container');
            toRemove.remove();
          }

          // if last picker is nonempty OR focused, append an blank picker.
          var $last = $inputs.last();
          var lastFocused = (ev.type == 'focusin' && ev.target == $last[0]);
          if (lastFocused || $last.val().trim() != '') {
            var input = $blankInput.clone();
            $collection.append(input);
            input.find('input').first().profilePicker({});
          }
        });
      } else {
        $collection.append(
          $('<button />')
            .attr({'type': 'button'})
            .addClass('btn btn-xs btn-default')
            .html('Add another')
            .on('click', function () {
              var input = $blankInput.clone();
              $(this).before(input);
              input.find('input').first().profilePicker({});
            })
        );
      }
    });

  });

// End of wrapping
})(jQuery);

