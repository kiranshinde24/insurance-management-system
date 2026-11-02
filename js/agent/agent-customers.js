// Agent Customers — calls backend API
var user = requireRole("agent");

function setAgentHeader() {
  var name = user.name || user.username || "Agent";
  document.getElementById("nav-username").innerText = name;
  document.getElementById("nav-avatar").innerText   = name.slice(0, 2).toUpperCase();
}

async function loadTable() {
  setAgentHeader();
  showTableSkeleton("tbody", 5);
  var list = await apiGet("/agent/customers");
  if (!list) return;

  var html = "";
  list.forEach(function (c) {
    html += "<tr>" +
      "<td><b>" + c.name + "</b></td>" +
      "<td>" + (c.phone || "-") + "</td>" +
      "<td>" + (c.email || "-") + "</td>" +
      "<td>" + (c.city  || "-") + "</td>" +
      "<td style='white-space:nowrap'>" +
        "<button class='btn btn-sm btn-primary' onclick='assignPolicy(" + c.id + ")'>Assign Policy</button>" +
      "</td></tr>";
  });

  if (!html) html = "<tr><td colspan='5' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-users'></i><span>No customers assigned to you yet</span></div></td></tr>";
  document.getElementById("tbody").innerHTML = html;
}

function assignPolicy(customerId) {
  window.location.href = "create-policy.html?customer_id=" + customerId;
}

loadTable();
