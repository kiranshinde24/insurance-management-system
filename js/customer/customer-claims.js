// Customer Claims — calls backend API
var currentUser = requireRole("customer");

function setCustomerHeader() {
  var name = currentUser.name || currentUser.username || "Customer";
  document.getElementById("nav-username").innerText = name;
  document.getElementById("nav-avatar").innerText   = name.slice(0, 2).toUpperCase();
}

async function loadClaimForm() {
  setCustomerHeader();
  var policies = await apiGet("/customer/policies");
  var dropdown = document.getElementById("claim-policy");
  dropdown.innerHTML = "<option value=''>-- Select --</option>";
  if (policies) {
    policies.forEach(function (p) {
      dropdown.innerHTML += "<option value='" + p.id + "'>" + p.polno + "</option>";
    });
  }
}

async function raiseClaim(btn) {
  var polId       = Number(document.getElementById("claim-policy").value);
  var amount      = Number(document.getElementById("claim-amount").value);
  var date        = document.getElementById("claim-date").value;
  var description = document.getElementById("claim-description").value.trim();
  var fileInput   = document.getElementById("claim-file");
  var file        = fileInput.files[0];

  clearFieldErrors(["claim-policy", "claim-amount", "claim-date", "claim-description"]);

  var missing = false;
  if (!polId)  { markFieldError("claim-policy", "Required.");      missing = true; }
  if (!amount) { markFieldError("claim-amount", "Required.");      missing = true; }
  if (!date)   { markFieldError("claim-date", "Required.");        missing = true; }
  if (!description) { markFieldError("claim-description", "Required."); missing = true; }
  if (missing) {
    showToast("Please fill all required fields.", "#dc2626"); return;
  }
  if (amount <= 0) {
    markFieldError("claim-amount", "Must be greater than zero.");
    showToast("Claim amount must be greater than zero.", "#dc2626"); return;
  }

  // Real file upload (multipart/form-data) — the file is stored on disk server-side
  // and only its URL comes back, instead of stuffing base64 into the database.
  var formData = new FormData();
  formData.append("polId", polId);
  formData.append("amount", amount);
  formData.append("date", date);
  formData.append("description", description);
  if (file) formData.append("file", file);

  setButtonLoading(btn, true, "Uploading...");
  var result = await apiPostForm("/customer/claims", formData);
  setButtonLoading(btn, false);
  if (result.error) { showToast(result.error, "#dc2626"); return; }

  showToast("Claim raised successfully.");
  document.getElementById("claim-policy").value      = "";
  document.getElementById("claim-amount").value      = "";
  document.getElementById("claim-date").value        = "";
  document.getElementById("claim-description").value = "";
  document.getElementById("claim-file").value        = "";
  loadTable();
}

async function loadTable() {
  showTableSkeleton("tbody", 8);
  await loadClaimForm();
  var list = await apiGet("/customer/claims");
  if (!list) return;

  var html = "";
  list.forEach(function (c) {
    var approvedCol = c.approvedAmount ? "₹" + c.approvedAmount : "-";
    var desc = c.description || "-";
    if (desc.length > 60) desc = desc.substring(0, 60) + "...";

    var docCol = "-";
    if (c.attachmentUrl) {
      docCol = "<a href='" + SERVER_ROOT + c.attachmentUrl + "' target='_blank' rel='noopener'>📎 View</a>";
    } else if (c.attachment) {
      docCol = "<a href='" + c.attachment + "' download='" + (c.attachmentName || "attachment") + "'>📎 View</a>";
    }

    html += "<tr>" +
      "<td>" + niceDate(c.date) + "</td>" +
      "<td><b>" + (c.policyNumber || "-") + "</b></td>" +
      "<td>" + (c.agentName || "-") + "</td>" +
      "<td>₹" + c.amount + "</td>" +
      "<td>" + approvedCol + "</td>" +
      "<td>" + desc + "</td>" +
      "<td>" + badge(c.status) + "</td>" +
      "<td>" + docCol + "</td>" +
      "</tr>";
  });

  if (!html) html = "<tr><td colspan='8' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-file-circle-exclamation'></i><span>No claims found</span></div></td></tr>";
  document.getElementById("tbody").innerHTML = html;
}

loadTable();
