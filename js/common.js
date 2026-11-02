// ============================================================
// BACKEND API BASE URL — change if your server runs elsewhere
// ============================================================
var API = "http://localhost:8080/api";
var SERVER_ROOT = API.replace(/\/api\/?$/, ""); // e.g. http://localhost:8080 — used for /uploads/... links

// ============================================================
// AUTH HELPERS
// ============================================================
function getToken() {
  return localStorage.getItem("token");
}

function getRefreshToken() {
  return localStorage.getItem("refreshToken");
}

// Called after login/register/refresh — stores both the short-lived access token
// and the refresh token used to silently renew it.
function setTokens(token, refreshToken) {
  localStorage.setItem("token", token);
  if (refreshToken) localStorage.setItem("refreshToken", refreshToken);
}

function getLoggedInUser() {
  var data = localStorage.getItem("loggedInUser");
  if (!data) return null;
  return JSON.parse(data);
}

function setLoggedInUser(user) {
  localStorage.setItem("loggedIn", "yes");
  localStorage.setItem("loggedInUser", JSON.stringify(user));
}

function clearLogin() {
  localStorage.removeItem("loggedIn");
  localStorage.removeItem("loggedInUser");
  localStorage.removeItem("token");
  localStorage.removeItem("refreshToken");
}

// ============================================================
// SILENT TOKEN REFRESH
// ============================================================
// The access token is short-lived (15 min). Rather than logging the user out the
// moment it expires, we transparently swap it for a new one using the refresh token
// — the user only gets logged out once the refresh token itself expires (7 days) or
// is invalid/revoked. `_refreshInFlight` de-dupes concurrent 401s into a single call.
var _refreshInFlight = null;

async function refreshAccessToken() {
  if (_refreshInFlight) return _refreshInFlight;

  _refreshInFlight = (async function () {
    var rToken = getRefreshToken();
    if (!rToken) return false;

    try {
      var res = await fetch(API + "/auth/refresh", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ refreshToken: rToken })
      });
      if (!res.ok) return false;

      var data = await res.json();
      if (!data.token) return false;

      setTokens(data.token, data.refreshToken);
      return true;
    } catch (e) {
      return false;
    }
  })();

  var result = await _refreshInFlight;
  _refreshInFlight = null;
  return result;
}

function loginPagePath() {
  var path = window.location.pathname;
  var base = path.split("/pages")[0] + "/pages";
  return base + "/login.html";
}

function requireRole(role) {
  if (!localStorage.getItem("loggedIn")) {
    window.location.href = loginPagePath();
    return null;
  }
  var user = getLoggedInUser();
  if (!user || user.role !== role) {
    clearLogin();
    window.location.href = loginPagePath();
    return null;
  }
  return user;
}

function logout() {
  var rToken = getRefreshToken();
  if (rToken) {
    // Best-effort: revoke server-side so the refresh token can't be reused. Don't block
    // on the network call — the user should be able to log out even if it's offline.
    fetch(API + "/auth/logout", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken: rToken })
    }).catch(function () {});
  }
  clearLogin();
  window.location.href = loginPagePath();
}

// ============================================================
// API CALL HELPERS
// ============================================================

// Core fetch wrapper: attaches the JWT, and if the access token has expired (401),
// tries a single silent refresh before retrying the request once. Only forces a
// logout if there's no refresh token, the refresh fails, or the retry still 401s.
async function authFetch(path, options) {
  options = options || {};
  options.headers = options.headers || {};
  options.headers["Authorization"] = "Bearer " + getToken();

  var res = await fetch(API + path, options);

  if (res.status === 401 || res.status === 403) {
    var refreshed = await refreshAccessToken();
    if (!refreshed) {
      clearLogin();
      window.location.href = loginPagePath();
      return null;
    }
    options.headers["Authorization"] = "Bearer " + getToken();
    res = await fetch(API + path, options);
    if (res.status === 401 || res.status === 403) {
      clearLogin();
      window.location.href = loginPagePath();
      return null;
    }
  }

  return res;
}

