(function ($) { "use strict";

/*
    Provides functionality to lists/tables that have checkboxes,
    so that you can select some/all rows and do stuff with them.

    Options:

        onSomeChecked($container): callback when at least 1 is checked.
        onNoneChecked($container): callback when no rows are checked.
        onAllChecked($container): callback when all rows selected. defaults to behaviour of onSomeChecked.
        onChange($checkbox): callback when a row is selected/unselected.
*/
jQuery.fn.bigList = function(options) {
    var $ = jQuery;
    this.each(function(){
        var $this = $(this);

        if (options == 'changed') {
            this.checkboxChangedFunction();
            return;
        }

        var checkboxClass = options.checkboxClass || 'collection-checkbox';
        var checkboxAllClass = options.checkboxClass || 'collection-check-all';

        var $checkboxes = $this.find('input.'+checkboxClass);
        var $selectAll = $this.find('input.'+checkboxAllClass);

        var doNothing = function(){};

        var onChange = options.onChange || doNothing;
        var onSomeChecked = options.onSomeChecked || doNothing;
        var onNoneChecked = options.onNoneChecked || doNothing;
        var onAllChecked = options.onAllChecked || onSomeChecked;

        this.checkboxChangedFunction = function(){
            onChange.call(jQuery(this)); // pass the checkbox as the context
            var allChecked = $checkboxes.not(":checked").length == 0;
            $selectAll.attr("checked", allChecked);
            if (allChecked) {
                $this.data('checked','all');
                onAllChecked.call($this);
            } else if ($checkboxes.is(":checked")) {
                $this.data('checked','some');
                onSomeChecked.call($this);
            } else {
                $this.data('checked','none');
                onNoneChecked.call($this);
            }
        };
        $checkboxes.change(this.checkboxChangedFunction);

        $selectAll.change(function(){
            $checkboxes.attr("checked", this.checked);
            $checkboxes.each(function(){
                onChange.call(jQuery(this));
            });
            if (this.checked) {
                $this.data('checked','all');
                onAllChecked.call($this);
            } else {
                $this.data('checked','none');
                onNoneChecked.call($this);
            }
        });

        options.setup.call($this);

        $(function(){
            $checkboxes.change();
        });

        // Returns an array of IDs.
        var getCheckedFeedbacks = function() {
            return $checkboxes.filter(":checked").map(function(i,input){ return input.value; });
        };
    });
    return this;
}

})(jQuery);
