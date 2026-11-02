// Agent My Policies — calls backend API
var user = requireRole("agent");

function setAgentHeader() {
  var name = user.name || user.username || "Agent";
  document.getElementById("nav-username").innerText = name;
  document.getElementById("nav-avatar").innerText   = name.slice(0, 2).toUpperCase();
}

async function loadTable() {
  setAgentHeader();
  showTableSkeleton("tbody", 5);
  var list = await apiGet("/agent/policies");
  if (!list) return;

  var html = "";
  list.forEach(function (p) {
    html += "<tr>" +
      "<td><b>" + p.polno + "</b></td>" +
      "<td>" + (p.customerName || "-") + "</td>" +
      "<td>₹" + p.premium + "</td>" +
      "<td>" + (p.typeName || "-") + "</td>" +
      "<td>" + badge(p.status) + "</td>" +
      "</tr>";
  });

  if (!html) html = "<tr><td colspan='5' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-file-contract'></i><span>No policies created yet</span></div></td></tr>";
  document.getElementById("tbody").innerHTML = html;
}

loadTable();
