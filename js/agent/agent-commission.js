// Agent Commission — calls backend API
var user = requireRole("agent");

function setAgentHeader() {
  var name = user.name || user.username || "Agent";
  document.getElementById("nav-username").innerText = name;
  document.getElementById("nav-avatar").innerText   = name.slice(0, 2).toUpperCase();
}

async function loadCommission() {
  setAgentHeader();
  showTableSkeleton("tbody", 4);
  var data = await apiGet("/agent/dashboard");
  if (!data) return;

  var policies = await apiGet("/agent/policies");
  if (!policies) return;

  var commPct = 0;
  var html    = "";
  var total   = 0;

  // Get commission % from dashboard
  var commTotal = Number(data.commissionTotal || 0);
  var polCount  = policies.length;

  // Calculate comm% from total and sum of premiums
  var premiumSum = policies.reduce(function (sum, p) { return sum + Number(p.premium || 0); }, 0);
  commPct = premiumSum > 0 ? (commTotal / premiumSum * 100) : 0;

  policies.forEach(function (p) {
    var earning = (Number(p.premium || 0) * commPct) / 100;
    total += earning;
    html += "<tr>" +
      "<td><b>" + p.polno + "</b></td>" +
      "<td>₹" + p.premium + "</td>" +
      "<td>" + commPct.toFixed(1) + "%</td>" +
      "<td>₹" + earning.toFixed(2) + "</td>" +
      "</tr>";
  });

  if (!html) html = "<tr><td colspan='4' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-file-contract'></i><span>No policies found</span></div></td></tr>";
  document.getElementById("tbody").innerHTML = html;
  document.getElementById("commissionTotal").innerText = "₹" + total.toFixed(2);
  document.getElementById("policiesCount").innerText   = polCount;
}

loadCommission();
