import { initializeApp } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-app.js";
import {
  getAuth,
  onAuthStateChanged,
  signInWithEmailAndPassword,
  signOut
} from "https://www.gstatic.com/firebasejs/10.14.1/firebase-auth.js";
import { getFirestore, doc, getDoc } from "https://www.gstatic.com/firebasejs/10.14.1/firebase-firestore.js";
import { firebaseConfig } from "./firebase-config.js";
import { REWARDS } from "./rewards.js";

const firebaseApp = initializeApp(firebaseConfig);
const auth = getAuth(firebaseApp);
const db = getFirestore(firebaseApp);

const LEGISLATION = "Legislación";
const COUNT_OPTIONS = [10, 20, 30, 40, 50];

const BADGES = [
  { id: "pos_3", type: "pos", n: 3, name: "Racha 3 aciertos", desc: "3 aciertos seguidos en un simulacro", img: "streak_positive_3_img.png", emoji: "🔥" },
  { id: "pos_5", type: "pos", n: 5, name: "Racha 5 aciertos", desc: "5 aciertos seguidos en un simulacro", img: "streak_positive_5_img.png", emoji: "🔥" },
  { id: "pos_10", type: "pos", n: 10, name: "Racha 10 aciertos", desc: "10 aciertos seguidos en un simulacro", img: "streak_positive_10_img.png", emoji: "🔥" },
  { id: "pos_20", type: "pos", n: 20, name: "Racha 20 aciertos", desc: "20 aciertos seguidos en un simulacro", img: "streak_positive_20_img.png", emoji: "🔥" },
  { id: "pos_30", type: "pos", n: 30, name: "Racha 30 aciertos", desc: "30 aciertos seguidos en un simulacro", img: "streak_positive_30_img.png", emoji: "🔥" },
  { id: "neg_3", type: "neg", n: 3, name: "Racha 3 fallos", desc: "3 fallos seguidos en un simulacro", img: "streak_negative_3_img.png", emoji: "🧊" },
  { id: "neg_5", type: "neg", n: 5, name: "Racha 5 fallos", desc: "5 fallos seguidos en un simulacro", img: "streak_negative_5_img.png", emoji: "🧊" },
  { id: "neg_10", type: "neg", n: 10, name: "Racha 10 fallos", desc: "10 fallos seguidos en un simulacro", img: "streak_negative_10_img.png", emoji: "🧊" },
  { id: "daily_1", type: "days", n: 1, name: "Un día seguido", desc: "Completa tu primer simulacro", img: "badge_daily_1_img.png", emoji: "📅" },
  { id: "daily_3", type: "days", n: 3, name: "3 días seguidos", desc: "Simulacros 3 días consecutivos", img: null, emoji: "📅" },
  { id: "daily_5", type: "days", n: 5, name: "5 días seguidos", desc: "Simulacros 5 días consecutivos", img: null, emoji: "📅" },
  { id: "daily_7", type: "days", n: 7, name: "7 días seguidos", desc: "Simulacros 7 días consecutivos", img: null, emoji: "🏆" }
];

let user = null;
let bank = null;
let state = null;
let quiz = null;

const root = document.getElementById("app");

function h(tag, attrs = {}, ...children) {
  const el = document.createElement(tag);
  for (const [key, value] of Object.entries(attrs || {})) {
    if (value == null || value === false) continue;
    if (key === "class") el.className = value;
    else if (key.startsWith("on")) el.addEventListener(key.slice(2), value);
    else if (value === true) el.setAttribute(key, "");
    else el.setAttribute(key, value);
  }
  for (const child of children.flat(Infinity)) {
    if (child == null || child === false) continue;
    el.append(child.nodeType ? child : document.createTextNode(String(child)));
  }
  return el;
}

function render(...nodes) {
  root.replaceChildren(...nodes);
  window.scrollTo(0, 0);
}

function renderKeep(...nodes) {
  root.replaceChildren(...nodes);
}

