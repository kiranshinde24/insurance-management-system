// Customer My Policies — calls backend API
var user = requireRole("customer");

function setCustomerHeader() {
  var name = user.name || user.username || "Customer";
  document.getElementById("nav-username").innerText = name;
  document.getElementById("nav-avatar").innerText   = name.slice(0, 2).toUpperCase();
}

async function loadTable() {
  setCustomerHeader();
  showTableSkeleton("tbody", 8);
  var list = await apiGet("/customer/policies");
  if (!list) return;

  var html = "";
  list.forEach(function (p) {
    html += "<tr>" +
      "<td><b>" + p.polno + "</b></td>" +
      "<td>" + (p.typeName || "-") + "</td>" +
      "<td>₹" + p.premium + "</td>" +
      "<td>" + niceDate(p.start) + "</td>" +
      "<td>" + niceDate(p.end) + "</td>" +
      "<td>" + badge(p.status) + "</td>" +
      "<td>" + (p.agentName || "-") + "</td>" +
      "<td><button class='btn btn-sm btn-edit' onclick='downloadCertificate(" + p.id + ")'>📄 Download</button></td>" +
      "</tr>";
  });

  if (!html) html = "<tr><td colspan='8' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-file-contract'></i><span>No policies assigned yet</span></div></td></tr>";
  document.getElementById("tbody").innerHTML = html;
}

async function downloadCertificate(policyId) {
  await apiDownload("/customer/policies/" + policyId + "/certificate", "policy-certificate-" + policyId + ".pdf");
}

loadTable();
