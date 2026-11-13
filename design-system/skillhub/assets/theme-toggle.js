/* theme-toggle.js — behaviour for components/theme-toggle.json.
   - toggles `.dark` on <html>
   - mirrors the state into aria-pressed (true = dark) and data-theme on <html>
   - restores the last choice from localStorage; never throws on file://
   Wire any element carrying [data-theme-toggle]; icons live in assets/icons.css. */
(function () {
  var root = document.documentElement;
  var KEY = 'sh-theme';

  try {
    var saved = localStorage.getItem(KEY);
    if (saved === 'dark') root.classList.add('dark');
    if (saved === 'light') root.classList.remove('dark');
  } catch (e) {}

  function sync() {
    var dark = root.classList.contains('dark');
    root.setAttribute('data-theme', dark ? 'dark' : 'light');
    var buttons = document.querySelectorAll('[data-theme-toggle]');
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].setAttribute('aria-pressed', dark ? 'true' : 'false');
    }
  }

  function init() {
    var buttons = document.querySelectorAll('[data-theme-toggle]');
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].addEventListener('click', function () {
        var dark = !root.classList.contains('dark');
        root.classList.toggle('dark', dark);
        try { localStorage.setItem(KEY, dark ? 'dark' : 'light'); } catch (e) {}
        sync();
      });
    }
    sync();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