function panel(title, backFn, ...children) {
  const top = h("div", { class: "topbar" },
    backFn ? h("button", { class: "btn small", type: "button", onclick: backFn }, "← Volver") : h("span"),
    h("span", { class: "muted" }, `Puntos: ${state ? state.points : 0}`)
  );
  return h("section", { class: "panel" }, top, h("h1", {}, title), ...children);
}

// ---------- estado local por usuario ----------

function defaultState() {
  return {
    points: 0,
    attempts: [],
    badges: {},
    badgeCounts: {},
    purchases: [],
    settings: { count: 10, theory: null, legislation: null }
  };
}

const storageKey = (uid) => `nursia:v1:${uid}`;

function loadState(uid) {
  try {
    const raw = localStorage.getItem(storageKey(uid));
    if (raw) {
      const saved = JSON.parse(raw);
      const base = defaultState();
      return { ...base, ...saved, settings: { ...base.settings, ...(saved.settings || {}) } };
    }
  } catch (e) {
    console.warn("No se pudo leer el estado local", e);
  }
  return defaultState();
}

function saveState() {
  try {
    localStorage.setItem(storageKey(user.uid), JSON.stringify(state));
  } catch (e) {
    console.warn("No se pudo guardar el estado local", e);
  }
}

// ---------- utilidades ----------

const isLegislation = (q) => (q.s || "").trim() === LEGISLATION;

function dayKey(date) {
  const d = new Date(date);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
}

function daysStreak(attempts, allowYesterday) {
  const days = new Set(attempts.map((a) => dayKey(a.ts)));
  const cursor = new Date();
  cursor.setHours(12, 0, 0, 0);
  if (!days.has(dayKey(cursor))) {
    if (!allowYesterday) return 0;
    cursor.setDate(cursor.getDate() - 1);
    if (!days.has(dayKey(cursor))) return 0;
  }
  let n = 0;
  while (days.has(dayKey(cursor))) {
    n++;
    cursor.setDate(cursor.getDate() - 1);
  }
  return n;
}

