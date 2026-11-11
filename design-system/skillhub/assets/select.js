/* select.js — reference behaviour for components/select.json.

   Library provides structure, CSS and this optional script. Host still owns data
   (static markup or async options), labels and any business formatting.

   Markup contract:
   <div class="select [select--block]" data-select [data-multiple] [data-placeholder="…"]>
     <button class="select-trigger" type="button" role="combobox" aria-haspopup="listbox"
             aria-expanded="false" aria-label="…">
       <span class="select-value">…</span>
       <span class="select-icon sh-i i16-chevron-down" aria-hidden="true"></span>
     </button>
     <div class="select-content" role="listbox" hidden>
       <div class="select-item" role="option" data-value="…" aria-selected="false">
         <span>…</span><span class="check sh-i i16-check"></span>
       </div>
     </div>
   </div>

   Multi mode uses `.select-item--multi` + `.select-check` instead of `.check`.
   Emits a bubbling `select:change` CustomEvent on `.select` with
   detail = { value, values }. No third-party dependency. */
(function () {
  var SEQ = 0;
  var TYPEAHEAD_MS = 500;

  function toArray(list) {
    return Array.prototype.slice.call(list);
  }

  function optionText(item) {
    var text = item.querySelector('.select-text') ||
      item.querySelector('span:not(.check):not(.select-check)');
    return (text ? text.textContent : item.textContent).replace(/\s+/g, ' ').trim();
  }

  function init(root) {
    if (root.dataset.selectBound === '1') return;
    root.dataset.selectBound = '1';

    var multiple = root.hasAttribute('data-multiple');
    var trigger = root.querySelector('.select-trigger');
    var content = root.querySelector('.select-content');
    if (!trigger || !content) return;

    var valueEl = trigger.querySelector('.select-value');
    var placeholder = root.getAttribute('data-placeholder') ||
      (valueEl ? valueEl.textContent.trim() : '');

    var listId = content.id || (content.id = 'sh-select-' + (++SEQ));
    trigger.setAttribute('role', 'combobox');
    trigger.setAttribute('aria-haspopup', 'listbox');
    trigger.setAttribute('aria-expanded', 'false');
    trigger.setAttribute('aria-controls', listId);
    content.setAttribute('role', 'listbox');
    if (multiple) content.setAttribute('aria-multiselectable', 'true');

    var active = null;
    var open = false;
    var typeBuffer = '';
    var typeTimer = 0;

    function options() {
      return toArray(content.querySelectorAll('.select-item[role="option"]'));
    }

    function enabledOptions() {
      return options().filter(function (item) {
        return item.getAttribute('aria-disabled') !== 'true';
      });
    }

    function selectedOptions() {
      return options().filter(function (item) {
        return item.getAttribute('aria-selected') === 'true';
      });
    }

    function optionId(item) {
      if (!item.id) item.id = listId + '-opt-' + options().indexOf(item);
      return item.id;
    }

    function setActive(item) {
      if (active) active.classList.remove('is-active');
      active = item || null;
      if (active) {
        active.classList.add('is-active');
        trigger.setAttribute('aria-activedescendant', optionId(active));
        if (active.scrollIntoView) active.scrollIntoView({ block: 'nearest' });
      } else {
        trigger.removeAttribute('aria-activedescendant');
      }
    }

    function move(direction) {
      var items = enabledOptions();
      if (!items.length) {
        setActive(null);
        return;
      }
      var index = active ? items.indexOf(active) : (direction > 0 ? -1 : 0);
      var next = direction > 0 ?
        Math.min(items.length - 1, index + 1) :
        Math.max(0, index - 1);
      setActive(items[next]);
    }

    function reflect() {
      var selected = selectedOptions();
      if (valueEl) {
        if (selected.length) {
          valueEl.textContent = multiple ?
            selected.map(optionText).join('、') :
            optionText(selected[0]);
          trigger.removeAttribute('data-placeholder');
        } else {
          valueEl.textContent = placeholder;
          if (placeholder) trigger.setAttribute('data-placeholder', '');
        }
      }
      if (multiple) {
        content.setAttribute('aria-multiselectable', 'true');
      }
    }

    function emit() {
      var values = selectedOptions().map(function (item) {
        return item.dataset.value !== undefined ? item.dataset.value : optionText(item);
      });
      try {
        root.dispatchEvent(new CustomEvent('select:change', {
          bubbles: true,
          detail: { value: values[0] || null, values: values }
        }));
      } catch (e) {
        /* CustomEvent is unavailable on some very old engines; state is still applied. */
      }
    }

    function choose(item) {
      if (!item || item.getAttribute('aria-disabled') === 'true') return;
      if (multiple) {
        var next = item.getAttribute('aria-selected') !== 'true';
        item.setAttribute('aria-selected', next ? 'true' : 'false');
        var box = item.querySelector('.select-check');
        if (box) {
          box.classList.toggle('sh-i', next);
          box.classList.toggle('i16-check', next);
        }
        reflect();
        emit();
      } else {
        options().forEach(function (option) {
          option.setAttribute('aria-selected', option === item ? 'true' : 'false');
          var mark = option.querySelector('.check');
          if (mark) mark.classList.add('sh-i', 'i16-check');
        });
        reflect();
        emit();
        close(true);
      }
    }

    /* Nearest scroll / clip ancestor intersected with the viewport.  A dialog
       with `overflow: auto` clips absolutely positioned descendants, so the
       viewport alone is not enough. */
    function clipRect(element) {
      var node = element.parentElement;
      while (node && node !== document.body && node !== document.documentElement) {
        var style = window.getComputedStyle(node);
        if (style.overflowX !== 'visible' || style.overflowY !== 'visible') {
          var rect = node.getBoundingClientRect();
          var top = Math.max(0, rect.top);
          var left = Math.max(0, rect.left);
          var right = Math.min(window.innerWidth, rect.right);
          var bottom = Math.min(window.innerHeight, rect.bottom);
          return {
            top: top,
            left: left,
            right: right,
            bottom: bottom,
            width: Math.max(0, right - left),
            height: Math.max(0, bottom - top)
          };
        }
        node = node.parentElement;
      }
      return {
        top: 0,
        left: 0,
        right: window.innerWidth,
        bottom: window.innerHeight,
        width: window.innerWidth,
        height: window.innerHeight
      };
    }

    function place() {
      if (!open) return;
      root.classList.remove('is-up', 'is-end');
      content.style.maxHeight = '';
      content.style.maxWidth = '';

      var triggerRect = trigger.getBoundingClientRect();
      content.style.setProperty('--sh-select-min-width', triggerRect.width + 'px');

      var clip = clipRect(trigger);
      var maxWidth = Math.min(360, window.innerWidth - 16);
      maxWidth = Math.min(
        maxWidth,
        Math.max(triggerRect.width, clip.width - 16)
      );
      content.style.maxWidth = maxWidth + 'px';

      /* Natural height after width clamping; max-height is cleared above. */
      var needed = content.scrollHeight + 2;
      var below = clip.bottom - triggerRect.bottom - 4;
      var above = triggerRect.top - clip.top - 4;

      /* Prefer the side with more room when the preferred side is short. */
      var side = 'down';
      if (below < needed && above > below) side = 'up';
      var available = Math.max(0, side === 'up' ? above : below);
      var cap = Math.min(320, Math.round(window.innerHeight * 0.6), available);
      content.style.maxHeight = cap + 'px';
      if (side === 'up') root.classList.add('is-up');

      var panel = content.getBoundingClientRect();
      var rightLimit = Math.min(clip.right, window.innerWidth) - 8;
      if (triggerRect.left + panel.width > rightLimit) {
        root.classList.add('is-end');
      }
    }

    function onDocumentPointer(event) {
      if (!root.contains(event.target)) close(false);
    }

    function onFocusOut(event) {
      if (event.relatedTarget && root.contains(event.relatedTarget)) return;
      close(false);
    }

    function openPanel(direction) {
      if (open) return;
      open = true;
      content.hidden = false;
      trigger.setAttribute('aria-expanded', 'true');
      place();
      window.requestAnimationFrame(place);

      var selected = selectedOptions();
      if (selected.length) {
        setActive(selected[0]);
      } else {
        var items = enabledOptions();
        if (items.length) setActive(direction < 0 ? items[items.length - 1] : items[0]);
      }

      document.addEventListener('pointerdown', onDocumentPointer, true);
      window.addEventListener('resize', place);
      window.addEventListener('scroll', place, true);
      root.addEventListener('focusout', onFocusOut);
    }

    function close(returnFocus) {
      if (!open) return;
      open = false;
      content.hidden = true;
      trigger.setAttribute('aria-expanded', 'false');
      setActive(null);
      root.classList.remove('is-up', 'is-end');
      content.style.maxHeight = '';
      content.style.maxWidth = '';
      document.removeEventListener('pointerdown', onDocumentPointer, true);
      window.removeEventListener('resize', place);
      window.removeEventListener('scroll', place, true);
      root.removeEventListener('focusout', onFocusOut);
      if (returnFocus) trigger.focus();
    }

    function typeahead(key) {
      typeBuffer += key.toLowerCase();
      window.clearTimeout(typeTimer);
      typeTimer = window.setTimeout(function () { typeBuffer = ''; }, TYPEAHEAD_MS);

      var items = enabledOptions();
      if (!items.length) return;
      var start = active ? items.indexOf(active) + 1 : 0;
      for (var step = 0; step < items.length; step++) {
        var item = items[(start + step) % items.length];
        var raw = optionText(item).toLowerCase();
        /* `@global` should be reachable by typing `g`, not only `@`. */
        var norm = raw.replace(/^[^a-z0-9\u4e00-\u9fa5]+/, '');
        if (raw.indexOf(typeBuffer) === 0 || norm.indexOf(typeBuffer) === 0) {
          setActive(item);
          return;
        }
      }
    }

    trigger.addEventListener('click', function () {
      if (open) close(true);
      else openPanel(1);
    });

    trigger.addEventListener('keydown', function (event) {
      var key = event.key;
      if (key === 'ArrowDown') {
        event.preventDefault();
        if (!open) openPanel(1);
        else move(1);
      } else if (key === 'ArrowUp') {
        event.preventDefault();
        if (!open) openPanel(-1);
        else move(-1);
      } else if (key === 'Home' || key === 'End') {
        if (!open) return;
        event.preventDefault();
        var items = enabledOptions();
        if (items.length) setActive(key === 'Home' ? items[0] : items[items.length - 1]);
      } else if (key === 'Enter') {
        if (open && active) {
          event.preventDefault();
          choose(active);
        } else if (!open) {
          event.preventDefault();
          openPanel(1);
        }
      } else if (key === ' ') {
        event.preventDefault();
        if (open && active) choose(active);
        else openPanel(1);
      } else if (key === 'Escape') {
        if (open) {
          event.preventDefault();
          close(true);
        }
      } else if (key === 'Tab') {
        close(false);
      } else if (key.length === 1 && !event.ctrlKey && !event.metaKey && !event.altKey) {
        if (!open) openPanel(1);
        typeahead(key);
      }
    });

    content.addEventListener('mousedown', function (event) {
      /* Keep focus on the trigger so the listbox click does not blur-close. */
      event.preventDefault();
    });

    content.addEventListener('click', function (event) {
      var item = event.target.closest ? event.target.closest('.select-item') : null;
      if (!item || !content.contains(item)) return;
      setActive(item);
      choose(item);
    });

    options().forEach(function (option) {
      option.setAttribute('role', 'option');
      option.setAttribute('tabindex', '-1');
      optionId(option);
    });

    reflect();
  }

  function boot() {
    toArray(document.querySelectorAll('[data-select]')).forEach(init);
  }

  window.SkillHubSelect = Object.freeze({ init: init, initAll: boot });

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
