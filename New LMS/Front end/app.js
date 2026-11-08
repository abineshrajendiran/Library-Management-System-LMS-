// =====================================================================
//  Library Management System - frontend logic
//  Talks to the Java server (REST API) with fetch().
//  Every Issue / Return is saved by the server in the MySQL tables.
// =====================================================================

// If you open index.html with VS Code Live Server / double-click, call the Java server directly.
const API_BASE = (location.protocol === 'file:' || location.port === '5500')
  ? 'http://localhost:8080' : '';

let token = sessionStorage.getItem('lms_token');
let user = JSON.parse(sessionStorage.getItem('lms_user') || 'null');
let searchTimer = null;

const $ = (id) => document.getElementById(id);

// ------------------------------------------------------------- helpers
function esc(value) {
  return String(value === null || value === undefined ? '' : value)
    .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
}

async function api(path, method = 'GET', body = null) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers['Authorization'] = 'Bearer ' + token;
  let res;
  try {
    res = await fetch(API_BASE + path, {
      method,
      headers,
      body: body ? JSON.stringify(body) : undefined
    });
  } catch (e) {
    throw new Error('Cannot reach the server. Is the Java server running?');
  }
  let data = {};
  try { data = await res.json(); } catch (e) { /* empty body */ }
  if (res.status === 401 && token) { logout(); throw new Error('Session expired. Please login again.'); }
  if (!res.ok) throw new Error(data.error || ('Request failed (' + res.status + ')'));
  return data;
}

let msgTimer = null;
function showMsg(el, text, ok) {
  el.textContent = text;
  el.className = 'msg show ' + (ok ? 'ok' : 'err');
  if (el.id === 'appMsg') {
    clearTimeout(msgTimer);
    msgTimer = setTimeout(() => { el.className = 'msg'; el.textContent = ''; }, 6000);
  }
}

const isStaff = () => user && user.role === 'staff';

// --------------------------------------------------------------- login
$('loginForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  const role = document.querySelector('input[name="role"]:checked').value;
  try {
    const data = await api('/api/login', 'POST', {
      role,
      username: $('username').value,
      password: $('password').value
    });
    token = data.token;
    user = { id: data.id, name: data.name, role: data.role };
    sessionStorage.setItem('lms_token', token);
    sessionStorage.setItem('lms_user', JSON.stringify(user));
    $('password').value = '';
    $('loginMsg').className = 'msg';
    startApp();
  } catch (err) {
    showMsg($('loginMsg'), err.message, false);
  }
});

$('logoutBtn').addEventListener('click', logout);

function logout() {
  token = null;
  user = null;
  sessionStorage.removeItem('lms_token');
  sessionStorage.removeItem('lms_user');
  $('appView').classList.add('hidden');
  $('loginView').classList.remove('hidden');
}

// ------------------------------------------------------------ app shell
function startApp() {
  $('loginView').classList.add('hidden');
  $('appView').classList.remove('hidden');
  $('whoName').textContent = user.name;
  $('whoRole').textContent = user.role;
  $('recordsTabBtn').textContent = isStaff() ? 'Issue Records' : 'My Books';

  document.querySelectorAll('.staff-only').forEach((el) => {
    el.classList.toggle('hidden', !isStaff());
  });

  switchTab('books');
  if (isStaff()) loadMembersForSelect();
}

document.querySelectorAll('.tab').forEach((btn) => {
  btn.addEventListener('click', () => switchTab(btn.dataset.tab));
});

function switchTab(name) {
  document.querySelectorAll('.tab').forEach((b) => b.classList.toggle('active', b.dataset.tab === name));
  document.querySelectorAll('.tab-panel').forEach((p) => p.classList.add('hidden'));
  $('tab-' + name).classList.remove('hidden');
  if (name === 'books') loadBooks();
  if (name === 'records') loadRecords();
  if (name === 'members') loadMembers();
}

// --------------------------------------------------------------- BOOKS
$('bookSearch').addEventListener('input', () => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(loadBooks, 250);
});

async function loadBooks() {
  try {
    const books = await api('/api/books?q=' + encodeURIComponent($('bookSearch').value));
    $('bookTable').innerHTML = books.length ? books.map((b) => {
      const available = b.available_copies > 0;
      return '<tr>'
        + '<td>' + b.book_id + '</td>'
        + '<td class="wrap"><b>' + esc(b.title) + '</b></td>'
        + '<td class="wrap">' + esc(b.author) + '</td>'
        + '<td>' + esc(b.category) + '</td>'
        + '<td><span class="pill ' + (available ? 'ok' : 'bad') + '">'
        + b.available_copies + ' / ' + b.total_copies + '</span></td>'
        + '<td><button class="btn row issue" data-issue="' + b.book_id + '"'
        + (available ? '' : ' disabled') + '>Issue</button></td>'
        + '</tr>';
    }).join('') : '<tr><td colspan="6" class="muted">No books found.</td></tr>';
  } catch (err) {
    showMsg($('appMsg'), err.message, false);
  }
}

