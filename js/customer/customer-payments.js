// Customer Payments — calls backend API
var user = requireRole("customer");

function setCustomerHeader() {
  var name = user.name || user.username || "Customer";
  document.getElementById("nav-username").innerText = name;
  document.getElementById("nav-avatar").innerText   = name.slice(0, 2).toUpperCase();
}

async function loadPaymentForm() {
  setCustomerHeader();
  var policies = await apiGet("/customer/policies");
  var select   = document.getElementById("policy-select");
  select.innerHTML = "<option value=''>-- Select --</option>";
  if (policies) {
    policies.forEach(function (p) {
      select.innerHTML += "<option value='" + p.id + "'>" + p.polno + "</option>";
    });
  }
}

async function savePayment(btn) {
  var polId  = Number(document.getElementById("policy-select").value);
  var amount = Number(document.getElementById("payment-amount").value);
  var date   = document.getElementById("payment-date").value;
  var mode   = document.getElementById("payment-mode").value;

  clearFieldErrors(["policy-select", "payment-amount", "payment-date", "payment-mode"]);

  var missing = false;
  if (!polId)  { markFieldError("policy-select", "Required.");  missing = true; }
  if (!amount) { markFieldError("payment-amount", "Required."); missing = true; }
  if (!date)   { markFieldError("payment-date", "Required.");   missing = true; }
  if (!mode)   { markFieldError("payment-mode", "Required.");   missing = true; }
  if (missing) {
    showToast("All fields are required.", "#dc2626"); return;
  }
  if (amount <= 0) {
    markFieldError("payment-amount", "Must be greater than zero.");
    showToast("Amount must be greater than zero.", "#dc2626"); return;
  }

  setButtonLoading(btn, true, "Saving...");
  var result = await apiPost("/customer/payments", {
    polId: polId, amount: amount, date: date, mode: mode
  });
  setButtonLoading(btn, false);

  if (result.error) { showToast(result.error, "#dc2626"); return; }

  showToast("Payment added successfully.");
  document.getElementById("payment-amount").value = "";
  document.getElementById("payment-date").value   = "";
  document.getElementById("payment-mode").value   = "";
  document.getElementById("policy-select").value  = "";
  loadTable();
}

async function loadTable() {
  showTableSkeleton("tbody", 5);
  await loadPaymentForm();
  var list = await apiGet("/customer/payments");
  if (!list) return;

  var html = "";
  list.forEach(function (p) {
    html += "<tr>" +
      "<td>" + niceDate(p.date) + "</td>" +
      "<td><b>" + (p.policyNumber || "-") + "</b></td>" +
      "<td>₹" + p.amount + "</td>" +
      "<td>" + p.mode + "</td>" +
      "<td><button class='btn btn-sm btn-edit' onclick='downloadReceipt(" + p.id + ")'>🧾 Download</button></td>" +
      "</tr>";
  });

  if (!html) html = "<tr><td colspan='5' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-receipt'></i><span>No payments recorded</span></div></td></tr>";
  document.getElementById("tbody").innerHTML = html;
}

async function downloadReceipt(paymentId) {
  await apiDownload("/customer/payments/" + paymentId + "/receipt", "payment-receipt-" + paymentId + ".pdf");
}

loadTable();
