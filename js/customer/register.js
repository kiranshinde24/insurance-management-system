// Customer registration — calls backend API

// Redirect already logged-in users
if (localStorage.getItem("loggedIn")) {
  var _u = getLoggedInUser();
  if (_u) {
    if (_u.role === "admin")         window.location.href = "dashboard.html";
    else if (_u.role === "customer") window.location.href = "customer/dashboard.html";
    else if (_u.role === "agent")    window.location.href = "agent/dashboard.html";
  }
}

function showError(message) {
  var el = document.getElementById("error-msg");
  el.textContent = message;
  el.style.display = "block";
  setTimeout(function () { el.style.display = "none"; }, 3000);
}

function validatePassword(password) {
  if (password.length < 6)             return "Password must be at least 6 characters.";
  if (!/[a-z]/.test(password))         return "Password must include a lowercase letter.";
  if (!/[A-Z]/.test(password))         return "Password must include an uppercase letter.";
  if (!/[0-9]/.test(password))         return "Password must include a number.";
  if (!/[@#$%^&*!()_+\-=\[\]{};':"\\|,.<>\/?]/.test(password))
                                        return "Password must include a special character.";
  return "";
}

// Load agents from backend for dropdown
async function loadAgentDropdown() {
  var select = document.getElementById("agent");
  select.innerHTML = "<option value=''>-- Select an Agent --</option>";

  try {
    var agents = await apiGet("/agents/list");
    if (!agents) return;
    agents.forEach(function (agent) {
      var option = document.createElement("option");
      option.value = agent.id;
      option.textContent = agent.name + (agent.phone ? " (" + agent.phone + ")" : "");
      select.appendChild(option);
    });
  } catch (e) {
    console.error("Could not load agents", e);
  }
}

loadAgentDropdown();

async function doRegister(btn) {
  var fullname = document.getElementById("fullname").value.trim();
  var email    = document.getElementById("email").value.trim();
  var username = document.getElementById("username").value.trim();
  var password = document.getElementById("password").value;
  var confirm  = document.getElementById("confirm-password").value;
  var phone    = document.getElementById("phone").value.trim();
  var city     = document.getElementById("city").value.trim();
  var dob      = document.getElementById("dob").value;
  var pan      = document.getElementById("pan").value.trim().toUpperCase();
  var agentId  = document.getElementById("agent").value;

  clearFieldErrors(["fullname", "email", "username", "password", "confirm-password", "dob", "agent"]);

  var missing = false;
  if (!fullname) { markFieldError("fullname", "Required."); missing = true; }
  if (!email)    { markFieldError("email", "Required.");    missing = true; }
  if (!username) { markFieldError("username", "Required."); missing = true; }
  if (!password) { markFieldError("password", "Required."); missing = true; }
  if (!confirm)  { markFieldError("confirm-password", "Required."); missing = true; }
  if (!dob)      { markFieldError("dob", "Required.");      missing = true; }
  if (missing) {
    showError("Name, email, username, password and DOB are required.");
    return;
  }
  if (!agentId) {
    markFieldError("agent", "Please select an agent.");
    showError("Please select an agent.");
    return;
  }
  if (!isValidEmail(email)) {
    markFieldError("email", "Enter a valid email address.");
    showError("Please enter a valid email address.");
    return;
  }
  if (password !== confirm) {
    markFieldError("password", "Passwords do not match.");
    markFieldError("confirm-password", "Passwords do not match.");
    showError("Passwords do not match.");
    return;
  }
  var pwErr = validatePassword(password);
  if (pwErr) { markFieldError("password", pwErr); showError(pwErr); return; }

  try {
    setButtonLoading(btn, true, "Creating account...");
    var res = await fetch("http://localhost:8080/api/auth/register", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        fullname: fullname,
        email:    email,
        username: username,
        password: password,
        phone:    phone,
        city:     city,
        dob:      dob,
        pan:      pan,
        agentId:  Number(agentId)
      })
    });

    var data = await res.json();
    setButtonLoading(btn, false);

    if (data.token) {
      setTokens(data.token, data.refreshToken);
      setLoggedInUser({
        id: data.id, name: data.name,
        username: data.username, role: data.role
      });
      window.location.href = "dashboard.html";
    } else {
      showError(data.error || "Registration failed.");
    }

  } catch (e) {
    setButtonLoading(btn, false);
    showError("Cannot connect to server. Make sure backend is running.");
  }
}
