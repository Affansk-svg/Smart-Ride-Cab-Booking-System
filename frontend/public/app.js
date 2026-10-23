const $ = s => document.querySelector(s);
const esc = s => String(s ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const inr = n => '₹' + Number(n).toFixed(2);
const BASE = 40, PER_KM = 12;

let token = localStorage.getItem('sr_token');
let user = JSON.parse(localStorage.getItem('sr_user') || 'null');
let map, layer, driverMarker, stomp, wsq = null, subs = [], poll, sim, trackedId = null, pins = [], rideCache = {}, activeDrawn = null;

/* ---------- helpers ---------- */
async function api(path, method = 'GET', body) {
  const r = await fetch(path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
    body: body ? JSON.stringify(body) : undefined
  });
  const data = await r.json().catch(() => ({}));
  if (r.status === 401 && token) { logout(); throw new Error('Session expired, please log in again'); }
  if (!r.ok) throw new Error(data.error || 'Request failed (' + r.status + ')');
  return data;
}
function toast(m, err) {
  const t = $('#toast'); t.textContent = m; t.className = 'show' + (err ? ' err' : '');
  clearTimeout(t._t); t._t = setTimeout(() => t.className = '', 3500);
}
function km(a, b, c, d) {
  const R = 6371, rad = x => x * Math.PI / 180, dLat = rad(c - a), dLon = rad(d - b);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a)) * Math.cos(rad(c)) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}
const pin = (ll, emoji) => L.marker(ll, { icon: L.divIcon({ html: `<div class="pin">${emoji}</div>`, className: '', iconSize: [30, 30], iconAnchor: [15, 28] }) });
function initMap(onClick) {
  if (map) map.remove();
  map = L.map('map').setView([12.9716, 77.5946], 12);
  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', { attribution: '© OpenStreetMap' }).addTo(map);
  layer = L.layerGroup().addTo(map); driverMarker = null; activeDrawn = null;
  if (onClick) map.on('click', e => onClick(e.latlng));
}
function moveDriver(lat, lng) {
  if (!driverMarker) driverMarker = pin([lat, lng], '🚕').addTo(map); else driverMarker.setLatLng([lat, lng]);
}
function drawRide(r) {
  layer.clearLayers();
  pin([r.pickupLat, r.pickupLng], '📍').addTo(layer);
  pin([r.dropLat, r.dropLng], '🏁').addTo(layer);
  L.polyline([[r.pickupLat, r.pickupLng], [r.dropLat, r.dropLng]], { dashArray: '6' }).addTo(layer);
  map.fitBounds([[r.pickupLat, r.pickupLng], [r.dropLat, r.dropLng]], { padding: [40, 40] });
}

/* ---------- websocket ---------- */
function connectWs(cb) {
  if (stomp && stomp.connected) return cb();
  if (wsq) { wsq.push(cb); return; }
  wsq = [cb];
  const c = Stomp.over(new SockJS('/ws')); c.debug = null;
  c.connect({ Authorization: 'Bearer ' + token },
    () => { stomp = c; const q = wsq; wsq = null; q.forEach(f => f()); },
    () => { stomp = null; wsq = null; trackedId = null; toast('Live connection lost, retrying…', true); });
}
function unsub() { subs.forEach(s => { try { s.unsubscribe(); } catch (e) {} }); subs = []; }
function subscribeRide(id, onLoc, onStatus) {
  unsub();
  connectWs(() => {
    subs = [
      stomp.subscribe(`/topic/rides/${id}/location`, m => onLoc(JSON.parse(m.body))),
      stomp.subscribe(`/topic/rides/${id}/status`, m => onStatus(JSON.parse(m.body)))
    ];
  });
}

/* ---------- shell ---------- */
function logout() {
  token = null; user = null; localStorage.removeItem('sr_token'); localStorage.removeItem('sr_user');
  if (stomp) { try { stomp.disconnect(); } catch (e) {} stomp = null; }
  start();
}
function start() {
  clearInterval(poll); clearInterval(sim); unsub(); trackedId = null;
  $('#who').innerHTML = user ? `${esc(user.name)} <span class="badge">${user.role}</span> <button id="out" class="ghost">Logout</button>` : '';
  if (!user) return authView();
  $('#out').onclick = logout;
  ({ RIDER: riderView, DRIVER: driverView, ADMIN: adminView })[user.role]();
}

