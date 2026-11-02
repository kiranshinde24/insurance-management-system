// Admin Dashboard — calls backend API
requireRole("admin");

async function loadDashboard() {
  showStatsSkeleton([
    "totalCustomers", "totalPolicies", "totalClaims", "totalAgents",
    "totalCollection", "totalCollectedCard", "activePolicies", "pendingClaims"
  ]);
  showTableSkeleton("recentPolicies", 4);

  var data = await apiGet("/admin/dashboard");
  if (!data) return;
  window.__lastDashboardData = data;

  document.getElementById("totalCustomers").innerText  = data.totalCustomers  || 0;
  document.getElementById("totalPolicies").innerText   = data.totalPolicies   || 0;
  document.getElementById("totalClaims").innerText     = data.totalClaims     || 0;
  document.getElementById("totalAgents").innerText     = data.totalAgents     || 0;
  document.getElementById("totalCollection").innerText    = "₹" + (data.totalCollection || 0);
  document.getElementById("totalCollectedCard").innerText = "₹" + (data.totalCollection || 0);
  document.getElementById("activePolicies").innerText  = data.activePolicies  || 0;
  document.getElementById("pendingClaims").innerText   = data.pendingClaims   || 0;

  var recentPolicies = data.recentPolicies || [];
  var tableHTML = "";

  recentPolicies.forEach(function (policy) {
    tableHTML += `
      <tr>
        <td><b>${policy.polno}</b></td>
        <td>${policy.customerName || "-"}</td>
        <td>₹${policy.premium}</td>
        <td>${policy.status}</td>
      </tr>
    `;
  });

  if (!tableHTML) {
    tableHTML = "<tr><td colspan='4' class='empty-state-cell'><div class='empty-state'><i class='fa-solid fa-file-contract'></i><span>No policies yet</span></div></td></tr>";
  }

  document.getElementById("recentPolicies").innerHTML = tableHTML;

  renderCharts(data);
}

// ===== CHARTS (Chart.js) =====
var charts = {}; // keeps references so we can destroy/redraw on theme change

function chartTheme() {
  var dark = document.body.classList.contains("dark");
  return {
    text: dark ? "#cbd5e1" : "#475569",
    grid: dark ? "#243044" : "#f1f5f9"
  };
}

function renderCharts(data) {
  if (typeof Chart === "undefined") return; // Chart.js failed to load — skip charts gracefully

  var theme = chartTheme();
  Chart.defaults.font.family = "Inter, sans-serif";
  Chart.defaults.color = theme.text;

  var policiesSoldTrend = data.policiesSoldTrend || [];
  var claimsTrend = data.claimsTrend || [];
  var revenueTrend = data.revenueTrend || [];

  var labels = policiesSoldTrend.map(function (p) { return p.label; });

  // --- Policies Sold (bar) ---
  drawChart("policiesSoldChart", "bar", {
    labels: labels,
    datasets: [{
      label: "Policies Sold",
      data: policiesSoldTrend.map(function (p) { return p.count; }),
      backgroundColor: "#2563eb",
      borderRadius: 6,
      maxBarThickness: 34
    }]
  }, theme);

  // --- Claims Trend (line) ---
  drawChart("claimsTrendChart", "line", {
    labels: claimsTrend.map(function (p) { return p.label; }),
    datasets: [{
      label: "Claims Filed",
      data: claimsTrend.map(function (p) { return p.count; }),
      borderColor: "#f59e0b",
      backgroundColor: "rgba(245,158,11,0.15)",
      tension: 0.35,
      fill: true,
      pointRadius: 4,
      pointBackgroundColor: "#f59e0b"
    }]
  }, theme);

  // --- Revenue (area line) ---
  drawChart("revenueChart", "line", {
    labels: revenueTrend.map(function (p) { return p.label; }),
    datasets: [{
      label: "Revenue Collected (₹)",
      data: revenueTrend.map(function (p) { return p.amount; }),
      borderColor: "#16a34a",
      backgroundColor: "rgba(22,163,74,0.12)",
      tension: 0.35,
      fill: true,
      pointRadius: 4,
      pointBackgroundColor: "#16a34a"
    }]
  }, theme, true);
}

function drawChart(canvasId, type, chartData, theme, isCurrency) {
  var canvas = document.getElementById(canvasId);
  if (!canvas) return;

  if (charts[canvasId]) charts[canvasId].destroy();

  charts[canvasId] = new Chart(canvas.getContext("2d"), {
    type: type,
    data: chartData,
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: function (ctx) {
              var v = ctx.parsed.y;
              return isCurrency ? "₹" + v.toLocaleString("en-IN") : v;
            }
          }
        }
      },
      scales: {
        x: {
          grid: { color: theme.grid },
          ticks: { color: theme.text }
        },
        y: {
          beginAtZero: true,
          grid: { color: theme.grid },
          ticks: {
            color: theme.text,
            callback: function (v) { return isCurrency ? "₹" + v : v; }
          }
        }
      }
    }
  });
}

// Redraw charts with updated colors whenever the theme toggle is used
var themeToggleBtn = document.getElementById("themeToggle");
if (themeToggleBtn) {
  themeToggleBtn.addEventListener("click", function () {
    // common.js flips body.dark before/after this listener depending on load order,
    // so re-read theme on next tick to be safe
    setTimeout(function () {
      var lastData = window.__lastDashboardData;
      if (lastData) renderCharts(lastData);
    }, 0);
  });
}

async function runExpiryCheck() {
  var result = await apiPost("/admin/policies/run-expiry-check", {});
  if (result.error) { showToast(result.error, "#dc2626"); return; }
  showToast("Expiry check complete — " + result.processed + " polic" + (result.processed === 1 ? "y" : "ies") + " processed.");
  loadDashboard();
}

loadDashboard();