// GET request with JWT token
async function apiGet(path) {
  var res = await authFetch(path, {});
  if (!res) return null;
  return await res.json();
}

// POST request with JWT token
async function apiPost(path, body) {
  var res = await authFetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  if (!res) return null;
  return await res.json();
}

// PUT request with JWT token
async function apiPut(path, body) {
  var res = await authFetch(path, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  });
  if (!res) return null;
  return await res.json();
}

// DELETE request with JWT token
async function apiDelete(path) {
  var res = await authFetch(path, { method: "DELETE" });
  if (!res) return null;
  return await res.json();
}

// POST multipart/form-data (file uploads) with JWT token — do NOT set Content-Type,
// the browser fills in the multipart boundary automatically.
async function apiPostForm(path, formData) {
  var res = await authFetch(path, { method: "POST", body: formData });
  if (!res) return null;
  return await res.json();
}

// Download a PDF (or any binary) endpoint that requires the JWT auth header —
// plain <a href> links can't attach auth headers, so we fetch as a blob and
// trigger a save-as via a temporary object URL.
async function apiDownload(path, filename) {
  var res = await authFetch(path, {});
  if (!res) return;
  if (!res.ok) {
    var err = null;
    try { err = await res.json(); } catch (e) {}
    showToast((err && err.error) || "Could not generate the file.", "#dc2626");
    return;
  }
  var blob = await res.blob();
  var url = window.URL.createObjectURL(blob);
  var a = document.createElement("a");
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  window.URL.revokeObjectURL(url);
}

// Live premium preview: POST /policy/calculate-premium — returns { premium } or { error }
async function calculatePremium(sumAssured, typeId, custId, start, end) {
  return await apiPost("/policy/calculate-premium", {
    sumAssured: sumAssured, typeId: typeId, custId: custId, start: start, end: end
  });
}

// ============================================================
// DROPDOWN FILLER — loads from API
// ============================================================
async function fillDropdown(selectId, apiPath, valField, textField) {
  var sel = document.getElementById(selectId);
  var list = await apiGet(apiPath);
  if (!list) return;
  sel.innerHTML = "<option value=''>-- Select --</option>";
  list.forEach(function (item) {
    sel.innerHTML += "<option value='" + item[valField] + "'>" + item[textField] + "</option>";
  });
}

// ============================================================
// UI HELPERS (unchanged from original)
// ============================================================
// ============================================================
// FIELD VALIDATION FEEDBACK
// ============================================================
// Highlights an invalid input/select with a red border and drops a small
// error message right below it (creating that message element on first
// use). Pair with a showToast() call for the same problem so the person
// gets both an at-a-glance summary and a pointer to exactly which field
// needs fixing.
function isValidEmail(email) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
}

function markFieldError(id, message) {
  var el = document.getElementById(id);
  if (!el) return;
  el.classList.add("input-error");

  var errEl = document.getElementById(id + "-error");
  if (!errEl) {
    errEl = document.createElement("div");
    errEl.id = id + "-error";
    errEl.className = "field-error-text";
    el.insertAdjacentElement("afterend", errEl);
  }
  errEl.textContent = message || "";
  errEl.classList.toggle("show", !!message);
}

function clearFieldError(id) {
  var el = document.getElementById(id);
  if (el) el.classList.remove("input-error");
  var errEl = document.getElementById(id + "-error");
  if (errEl) errEl.classList.remove("show");
}

function clearFieldErrors(ids) {
  ids.forEach(clearFieldError);
}

function openModal(id) {
  document.getElementById(id).classList.add("show");
}

function closeModal(id) {
  document.getElementById(id).classList.remove("show");
}