function shuffle(array) {
  const a = array.slice();
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function communitiesFor(legislation) {
  const set = new Set(bank.filter((q) => isLegislation(q) === legislation).map((q) => q.c));
  return [...set].sort((a, b) => a.localeCompare(b, "es"));
}

function buildPool(mode) {
  const { theory, legislation } = state.settings;
  return bank.filter((q) => {
    const leg = isLegislation(q);
    if (mode === "teoria" && leg) return false;
    if (mode === "legislacion" && !leg) return false;
    const selected = leg ? legislation : theory;
    return selected == null || selected.includes(q.c);
  });
}

const formatDate = (ts) => new Date(ts).toLocaleDateString("es-ES");
const formatNota = (n) => n.toFixed(2).replace(".", ",");

function formatMs(ms) {
  const s = Math.round(ms / 1000);
  return `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
}

// ---------- acceso ----------

function showLoading(text) {
  render(h("section", { class: "panel center" }, h("p", {}, text)));
}

function showLogin(message) {
  const email = h("input", { type: "email", autocomplete: "username", required: true, placeholder: "correo@ejemplo.com" });
  const password = h("input", { type: "password", autocomplete: "current-password", required: true, placeholder: "Contraseña" });
  const error = h("div", { class: "error", role: "alert" }, message || "");
  const button = h("button", { class: "btn primary", type: "submit" }, "Entrar");

  const form = h("form", {
    onsubmit: async (event) => {
      event.preventDefault();
      button.disabled = true;
      error.textContent = "";
      try {
        await signInWithEmailAndPassword(auth, email.value.trim(), password.value);
      } catch (e) {
        error.textContent = authErrorMessage(e);
        button.disabled = false;
      }
    }
  },
    h("label", { class: "field" }, "Correo", email),
    h("label", { class: "field" }, "Contraseña", password),
    button,
    error
  );

  render(h("section", { class: "panel" },
    h("h1", {}, "Nursia"),
    h("p", { class: "muted" }, "Acceso solo para usuarios autorizados."),
    form
  ));
}

function authErrorMessage(e) {
  switch (e && e.code) {
    case "auth/invalid-credential":
    case "auth/wrong-password":
    case "auth/user-not-found":
    case "auth/invalid-email":
      return "Correo o contraseña incorrectos.";
    case "auth/too-many-requests":
      return "Demasiados intentos. Espera unos minutos e inténtalo de nuevo.";
    case "auth/network-request-failed":
      return "Sin conexión. Revisa tu red.";
    default:
      return "No se pudo iniciar sesión.";
  }
}

function showBlocked(title, text) {
  render(h("section", { class: "panel" },
    h("h1", {}, title),
    h("p", {}, text),
    h("button", { class: "btn", type: "button", onclick: () => signOut(auth) }, "Cerrar sesión")
  ));
}

async function loadBank() {
  const snap = await getDoc(doc(db, "banco", "preguntas"));
  if (!snap.exists()) throw new Error("banco-no-existe");
  const data = JSON.parse(snap.data().json);
  if (!Array.isArray(data) || data.length === 0) throw new Error("banco-vacio");
  return data;
}

onAuthStateChanged(auth, async (u) => {
  quiz = null;
  if (!u) {
    user = null;
    bank = null;
    state = null;
    showLogin();
    return;
  }
  user = u;
  showLoading("Cargando preguntas…");
  try {
    bank = await loadBank();
    state = loadState(u.uid);
    showHome();
  } catch (e) {
    console.error(e);
    if (e && e.code === "permission-denied") {
      showBlocked("Sin acceso", "Tu cuenta no tiene acceso a esta prueba. Contacta con quien te la facilitó.");
    } else {
      const detail = e && (e.code || e.message) ? ` (${e.code || e.message})` : "";
      showBlocked("No se pudo cargar", `No se han podido cargar las preguntas${detail}. Inténtalo de nuevo más tarde.`);
    }
  }
});

// ---------- inicio ----------

function showHome() {
  const streak = daysStreak(state.attempts, true);
  const mode = h("select", {},
    h("option", { value: "todas" }, "Teoría y legislación"),
    h("option", { value: "teoria" }, "Solo teoría"),
    h("option", { value: "legislacion" }, "Solo legislación")
  );
  const info = h("p", { class: "muted" });
  const start = h("button", { class: "btn primary", type: "button" }, "Empezar simulacro");

  const refresh = () => {
    const available = buildPool(mode.value).length;
    const n = Math.min(state.settings.count, available);
    info.textContent = available === 0
      ? "No hay preguntas con los filtros actuales. Revisa los ajustes."
      : `${n} preguntas (${available} disponibles con tus filtros).`;
    start.disabled = available === 0;
  };
  mode.addEventListener("change", refresh);
  start.addEventListener("click", () => startQuiz(mode.value));
  refresh();

  render(h("section", { class: "panel" },
    h("div", { class: "topbar" },
      h("span", { class: "muted" }, user.email),
      h("button", { class: "btn small", type: "button", onclick: () => signOut(auth) }, "Salir")
    ),
    h("h1", {}, "Nursia"),
    h("div", { class: "stats-grid" },
      h("div", { class: "stat" }, h("b", {}, state.points), h("span", {}, "Puntos")),
      h("div", { class: "stat" }, h("b", {}, streak), h("span", {}, streak === 1 ? "Día seguido" : "Días seguidos"))
    ),
    h("label", { class: "field" }, "Tipo de preguntas", mode),
    info,
    start,
    h("div", { class: "row" },
      h("button", { class: "btn", type: "button", onclick: showStats }, "Estadísticas"),
      h("button", { class: "btn", type: "button", onclick: showBadges }, "Insignias")
    ),
    h("div", { class: "row" },
      h("button", { class: "btn", type: "button", onclick: showRewards }, "Premios"),
      h("button", { class: "btn", type: "button", onclick: showSettings }, "Ajustes")
    )
  ));
}

// ---------- simulacro ----------

function streaksOf(qs, answers) {
  let pos = 0, neg = 0, bestPos = 0, bestNeg = 0;
  qs.forEach((q, idx) => {
    const a = answers[idx];
    if (a == null) {
      return;
    } else if (a === q.a) {
      pos++; neg = 0;
    } else {
      neg++; pos = 0;
    }
    bestPos = Math.max(bestPos, pos);
    bestNeg = Math.max(bestNeg, neg);
  });
  return { bestPos, bestNeg };
}

function runLengthAt(idx) {
  const sameKind = (i) => (quiz.answers[i] === quiz.qs[i].a) === (quiz.answers[idx] === quiz.qs[idx].a);
  let len = 1;
  for (let i = idx - 1; i >= 0; i--) {
    if (quiz.answers[i] == null) continue;
    if (!sameKind(i)) break;
    len++;
  }
  for (let i = idx + 1; i < quiz.qs.length; i++) {
    if (quiz.answers[i] == null) continue;
    if (!sameKind(i)) break;
    len++;
  }
  return len;
}

function unlockStreakBadges(idx) {
  const isOk = quiz.answers[idx] === quiz.qs[idx].a;
  const run = runLengthAt(idx);
  const earned = [];
  for (const badge of BADGES) {
    if (badge.type === "days" || badge.type !== (isOk ? "pos" : "neg")) continue;
    if (run === badge.n) {
      if (!state.badges[badge.id]) state.badges[badge.id] = Date.now();
      state.badgeCounts[badge.id] = (state.badgeCounts[badge.id] || 0) + 1;
      earned.push(badge);
    }
  }
  if (earned.length) {
    saveState();
    quiz.unlocked.push(...earned);
  }
  return earned;
}

function badgeTitle(badge) {
  const times = state.badgeCounts[badge.id] || 1;
  return times > 1
    ? `¡Insignia conseguida otra vez: ${badge.name}! (${times} veces)`
    : `¡Nueva insignia: ${badge.name}!`;
}

function startQuiz(mode) {
  const pool = shuffle(buildPool(mode));
  const qs = pool.slice(0, Math.min(state.settings.count, pool.length));
  if (qs.length === 0) return;
  quiz = { qs, i: 0, answers: new Array(qs.length).fill(null), start: Date.now(), unlocked: [], toast: [] };
  showQuestion();
}

function buildAiPrompt(q) {
  const lines = [
    "Soy enfermero/a preparando las oposiciones OPE SESPA.",
    "Tengo la siguiente pregunta de examen y quiero que me expliques POR QUÉ la respuesta correcta es la que es, y por qué las demás opciones son incorrectas.",
    "",
    `PREGUNTA (${q.c}, ${q.y}, nº ${q.n}):`,
    q.q,
    "",
    "OPCIONES:",
    ...q.o.map((text, idx) => `${"ABCD"[idx]}) ${text}${idx === q.a ? " <- RESPUESTA CORRECTA" : ""}`),
    "",
    `Por favor, explica el razonamiento clínico o teórico que justifica que la opción correcta sea la ${"ABCD"[q.a]}, y desmonta brevemente las otras opciones.`
  ];
  return lines.join("\n");
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
    return true;
  } catch (e) {
    const area = h("textarea", { style: "position:fixed;opacity:0" }, text);
    document.body.append(area);
    area.select();
    let ok = false;
    try { ok = document.execCommand("copy"); } catch (err) { ok = false; }
    area.remove();
    return ok;
  }
}

function showQuestion(keepScroll = false) {
  const { qs, i, answers } = quiz;
  const q = qs[i];
  const last = i === qs.length - 1;
  const chosen = answers[i];
  const answered = chosen != null;

  const options = q.o.map((text, idx) => {
    let cls = "option";
    if (answered) {
      cls += " static";
      if (idx === q.a) cls += " correct";
      else if (idx === chosen) cls += " wrong";
    }
    return h("button", {
      class: cls,
      type: "button",
      onclick: () => {
        if (quiz.answers[i] != null) return;
        quiz.answers[i] = idx;
        quiz.toast = unlockStreakBadges(i);
        showQuestion(true);
      }
    }, h("span", { class: "letter" }, "ABCD"[idx]), h("span", {}, text));
  });

  let feedback = null;
  if (answered) {
    const aiButton = h("button", { class: "btn", type: "button" }, "🤖 Preguntar a IA");
    aiButton.addEventListener("click", async () => {
      const ok = await copyText(buildAiPrompt(q));
      aiButton.textContent = ok ? "Copiado. Pégalo en ChatGPT, Gemini…" : "No se pudo copiar";
      setTimeout(() => { aiButton.textContent = "🤖 Preguntar a IA"; }, 3500);
    });
    feedback = h("div", { role: "status" },
      h("p", { class: chosen === q.a ? "mark-ok" : "mark-ko" },
        chosen === q.a ? "✔ ¡Correcto!" : `✘ Incorrecto. La respuesta correcta es la ${"ABCD"[q.a]}.`),
      aiButton
    );
  }

  const newBadgeBanners = quiz.toast.map((b) =>
    h("div", { class: "badge-new", role: "status" },
      badgeImage(b, true),
      h("div", {}, h("b", {}, badgeTitle(b)), h("div", { class: "muted" }, b.desc)))
  );
  quiz.toast = [];

  (keepScroll ? renderKeep : render)(h("section", { class: "panel" },
    h("div", { class: "topbar" },
      h("span", { class: "muted" }, `Pregunta ${i + 1} de ${qs.length}`),
      h("button", {
        class: "btn small",
        type: "button",
        onclick: () => { if (confirm("¿Abandonar el simulacro? Se perderá el progreso.")) { quiz = null; showHome(); } }
      }, "Abandonar")
    ),
    h("div", { class: "progress" }, h("div", { style: `width:${((i + 1) / qs.length) * 100}%` })),
    h("div", { class: "meta" }, `${q.c} · ${q.y}${isLegislation(q) ? " · Legislación" : ""}`),
    h("div", { class: "question" }, q.q),
    options,
    feedback,
    newBadgeBanners,
    h("div", { class: "row" },
      h("button", { class: "btn", type: "button", disabled: i === 0, onclick: () => { quiz.i--; showQuestion(); } }, "Anterior"),
      last
        ? h("button", { class: "btn primary", type: "button", onclick: finishQuiz }, "Finalizar")
        : h("button", { class: "btn primary", type: "button", onclick: () => { quiz.i++; showQuestion(); } }, "Siguiente")
    )
  ));
}

function finishQuiz() {
  const unanswered = quiz.answers.filter((a) => a == null).length;
  if (unanswered > 0 && !confirm(`Tienes ${unanswered} pregunta(s) en blanco. ¿Finalizar igualmente?`)) return;

  const { qs, answers } = quiz;
  let ok = 0, ko = 0, blank = 0;
  qs.forEach((q, idx) => {
    const a = answers[idx];
    if (a == null) blank++;
    else if (a === q.a) ok++;
    else ko++;
  });
  const { bestPos, bestNeg } = streaksOf(qs, answers);

  const n = qs.length;
  const nota = ((ok - ko / 3) * 10) / n;
  const ms = Date.now() - quiz.start;

  state.points += ok;
  state.attempts.push({ ts: quiz.start, ms, n, ok, ko, blank, nota, bestPos, bestNeg });
  const newBadges = [...quiz.unlocked, ...evaluateBadges()];
  saveState();

  const result = { qs, answers, ok, ko, blank, nota, ms, newBadges, earned: ok };
  quiz = null;
  showResult(result);
}

function evaluateBadges() {
  const bestPos = Math.max(0, ...state.attempts.map((a) => a.bestPos || 0));
  const bestNeg = Math.max(0, ...state.attempts.map((a) => a.bestNeg || 0));
  const streak = daysStreak(state.attempts, false);
  const unlocked = [];
  for (const badge of BADGES) {
    if (state.badges[badge.id]) continue;
    const value = badge.type === "pos" ? bestPos : badge.type === "neg" ? bestNeg : streak;
    if (value >= badge.n) {
      state.badges[badge.id] = Date.now();
      unlocked.push(badge);
    }
  }
  return unlocked;
}

function badgeImage(badge, unlocked) {
  if (!unlocked) {
    return h("img", { src: "img/badge_locked_default.jpg", alt: "Insignia bloqueada" });
  }
  if (badge.img) {
    return h("img", { src: `img/${badge.img}`, alt: badge.name });
  }
  return h("div", { class: "emoji", "aria-hidden": "true" }, badge.emoji);
}

function showResult(r) {
  const review = r.qs.map((q, idx) => {
    const a = r.answers[idx];
    const mark = a == null ? ["—", "mark-blank"] : a === q.a ? ["✔", "mark-ok"] : ["✘", "mark-ko"];
    return h("details", { class: "review" },
      h("summary", {}, h("span", { class: mark[1] }, mark[0]), ` ${idx + 1}. ${q.q}`),
      q.o.map((text, oi) =>
        h("div", {
          class: "option static" + (oi === q.a ? " correct" : oi === a ? " wrong" : "")
        }, h("span", { class: "letter" }, "ABCD"[oi]), h("span", {}, text))
      )
    );
  });

  render(panel("Resultado", showHome,
    h("div", { class: "stats-grid" },
      h("div", { class: "stat" }, h("b", {}, `${r.ok}/${r.qs.length}`), h("span", {}, "Aciertos")),
      h("div", { class: "stat" }, h("b", {}, formatNota(r.nota)), h("span", {}, "Nota sobre 10")),
      h("div", { class: "stat" }, h("b", {}, `${r.ko} / ${r.blank}`), h("span", {}, "Fallos / En blanco")),
      h("div", { class: "stat" }, h("b", {}, formatMs(r.ms)), h("span", {}, "Tiempo"))
    ),
    h("p", {}, `Has ganado ${r.earned} puntos. Total: ${state.points}.`),
    r.newBadges.map((b) =>
      h("div", { class: "badge-new" }, badgeImage(b, true), h("div", {}, h("b", {}, badgeTitle(b)), h("div", { class: "muted" }, b.desc)))
    ),
    h("button", { class: "btn primary", type: "button", onclick: showHome }, "Volver al inicio"),
    h("h2", {}, "Corrección"),
    review
  ));
}

// ---------- estadísticas ----------

function buildCalendar(attempts, view) {
  const byDay = new Map();
  for (const a of attempts) {
    const k = dayKey(a.ts);
    const e = byDay.get(k) || { n: 0, sum: 0 };
    e.n++;
    e.sum += a.nota;
    byDay.set(k, e);
  }

  const now = new Date();
  const isCurrent = view.y === now.getFullYear() && view.m === now.getMonth();
  const todayKey = dayKey(now);
  const first = new Date(view.y, view.m, 1);
  const offset = (first.getDay() + 6) % 7;
  const daysInMonth = new Date(view.y, view.m + 1, 0).getDate();
  const goTo = (delta) => {
    const d = new Date(view.y, view.m + delta, 1);
    showStats({ y: d.getFullYear(), m: d.getMonth(), sel: null });
  };

  const cells = [];
  for (let i = 0; i < offset; i++) cells.push(h("span", { class: "cal-cell empty" }));
  for (let day = 1; day <= daysInMonth; day++) {
    const key = dayKey(new Date(view.y, view.m, day));
    const e = byDay.get(key);
    const avg = e ? e.sum / e.n : null;
    const hue = avg == null ? 0 : Math.round(Math.min(Math.max(avg / 10, 0), 1) * 120);
    const cls = "cal-cell" + (e ? " has" : "") + (key === todayKey ? " today" : "") + (view.sel === key ? " sel" : "");
    cells.push(h("button", {
      class: cls,
      type: "button",
      style: e ? `background: hsl(${hue}, 70%, 45%); color: #fff` : null,
      title: e ? `${e.n} simulacro(s), nota media ${formatNota(avg)}` : "Sin simulacros",
      onclick: () => showStats({ y: view.y, m: view.m, sel: e ? key : null })
    }, String(day)));
  }

  const sel = view.sel ? byDay.get(view.sel) : null;
  const monthName = first.toLocaleDateString("es-ES", { month: "long", year: "numeric" });

  return [
    h("h2", {}, "Calendario"),
    h("div", { class: "cal-head" },
      h("button", { class: "btn small", type: "button", onclick: () => goTo(-1) }, "‹"),
      h("b", { class: "cal-title" }, monthName),
      h("button", { class: "btn small", type: "button", disabled: isCurrent, onclick: () => goTo(1) }, "›")
    ),
    h("div", { class: "cal-grid" },
      ["L", "M", "X", "J", "V", "S", "D"].map((d) => h("span", { class: "cal-dow" }, d)),
      cells
    ),
    h("div", { class: "cal-legend muted" }, "Nota media: 0", h("span", { class: "cal-bar" }), "10"),
    sel ? h("p", {}, `${formatDate(new Date(view.sel + "T12:00:00").getTime())}: ${sel.n} simulacro(s), nota media ${formatNota(sel.sum / sel.n)}`) : null
  ];
}

