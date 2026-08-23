const API = '/api';

let allPeople = [];
let selectedPersonId = null;

const el = (id) => document.getElementById(id);

async function checkHealth() {
  try {
    const res = await fetch(`${API}/health`);
    const data = await res.json();
    el('db-banner').classList.toggle('hidden', data.status === 'UP');
  } catch {
    el('db-banner').classList.remove('hidden');
  }
}
checkHealth();
setInterval(checkHealth, 15000);

async function loadPeople(query = '') {
  const list = el('people-list');
  list.innerHTML = '<div class="empty-state">Loading people…</div>';
  try {
    const res = await fetch(`${API}/people?q=${encodeURIComponent(query)}`);
    if (!res.ok) throw new Error('request failed');
    allPeople = await res.json();
    renderPeopleList(allPeople);
    populatePathSelectors(allPeople);
  } catch (err) {
    list.innerHTML = '<div class="empty-state">Could not load people. Is the database reachable?</div>';
  }
}

function renderPeopleList(people) {
  const list = el('people-list');
  if (!people.length) {
    list.innerHTML = '<div class="empty-state">No matching people found.</div>';
    return;
  }
  list.innerHTML = '';
  people.forEach((p) => {
    const card = document.createElement('div');
    card.className = 'person-card' + (p.id === selectedPersonId ? ' active' : '');
    card.innerHTML = `
      <div class="info">
        <div class="name">${escapeHtml(p.name)}</div>
        <div class="headline">${escapeHtml(p.headline || '')}${p.company ? ' · ' + escapeHtml(p.company) : ''}</div>
      </div>`;
    card.addEventListener('click', () => selectPerson(p.id));
    list.appendChild(card);
  });
}

async function loadSkillsFilter() {
  try {
    const res = await fetch(`${API}/skills`);
    const skills = await res.json();
    const select = el('skill-filter');
    skills.forEach((s) => {
      const opt = document.createElement('option');
      opt.value = s;
      opt.textContent = s;
      select.appendChild(opt);
    });
  } catch { /* non-fatal */ }
}

el('skill-filter').addEventListener('change', async (e) => {
  const skill = e.target.value;
  if (!skill) { loadPeople(el('search-input').value); return; }
  const list = el('people-list');
  list.innerHTML = '<div class="empty-state">Loading…</div>';
  try {
    const res = await fetch(`${API}/skills/${encodeURIComponent(skill)}/people`);
    const people = await res.json();
    renderPeopleList(people);
  } catch {
    list.innerHTML = '<div class="empty-state">Could not load people for that skill.</div>';
  }
});

let searchTimer;
el('search-input').addEventListener('input', (e) => {
  clearTimeout(searchTimer);
  const value = e.target.value;
  searchTimer = setTimeout(() => loadPeople(value), 250);
});

async function selectPerson(id) {
  selectedPersonId = id;
  renderPeopleList(allPeople);

  el('profile-empty').classList.add('hidden');
  el('profile-content').classList.remove('hidden');
  el('profile-name').textContent = 'Loading…';
  el('profile-headline').textContent = '';
  el('profile-meta').textContent = '';
  el('profile-skills').innerHTML = '';
  el('connections-list').innerHTML = '<div class="empty-state">Loading…</div>';
  el('suggestions-list').innerHTML = '<div class="empty-state">Loading…</div>';

  try {
    const res = await fetch(`${API}/people/${id}`);
    if (!res.ok) throw new Error('not found');
    const person = await res.json();
    renderProfile(person);
  } catch {
    el('profile-name').textContent = 'Could not load this profile';
    return;
  }

  loadConnections(id);
  loadSuggestions(id);
}

function renderProfile(p) {
  el('profile-avatar').textContent = (p.name || '?').charAt(0).toUpperCase();
  el('profile-name').textContent = p.name;
  el('profile-headline').textContent = p.headline || '';
  el('profile-meta').textContent = [p.company, p.location].filter(Boolean).join(' · ');
  el('profile-skills').innerHTML = (p.skills || [])
    .map((s) => `<span class="chip">${escapeHtml(s)}</span>`).join('');
}

