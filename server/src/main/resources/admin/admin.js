// Copyright (c) 2026 Multimedia Entertainment Hub LLC
// SPDX-License-Identifier: LicenseRef-Proprietary

// The dashboard works without this file; it only adds conveniences.
(function () {
  "use strict";

  var root = document.documentElement;

  function isDark() {
    var set = root.getAttribute("data-theme");
    if (set) return set === "dark";
    return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
  }

  // The theme switch: the server reads the cookie, so the next page loads in the same theme.
  document.querySelectorAll("[data-theme-toggle]").forEach(function (button) {
    button.hidden = false;
    button.addEventListener("click", function () {
      var next = isDark() ? "light" : "dark";
      root.setAttribute("data-theme", next);
      document.cookie = "ett_theme=" + next + "; path=/admin; max-age=31536000; SameSite=Lax";
    });
  });

  // A select that applies its form as soon as it changes, such as rows per page.
  document.querySelectorAll("select[data-autosubmit]").forEach(function (select) {
    select.addEventListener("change", function () {
      select.form.submit();
    });
  });
})();