function showStats(arg) {
  const hasView = Boolean(arg) && typeof arg.y === "number" && typeof arg.m === "number";
  const now = new Date();
  const view = hasView ? arg : { y: now.getFullYear(), m: now.getMonth(), sel: null };
  const attempts = state.attempts;
  const total = attempts.length;
  const questions = attempts.reduce((s, a) => s + a.n, 0);
  const oks = attempts.reduce((s, a) => s + a.ok, 0);
  const avg = total ? attempts.reduce((s, a) => s + a.nota, 0) / total : 0;
  const best = total ? Math.max(...attempts.map((a) => a.nota)) : 0;
  const pct = questions ? Math.round((oks / questions) * 100) : 0;

  const rows = attempts.slice(-10).reverse().map((a) =>
    h("tr", {}, h("td", {}, formatDate(a.ts)), h("td", {}, `${a.ok}/${a.n}`), h("td", {}, formatNota(a.nota)), h("td", {}, formatMs(a.ms)))
  );

  (hasView ? renderKeep : render)(panel("Estadísticas", showHome,
    total === 0
      ? h("p", { class: "muted" }, "Todavía no has completado ningún simulacro.")
      : [
        h("div", { class: "stats-grid" },
          h("div", { class: "stat" }, h("b", {}, total), h("span", {}, "Simulacros")),
          h("div", { class: "stat" }, h("b", {}, `${pct}%`), h("span", {}, "Acierto global")),
          h("div", { class: "stat" }, h("b", {}, formatNota(avg)), h("span", {}, "Nota media")),
          h("div", { class: "stat" }, h("b", {}, formatNota(best)), h("span", {}, "Mejor nota")),
          h("div", { class: "stat" }, h("b", {}, Math.max(...attempts.map((a) => a.bestPos || 0))), h("span", {}, "Mejor racha de aciertos")),
          h("div", { class: "stat" }, h("b", {}, daysStreak(attempts, true)), h("span", {}, "Días seguidos"))
        ),
        buildCalendar(attempts, view),
        h("h2", {}, "Últimos simulacros"),
        h("table", {},
          h("thead", {}, h("tr", {}, h("th", {}, "Fecha"), h("th", {}, "Aciertos"), h("th", {}, "Nota"), h("th", {}, "Tiempo"))),
          h("tbody", {}, rows)
        )
      ]
  ));
}