async function loadConnections(id) {
  const container = el('connections-list');
  try {
    const res = await fetch(`${API}/people/${id}/connections`);
    const connections = await res.json();
    el('connections-count').textContent = connections.length;
    if (!connections.length) {
      container.innerHTML = '<div class="empty-state">No direct connections yet.</div>';
      return;
    }
    container.innerHTML = '';
    connections.forEach((c) => {
      const card = document.createElement('div');
      card.className = 'person-card';
      card.innerHTML = `
        <div class="info">
          <div class="name">${escapeHtml(c.name)}</div>
          <div class="headline">${escapeHtml(c.headline || '')}</div>
        </div>`;
      card.addEventListener('click', () => selectPerson(c.id));
      container.appendChild(card);
    });
  } catch {
    container.innerHTML = '<div class="empty-state">Could not load connections.</div>';
  }
}

async function loadSuggestions(id) {
  const container = el('suggestions-list');
  try {
    const res = await fetch(`${API}/people/${id}/suggestions`);
    const suggestions = await res.json();
    if (!suggestions.length) {
      container.innerHTML = '<div class="empty-state">No suggestions right now — connect with a few people first.</div>';
      return;
    }
    container.innerHTML = '';
    suggestions.forEach((s) => {
      const card = document.createElement('div');
      card.className = 'person-card';
      card.innerHTML = `
        <div class="info">
          <div class="name">${escapeHtml(s.name)}</div>
          <div class="headline">${escapeHtml(s.headline || '')}</div>
          <div class="suggestion-meta">${s.mutualConnections} mutual · ${s.sharedSkills} shared skill(s)</div>
        </div>
        <button class="tiny-btn">Connect</button>`;
      card.querySelector('.info').addEventListener('click', () => selectPerson(s.id));
      card.querySelector('button').addEventListener('click', async (e) => {
        e.stopPropagation();
        const btn = e.target;
        btn.disabled = true;
        btn.textContent = '…';
        try {
          await fetch(`${API}/connections`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ personId: id, otherPersonId: s.id }),
          });
          btn.textContent = 'Connected ✓';
          loadConnections(id);
        } catch {
          btn.textContent = 'Failed';
        }
      });
      container.appendChild(card);
    });
  } catch {
    container.innerHTML = '<div class="empty-state">Could not load suggestions.</div>';
  }
}

const connectInput = el('connect-search-input');
const connectResults = el('connect-search-results');
let connectSearchTimer;

connectInput.addEventListener('input', (e) => {
  clearTimeout(connectSearchTimer);
  const value = e.target.value.trim();
  if (!value) {
    connectResults.classList.add('hidden');
    connectResults.innerHTML = '';
    return;
  }
  connectSearchTimer = setTimeout(() => searchToConnect(value), 250);
});

document.addEventListener('click', (e) => {
  if (!connectResults.contains(e.target) && e.target !== connectInput) {
    connectResults.classList.add('hidden');
  }
});

async function searchToConnect(query) {
  try {
    const res = await fetch(`${API}/people?q=${encodeURIComponent(query)}`);
    if (!res.ok) throw new Error('request failed');
    const people = (await res.json()).filter((p) => p.id !== selectedPersonId);

    connectResults.classList.remove('hidden');
    if (!people.length) {
      connectResults.innerHTML = '<div class="empty-state">No matching people found.</div>';
      return;
    }
    connectResults.innerHTML = '';
    people.forEach((p) => {
      const row = document.createElement('div');
      row.className = 'connect-result-row';
      row.innerHTML = `
        <div class="info">
          <div class="name">${escapeHtml(p.name)}</div>
          <div class="headline">${escapeHtml(p.headline || '')}${p.company ? ' · ' + escapeHtml(p.company) : ''}</div>
        </div>
        <button class="tiny-btn">Connect</button>`;
      row.querySelector('button').addEventListener('click', async (ev) => {
        ev.stopPropagation();
        const btn = ev.target;
        btn.disabled = true;
        btn.textContent = '…';
        try {
          const connRes = await fetch(`${API}/connections`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ personId: selectedPersonId, otherPersonId: p.id }),
          });
          if (!connRes.ok) throw new Error('connect failed');
          btn.textContent = 'Connected ✓';
          loadConnections(selectedPersonId);
          loadSuggestions(selectedPersonId);
          setTimeout(() => {
            connectResults.classList.add('hidden');
            connectInput.value = '';
          }, 600);
        } catch {
          btn.disabled = false;
          btn.textContent = 'Failed — retry';
        }
      });
      connectResults.appendChild(row);
    });
  } catch {
    connectResults.classList.remove('hidden');
    connectResults.innerHTML = '<div class="empty-state">Could not search people.</div>';
  }
}

