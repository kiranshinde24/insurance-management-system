// Customer Dashboard — calls backend API
var currentUser = requireRole("customer");

function setUserInfo() {
  var name = currentUser.name || currentUser.username || "Customer";
  document.getElementById("nav-username").innerText = name;
  document.getElementById("welcome-name").innerText = name.split(" ")[0];
  document.getElementById("nav-avatar").innerText   = name.slice(0, 2).toUpperCase();
}

async function loadDashboard() {
  setUserInfo();
  showStatsSkeleton([
    "totalPolicies", "activePolicies", "pendingClaims", "pastClaims",
    "agentCount", "totalPayments", "lastPayment"
  ]);
  showTableSkeleton("recentPolicies", 6);

  var data = await apiGet("/customer/dashboard");
  if (!data) return;

  document.getElementById("totalPolicies").innerText  = data.totalPolicies  || 0;
  document.getElementById("activePolicies").innerText = data.activePolicies || 0;
  document.getElementById("pendingClaims").innerText  = data.pendingClaims  || 0;
  document.getElementById("pastClaims").innerText     = data.pastClaims     || 0;
  document.getElementById("agentCount").innerText     = data.agentCount     || 0;
  document.getElementById("totalPayments").innerText  = "₹" + (data.totalPayments || 0);
  document.getElementById("lastPayment").innerText    = data.lastPayment ? niceDate(data.lastPayment) : "-";

  var recent = data.recentPolicies || [];
  var html   = "";

  recent.forEach(function (p) {
    html += "<tr>" +
      "<td><b>" + p.polno + "</b></td>" +
      "<td>" + (p.typeName || "-") + "</td>" +
      "<td>₹" + p.premium + "</td>" +
      "<td>" + (p.agentName || "-") + "</td>" +
      "<td>-</td>" +
      "<td>" + badge(p.status) + "</td>" +
      "</tr>";
  });

  if (!html) html = "<tr><td colspan='6' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-file-contract'></i><span>No policies found</span></div></td></tr>";
  document.getElementById("recentPolicies").innerHTML = html;
}

loadDashboard();
