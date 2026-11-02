// Admin Customers — calls backend API
requireRole("admin");

var currentPage = 0;
var pageSize    = 10;
var totalPages  = 1;

async function loadTable() {
  showTableSkeleton("tbody", 7);
  var search = document.getElementById("search").value.trim();
  var params = ["page=" + currentPage, "size=" + pageSize];
  if (search) params.push("search=" + encodeURIComponent(search));
  var url = "/admin/customers?" + params.join("&");

  var data = await apiGet(url);
  if (!data) return;

  var list = Array.isArray(data) ? data : data.content;
  totalPages = Array.isArray(data) ? 1 : data.totalPages;

  var html = "";
  list.forEach(function (c, i) {
    html += "<tr>" +
      "<td>" + (currentPage * pageSize + i + 1) + "</td>" +
      "<td><b>" + c.name + "</b></td>" +
      "<td>" + niceDate(c.dob) + "</td>" +
      "<td>" + (c.pan || "-") + "</td>" +
      "<td>" + (c.phone || "-") + "</td>" +
      "<td>" + (c.email || "-") + "</td>" +
      "<td>" + (c.city || "-") + "</td>" +
      "</tr>";
  });

  if (!html) html = "<tr><td colspan='7' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-users'></i><span>No customers found</span></div></td></tr>";
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

function openAdd() {
  document.getElementById("modal-title").innerHTML = "Add Customer";
  document.getElementById("edit-id").value = "";
  ["f-name","f-dob","f-pan","f-phone","f-email","f-city"]
    .forEach(function (id) { document.getElementById(id).value = ""; });
  clearFieldErrors(["f-name","f-dob","f-email"]);
  openModal("modal");
}

async function saveCustomer(btn) {
  var name  = document.getElementById("f-name").value.trim();
  var dob   = document.getElementById("f-dob").value;
  var email = document.getElementById("f-email").value.trim();

  clearFieldErrors(["f-name", "f-dob", "f-email"]);

  var missing = false;
  if (!name) { markFieldError("f-name", "Required."); missing = true; }
  if (!dob)  { markFieldError("f-dob", "Required.");  missing = true; }
  if (missing) { showToast("Name and DOB are required!", "#dc2626"); return; }

  if (email && !isValidEmail(email)) {
    markFieldError("f-email", "Enter a valid email address.");
    showToast("Please enter a valid email address.", "#dc2626"); return;
  }

  var body = {
    name:  name,
    dob:   dob,
    pan:   document.getElementById("f-pan").value.trim().toUpperCase(),
    phone: document.getElementById("f-phone").value.trim(),
    email: email,
    city:  document.getElementById("f-city").value.trim()
  };

  setButtonLoading(btn, true, "Saving...");
  var result = await apiPost("/admin/customers", body);
  setButtonLoading(btn, false);
  if (result.error) { showToast(result.error, "#dc2626"); return; }

  showToast("Customer added!");
  closeModal("modal");
  loadTable();
}

loadTable();
