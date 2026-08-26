import fs from "node:fs";

const IN = "doc/transcripts/transcript-1.txt";
const OUT = "doc/transcripts/uncle-bob-software-fundamentals-ai.md";
const lines = fs.readFileSync(IN, "utf8").split("\n");

// ---------- Correções de nomes/termos (ASR -> correto) ----------
const REPLAC = [
  [/\bForran\b/g, "FORTRAN"],
  [/\bCobalt\b/g, "COBOL"],
  [/\bPL1\b/g, "PL/I"],
  [/\bchaty PT\b/gi, "ChatGPT"],
  [/\bGrock\b/g, "Grok"],
  [/\bcyclatic complexity\b/g, "cyclomatic complexity"],
  [/\bcyclatic\b/g, "cyclomatic"],
  [/\bgirkin\b/gi, "Gherkin"],
  [/\bJohn Aster(?:how)?\b/g, "John Ousterhout"],
  [/\bJohn Alart\b/g, "John Ousterhout"],
  [/\bJohn Ora\b/g, "John Ousterhout"],
  [/\bDystra\b/g, "Dijkstra"],
  [/\bEd Yordan\b/g, "Ed Yourdon"],
  [/claw\.md/gi, "CLAUDE.md"],
  [/agents\.mmd/gi, "AGENTS.md"],
  [/\bspecdriven\b/gi, "spec-driven"],
  [/\bspecri\b/gi, "spec-driven"],
  [/\bspective\b/gi, "spec-driven"],
  [/\bspectriven\b/gi, "spec-driven"],
  [/multi- aent/g, "multi-agent"],
  [/\bsequel\b/gi, "SQL"],
  [/\bfor closure\b/gi, "for Clojure"],
  [/\bdog dude\b/gi, "dog doo"],
  [/\bdog deus?\b/gi, "dog doo"],
  [/\bdog do\b/gi, "dog doo"],
  [/\bdog on book\b/gi, "doggone book"],
  [/\bephemeris\b/gi, "ephemeral"],
  [/\bfiddleled\b/gi, "fiddled"],
  [/\bsubm modules\b/g, "submodules"],
  [/welld disciplined/g, "well-disciplined"],
  [/soap op opera/g, "soap opera"],
  [/straight jacket/g, "straitjacket"],
  [/\bpreAI\b/g, "pre-AI"],
  [/\ba important\b/g, "an important"],
  [/The bathroom off\. I have me\./, "The bathrobe's off. I'm me."],
  [/Can I just Can I just/, "Can I just"],
  [/It's great to have you\. It's great to have you\./, "It's great to have you."],
  [/It's great to have you\. for folks/, "It's great to have you. For folks"],
  [/In the Okay, we've talked/, "Okay, we've talked"],
  [/spec driven development/gi, "spec-driven development"],
  [/Or even just mis even just misalignment/, "Or even just misalignment"],
  [/I want \[laughs\] clear\./, "I want to be clear."],
];

function fixNames(t) {
  for (const [re, to] of REPLAC) t = t.replace(re, to);
  return t;
}

function fixCrap(t) {
  return t
    .replace(/\bcrap scores?\b/gi, "CRAP score")
    .replace(/\bcrap numbers\b/gi, "CRAP numbers")
    .replace(/\bcrap analysis\b/gi, "CRAP analysis")
    .replace(/\bcrap tool\b/gi, "CRAP tool")
    .replace(/\bcalled crap\b/gi, "called CRAP")
    .replace(/\brun crap\b/gi, "run CRAP")
    .replace(/\bof crap\b/gi, "of CRAP")
    .replace(/\badjusting the crap\b/gi, "adjusting the CRAP");
}

function clean(t) {
  t = t.replace(/\[snort(?:s)?\]/g, "").replace(/\[clears throat\]/g, "");
  t = t.replace(/\[laughter\]/gi, " [laughs]");
  t = t.replace(/(?:\s|^)(?:uh|um),?(?=\s|$)/gi, " ");
  t = t.replace(/[?.!]\s*,/g, (m) => m[0]);
  t = t.replace(/,\s*[?.!]/g, (m) => m[m.length - 1]);
  t = t.replace(/\.\s*\./g, ".");
  t = t.replace(/,\s*,/g, ",");
  t = t.replace(/\s{2,}/g, " ");
  t = t.replace(/\s+([.,;:!?'"-])/g, "$1");
  t = t.replace(/\b([a-z']+)( \1\b)+/gi, "$1");
  t = t.replace(/\b([a-z']+ [a-z']+)( \1\b)+/gi, "$1");
  t = fixNames(t);
  t = fixCrap(t);
  t = t.replace(/^[\s,;:.]+/, "");
  t = t.trim();
  return t;
}

const cap = (s) => (s && /^[a-z]/.test(s) ? s[0].toUpperCase() + s.slice(1) : s);

// ---------- Seções ----------
const SECTION = [
  [0, "Introdução: o bathrobe e a história do Uncle Bob"],
  [5, "CRAP score e mutation testing — ferramentas que a IA viabilizou"],
  [10, "Código sujo derruba agente — steering vs checks determinísticos"],
  [15, "Lost in the middle e o controle do contexto"],
  [20, "Pipeline multi-agente: specifier, coder, cleaner, hardener, QA"],
  [25, "Arquitetura de módulos e o architecture viewer"],
  [30, "Deep modules e o debate com Ousterhout"],
  [32, "O que mudaria no Clean Code — thresholds, CRAP e TDD"],
  [35, "Planejamento upfront vs agile — o custo da mudança"],
  [40, "Spec-driven development — a especificação upfront não funciona"],
  [45, "Tático vs estratégico — como aprender programação estratégica"],
  [50, "Livros antigos, assembly e virar sub-agente"],
  [53, "Fundamentos importam — o argumento da abstração"],
];
function sectionTitle(t) {
  let title = "Conversa";
  for (const [mm, lbl] of SECTION) if (t >= mm * 60) title = lbl;
  return title;
}

// ---------- Parse das unidades (markers ">>" da ASR) ----------
const turnRe = /^\[(\d{1,2}):(\d{2})\] ?(.*)$/;
const units = [];
let cur = null;
for (const raw of lines) {
  const m = raw.match(turnRe);
  if (!m) {
    if (cur) cur.text += " " + raw.trim();
    continue;
  }
  const [, mm, ss, rest] = m;
  const t = +mm * 60 + +ss;
  const hasMarker = /^>>/.test(rest);
  const text = hasMarker ? rest.replace(/^>>\s*/, "") : rest;
  if (hasMarker && cur) {
    if (cur.text.trim()) units.push(cur);
    cur = null;
  }
  if (!cur) cur = { start: t, text: "" };
  cur.text += (cur.text ? " " : "") + text;
}
if (cur && cur.text.trim()) units.push(cur);
console.log("units:", units.length);

// ---------- Mapa manual por conteúdo ----------
// H = apresentador (britânico, faz as perguntas), B = Uncle Bob.
// Split interno de unidade: [falante, marcador de texto (case-insensitive)].
const SEG = {
  1: ["H"],
  2: ["B", ["H", "It's great"]],
  3: ["B"],
  4: ["H"],
  5: ["B"],
  6: ["H"],
  7: ["B"],
  8: ["B"],
  9: ["H"],
  10: ["B", ["H", "Okay. So, you're 12 years old"]],
  11: ["B"],
  12: ["H"],
  13: ["B"],
  14: ["H"],
  15: ["B"],
  16: ["H"],
  17: ["H", ["B", "so this took me a little by surprise"]],
  18: ["H"],
  19: ["B"],
  20: ["B", ["H", "So your goal is to start pulling yourself away"], ["B", "One of the things I noticed really early on"]],
  21: ["H", ["B", "So, initially I started with that"]],
  22: ["B"],
  23: ["H"],
  24: ["B"],
  25: ["H"],
  26: ["B"],
  27: ["H"],
  28: ["B"],
  29: ["B"],
  30: ["B"],
  31: ["H"],
  32: ["H", ["B", "So there's two advantages to having multiple agents like this"], ["H", "What I found really interesting about that"]],
  33: ["B"],
  34: ["H"],
  35: ["B", ["H", "I'm basically in exactly the same boat as you"]],
  36: ["B"],
  37: ["H"],
  38: ["B"],
  39: ["H"],
  40: ["H"],
  41: ["B"],
  42: ["B"],
  43: ["B"],
  44: ["H"],
  45: ["B"],
  46: ["H"],
  47: ["B"],
  48: ["H"],
  49: ["B"],
  50: ["H"],
  51: ["B", ["H", "You're welcome."]],
  52: ["H", ["B", "Well, so the temptation is to specify"]],
  53: ["H"],
  54: ["B"],
  55: ["H"],
  56: ["B"],
  57: ["H"],
  58: ["H"],
  59: ["B"],
  60: ["H"],
  61: ["B"],
  62: ["H"],
  63: ["B", ["H", "You know, long term, I'm interested in how human relationships differ"]],
  64: ["H"],
  65: ["H"],
  66: ["H"],
  67: ["B"],
  68: ["H"],
  69: ["B"],
  70: ["H"],
  71: ["B"],
  72: ["B"],
  73: ["H"],
  74: ["B"],
  75: ["H"],
  76: ["H"],
  77: ["B"],
  78: ["H"],
  79: ["H"],
  80: ["B"],
  81: ["H"],
  82: ["B"],
  83: ["H"],
  84: ["H"],
  85: ["B"],
  86: ["B"],
  87: ["H"],
  88: ["B"],
  89: ["B"],
  90: ["H"],
  91: ["B", ["H", "So, okay. I do have one more question"], ["B", "Sure."]],
  92: ["H"],
  93: ["H"],
  94: ["B"],
  95: ["H"],
  96: ["B"],
  97: ["B"],
  98: ["H"],
  99: ["B"],
  100: ["H"],
};

// ---------- Monta segmentos ----------
const segments = [];
for (let i = 1; i <= units.length; i++) {
  const u = units[i - 1];
  if (!u || !SEG[i]) continue;
  let cursor = 0;
  let spk = SEG[i][0];
  for (const spec of SEG[i].slice(1)) {
    const [nextSpk, at] = spec;
    const idx = u.text.toLowerCase().indexOf(at.toLowerCase(), cursor);
    if (idx === -1) continue;
    const text = clean(u.text.slice(cursor, idx));
    if (text) segments.push({ spk, start: u.start, text });
    cursor = idx;
    spk = nextSpk;
  }
  const text = clean(u.text.slice(cursor));
  if (text) segments.push({ spk, start: u.start, text });
}

// junta consecutivos do mesmo falante
const merged = [];
for (const s of segments) {
  const prev = merged[merged.length - 1];
  if (prev && prev.spk === s.spk) {
    prev.text += " " + s.text;
  } else {
    merged.push({ ...s });
  }
}
for (const p of merged) p.text = cap(clean(p.text));

// ---------- Markdown ----------
const md = [];
md.push("# Uncle Bob on Software Fundamentals in the Age of AI — Transcrição");
md.push("");
md.push("> Fonte: <https://www.youtube.com/watch?v=zcLPGC-tvgk> (56:39)");
md.push(">");
md.push("> Transcrição automática + pós-edição: nomes/termos corrigidos, fala agrupada por falante, timestamps por seção. A atribuição de falante foi inferida por conteúdo (os marcadores de turno da ASR são inconsistentes) — host = apresentador britânico, Uncle Bob = Robert C. Martin.");
md.push("");
md.push("---");
md.push("");

let last = null;
for (const p of merged) {
  const ts = `[${String(Math.floor(p.start / 60)).padStart(2, "0")}:${String(p.start % 60).padStart(2, "0")}]`;
  const t = sectionTitle(p.start);
  if (t !== last) {
    md.push(`## ${ts} ${t}`);
    md.push("");
    last = t;
  }
  const label = p.spk === "B" ? "Uncle Bob" : "Host";
  md.push(`**${label}:** ${p.text}`);
  md.push("");
}

md.push("---");
md.push("");
md.push("_Fim da transcrição (stream público). O trecho final (`[56:37]`) corta em \"vamos ficar para\" — o host encerra a live._");

fs.writeFileSync(OUT, md.join("\n"), "utf8");
console.log("segments:", segments.length, "merged:", merged.length, "OK");
