// Admin Payments — calls backend API
requireRole("admin");

async function loadTable() {
  showTableSkeleton("tbody", 7);
  var mode = document.getElementById("filter-mode").value;
  var url  = mode ? "/admin/payments?mode=" + encodeURIComponent(mode) : "/admin/payments";
  var list = await apiGet(url);
  if (!list) return;

  var html  = "";
  var total = 0;

  list.forEach(function (p, i) {
    total += Number(p.amount || 0);
    html += "<tr>" +
      "<td>" + (i + 1) + "</td>" +
      "<td>" + niceDate(p.date) + "</td>" +
      "<td><b>" + (p.policyNumber || "-") + "</b></td>" +
      "<td>" + (p.customerName || "-") + "</td>" +
      "<td><b style='color:#2563eb'>₹" + p.amount + "</b></td>" +
      "<td>" + p.mode + "</td>" +
      "<td><button class='btn btn-sm btn-edit' onclick='downloadReceipt(" + p.id + ")'>🧾 PDF</button></td>" +
      "</tr>";
  });

  if (!html) html = "<tr><td colspan='7' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-receipt'></i><span>No records found</span></div></td></tr>";
  document.getElementById("tbody").innerHTML = html;

  document.getElementById("summary-row").innerHTML =
    "<div class='sum-box'><h3>" + list.length + "</h3><p>Total Transactions</p></div>" +
    "<div class='sum-box'><h3>₹" + total.toLocaleString("en-IN") + "</h3><p>Total Collected</p></div>";
}

async function downloadReceipt(paymentId) {
  await apiDownload("/admin/payments/" + paymentId + "/receipt", "payment-receipt-" + paymentId + ".pdf");
}

async function openAdd() {
  document.getElementById("f-date").value   = new Date().toISOString().split("T")[0];
  document.getElementById("f-amount").value = "";
  document.getElementById("f-mode").value   = "Online";
  clearFieldErrors(["f-policy", "f-date", "f-amount"]);
  await fillDropdown("f-policy", "/admin/policies", "id", "polno");
  openModal("modal");
}

async function savePayment(btn) {
  var polId  = document.getElementById("f-policy").value;
  var date   = document.getElementById("f-date").value;
  var amount = document.getElementById("f-amount").value;
  var mode   = document.getElementById("f-mode").value;

  clearFieldErrors(["f-policy", "f-date", "f-amount"]);

  var missing = false;
  if (!polId) { markFieldError("f-policy", "Required."); missing = true; }
  if (!date)  { markFieldError("f-date", "Required.");   missing = true; }
  if (!amount) { markFieldError("f-amount", "Required."); missing = true; }
  if (missing) {
    showToast("Policy, date and amount required!", "#dc2626"); return;
  }
  if (Number(amount) <= 0) {
    markFieldError("f-amount", "Must be greater than zero.");
    showToast("Amount must be greater than zero.", "#dc2626"); return;
  }

  setButtonLoading(btn, true, "Saving...");
  var result = await apiPost("/admin/payments", {
    polId: Number(polId), date: date,
    amount: parseFloat(amount), mode: mode
  });
  setButtonLoading(btn, false);

  if (result.error) { showToast(result.error, "#dc2626"); return; }
  showToast("Payment recorded!");
  closeModal("modal");
  loadTable();
}

loadTable();
