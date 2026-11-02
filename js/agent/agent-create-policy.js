// Agent Create Policy — calls backend API
var user = requireRole("agent");

function setAgentHeader() {
  var name = user.name || user.username || "Agent";
  document.getElementById("nav-username").innerText = name;
  document.getElementById("nav-avatar").innerText   = name.slice(0, 2).toUpperCase();
}

async function loadOptions() {
  setAgentHeader();

  // Load customers assigned to this agent
  var customers = await apiGet("/agent/customers");
  var types     = await apiGet("/policy-types");

  var custSel = document.getElementById("customer-select");
  var typeSel = document.getElementById("type-select");

  custSel.innerHTML = "<option value=''>-- Select --</option>";
  typeSel.innerHTML = "<option value=''>-- Select --</option>";

  if (customers) {
    customers.forEach(function (c) {
      custSel.innerHTML += "<option value='" + c.id + "'>" + c.name + "</option>";
    });
  }

  if (types) {
    types.forEach(function (t) {
      typeSel.innerHTML += "<option value='" + t.id + "'>" + t.name + "</option>";
    });
  }

  // Pre-select customer from URL param
  var params     = new URLSearchParams(window.location.search);
  var customerId = params.get("customer_id");
  if (customerId) custSel.value = customerId;

  // Live premium preview whenever the inputs needed for the calculation change
  ["customer-select", "type-select", "sum-assured", "start-date", "end-date"].forEach(function (id) {
    document.getElementById(id).addEventListener("change", updatePremiumPreview);
  });
}

async function updatePremiumPreview() {
  var custId     = Number(document.getElementById("customer-select").value);
  var typeId     = Number(document.getElementById("type-select").value);
  var sumAssured = Number(document.getElementById("sum-assured").value);
  var start      = document.getElementById("start-date").value;
  var end        = document.getElementById("end-date").value;
  var hint       = document.getElementById("premium-hint");

  if (!custId || !typeId || !sumAssured || !start || !end) return;

  var result = await calculatePremium(sumAssured, typeId, custId, start, end);
  if (result.error) {
    hint.style.color = "#dc2626";
    hint.innerText = result.error;
    return;
  }

  document.getElementById("premium").value = result.premium;
  hint.style.color = "#16a34a";
  hint.innerText = "Auto-calculated premium: ₹" + result.premium + " (you can override before saving)";
}

async function createPolicy(btn) {
  var custId     = Number(document.getElementById("customer-select").value);
  var typeId     = Number(document.getElementById("type-select").value);
  var sumAssured = Number(document.getElementById("sum-assured").value) || null;
  var premium    = Number(document.getElementById("premium").value) || null;
  var start      = document.getElementById("start-date").value;
  var end        = document.getElementById("end-date").value;

  clearFieldErrors(["customer-select", "type-select", "start-date", "end-date"]);

  var missing = false;
  if (!custId) { markFieldError("customer-select", "Required."); missing = true; }
  if (!typeId) { markFieldError("type-select", "Required.");     missing = true; }
  if (!start)  { markFieldError("start-date", "Required.");      missing = true; }
  if (!end)    { markFieldError("end-date", "Required.");        missing = true; }
  if (missing) {
    showToast("Customer, policy type and dates are required.", "#dc2626"); return;
  }

  if (new Date(end) <= new Date(start)) {
    markFieldError("end-date", "Must be after the start date.");
    showToast("End date must be after the start date.", "#dc2626"); return;
  }

  // Leave premium out (or 0) to let the backend auto-calculate it from sum assured, type and age.
  setButtonLoading(btn, true, "Saving...");
  var result = await apiPost("/agent/policies", {
    custId: custId, typeId: typeId, sumAssured: sumAssured,
    premium: premium, start: start, end: end
  });
  setButtonLoading(btn, false);

  if (result.error) { showToast(result.error, "#dc2626"); return; }

  showToast("Policy created successfully.");
  window.location.href = "my-policies.html";
}

loadOptions();