$('bookTable').addEventListener('click', async (e) => {
  const btn = e.target.closest('[data-issue]');
  if (!btn) return;
  const body = { book_id: btn.dataset.issue };
  if (isStaff()) {
    body.member_id = $('issueMember').value;
    if (!body.member_id) { showMsg($('appMsg'), 'Choose a student first.', false); return; }
  }
  btn.disabled = true;
  try {
    const res = await api('/api/issue', 'POST', body);
    showMsg($('appMsg'), res.message + '  (Record #' + res.issue_id + ' saved in database)', true);
  } catch (err) {
    showMsg($('appMsg'), err.message, false);
  }
  loadBooks();
});

$('addBookForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    const res = await api('/api/books', 'POST', {
      title: $('nbTitle').value,
      author: $('nbAuthor').value,
      category: $('nbCategory').value,
      copies: $('nbCopies').value
    });
    showMsg($('appMsg'), res.message, true);
    e.target.reset();
    $('nbCopies').value = 1;
    loadBooks();
  } catch (err) {
    showMsg($('appMsg'), err.message, false);
  }
});

// ------------------------------------------------------------- RECORDS
$('statusFilter').addEventListener('change', loadRecords);

async function loadRecords() {
  try {
    const rows = await api('/api/issues?status=' + $('statusFilter').value);
    $('recordTable').innerHTML = rows.length ? rows.map((r) => {
      const issued = r.status === 'ISSUED';
      const overdue = issued && Number(r.fine) > 0;
      const fine = Number(r.fine);
      return '<tr>'
        + '<td>' + r.issue_id + '</td>'
        + '<td class="wrap"><b>' + esc(r.title) + '</b><br><span class="muted">' + esc(r.author) + '</span></td>'
        + (isStaff() ? '<td>' + esc(r.member_name) + '</td>' : '')
        + '<td>' + esc(r.issue_date) + '</td>'
        + '<td>' + esc(r.due_date) + '</td>'
        + '<td>' + (r.return_date ? esc(r.return_date) : '-') + '</td>'
        + '<td>' + r.days_kept + '</td>'
        + '<td>' + (fine > 0 ? '<span class="fine">' + fine + '</span>' : '0') + '</td>'
        + '<td><span class="pill ' + (issued ? (overdue ? 'bad' : 'warn') : 'ok') + '">'
        + (issued ? (overdue ? 'OVERDUE' : 'ISSUED') : 'RETURNED') + '</span></td>'
        + '<td>' + (issued ? '<button class="btn row return" data-return="' + r.issue_id + '">Return</button>' : '') + '</td>'
        + '</tr>';
    }).join('') : '<tr><td colspan="10" class="muted">No records yet.</td></tr>';
  } catch (err) {
    showMsg($('appMsg'), err.message, false);
  }
}

$('recordTable').addEventListener('click', async (e) => {
  const btn = e.target.closest('[data-return]');
  if (!btn) return;
  const body = { issue_id: btn.dataset.return };
  if (isStaff() && $('daysKept').value !== '') body.days_kept = $('daysKept').value;
  btn.disabled = true;
  try {
    const res = await api('/api/return', 'POST', body);
    showMsg($('appMsg'), res.message + '  (Record #' + res.issue_id + ' updated in database)', true);
  } catch (err) {
    showMsg($('appMsg'), err.message, false);
  }
  loadRecords();
});

// ------------------------------------------------------------- MEMBERS
async function loadMembers() {
  try {
    const rows = await api('/api/members');
    $('memberTable').innerHTML = rows.map((m) =>
      '<tr><td>' + m.member_id + '</td><td>' + esc(m.full_name) + '</td><td>' + esc(m.username)
      + '</td><td>' + esc(m.email) + '</td><td>' + esc(m.department)
      + '</td><td>' + m.books_held + '</td></tr>').join('');
  } catch (err) {
    showMsg($('appMsg'), err.message, false);
  }
}

async function loadMembersForSelect() {
  try {
    const rows = await api('/api/members');
    $('issueMember').innerHTML = rows.map((m) =>
      '<option value="' + m.member_id + '">' + esc(m.full_name) + ' (' + esc(m.username) + ')</option>').join('');
  } catch (err) {
    showMsg($('appMsg'), err.message, false);
  }
}

$('addMemberForm').addEventListener('submit', async (e) => {
  e.preventDefault();
  try {
    const res = await api('/api/members', 'POST', {
      full_name: $('nmName').value,
      username: $('nmUser').value,
      password: $('nmPass').value,
      email: $('nmEmail').value,
      department: $('nmDept').value
    });
    showMsg($('appMsg'), res.message, true);
    e.target.reset();
    loadMembers();
    loadMembersForSelect();
  } catch (err) {
    showMsg($('appMsg'), err.message, false);
  }
});

// ------------------------------------------------- restore login on refresh
if (token && user) {
  api('/api/me').then(startApp).catch(logout);
}
