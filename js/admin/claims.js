// Admin Claims — calls backend API
requireRole("admin");

var openClaimId  = null;
var openClaim    = null;
var currentPage  = 0;
var pageSize     = 10;
var totalPages   = 1;

async function loadTable() {
  showTableSkeleton("tbody", 8);
  var status = document.getElementById("filter-status").value;
  var params = ["page=" + currentPage, "size=" + pageSize];
  if (status) params.push("status=" + encodeURIComponent(status));
  var url = "/admin/claims?" + params.join("&");

  var data = await apiGet(url);
  if (!data) return;

  var list = Array.isArray(data) ? data : data.content;
  totalPages = Array.isArray(data) ? 1 : data.totalPages;

  var pending = 0, completed = 0;
  list.forEach(function (c) {
    if (c.status === "Pending")   pending++;
    if (c.status === "Approved")  completed++;
  });

  document.getElementById("summary-row").innerHTML =
    "<div class='sum-box'><h3>" + (Array.isArray(data) ? list.length : data.totalElements) + "</h3><p>Total Claims</p></div>" +
    "<div class='sum-box'><h3 style='color:#d97706'>" + pending + "</h3><p>Pending (this page)</p></div>" +
    "<div class='sum-box'><h3 style='color:#16a34a'>" + completed + "</h3><p>Approved (this page)</p></div>";

  var html = "", sn = 0;
  list.forEach(function (c) {
    sn++;
    html += "<tr>" +
      "<td>" + (currentPage * pageSize + sn) + "</td>" +
      "<td>" + niceDate(c.date) + "</td>" +
      "<td><b>" + (c.policyNumber || "-") + "</b></td>" +
      "<td>" + (c.customerName || "-") + "</td>" +
      "<td>₹" + c.amount + "</td>" +
      "<td>" + (c.approvedAmount ? "₹" + c.approvedAmount : "-") + "</td>" +
      "<td>" + badge(c.status) + "</td>" +
      "<td><button class='btn btn-sm btn-primary' onclick='openClaimModal(" + c.id + ")'>View</button></td>" +
      "</tr>";
  });

  if (!html) html = "<tr><td colspan='8' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-file-circle-exclamation'></i><span>No claims found</span></div></td></tr>";
  document.getElementById("tbody").innerHTML = html;

  document.getElementById("page-info").innerText = "Page " + (currentPage + 1) + " of " + Math.max(totalPages, 1);
  document.getElementById("prev-page").disabled = currentPage <= 0;
  document.getElementById("next-page").disabled = currentPage >= totalPages - 1;
}

function changePage(delta) {
  var next = currentPage + delta;
  if (next < 0 || next > totalPages - 1) return;
  currentPage = next;
  loadTable();
}

async function openClaimModal(claimId) {
  openClaimId = claimId;
  var c = await apiGet("/admin/claims/" + claimId);
  if (!c) return;
  openClaim = c;

  document.getElementById("d-customer").innerText    = c.customerName  || "-";
  document.getElementById("d-polno").innerText       = c.policyNumber  || "-";
  document.getElementById("d-poltype").innerText     = c.policyType    || "-";
  document.getElementById("d-agent").innerText       = c.agentName     || "-";
  document.getElementById("d-date").innerText        = niceDate(c.date);
  document.getElementById("d-amount").innerText      = "₹" + c.amount;
  document.getElementById("d-description").innerText = c.description   || "-";

  var noDocEl    = document.getElementById("d-no-doc");
  var viewDocBtn = document.getElementById("d-view-doc-btn");

  if (c.attachmentUrl || c.attachment) {
    noDocEl.style.display    = "none";
    viewDocBtn.style.display = "inline-flex";
  } else {
    noDocEl.style.display    = "block";
    viewDocBtn.style.display = "none";
  }

  var isDecided = c.status === "Approved" || c.status === "Rejected";
  document.getElementById("d-approve-wrap").style.display  = isDecided ? "none"  : "block";
  document.getElementById("d-completed-msg").style.display = isDecided ? "block" : "none";
  document.getElementById("d-save-btn").style.display      = isDecided ? "none"  : "inline-flex";
  document.getElementById("d-reject-btn").style.display    = isDecided ? "none"  : "inline-flex";
  if (isDecided) {
    document.getElementById("d-approved-show").innerText = c.approvedAmount ? "₹" + c.approvedAmount : c.status;
  } else {
    document.getElementById("d-approved-input").value = "";
    clearFieldErrors(["d-approved-input"]);
  }

  document.getElementById("claim-modal").style.display = "flex";
  loadRiskScore(claimId);
}

