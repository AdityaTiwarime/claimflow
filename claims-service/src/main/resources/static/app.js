// ClaimFlow console: plain JavaScript over the REST API (no build step).
const $ = (id) => document.getElementById(id);
const money = (v) => (v === null || v === undefined) ? "–" : "$" + Number(v).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
const SERVICE = { PRIMARY_CARE: "Primary care", SPECIALIST: "Specialist", LAB: "Lab", IMAGING: "Imaging", PHARMACY: "Pharmacy", EMERGENCY: "Emergency" };
let members = [];
let plans = {};
let selected = null;

async function api(path, options) {
  const res = await fetch(path, options);
  const body = res.status === 204 ? null : await res.json().catch(() => null);
  if (!res.ok) throw new Error(body && body.detail ? body.detail : res.status + " " + res.statusText);
  return body;
}

function showMemberInfo() {
  const m = members.find((x) => x.memberNumber === $("member").value);
  if (!m) return;
  const p = plans[m.planCode];
  const ended = m.coverageEnd ? ` · coverage ended ${m.coverageEnd}` : "";
  $("member-info").textContent =
    `${p.name}: deductible ${money(m.deductibleMet)} of ${money(p.deductible)} met · you pay ${p.coinsurancePercent}% after that · ` +
    `plan paid ${money(m.benefitsUsed)} of ${money(p.annualMax)} this year${ended}`;
}

async function loadReference() {
  const [ps, ms] = await Promise.all([api("/api/plans"), api("/api/members")]);
  ps.forEach((p) => (plans[p.code] = p));
  members = ms;
  $("member").innerHTML = ms.map((m) => `<option value="${m.memberNumber}">${m.memberNumber} · ${m.fullName} (${m.planCode})</option>`).join("");
  showMemberInfo();
}

function row(c) {
  return `<tr data-claim-id="${c.id}" class="${c.id === selected ? "selected" : ""}">
    <td class="mono">${c.id}</td>
    <td>${c.memberName}</td>
    <td>${SERVICE[c.serviceType] || c.serviceType}</td>
    <td class="num">${money(c.billedAmount)}</td>
    <td><span class="status ${c.status}">${c.status}</span></td>
    <td class="num plan-pays">${money(c.planPays)}</td>
    <td class="num member-pays">${money(c.memberPays)}</td>
  </tr>`;
}

async function refresh() {
  try {
    const [claims, summary, ms] = await Promise.all([api("/api/claims"), api("/api/summary"), api("/api/members")]);
    members = ms; // keeps the deductible / benefits line under the member picker current
    showMemberInfo();
    document.querySelector("#claims tbody").innerHTML = claims.map(row).join("");
    $("empty").hidden = claims.length > 0;
    ["SUBMITTED", "APPROVED", "DENIED", "PENDED"].forEach((s) => {
      const t = summary.find((x) => x.status === s);
      $("kpi-" + s).textContent = t ? t.count : 0;
    });
    $("kpi-paid").textContent = money(summary.reduce((sum, t) => sum + Number(t.planPaid || 0), 0));
    if (selected) await showDetail(selected);
  } catch (e) {
    console.error(e);
  }
}

async function showDetail(id) {
  const c = await api("/api/claims/" + encodeURIComponent(id));
  const d = $("detail");
  d.hidden = false;
  const fmt = (iso) => new Date(iso).toLocaleTimeString("en-GB");
  d.innerHTML = `<h3>${c.id} · ${c.memberName} · <span class="status ${c.status}">${c.status}</span></h3>
    <p class="reasons">${c.providerName} · ${c.serviceDate} · billed ${money(c.billedAmount)}${c.reasonCodes.length ? " · " + c.reasonCodes.join(", ") : ""}</p>
    <ul class="timeline">${c.timeline.map((e) => `<li><span class="t">${fmt(e.at)}</span><span class="type">${e.type}</span><span>${e.detail || ""}</span></li>`).join("")}</ul>`;
}

$("claims").addEventListener("click", (e) => {
  const tr = e.target.closest("tr[data-claim-id]");
  if (!tr) return;
  selected = tr.dataset.claimId;
  refresh();
});

$("member").addEventListener("change", showMemberInfo);

$("claim-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const err = $("error");
  err.hidden = true;
  $("submit").disabled = true;
  try {
    const claim = await api("/api/claims", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        memberNumber: $("member").value,
        serviceType: $("serviceType").value,
        serviceDate: $("serviceDate").value,
        providerName: $("provider").value,
        billedAmount: $("amount").value === "" ? null : Number($("amount").value),
      }),
    });
    selected = claim.id;
    $("amount").value = "";
    await refresh();
  } catch (ex) {
    err.textContent = ex.message;
    err.hidden = false;
  } finally {
    $("submit").disabled = false;
  }
});

$("serviceDate").value = new Date().toISOString().slice(0, 10);
loadReference().then(refresh);
setInterval(refresh, 1500);
