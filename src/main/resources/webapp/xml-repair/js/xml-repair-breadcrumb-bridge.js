(function () {
  if (window.__xmlRepairBreadcrumbBridge) return;
  window.__xmlRepairBreadcrumbBridge = true;

  function show(el) {
    if (el && el.style.display === "none") el.style.display = "";
  }
  function hide(el) {
    if (el && el.style.display !== "none") el.style.display = "none";
  }

  function findOriginal() {
    return document.querySelector(".polarion-ApplicationHeader-breadcrumb");
  }

  function ensureCustom(original) {
    let custom = document.querySelector("#xml-repair-custom-breadcrumb");
    if (!custom) {
      if (!original || !original.parentNode) return null;
      custom = document.createElement("div");
      custom.id = "xml-repair-custom-breadcrumb";
      original.insertAdjacentElement("afterend", custom);
      custom.style.font = "inherit";
      custom.style.whiteSpace = "nowrap";
      custom.style.display = "none";
    }

    const originalOrder = window.getComputedStyle(original).order;
    if (originalOrder && originalOrder !== "0") {
      custom.style.order = originalOrder;
    }

    return custom;
  }

  const xmlRepairMarker = "xml-repair";

  function isXmlRepairUrl(href) {
    return (
      (location.hash && location.hash.indexOf(xmlRepairMarker) !== -1) ||
      href.indexOf(xmlRepairMarker) !== -1
    );
  }

  //language=HTML
  const breadcrumbHtml =
    '<div class="polarion-ApplicationHeader-breadcrumb">' +
    '   <div class="polarion-ApplicationHeader-imagePanel">' +
    '       <img src="/polarion/xml-repair-admin/ui/images/menu/30x30/_parent.svg" alt="" class="gwt-Image" style="width: 30px; height: 30px;">' +
    "   </div>" +
    '   <div class="polarion-ApplicationHeader-breadcrumbTitlePanel">' +
    '       <div class="polarion-ApplicationHeader-breadcrumbTitle" title="XML-Repair">XML-Repair</div>' +
    "   </div>" +
    "</div>";

  function sync() {
    const href = window.location.href;
    const original = findOriginal();
    if (!original) return;
    const custom = ensureCustom(original);
    if (!custom) return;

    if (isXmlRepairUrl(href)) {
      hide(original);
      show(custom);
      custom.innerHTML = breadcrumbHtml;
    } else {
      show(original);
      hide(custom);
    }
  }

  window.addEventListener("popstate", sync);
  window.addEventListener("hashchange", sync);

  // The original breadcrumb is rendered by GWT and may not exist yet.
  // Observe the DOM until it appears, then run sync.
  const observer = new MutationObserver(function () {
    if (findOriginal()) {
      observer.disconnect();
      sync();
    }
  });
  observer.observe(document.body, { childList: true, subtree: true });

  sync();
})();