/* ---------- auth ---------- */
function authView() {
  let mode = 'login';
  const draw = () => {
    $('#app').innerHTML = `<div class="card auth">
      <div class="tabs"><button data-m="login" class="${mode === 'login' ? 'on' : ''}">Login</button><button data-m="register" class="${mode === 'register' ? 'on' : ''}">Register</button></div>
      ${mode === 'register' ? '<input id="name" placeholder="Full name"><input id="phone" placeholder="Phone (optional)">' : ''}
      <input id="email" type="email" placeholder="Email"><input id="pw" type="password" placeholder="Password (min 6 chars)">
      ${mode === 'register' ? '<select id="role"><option value="RIDER">I am a Rider</option><option value="DRIVER">I am a Driver</option></select>' : ''}
      <button id="go" style="width:100%">${mode === 'login' ? 'Login' : 'Create account'}</button>
      <p class="muted">Demo admin: admin@smartride.com / Admin@123</p></div>`;
    document.querySelectorAll('[data-m]').forEach(b => b.onclick = () => { mode = b.dataset.m; draw(); });
    $('#go').onclick = async () => {
      try {
        const body = { email: $('#email').value, password: $('#pw').value };
        if (mode === 'register') Object.assign(body, { name: $('#name').value, phone: $('#phone').value, role: $('#role').value });
        const d = await api('/api/users/' + mode, 'POST', body);
        token = d.token; user = { id: d.id, name: d.name, email: d.email, role: d.role };
        localStorage.setItem('sr_token', token); localStorage.setItem('sr_user', JSON.stringify(user));
        start();
      } catch (e) { toast(e.message, true); }
    };
  };
  draw();
}

/* ---------- ride card ---------- */
function rideCard(r, actions) {
  rideCache[r.id] = r;
  return `<div class="ride"><div class="row"><b>Ride #${r.id}</b><span class="st ${r.status}">${r.status}</span></div>
    <div>${esc(r.pickupAddress)} → ${esc(r.dropAddress)}</div>
    <div class="muted">${r.distanceKm.toFixed(1)} km · ${inr(r.fare)}${r.driverName ? ' · Driver: ' + esc(r.driverName) : ''}${user.role !== 'RIDER' ? ' · Rider: ' + esc(r.riderName) : ''}</div>
    <div>${actions || ''}</div></div>`;
}
const btn = (act, id, label, cls = '') => `<button class="small ${cls}" data-act="${act}" data-id="${id}">${label}</button>`;

/* ---------- rider ---------- */
function riderView() {
  pins = [];
  $('#app').innerHTML = `<div class="grid">
    <section class="card"><h2>Book a ride</h2>
      <div class="muted" id="hint"></div><div id="map"></div>
      <input id="pk" placeholder="Pickup label (optional)"><input id="dp" placeholder="Drop label (optional)">
      <div id="est" class="muted"></div>
      <button id="req">Request ride</button> <button id="reset" class="alt">Reset pins</button></section>
    <section class="card"><h2>My rides</h2><div id="list"></div></section></div>`;
  const hint = () => {
    $('#hint').textContent = pins.length === 0 ? 'Click the map to set your pickup 📍' : pins.length === 1 ? 'Now click your drop-off 🏁' : 'Ready to book';
    if (pins.length === 2) { const d = km(...pins[0], ...pins[1]); $('#est').textContent = `~${d.toFixed(1)} km · estimated fare ${inr(BASE + PER_KM * d)}`; } else $('#est').textContent = '';
  };
  initMap(ll => {
    if (pins.length >= 2) { pins = []; layer.clearLayers(); }
    pins.push([ll.lat, ll.lng]); pin(ll, pins.length === 1 ? '📍' : '🏁').addTo(layer); hint();
  });
  hint();
  $('#reset').onclick = () => { pins = []; layer.clearLayers(); hint(); };
  $('#req').onclick = async () => {
    if (pins.length < 2) return toast('Pick pickup and drop on the map', true);
    try {
      await api('/api/rides', 'POST', { pickupAddress: $('#pk').value || 'Pickup pin', dropAddress: $('#dp').value || 'Drop pin',
        pickupLat: pins[0][0], pickupLng: pins[0][1], dropLat: pins[1][0], dropLng: pins[1][1] });
      toast('Ride requested! Waiting for a driver…'); pins = []; layer.clearLayers(); hint(); loadRider();
    } catch (e) { toast(e.message, true); }
  };
  loadRider(); poll = setInterval(loadRider, 5000);
}
async function loadRider() {
  if (document.activeElement && document.activeElement.tagName === 'SELECT') return;
  try {
    const [rides, pays] = await Promise.all([api('/api/rides/my'), api('/api/payments/my')]);
    const paid = new Set(pays.map(p => p.rideId));
    $('#list').innerHTML = rides.length ? rides.map(r => rideCard(r, riderActions(r, paid))).join('') : '<p class="muted">No rides yet.</p>';
    const active = rides.find(r => ['ACCEPTED', 'STARTED'].includes(r.status));
    if (active) track(active); else if (trackedId) { trackedId = null; unsub(); }
  } catch (e) { /* ignore transient polling errors */ }
}
function riderActions(r, paid) {
  if (r.status === 'REQUESTED' || r.status === 'ACCEPTED') return btn('cancel', r.id, 'Cancel', 'alt');
  if (r.status === 'COMPLETED') return paid.has(r.id) ? '✅ Paid'
    : `<div class="pay"><select><option>CARD</option><option>UPI</option><option>CASH</option></select>${btn('pay', r.id, 'Pay ' + inr(r.fare))}</div>`;
  return '';
}
function track(r) {
  if (trackedId === r.id) return;
  trackedId = r.id; pins = []; drawRide(r);
  subscribeRide(r.id, p => moveDriver(p.lat, p.lng), s => { toast(`Ride #${s.id} is now ${s.status}`); loadRider(); });
}