// ---------- insignias ----------

function showBadges() {
  const cells = BADGES.map((b) => {
    const unlocked = Boolean(state.badges[b.id]);
    return h("div", { class: "badge" + (unlocked ? "" : " locked") },
      badgeImage(b, unlocked),
      h("b", {}, unlocked ? b.name : "???"),
      h("small", {}, b.desc),
      unlocked ? h("small", {}, `Conseguida el ${formatDate(state.badges[b.id])}`) : null,
      unlocked && (state.badgeCounts[b.id] || 0) > 1 ? h("small", {}, `${state.badgeCounts[b.id]} veces`) : null
    );
  });
  const count = BADGES.filter((b) => state.badges[b.id]).length;
  render(panel("Insignias", showHome,
    h("p", { class: "muted" }, `${count} de ${BADGES.length} conseguidas`),
    h("div", { class: "badge-grid" }, cells)
  ));
}

// ---------- premios ----------

function rewardImg(id) {
  const r = REWARDS.find((x) => x.id === id);
  return r ? r.img : null;
}

function showRewards() {
  const catalog = REWARDS.map((r) =>
    h("div", { class: "reward" },
      r.img ? h("img", { class: "reward-img", src: r.img, alt: r.name, loading: "lazy" }) : null,
      h("div", { class: "reward-body" }, h("b", {}, r.name), h("div", { class: "muted" }, r.description), h("div", { class: "muted" }, `${r.cost} puntos`)),
      h("button", {
        class: "btn primary",
        type: "button",
        disabled: state.points < r.cost,
        onclick: () => {
          if (state.points < r.cost) return;
          state.points -= r.cost;
          state.purchases.push({ id: r.id, name: r.name, ts: Date.now(), used: false });
          saveState();
          showRewards();
        }
      }, "Comprar")
    )
  );

  const bag = state.purchases
    .map((p, idx) => ({ p, idx }))
    .reverse()
    .map(({ p, idx }) =>
      h("div", { class: "reward" + (p.used ? " used" : "") },
        rewardImg(p.id) ? h("img", { class: "reward-img", src: rewardImg(p.id), alt: p.name, loading: "lazy" }) : null,
        h("div", { class: "reward-body" }, h("b", {}, p.name), h("div", { class: "muted" }, `Comprado el ${formatDate(p.ts)}${p.used ? " · usado" : ""}`)),
        p.used ? null : h("button", {
          class: "btn",
          type: "button",
          onclick: () => { state.purchases[idx].used = true; saveState(); showRewards(); }
        }, "Marcar usado")
      )
    );

  render(panel("Premios", showHome,
    h("p", {}, `Tienes ${state.points} puntos. Ganas un punto por cada acierto.`),
    catalog,
    h("h2", {}, "Mi bolsa"),
    bag.length ? bag : h("p", { class: "muted" }, "Aún no has comprado ningún premio.")
  ));
}

