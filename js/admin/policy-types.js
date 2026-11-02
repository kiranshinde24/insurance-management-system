// Admin Policy Types — calls backend API
requireRole("admin");

async function loadTable() {
  showTableSkeleton("tbody", 7);
  var types    = await apiGet("/policy-types");
  if (!types) return;

  var html = "";
  types.forEach(function (t, i) {
    html += "<tr>" +
      "<td>" + (i + 1) + "</td>" +
      "<td><b>" + t.name + "</b></td>" +
      "<td style='color:#64748b'>" + (t.desc || "-") + "</td>" +
      "<td>₹" + (t.baseRate != null ? t.baseRate : "1.00") + "</td>" +
      "<td>" + (t.riskFactor != null ? t.riskFactor : "1.00") + "x</td>" +
      "<td style='white-space:nowrap'>" +
        "<button class='btn btn-sm btn-edit' onclick='editType(" + t.id + ")'>Edit</button> " +
        "<button class='btn btn-sm btn-del' onclick='delType(" + t.id + ")'>Delete</button>" +
      "</td></tr>";
  });

  if (!html) html = "<tr><td colspan='7' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-layer-group'></i><span>No types found</span></div></td></tr>";
  document.getElementById("tbody").innerHTML = html;
}

function openAdd() {
  document.getElementById("modal-title").innerHTML = "Add Policy Type";
  document.getElementById("edit-id").value = "";
  document.getElementById("f-name").value  = "";
  document.getElementById("f-desc").value  = "";
  document.getElementById("f-base-rate").value   = "1.00";
  document.getElementById("f-risk-factor").value = "1.00";
  clearFieldErrors(["f-name"]);
  openModal("modal");
}

async function editType(typeId) {
  var list = await apiGet("/policy-types");
  var t = list.find(function (x) { return x.id == typeId; });
  if (!t) return;

  document.getElementById("modal-title").innerHTML = "Edit Policy Type";
  document.getElementById("edit-id").value = typeId;
  document.getElementById("f-name").value  = t.name;
  document.getElementById("f-desc").value  = t.desc || "";
  document.getElementById("f-base-rate").value   = t.baseRate   != null ? t.baseRate   : "1.00";
  document.getElementById("f-risk-factor").value = t.riskFactor != null ? t.riskFactor : "1.00";
  clearFieldErrors(["f-name"]);
  openModal("modal");
}

async function saveType(btn) {
  var name   = document.getElementById("f-name").value.trim();
  var editId = document.getElementById("edit-id").value;
  clearFieldErrors(["f-name"]);
  if (!name) { markFieldError("f-name", "Required."); showToast("Type name is required!", "#dc2626"); return; }

  var body = {
    name: name,
    desc: document.getElementById("f-desc").value.trim(),
    baseRate:   parseFloat(document.getElementById("f-base-rate").value)   || 1.00,
    riskFactor: parseFloat(document.getElementById("f-risk-factor").value) || 1.00
  };
  setButtonLoading(btn, true, "Saving...");
  var result = editId
    ? await apiPut("/policy-types/" + editId, body)
    : await apiPost("/policy-types", body);
  setButtonLoading(btn, false);

  if (result.error) { showToast(result.error, "#dc2626"); return; }
  showToast(editId ? "Type updated!" : "Type added!");
  closeModal("modal");
  loadTable();
}

async function delType(typeId) {
  if (!confirm("Delete this type?")) return;
  var result = await apiDelete("/policy-types/" + typeId);
  if (result.error) { showToast(result.error, "#dc2626"); return; }
  showToast("Deleted.", "#dc2626");
  loadTable();
}

loadTable();
