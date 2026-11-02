// Agent Dashboard — calls backend API
var user = requireRole("agent");

function setAgentHeader() {
  var name = user.name || user.username || "Agent";
  document.getElementById("nav-username").innerText = name;
  document.getElementById("nav-avatar").innerText   = name.slice(0, 2).toUpperCase();
}

async function loadDashboard() {
  setAgentHeader();
  showStatsSkeleton([
    "customerCount", "policyCount", "commissionTotal",
    "activeAgentPolicies", "pendingClaims", "totalEarnings"
  ]);
  showTableSkeleton("recentPolicies", 4);

  var data = await apiGet("/agent/dashboard");
  if (!data) return;

  document.getElementById("customerCount").innerText        = data.customerCount        || 0;
  document.getElementById("policyCount").innerText          = data.policyCount          || 0;
  document.getElementById("commissionTotal").innerText      = "₹" + (data.commissionTotal || 0);
  document.getElementById("activeAgentPolicies").innerText  = data.activePolicies       || 0;
  document.getElementById("pendingClaims").innerText        = data.pendingClaims        || 0;
  document.getElementById("totalEarnings").innerText        = "₹" + (data.commissionTotal || 0);
  document.getElementById("policiesWithPayments").innerText = data.policiesWithPayments || 0;

  var recent = data.recentPolicies || [];
  var html   = "";

  recent.forEach(function (p) {
    html += "<tr>" +
      "<td><b>" + p.polno + "</b></td>" +
      "<td>" + (p.customerName || "-") + "</td>" +
      "<td>₹" + p.premium + "</td>" +
      "<td>" + badge(p.status) + "</td>" +
      "</tr>";
  });

  if (!html) html = "<tr><td colspan='4' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-file-contract'></i><span>No assigned policies</span></div></td></tr>";
  document.getElementById("recentPolicies").innerHTML = html;
}

loadDashboard();
