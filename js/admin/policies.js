// Admin Policies — calls backend API
requireRole("admin");

var currentPage = 0;
var pageSize    = 10;
var totalPages  = 1;

async function loadTable() {
  showTableSkeleton("tbody", 11);
  var search = document.getElementById("search").value.trim();
  var status = document.getElementById("filter-status").value;

  var url = "/admin/policies";
  var params = ["page=" + currentPage, "size=" + pageSize];
  if (search) params.push("search=" + encodeURIComponent(search));
  if (status) params.push("status=" + encodeURIComponent(status));
  url += "?" + params.join("&");

  var data = await apiGet(url);
  if (!data) return;

  // Backward compatible: plain array (shouldn't happen since we always pass page/size,
  // but keep this safe in case the endpoint ever returns a raw list).
  var list = Array.isArray(data) ? data : data.content;
  totalPages = Array.isArray(data) ? 1 : data.totalPages;

  var html = "";
  list.forEach(function (p, i) {
    html += `
      <tr>
        <td>${currentPage * pageSize + i + 1}</td>
        <td><b>${p.polno}</b></td>
        <td>${p.customerName || "-"}</td>
        <td>${p.typeName || "-"}</td>
        <td>${p.agentName || "-"}</td>
        <td>${niceDate(p.start)}</td>
        <td>${niceDate(p.end)}</td>
        <td>₹${p.premium}</td>
        <td>${badge(p.status)}</td>
        <td style="white-space:nowrap">
          <button class="btn btn-sm btn-edit" onclick="editPolicy(${p.id})">Edit</button>
          <button class="btn btn-sm btn-del"  onclick="deletePolicy(${p.id})">Delete</button>
        </td>
        <td>
          <button class="btn btn-sm btn-edit" onclick="downloadCertificate(${p.id})">📄 PDF</button>
        </td>
      </tr>`;
  });

  if (!html) html = "<tr><td colspan='11' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-file-contract'></i><span>No policies found</span></div></td></tr>";
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

async function downloadCertificate(policyId) {
  await apiDownload("/admin/policies/" + policyId + "/certificate", "policy-certificate-" + policyId + ".pdf");
}

// ============================================================
// PREMIUM PREVIEW — auto-calculates as the form fields are filled in
// ============================================================
async function refreshPremiumPreview() {
  var sumAssured = Number(document.getElementById("f-sum-assured").value);
  var typeId     = Number(document.getElementById("f-type").value);
  var custId     = Number(document.getElementById("f-cust").value);
  var start      = document.getElementById("f-start").value;
  var end        = document.getElementById("f-end").value;
  var hint       = document.getElementById("f-premium-hint");

  if (!sumAssured || !typeId || !custId || !start || !end) return;

  var result = await calculatePremium(sumAssured, typeId, custId, start, end);
  if (result.error) {
    hint.style.color = "#dc2626";
    hint.innerText = result.error;
    return;
  }

  document.getElementById("f-premium").value = result.premium;
  hint.style.color = "#16a34a";
  hint.innerText = "Auto-calculated: ₹" + result.premium + " (editable before saving)";
}

function wirePremiumPreviewListeners() {
  ["f-sum-assured", "f-type", "f-cust", "f-start", "f-end"].forEach(function (id) {
    var el = document.getElementById(id);
    el.removeEventListener("change", refreshPremiumPreview);
    el.addEventListener("change", refreshPremiumPreview);
  });
}

async function openAdd() {
  document.getElementById("modal-title").innerHTML = "New Policy";
  document.getElementById("edit-id").value = "";
  ["f-polno","f-start","f-end","f-premium","f-sum-assured"].forEach(function (id) {
    document.getElementById(id).value = "";
  });
  document.getElementById("f-premium-hint").innerText = "Fill sum assured, customer & type to auto-calculate.";
  document.getElementById("f-premium-hint").style.color = "#64748b";
  clearFieldErrors(["f-polno", "f-premium", "f-cust", "f-type", "f-start", "f-end"]);
  await fillDropdown("f-cust",  "/admin/customers",  "id", "name");
  await fillDropdown("f-type",  "/policy-types",     "id", "name");
  await fillDropdown("f-agent", "/admin/agents",     "id", "name");
  wirePremiumPreviewListeners();
  openModal("modal");
}

async function editPolicy(policyId) {
  var data = await apiGet("/admin/policies?page=0&size=1000");
  var list = Array.isArray(data) ? data : data.content;
  var p = list.find(function (x) { return x.id == policyId; });
  if (!p) return;

  document.getElementById("modal-title").innerHTML = "Edit Policy";
  document.getElementById("edit-id").value = policyId;

  await fillDropdown("f-cust",  "/admin/customers",  "id", "name");
  await fillDropdown("f-type",  "/policy-types",     "id", "name");
  await fillDropdown("f-agent", "/admin/agents",     "id", "name");

  document.getElementById("f-polno").value       = p.polno;
  document.getElementById("f-start").value       = p.start;
  document.getElementById("f-end").value         = p.end;
  document.getElementById("f-premium").value     = p.premium;
  document.getElementById("f-sum-assured").value = p.sumAssured || "";
  document.getElementById("f-cust").value        = p.custId;
  document.getElementById("f-type").value        = p.typeId;
  document.getElementById("f-agent").value       = p.agentId || "";
  document.getElementById("f-premium-hint").innerText = "Change sum assured, customer, type or dates to recalculate.";
  document.getElementById("f-premium-hint").style.color = "#64748b";
  clearFieldErrors(["f-polno", "f-premium", "f-cust", "f-type", "f-start", "f-end"]);
  wirePremiumPreviewListeners();
  openModal("modal");
}

async function savePolicy(btn) {
  var polno   = document.getElementById("f-polno").value.trim();
  var premium = document.getElementById("f-premium").value;
  var custId  = document.getElementById("f-cust").value;
  var typeId  = document.getElementById("f-type").value;
  var start   = document.getElementById("f-start").value;
  var end     = document.getElementById("f-end").value;
  var editId  = document.getElementById("edit-id").value;

  clearFieldErrors(["f-polno", "f-premium", "f-cust", "f-type", "f-start", "f-end"]);

  var missing = false;
  if (!polno)   { markFieldError("f-polno", "Required.");   missing = true; }
  if (!premium) { markFieldError("f-premium", "Required."); missing = true; }
  if (!custId)  { markFieldError("f-cust", "Required.");    missing = true; }
  if (!typeId)  { markFieldError("f-type", "Required.");    missing = true; }
  if (missing) {
    showToast("Policy no, premium, customer and type required!", "#dc2626"); return;
  }

  if (start && end && new Date(end) <= new Date(start)) {
    markFieldError("f-end", "Must be after the start date.");
    showToast("End date must be after the start date.", "#dc2626"); return;
  }

  var body = {
    polno:      polno,
    start:      start,
    end:        end,
    premium:    parseFloat(premium),
    sumAssured: Number(document.getElementById("f-sum-assured").value) || null,
    custId:     Number(custId),
    typeId:     Number(typeId),
    agentId:    Number(document.getElementById("f-agent").value) || null
  };

  setButtonLoading(btn, true, "Saving...");
  var result = editId
    ? await apiPut("/admin/policies/" + editId, body)
    : await apiPost("/admin/policies", body);
  setButtonLoading(btn, false);

  if (result.error) { showToast(result.error, "#dc2626"); return; }
  showToast(editId ? "Policy updated!" : "Policy created!");
  closeModal("modal");
  loadTable();
}

async function deletePolicy(policyId) {
  if (!confirm("Delete this policy?")) return;
  await apiDelete("/admin/policies/" + policyId);
  showToast("Deleted.", "#dc2626");
  loadTable();
}

loadTable();