// ---------------------------------------------------------------
// Add / delete person
// ---------------------------------------------------------------
const addModal = el('add-person-modal');
const addForm = el('add-person-form');
const addError = el('add-person-error');

function openAddModal() {
  addForm.reset();
  addError.classList.add('hidden');
  addError.textContent = '';
  addModal.classList.remove('hidden');
  el('ap-name').focus();
}

function closeAddModal() {
  addModal.classList.add('hidden');
}

el('add-person-btn').addEventListener('click', openAddModal);
el('close-modal-btn').addEventListener('click', closeAddModal);
addModal.addEventListener('click', (e) => {
  if (e.target === addModal) closeAddModal();
});

addForm.addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = el('ap-name').value.trim();
  if (!name) return;

  const payload = {
    name,
    headline: el('ap-headline').value.trim(),
    company: el('ap-company').value.trim(),
    location: el('ap-location').value.trim(),
    email: el('ap-email').value.trim(),
    skills: el('ap-skills').value.split(',').map((s) => s.trim()).filter(Boolean),
  };

  const submitBtn = addForm.querySelector('button[type="submit"]');
  submitBtn.disabled = true;
  submitBtn.textContent = 'Adding…';
  addError.classList.add('hidden');

  try {
    const res = await fetch(`${API}/people`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
    });
    if (!res.ok) {
      const err = await res.json().catch(() => ({}));
      throw new Error(err.error || 'Could not add this person.');
    }
    const created = await res.json();
    closeAddModal();
    await loadPeople(el('search-input').value);
    selectPerson(created.id);
  } catch (err) {
    addError.textContent = err.message || 'Something went wrong.';
    addError.classList.remove('hidden');
  } finally {
    submitBtn.disabled = false;
    submitBtn.textContent = 'Add person';
  }
});

el('delete-person-btn').addEventListener('click', async () => {
  if (!selectedPersonId) return;
  const name = el('profile-name').textContent;
  if (!confirm(`Delete ${name}? This removes them and all of their connections.`)) return;

  const btn = el('delete-person-btn');
  btn.disabled = true;
  btn.textContent = 'Deleting…';
  try {
    const res = await fetch(`${API}/people/${selectedPersonId}`, { method: 'DELETE' });
    if (!res.ok) throw new Error('delete failed');
    selectedPersonId = null;
    el('profile-content').classList.add('hidden');
    el('profile-empty').classList.remove('hidden');
    await loadPeople(el('search-input').value);
  } catch {
    alert('Could not delete this person. Please try again.');
  } finally {
    btn.disabled = false;
    btn.textContent = 'Delete';
  }
});

function populatePathSelectors(people) {
  const from = el('path-from');
  const to = el('path-to');
  [from, to].forEach((select) => {
    const current = select.value;
    select.innerHTML = '<option value="">Choose a person…</option>' +
      people.map((p) => `<option value="${p.id}">${escapeHtml(p.name)}</option>`).join('');
    if (current) select.value = current;
  });
}

el('path-find-btn').addEventListener('click', async () => {
  const from = el('path-from').value;
  const to = el('path-to').value;
  const result = el('path-result');
  if (!from || !to) {
    result.innerHTML = '<div class="empty-state">Choose two people first.</div>';
    return;
  }
  if (from === to) {
    result.innerHTML = '<div class="empty-state">Choose two different people.</div>';
    return;
  }
  result.innerHTML = '<div class="empty-state">Searching…</div>';
  try {
    const res = await fetch(`${API}/path?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`);
    const data = await res.json();
    if (!data.connected) {
      result.innerHTML = '<div class="empty-state">No connection path found within 6 hops.</div>';
      return;
    }
    result.innerHTML = `
      <p class="muted small">${data.hops} hop(s) apart</p>
      <div class="path-chain">
        ${data.people.map((p, i) => `
          ${i > 0 ? '<span class="path-arrow">→</span>' : ''}
          <span class="path-node">${escapeHtml(p.name)}</span>
        `).join('')}
      </div>`;
  } catch {
    result.innerHTML = '<div class="empty-state">Could not compute a path.</div>';
  }
});

function escapeHtml(str) {
  return String(str).replace(/[&<>"']/g, (c) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
  }[c]));
}

// Boot
loadPeople();
loadSkillsFilter();

