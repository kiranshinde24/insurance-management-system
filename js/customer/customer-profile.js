// Customer Profile — calls backend API
var user = requireRole("customer");

function setProfileHeader() {
  var name = user.name || user.username || "Customer";
  document.getElementById("nav-username").innerText = name;
  document.getElementById("nav-avatar").innerText   = name.slice(0, 2).toUpperCase();
}

async function loadProfile() {
  setProfileHeader();
  var profile = await apiGet("/customer/profile");
  if (!profile) return;

  document.getElementById("profile-username").value = user.username;
  document.getElementById("profile-name").value     = profile.name  || "";
  document.getElementById("profile-email").value    = profile.email || "";
  document.getElementById("profile-phone").value    = profile.phone || "";
  document.getElementById("profile-city").value     = profile.city  || "";
  document.getElementById("profile-dob").value      = profile.dob   || "";
  document.getElementById("profile-pan").value      = profile.pan   || "";
}

async function saveProfile(btn) {
  var name  = document.getElementById("profile-name").value.trim();
  var email = document.getElementById("profile-email").value.trim();
  var dob   = document.getElementById("profile-dob").value;

  clearFieldErrors(["profile-name", "profile-email", "profile-dob"]);

  var missing = false;
  if (!name)  { markFieldError("profile-name", "Required.");  missing = true; }
  if (!email) { markFieldError("profile-email", "Required."); missing = true; }
  if (!dob)   { markFieldError("profile-dob", "Required.");   missing = true; }
  if (missing) {
    showToast("Name, email and DOB are required.", "#dc2626"); return;
  }
  if (!isValidEmail(email)) {
    markFieldError("profile-email", "Enter a valid email address.");
    showToast("Please enter a valid email address.", "#dc2626"); return;
  }

  setButtonLoading(btn, true, "Saving...");
  var result = await apiPut("/customer/profile", {
    name:  name,
    email: email,
    dob:   dob,
    phone: document.getElementById("profile-phone").value.trim(),
    city:  document.getElementById("profile-city").value.trim(),
    pan:   document.getElementById("profile-pan").value.trim().toUpperCase()
  });
  setButtonLoading(btn, false);

  if (result.error) { showToast(result.error, "#dc2626"); return; }

  // Update local session name
  user.name = name;
  setLoggedInUser(user);
  setProfileHeader();
  showToast("Profile updated successfully.");
}

loadProfile();