function showToast(msg, color) {
  var t = document.getElementById("toast");
  if (!t) {
    t = document.createElement("div");
    t.id = "toast";
    document.body.appendChild(t);
  }
  t.textContent = msg;
  t.style.background = color || "#2563eb";
  t.style.color = "white";
  t.style.display = "block";
  t.style.position = "fixed";
  t.style.bottom = "24px";
  t.style.right = "24px";
  t.style.padding = "14px 18px";
  t.style.borderRadius = "14px";
  t.style.boxShadow = "0 16px 40px rgba(15,23,42,0.18)";
  t.style.zIndex = "999";
  t.style.fontSize = "14px";
  t.style.opacity = "1";
  setTimeout(function () {
    t.style.opacity = "0";
    setTimeout(function () { t.style.display = "none"; }, 200);
  }, 2500);
}

// ============================================================
// LOADING STATES
// ============================================================

// Renders `rows` placeholder <tr> elements with `cols` shimmering cells each.
// Call this into a tbody right before an apiGet() so the table never shows
// blank while data is in flight, then overwrite it with the real rows once
// the response arrives.
function skeletonRows(cols, rows) {
  rows = rows || 5;
  var cell = "<td><span class='skeleton skeleton-text'></span></td>";
  var row = "<tr class='skeleton-row'>" + cell.repeat(cols) + "</tr>";
  return row.repeat(rows);
}

function showTableSkeleton(tbodyId, cols, rows) {
  var el = document.getElementById(tbodyId);
  if (el) el.innerHTML = skeletonRows(cols, rows);
}

// Puts a shimmering placeholder into one or more stat-card elements (by id)
// while their values are being fetched.
function showStatsSkeleton(ids) {
  ids.forEach(function (id) {
    var el = document.getElementById(id);
    if (el) el.innerHTML = "<span class='skeleton stat-skeleton'></span>";
  });
}

// Toggles a small spinner + disabled state on a submit/save button while an
// async action runs, so the person gets feedback instead of a button that
// looks unresponsive. Usage:
//   async function savePolicy(btn) {
//     setButtonLoading(btn, true, "Saving...");
//     ... await apiPost(...) ...
//     setButtonLoading(btn, false);
//   }
function setButtonLoading(btn, isLoading, loadingLabel) {
  if (!btn) return;
  if (isLoading) {
    btn.dataset.originalLabel = btn.innerHTML;
    btn.disabled = true;
    btn.innerHTML = "<span class='btn-spinner'></span>" + (loadingLabel || "Please wait...");
  } else {
    btn.disabled = false;
    if (btn.dataset.originalLabel) btn.innerHTML = btn.dataset.originalLabel;
  }
}

function niceDate(d) {
  if (!d) return "-";
  var months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
  var dt = new Date(d);
  return dt.getDate() + " " + months[dt.getMonth()] + " " + dt.getFullYear();
}

function badge(status) {
  var map = {
    "Active": "b-green", "Approved": "b-green",
    "Inactive": "b-red", "Rejected": "b-red",
    "Pending": "b-amber", "Expired": "b-gray", "Completed": "b-green"
  };
  var cls = map[status] || "b-gray";
  return "<span class='badge " + cls + "'>" + status + "</span>";
}

// ============================================================
// THEME TOGGLE (unchanged)
// ============================================================
var themeToggleBtn = document.getElementById("themeToggle");
var body = document.body;

if (localStorage.getItem("theme") === "dark") {
  body.classList.add("dark");
  if (themeToggleBtn) themeToggleBtn.textContent = "Light";
} else {
  if (themeToggleBtn) themeToggleBtn.textContent = "Dark";
}

if (themeToggleBtn) {
  themeToggleBtn.addEventListener("click", function () {
    if (body.classList.contains("dark")) {
      body.classList.remove("dark");
      localStorage.setItem("theme", "light");
      themeToggleBtn.textContent = "Dark";
    } else {
      body.classList.add("dark");
      localStorage.setItem("theme", "dark");
      themeToggleBtn.textContent = "Light";
    }
  });
}
