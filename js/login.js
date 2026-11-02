// Login page — calls backend API

// Auto redirect if already logged in
if (localStorage.getItem("loggedIn")) {
  var _u = getLoggedInUser();
  if (_u) redirectByRole(_u.role);
}

function redirectByRole(role) {
  if (role === "admin")         window.location.href = "admin/dashboard.html";
  else if (role === "customer") window.location.href = "customer/dashboard.html";
  else if (role === "agent")    window.location.href = "agent/dashboard.html";
  else                          window.location.href = "login.html";
}

async function doLogin(btn) {
  var username = document.getElementById("username").value.trim();
  var password = document.getElementById("password").value;
  var errBox   = document.getElementById("error-msg");

  clearFieldErrors(["username", "password"]);
  errBox.style.display = "none";

  if (!username || !password) {
    if (!username) markFieldError("username", "Username is required.");
    if (!password) markFieldError("password", "Password is required.");
    errBox.textContent = "Please enter both your username and password.";
    errBox.style.display = "block";
    return;
  }

  try {
    setButtonLoading(btn, true, "Signing in...");
    var res = await fetch("http://localhost:8080/api/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: username, password: password })
    });

    var data = await res.json();
    setButtonLoading(btn, false);

    if (data.token) {
      // Save access + refresh tokens and user info
      setTokens(data.token, data.refreshToken);
      setLoggedInUser({
        id:       data.id,
        name:     data.name,
        username: data.username,
        role:     data.role
      });
      redirectByRole(data.role);
    } else {
      markFieldError("username");
      markFieldError("password");
      errBox.textContent = "Wrong username or password. Please try again.";
      errBox.style.display = "block";
    }

  } catch (e) {
    setButtonLoading(btn, false);
    showToast("Cannot connect to server. Make sure the backend is running on port 8080.", "#dc2626");
  }
}

function togglePassword() {
  var input = document.getElementById("password");
  var btn   = document.getElementById("toggleIcon");
  var icon  = document.querySelector("#toggleIcon i");
  if (input.type === "password") {
    input.type = "text";
    icon.classList.replace("fa-eye", "fa-eye-slash");
    btn.setAttribute("aria-label", "Hide password");
  } else {
    input.type = "password";
    icon.classList.replace("fa-eye-slash", "fa-eye");
    btn.setAttribute("aria-label", "Show password");
  }
}

document.addEventListener("keydown", function (e) {
  if (e.key === "Enter") doLogin(document.querySelector(".login-btn"));
});