/* ---------- driver ---------- */
function driverView() {
  $('#app').innerHTML = `<div class="grid">
    <section class="card"><h2>Active trip</h2><div class="muted" id="hint">Accept a ride to see it on the map</div><div id="map"></div></section>
    <div class="stack"><section class="card"><h2>Available rides</h2><div id="avail"></div></section>
    <section class="card"><h2>My trips</h2><div id="mine"></div></section></div></div>`;
  initMap(); loadDriver(); poll = setInterval(loadDriver, 4000);
}
async function loadDriver() {
  try {
    const [av, mine] = await Promise.all([api('/api/rides/available'), api('/api/rides/my')]);
    $('#avail').innerHTML = av.length ? av.map(r => rideCard(r, btn('accept', r.id, 'Accept'))).join('') : '<p class="muted">No requests right now.</p>';
    $('#mine').innerHTML = mine.length ? mine.map(r => rideCard(r, driverActions(r))).join('') : '<p class="muted">No trips yet.</p>';
    const active = mine.find(r => ['ACCEPTED', 'STARTED'].includes(r.status));
    if (active && activeDrawn !== active.id) { activeDrawn = active.id; drawRide(active); $('#hint').textContent = `Ride #${active.id}: ${active.pickupAddress} → ${active.dropAddress}`; }
    if (!active && activeDrawn) { activeDrawn = null; layer.clearLayers(); clearInterval(sim); $('#hint').textContent = 'Accept a ride to see it on the map'; }
  } catch (e) { /* ignore */ }
}
function driverActions(r) {
  if (r.status === 'ACCEPTED') return btn('start', r.id, 'Start trip') + btn('sim', r.id, '🚗 Drive to pickup', 'alt') + btn('cancel', r.id, 'Cancel', 'alt');
  if (r.status === 'STARTED') return btn('sim', r.id, '🚗 Drive trip', 'alt') + btn('complete', r.id, 'Complete');
  return '';
}
/* Simulates GPS: posts a position every second; ride-service relays it over WebSocket to the rider */
function simulate(r) {
  clearInterval(sim);
  const toPickup = r.status === 'ACCEPTED';
  const from = toPickup ? [r.pickupLat + 0.01, r.pickupLng + 0.01] : [r.pickupLat, r.pickupLng];
  const to = toPickup ? [r.pickupLat, r.pickupLng] : [r.dropLat, r.dropLng];
  const N = 20; let i = 0;
  sim = setInterval(async () => {
    i++;
    const lat = from[0] + (to[0] - from[0]) * i / N, lng = from[1] + (to[1] - from[1]) * i / N;
    try { await api(`/api/rides/${r.id}/location`, 'POST', { lat, lng }); moveDriver(lat, lng); }
    catch (e) { clearInterval(sim); toast(e.message, true); }
    if (i >= N) clearInterval(sim);
  }, 1000);
  toast('Simulating GPS updates…');
}

/* ---------- admin ---------- */
function adminView() {
  $('#app').innerHTML = `<div class="stack"><section class="card"><h2>Users</h2><div id="u"></div></section>
    <section class="card"><h2>Rides</h2><div id="r"></div></section><section class="card"><h2>Payments</h2><div id="p"></div></section></div>`;
  const table = (cols, rows) => `<div style="overflow-x:auto"><table><tr>${cols.map(c => `<th>${c}</th>`).join('')}</tr>${rows.map(r => `<tr>${r.map(c => `<td>${esc(c)}</td>`).join('')}</tr>`).join('')}</table></div>`;
  const load = async () => {
    try {
      const [u, r, p] = await Promise.all([api('/api/users'), api('/api/rides/all'), api('/api/payments')]);
      $('#u').innerHTML = table(['ID', 'Name', 'Email', 'Role'], u.map(x => [x.id, x.name, x.email, x.role]));
      $('#r').innerHTML = table(['ID', 'Rider', 'Driver', 'Status', 'Km', 'Fare'], r.map(x => [x.id, x.riderName, x.driverName || '-', x.status, x.distanceKm, inr(x.fare)]));
      $('#p').innerHTML = table(['ID', 'Ride', 'Amount', 'Method', 'Status', 'Ref'], p.map(x => [x.id, x.rideId, inr(x.amount), x.method, x.status, x.transactionRef]));
    } catch (e) { toast(e.message, true); }
  };
  load(); poll = setInterval(load, 8000);
}

/* ---------- actions ---------- */
document.addEventListener('click', async e => {
  const b = e.target.closest('[data-act]'); if (!b) return;
  const { act, id } = b.dataset;
  try {
    if (act === 'pay') {
      await api('/api/payments', 'POST', { rideId: +id, method: b.parentElement.querySelector('select').value });
      toast('Payment successful 🎉'); loadRider();
    } else if (act === 'sim') {
      simulate(rideCache[id]);
    } else {
      if (act === 'complete' || act === 'cancel') clearInterval(sim);
      await api(`/api/rides/${id}/${act}`, 'POST'); toast('Done');
      user.role === 'DRIVER' ? loadDriver() : loadRider();
    }
  } catch (err) { toast(err.message, true); }
});

start();
