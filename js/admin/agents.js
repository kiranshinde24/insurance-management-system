// Admin Agents — calls backend API
requireRole("admin");

function validateAgentPassword(password) {
  if (password.length < 8)       return "Password must be at least 8 characters.";
  if (!/[a-z]/.test(password))   return "Must include a lowercase letter.";
  if (!/[A-Z]/.test(password))   return "Must include an uppercase letter.";
  if (!/[0-9]/.test(password))   return "Must include a number.";
  if (!/[@#$%^&*!()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password))
                                  return "Must include a special character.";
  return "";
}

async function loadTable() {
  showTableSkeleton("tbody", 7);
  var agents = await apiGet("/admin/agents");
  if (!agents) return;

  var html = "";
  agents.forEach(function (agent, index) {
    html += "<tr>" +
      "<td>" + (index + 1) + "</td>" +
      "<td><b>" + agent.name + "</b></td>" +
      "<td>" + (agent.username || "-") + "</td>" +
      "<td>" + (agent.phone || "-") + "</td>" +
      "<td>" + (agent.email || "-") + "</td>" +
      "<td><b style='color:#2563eb'>" + agent.comm + "%</b></td>" +
      "<td style='white-space:nowrap'>" +
        "<button class='btn btn-sm btn-edit' onclick='editAgent(" + agent.id + ")'>Edit</button> " +
        "<button class='btn btn-sm btn-del' onclick='deleteAgent(" + agent.id + ")'>Delete</button>" +
      "</td></tr>";
  });

  if (!html) html = "<tr><td colspan='7' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-user-tie'></i><span>No agents found</span></div></td></tr>";
  document.getElementById("tbody").innerHTML = html;
}

function openAdd() {
  document.getElementById("modal-title").innerHTML = "Add Agent";
  document.getElementById("edit-id").value = "";
  ["f-name","f-username","f-password","f-confirm-password","f-comm","f-phone","f-email"]
    .forEach(function (id) { document.getElementById(id).value = ""; });
  clearFieldErrors(["f-name","f-username","f-comm","f-password","f-confirm-password","f-email"]);
  openModal("modal");
}

async function editAgent(agentId) {
  var agent = await apiGet("/admin/agents/" + agentId);
  if (!agent) return;

  document.getElementById("modal-title").innerHTML = "Edit Agent";
  document.getElementById("edit-id").value         = agentId;
  document.getElementById("f-name").value          = agent.name;
  document.getElementById("f-username").value      = agent.username || "";
  document.getElementById("f-comm").value          = agent.comm;
  document.getElementById("f-phone").value         = agent.phone || "";
  document.getElementById("f-email").value         = agent.email || "";
  document.getElementById("f-password").value      = "";
  document.getElementById("f-confirm-password").value = "";
  clearFieldErrors(["f-name","f-username","f-comm","f-password","f-confirm-password","f-email"]);
  openModal("modal");
}

async function saveAgent(btn) {
  var name     = document.getElementById("f-name").value.trim();
  var username = document.getElementById("f-username").value.trim();
  var password = document.getElementById("f-password").value;
  var confirm  = document.getElementById("f-confirm-password").value;
  var comm     = document.getElementById("f-comm").value;
  var email    = document.getElementById("f-email").value.trim();
  var editId   = document.getElementById("edit-id").value;

  clearFieldErrors(["f-name", "f-username", "f-comm", "f-password", "f-confirm-password", "f-email"]);

  var missing = false;
  if (!name)     { markFieldError("f-name", "Required.");     missing = true; }
  if (!username) { markFieldError("f-username", "Required."); missing = true; }
  if (!comm)     { markFieldError("f-comm", "Required.");     missing = true; }
  if (missing) {
    showToast("Name, username and commission required!", "#dc2626"); return;
  }

  if (email && !isValidEmail(email)) {
    markFieldError("f-email", "Enter a valid email address.");
    showToast("Please enter a valid email address.", "#dc2626"); return;
  }

  if (!editId || password || confirm) {
    if (!password || !confirm) {
      if (!password) markFieldError("f-password", "Required.");
      if (!confirm)  markFieldError("f-confirm-password", "Required.");
      showToast("Please set and confirm the password.", "#dc2626"); return;
    }
    if (password !== confirm) {
      markFieldError("f-password", "Passwords do not match.");
      markFieldError("f-confirm-password", "Passwords do not match.");
      showToast("Passwords do not match!", "#dc2626"); return;
    }
    var pwErr = validateAgentPassword(password);
    if (pwErr) { markFieldError("f-password", pwErr); showToast(pwErr, "#dc2626"); return; }
  }

  var body = {
    name: name, username: username, comm: parseFloat(comm),
    phone: document.getElementById("f-phone").value.trim(),
    email: email
  };
  if (password) body.password = password;

  setButtonLoading(btn, true, "Saving...");
  var result = editId
    ? await apiPut("/admin/agents/" + editId, body)
    : await apiPost("/admin/agents", body);
  setButtonLoading(btn, false);

  if (result.error) { showToast(result.error, "#dc2626"); return; }

  showToast(editId ? "Agent updated!" : "Agent added!");
  closeModal("modal");
  loadTable();
}

async function deleteAgent(agentId) {
  if (!confirm("Delete this agent?")) return;
  await apiDelete("/admin/agents/" + agentId);
  showToast("Deleted.", "#dc2626");
  loadTable();
}

loadTable();