// Fetches the AI fraud/risk score for the open claim and renders it as a
// colored badge + up to a few reasons. Backed by the Python model in /insurance-ai
// (see RiskScoringClient.java / ClaimController#riskScore on the backend).
async function loadRiskScore(claimId) {
  var content = document.getElementById("d-risk-content");
  content.innerHTML = "<span style='color:#94a3b8;font-size:13px'>Loading risk score…</span>";

  var risk = await apiGet("/admin/claims/" + claimId + "/risk-score");
  if (!risk) {
    content.innerHTML = "<span style='color:#94a3b8;font-size:13px'>Risk score unavailable</span>";
    return;
  }

  if (!risk.available) {
    content.innerHTML = "<span style='color:#94a3b8;font-size:13px'>" +
      (risk.message || "AI risk scoring is currently unavailable") + "</span>";
    return;
  }

  var colors = { Low: "#16a34a", Medium: "#d97706", High: "#dc2626" };
  var color = colors[risk.riskLevel] || "#64748b";
  var pct = Math.round((risk.riskScore || 0) * 100);

  var html = "<span style='display:inline-block;padding:4px 10px;border-radius:999px;" +
    "background:" + color + "22;color:" + color + ";font-weight:600;font-size:13px'>" +
    risk.riskLevel + " risk — " + pct + "%</span>";

  if (risk.topFactors && risk.topFactors.length) {
    html += "<ul style='margin:8px 0 0 18px;padding:0;font-size:12.5px;color:#475569;line-height:1.6'>";
    risk.topFactors.forEach(function (f) {
      html += "<li>" + f.detail + "</li>";
    });
    html += "</ul>";
  }

  content.innerHTML = html;
}

// Real-file claims come back with an attachmentUrl (served from /uploads/...);
// legacy base64 claims still have the raw data URL in `attachment`.
function openDocViewer() {
  if (!openClaim) return;
  var body = document.getElementById("doc-viewer-body");
  document.getElementById("doc-viewer-title").innerText = "Supporting Document";

  if (openClaim.attachmentUrl) {
    var fullUrl = SERVER_ROOT + openClaim.attachmentUrl;
    if (/\.(png|jpe?g|gif|webp)$/i.test(openClaim.attachmentUrl)) {
      body.innerHTML = "<img src='" + fullUrl + "' style='max-width:100%;border-radius:8px'>";
    } else {
      body.innerHTML = "<iframe src='" + fullUrl + "' style='width:100%;height:60vh;border:none'></iframe>" +
        "<p style='margin-top:10px'><a href='" + fullUrl + "' target='_blank' rel='noopener'>Open in new tab ↗</a></p>";
    }
  } else if (openClaim.attachment) {
    var type = openClaim.attachmentType || "";
    if (type.indexOf("image") === 0) {
      body.innerHTML = "<img src='" + openClaim.attachment + "' style='max-width:100%;border-radius:8px'>";
    } else {
      body.innerHTML = "<iframe src='" + openClaim.attachment + "' style='width:100%;height:60vh;border:none'></iframe>";
    }
  } else {
    body.innerHTML = "<p style='color:#94a3b8'>No document available.</p>";
  }

  document.getElementById("doc-viewer-modal").style.display = "flex";
}

function closeDocViewer(event) {
  if (event.target === document.getElementById("doc-viewer-modal")) {
    document.getElementById("doc-viewer-modal").style.display = "none";
  }
}

async function saveApproved(btn) {
  var amount = parseFloat(document.getElementById("d-approved-input").value);
  clearFieldErrors(["d-approved-input"]);
  if (!amount || amount <= 0) {
    markFieldError("d-approved-input", "Enter a valid amount greater than zero.");
    showToast("Please enter a valid approved amount.", "#dc2626"); return;
  }

  setButtonLoading(btn, true, "Saving...");
  var result = await apiPut("/admin/claims/" + openClaimId + "/approve", {
    approvedAmount: amount
  });
  setButtonLoading(btn, false);

  if (result.error) { showToast(result.error, "#dc2626"); return; }

  document.getElementById("claim-modal").style.display = "none";
  showToast("Claim approved with ₹" + amount);
  loadTable();
}

async function rejectClaim(btn) {
  if (!confirm("Reject this claim?")) return;
  setButtonLoading(btn, true, "Rejecting...");
  var result = await apiPut("/admin/claims/" + openClaimId + "/reject", {});
  setButtonLoading(btn, false);
  if (result.error) { showToast(result.error, "#dc2626"); return; }

  document.getElementById("claim-modal").style.display = "none";
  showToast("Claim rejected.", "#dc2626");
  loadTable();
}

function closeModalOutside(event) {
  if (event.target === document.getElementById("claim-modal")) {
    document.getElementById("claim-modal").style.display = "none";
  }
}

loadTable();
