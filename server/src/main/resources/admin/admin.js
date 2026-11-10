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

  // "Select all on this page", and how many rows are ticked.
  document.querySelectorAll("input[data-select-all]").forEach(function (all) {
    var boxes = Array.prototype.slice.call(
      document.querySelectorAll('input[type=checkbox][name="' + all.getAttribute("data-select-all") + '"]')
    );
    var counts = document.querySelectorAll("[data-selected-count]");
    function update() {
      var ticked = boxes.filter(function (box) { return box.checked; }).length;
      all.checked = ticked > 0 && ticked === boxes.length;
      all.indeterminate = ticked > 0 && ticked < boxes.length;
      counts.forEach(function (count) {
        count.textContent = ticked === 0 ? "" : ticked === 1 ? "1 selectată" : ticked + " selectate";
      });
    }
    all.hidden = false;
    all.addEventListener("change", function () {
      boxes.forEach(function (box) { box.checked = all.checked; });
      update();
    });
    boxes.forEach(function (box) { box.addEventListener("change", update); });
    update();
  });
  // The compose pages: the phone beside the form follows what is typed. Without this file the
  // "Previzualizează" button draws the same preview on the server.
  var phone = document.querySelector("[data-phone-preview]");
  var compose = document.querySelector("form[data-compose]");
  if (phone && compose) livePreview(compose);

  function livePreview(form) {
    var DAYS = ["Duminică", "Luni", "Marți", "Miercuri", "Joi", "Vineri", "Sâmbătă"];
    var MONTHS = ["ianuarie", "februarie", "martie", "aprilie", "mai", "iunie", "iulie", "august",
      "septembrie", "octombrie", "noiembrie", "decembrie"];

    function each(selector, action) {
      Array.prototype.forEach.call(document.querySelectorAll(selector), action);
    }
    function value(name) {
      var field = form.elements[name];
      if (!field) return "";
      if (field.type === "checkbox") return field.checked ? "on" : "";
      return field.value.trim();
    }
    function show(element, text) {
      var blank = !text;
      element.textContent = blank ? element.getAttribute("data-empty") || "" : text;
      element.classList.toggle("is-empty", blank);
    }

    // "Joi, 20 noiembrie, 10:00 - 12:00 · Aula BN", as whenAndWhere in EventRoutes.kt writes it
    function day(text) {
      var m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text);
      if (!m) return null;
      var date = new Date(+m[1], +m[2] - 1, +m[3]);
      return DAYS[date.getDay()] + ", " + date.getDate() + " " + MONTHS[date.getMonth()];
    }
    function when(withPlace) {
      var start = day(value("start_date"));
      var from = value("start_time").slice(0, 5);
      if (!start || !from) return "";
      var to = value("end_time").slice(0, 5);
      var endDay = value("end_date") && value("end_date") !== value("start_date") ? day(value("end_date")) : null;
      var line = start + ", " + from;
      if (to) line += endDay ? " - " + endDay + ", " + to : " - " + to;
      var place = value("place");
      return withPlace && place ? line + " · " + place : line;
    }

    function fields() {
      each("[data-preview-text]", function (el) { show(el, value(el.getAttribute("data-preview-text"))); });
      each("[data-preview-when]", function (el) { show(el, when(el.getAttribute("data-preview-when") === "full")); });
      each("[data-preview-show]", function (el) { el.hidden = !value(el.getAttribute("data-preview-show")); });
      each("[data-preview-hide]", function (el) { el.hidden = !!value(el.getAttribute("data-preview-hide")); });
      each("[data-count]", function (el) {
        var field = form.elements[el.getAttribute("data-count")];
        if (field) el.textContent = field.value.length + " / " + el.getAttribute("data-max");
      });
    }

    // The list item shows the text without its marks, cut as markdownPreview cuts it.
    function excerpt(html) {
      var holder = document.createElement("div");
      holder.innerHTML = html;
      var parts = [];
      Array.prototype.forEach.call(holder.children, function (block) {
        if (block.tagName === "UL") {
          Array.prototype.forEach.call(block.children, function (item) { parts.push(item.textContent); });
        } else {
          parts.push(block.textContent);
        }
      });
      var text = parts.join(" ").replace(/\s+/g, " ").trim();
      if (text.length <= 240) return text;
      var cut = text.charCodeAt(239) >= 0xD800 && text.charCodeAt(239) <= 0xDBFF ? 239 : 240;
      return text.slice(0, cut).trim() + "...";
    }

    var timer = null;
    var latest = 0;
    function markdown() {
      var body = form.elements.body;
      if (!body) return;
      clearTimeout(timer);
      timer = setTimeout(function () {
        var mine = ++latest;
        var request = new XMLHttpRequest();
        request.open("POST", "/admin/preview/markdown");
        request.setRequestHeader("Content-Type", "application/x-www-form-urlencoded");
        request.onload = function () {
          // a signed-out session ends on the sign-in page; that is not a preview
          if (mine !== latest || request.status !== 200) return;
          if (request.responseURL && request.responseURL.indexOf("/admin/preview/markdown") < 0) return;
          var html = request.responseText;
          var blank = !body.value.trim();
          each("[data-preview-markdown]", function (el) {
            el.innerHTML = html;
            el.hidden = blank;
          });
          each("[data-preview-plain]", function (el) { show(el, excerpt(html)); });
        };
        request.send("text=" + encodeURIComponent(body.value));
      }, 300);
    }

    // The hero's shape follows the picture: square for a photo, up to 3:4 for a poster.
    each("[data-preview-hero] img", function (img) {
      img.addEventListener("load", function () {
        if (!img.naturalWidth || !img.naturalHeight) return;
        var ratio = Math.min(1, Math.max(0.75, img.naturalWidth / img.naturalHeight));
        img.parentNode.style.aspectRatio = String(ratio);
      });
    });

    var file = form.elements.image;
    var shown = null;
    if (file && window.URL && URL.createObjectURL) {
      file.addEventListener("change", function () {
        var chosen = file.files && file.files[0];
        if (!chosen) return;
        if (shown) URL.revokeObjectURL(shown);
        shown = URL.createObjectURL(chosen);
        each("img[data-preview-image]", function (img) {
          img.src = shown;
          img.hidden = false;
        });
        each("[data-preview-image-empty]", function (el) { el.hidden = true; });
      });
    }

    form.addEventListener("input", function (event) {
      fields();
      if (event.target.name === "body") markdown();
    });
    form.addEventListener("change", fields);
    fields();

    // The phone starts in the dashboard's theme, also when that follows the system.
    if (isDark()) {
      var dark = phone.querySelector("[data-phone-tone=dark]");
      if (dark) dark.checked = true;
    }
  }
})();