// ---------- ajustes ----------

function communityPicker(title, legislation, initial) {
  const all = communitiesFor(legislation);
  const selected = new Set(initial == null ? all : initial.filter((c) => all.includes(c)));
  const total = bank.filter((q) => isLegislation(q) === legislation);
  const summary = h("span", { class: "muted" });
  const boxes = [];

  const update = () => {
    const n = total.filter((q) => selected.has(q.c)).length;
    summary.textContent = `${selected.size}/${all.length} comunidades · ${n} preguntas`;
  };

  const checks = all.map((c) => {
    const input = h("input", { type: "checkbox", checked: selected.has(c) });
    input.addEventListener("change", () => {
      if (input.checked) selected.add(c); else selected.delete(c);
      update();
    });
    boxes.push({ c, input });
    return h("label", {}, input, c);
  });

  const setAll = (value) => {
    boxes.forEach(({ c, input }) => {
      input.checked = value;
      if (value) selected.add(c); else selected.delete(c);
    });
    update();
  };

  update();
  return {
    el: h("div", {},
      h("h2", {}, title),
      h("div", { class: "row" },
        h("button", { class: "btn small", type: "button", onclick: () => setAll(true) }, "Todas"),
        h("button", { class: "btn small", type: "button", onclick: () => setAll(false) }, "Ninguna"),
        summary
      ),
      h("div", { class: "checks" }, checks)
    ),
    get: () => (selected.size === all.length ? null : [...selected])
  };
}

function showSettings() {
  const count = h("select", {}, COUNT_OPTIONS.map((n) =>
    h("option", { value: n, selected: n === state.settings.count }, `${n} preguntas`)
  ));
  const theory = communityPicker("Comunidades · Teoría", false, state.settings.theory);
  const legislation = communityPicker("Comunidades · Legislación", true, state.settings.legislation);

  render(panel("Ajustes", showHome,
    h("label", { class: "field" }, "Preguntas por simulacro", count),
    theory.el,
    legislation.el,
    h("button", {
      class: "btn primary",
      type: "button",
      onclick: () => {
        state.settings = { count: Number(count.value), theory: theory.get(), legislation: legislation.get() };
        saveState();
        showHome();
      }
    }, "Guardar")
  ));
}
