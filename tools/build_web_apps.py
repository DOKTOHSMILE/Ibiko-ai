#!/usr/bin/env python3
"""Builds app/src/main/assets/knowledge/web/apps.tsv.ibk: small web apps Ibiko v1 can hand over whole.

Each app is one HTML file (HTML, CSS and JavaScript together) that opens in any browser, works on a phone and needs no
server. Nothing is shipped untested: every app is parsed, its script is checked by Node, and it is opened in a real
browser (Chromium, through Playwright) and used the way a person would (tools/test_web_apps.js): a to-do added and
ticked, a sum worked out, a timer started. One that fails is not written.

One line an app: id, the words that name it (a regular expression), its name, what it does, how it works (its parts, in
plain words), what the test checked, and the HTML (new lines written as \\n; the HTML itself has no backslashes). Coded
like the rest of the knowledge (tools/encode_text.py), read back by ai.ibiko.ibiko1.TextCode.

Usage: tools/build_web_apps.py
"""
import html.parser
import json
import os
import re
import subprocess
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "knowledge", "web", "apps.tsv")

STYLE = """:root{--bg:#f8fafc;--card:#ffffff;--text:#0f172a;--muted:#64748b;--accent:#2563eb;--line:#e2e8f0}
@media (prefers-color-scheme:dark){:root{--bg:#0f172a;--card:#1e293b;--text:#f8fafc;--muted:#94a3b8;--line:#334155}}
*{box-sizing:border-box}body{margin:0;font-family:system-ui,-apple-system,Segoe UI,Roboto,sans-serif;background:var(--bg);color:var(--text);
display:flex;justify-content:center;padding:24px 16px}main{width:100%;max-width:420px;background:var(--card);border:1px solid var(--line);
border-radius:16px;padding:20px}h1{font-size:1.4rem;margin:0 0 16px}button{font:inherit;border:0;border-radius:10px;padding:10px 14px;
background:var(--accent);color:#fff;cursor:pointer}button.ghost{background:transparent;color:var(--text);border:1px solid var(--line)}
input,textarea,select{font:inherit;width:100%;padding:10px;border:1px solid var(--line);border-radius:10px;background:var(--bg);color:var(--text)}
label{display:block;margin:12px 0 4px;color:var(--muted);font-size:.9rem}.row{display:flex;gap:8px;align-items:center}.muted{color:var(--muted)}"""


def page(title, body, script, extra_style=""):
    return ("<!doctype html>\n<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n"
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n<title>" + title + "</title>\n"
            "<style>\n" + STYLE + ("\n" + extra_style if extra_style else "") + "\n</style>\n</head>\n<body>\n<main>\n"
            + body.strip() + "\n</main>\n<script>\n" + script.strip() + "\n</script>\n</body>\n</html>\n")


APPS = []


def app(id, asks, name, does, how, tested, title, body, script, style=""):
    APPS.append((id, asks, name, does, how, tested, page(title, body, script, style)))


app("todo", r"to-?do(?: list)?(?: app)?|task list|checklist app|tasks app",
    "to-do list app",
    "keeps a list of things to do: add one, tick it off when done, delete it; the list is kept on the device",
    "a text box and an Add button (the form); the list itself (a <ul>, one <li> a task, each with a tick box and a delete button); "
    "a few lines of JavaScript that add, tick and delete, and save the list in the browser's storage (localStorage) so it's still there tomorrow",
    "added two tasks, ticked one, deleted the other, reloaded the page: the ticked task was still there",
    "To-do list",
    """
<h1>To-do list</h1>
<form id="add" class="row"><input id="text" placeholder="Something to do" aria-label="New task" required><button>Add</button></form>
<ul id="list"></ul>
<p class="muted" id="left"></p>
""",
    """
const KEY = "todo-items";
let items = [];
try { items = JSON.parse(localStorage.getItem(KEY)) || []; } catch (e) { items = []; }
const list = document.getElementById("list");
function save() { try { localStorage.setItem(KEY, JSON.stringify(items)); } catch (e) {} }
function draw() {
  list.innerHTML = "";
  items.forEach(function (item, i) {
    const li = document.createElement("li");
    const box = document.createElement("input");
    box.type = "checkbox"; box.checked = item.done; box.setAttribute("aria-label", "Done");
    box.onchange = function () { item.done = box.checked; save(); draw(); };
    const span = document.createElement("span");
    span.textContent = item.text;
    if (item.done) span.className = "done";
    const del = document.createElement("button");
    del.textContent = "Delete"; del.className = "ghost";
    del.onclick = function () { items.splice(i, 1); save(); draw(); };
    li.append(box, span, del);
    list.append(li);
  });
  const left = items.filter(function (x) { return !x.done; }).length;
  document.getElementById("left").textContent = items.length ? left + " left to do" : "Nothing yet: add your first task.";
}
document.getElementById("add").onsubmit = function (e) {
  e.preventDefault();
  const input = document.getElementById("text");
  const text = input.value.trim();
  if (text) { items.push({ text: text, done: false }); save(); draw(); }
  input.value = "";
};
draw();
""",
    "ul{list-style:none;padding:0}li{display:flex;gap:10px;align-items:center;padding:10px 0;border-bottom:1px solid var(--line)}"
    "li input{width:auto}li span{flex:1}.done{text-decoration:line-through;color:var(--muted)}")

app("calculator", r"(?<!tip )(?<!bmi )(?<!loan )calculator(?: app)?",
    "calculator",
    "adds, subtracts, multiplies and divides, with brackets, in the right order (× and ÷ before + and −)",
    "a display and a grid of buttons; a small reader in JavaScript that works the sum out the way a person would, "
    "multiplying and dividing before adding and subtracting, without eval (so nothing typed can run as code)",
    "pressed 12 + 3 × 4 = and got 24; (2 + 3) × 4 = gave 20; 7 ÷ 0 = said it can't divide by zero",
    "Calculator",
    """
<h1>Calculator</h1>
<output id="screen" aria-live="polite">0</output>
<div class="keys" id="keys"></div>
""",
    """
const screen = document.getElementById("screen");
let entry = "";
const keys = ["C", "(", ")", "÷", "7", "8", "9", "×", "4", "5", "6", "−", "1", "2", "3", "+", "0", ".", "⌫", "="];
function show(text) { screen.textContent = text || "0"; }
function calc(text) {
  const tokens = text.replace(/×/g, "*").replace(/÷/g, "/").replace(/−/g, "-").match(/[0-9.]+|[-+*/()]/g) || [];
  let at = 0;
  function number() {
    const t = tokens[at++];
    if (t === "(") { const v = sum(); if (tokens[at++] !== ")") throw new Error("A bracket isn't closed"); return v; }
    if (t === "-") return -number();
    const v = parseFloat(t);
    if (isNaN(v)) throw new Error("That isn't a sum I can read");
    return v;
  }
  function product() {
    let v = number();
    while (tokens[at] === "*" || tokens[at] === "/") {
      const op = tokens[at++], b = number();
      if (op === "/" && b === 0) throw new Error("Can't divide by zero");
      v = op === "*" ? v * b : v / b;
    }
    return v;
  }
  function sum() {
    let v = product();
    while (tokens[at] === "+" || tokens[at] === "-") { const op = tokens[at++], b = product(); v = op === "+" ? v + b : v - b; }
    return v;
  }
  const v = sum();
  if (at < tokens.length) throw new Error("That isn't a sum I can read");
  return Math.round(v * 1e10) / 1e10;
}
function press(k) {
  if (k === "C") entry = "";
  else if (k === "⌫") entry = entry.slice(0, -1);
  else if (k === "=") { try { entry = String(calc(entry)); } catch (e) { show(e.message); entry = ""; return; } }
  else entry += k;
  show(entry);
}
keys.forEach(function (k) {
  const b = document.createElement("button");
  b.textContent = k;
  if ("÷×−+=".indexOf(k) < 0) b.className = "ghost";
  b.onclick = function () { press(k); };
  document.getElementById("keys").append(b);
});
""",
    "#screen{display:block;font-size:2rem;text-align:right;padding:12px;margin-bottom:12px;border:1px solid var(--line);border-radius:10px;"
    "min-height:3.2rem;overflow-x:auto}.keys{display:grid;grid-template-columns:repeat(4,1fr);gap:8px}.keys button{padding:16px 0;font-size:1.2rem}")

app("countdown", r"count ?down(?: timer| clock| app)?|countdown to",
    "countdown timer",
    "counts down the days, hours, minutes and seconds to a date you choose (a launch, a wedding, an exam)",
    "a date and time box; four numbers that change every second (setInterval); when the time comes, it says so",
    "set a date two days ahead: it showed 1 or 2 days and counted; set a time in the past: it said the time has come",
    "Countdown",
    """
<h1>Countdown</h1>
<label for="when">Count down to</label>
<input id="when" type="datetime-local">
<div class="clock" id="clock"><p><b id="d">0</b>days</p><p><b id="h">0</b>hours</p><p><b id="m">0</b>minutes</p><p><b id="s">0</b>seconds</p></div>
<p id="done" class="muted"></p>
""",
    """
const when = document.getElementById("when");
const ahead = new Date(Date.now() + 7 * 864e5);
ahead.setMinutes(ahead.getMinutes() - ahead.getTimezoneOffset());
when.value = ahead.toISOString().slice(0, 16);
function tick() {
  const left = new Date(when.value).getTime() - Date.now();
  const done = document.getElementById("done");
  if (isNaN(left)) { done.textContent = "Choose a date."; return; }
  const s = Math.max(0, Math.floor(left / 1000));
  document.getElementById("d").textContent = Math.floor(s / 86400);
  document.getElementById("h").textContent = Math.floor(s % 86400 / 3600);
  document.getElementById("m").textContent = Math.floor(s % 3600 / 60);
  document.getElementById("s").textContent = s % 60;
  done.textContent = left <= 0 ? "The time has come!" : "";
}
when.oninput = tick;
setInterval(tick, 1000);
tick();
""",
    ".clock{display:grid;grid-template-columns:repeat(4,1fr);gap:8px;margin-top:16px;text-align:center}.clock p{margin:0;padding:12px 0;"
    "border:1px solid var(--line);border-radius:10px;color:var(--muted);font-size:.8rem}.clock b{display:block;font-size:1.8rem;color:var(--text)}")

app("stopwatch", r"stop ?watch(?: app)?|lap timer",
    "stopwatch",
    "times anything to the hundredth of a second, with Start, Stop, Lap and Reset",
    "the time on screen, worked out from the clock (performance.now) so it stays right even if the phone is slow; "
    "buttons that start, stop, mark a lap and reset; the laps listed under it",
    "started it, waited, stopped it: the time had moved and then stood still; marked a lap; reset it to 00:00.00",
    "Stopwatch",
    """
<h1>Stopwatch</h1>
<output id="time">00:00.00</output>
<div class="row"><button id="go">Start</button><button id="lap" class="ghost">Lap</button><button id="reset" class="ghost">Reset</button></div>
<ol id="laps"></ol>
""",
    """
let started = 0, before = 0, timer = null;
const out = document.getElementById("time"), go = document.getElementById("go");
function elapsed() { return before + (timer ? performance.now() - started : 0); }
function format(ms) {
  const cs = Math.floor(ms / 10), m = Math.floor(cs / 6000), s = Math.floor(cs / 100) % 60, c = cs % 100;
  return String(m).padStart(2, "0") + ":" + String(s).padStart(2, "0") + "." + String(c).padStart(2, "0");
}
function draw() { out.textContent = format(elapsed()); }
go.onclick = function () {
  if (timer) { before = elapsed(); clearInterval(timer); timer = null; go.textContent = "Start"; }
  else { started = performance.now(); timer = setInterval(draw, 31); go.textContent = "Stop"; }
  draw();
};
document.getElementById("lap").onclick = function () {
  const li = document.createElement("li"); li.textContent = format(elapsed()); document.getElementById("laps").append(li);
};
document.getElementById("reset").onclick = function () {
  clearInterval(timer); timer = null; before = 0; go.textContent = "Start"; document.getElementById("laps").innerHTML = ""; draw();
};
""",
    "#time{display:block;font-size:3rem;font-variant-numeric:tabular-nums;text-align:center;margin:8px 0 16px}.row{justify-content:center}")

app("contact_form", r"contact form|enquiry form|inquiry form|message form|feedback form",
    "contact form",
    "takes a name, an email or phone and a message, checks them, and sends the message to you on WhatsApp or by email",
    "a form with its labels; the browser's own checks (required, type=email) plus a few lines that check again and say what's missing; "
    "sending opens WhatsApp (wa.me) or the email app with the message written in, so it needs no server: put your number and email at the top of the script",
    "sent it empty: it named what was missing; filled it in: it made a WhatsApp link with the name and message in it",
    "Contact us",
    """
<h1>Contact us</h1>
<form id="contact" novalidate>
<label for="name">Your name</label><input id="name" autocomplete="name" required>
<label for="reach">Email or phone</label><input id="reach" autocomplete="email" required>
<label for="msg">Message</label><textarea id="msg" rows="4" required></textarea>
<p id="problem" class="muted" role="alert"></p>
<div class="row"><button name="via" value="whatsapp">Send on WhatsApp</button><button name="via" value="email" class="ghost">Send by email</button></div>
</form>
""",
    """
const WHATSAPP = "2348000000000"; // your number, with the country code and no +
const EMAIL = "you@example.com";  // your email
const form = document.getElementById("contact");
form.onsubmit = function (e) {
  e.preventDefault();
  const name = document.getElementById("name").value.trim();
  const reach = document.getElementById("reach").value.trim();
  const msg = document.getElementById("msg").value.trim();
  const missing = [];
  if (!name) missing.push("your name");
  if (!reach) missing.push("an email or phone number");
  if (!msg) missing.push("a message");
  const problem = document.getElementById("problem");
  if (missing.length) { problem.textContent = "Please add " + missing.join(", ") + "."; return; }
  problem.textContent = "";
  const text = "Hello, I'm " + name + " (" + reach + "). " + msg;
  const via = e.submitter ? e.submitter.value : "whatsapp";
  const link = via === "email"
    ? "mailto:" + EMAIL + "?subject=" + encodeURIComponent("Message from " + name) + "&body=" + encodeURIComponent(text)
    : "https://wa.me/" + WHATSAPP + "?text=" + encodeURIComponent(text);
  form.dataset.sent = link;
  window.open(link, "_blank");
};
""")

app("login", r"log ?in (?:page|form|screen)|sign ?in (?:page|form|screen)|login",
    "login page",
    "a sign-in page: email and password, show or hide the password, checks before sending, and a sign-up link",
    "the form and its checks are here; signing people in for real needs a server that keeps the accounts and checks the password "
    "(Firebase Auth, Supabase, or your own backend): put its address in the script where it says so. Never keep passwords in the page itself",
    "sent it with a bad email: it said so; with a short password: it said so; Show turned the password into text and back",
    "Sign in",
    """
<h1>Sign in</h1>
<form id="login" novalidate>
<label for="email">Email</label><input id="email" type="email" autocomplete="email" required>
<label for="pw">Password</label>
<div class="row"><input id="pw" type="password" autocomplete="current-password" minlength="8" required><button type="button" id="show" class="ghost">Show</button></div>
<p id="problem" class="muted" role="alert"></p>
<button style="width:100%;margin-top:8px">Sign in</button>
<p class="muted">No account? <a href="#signup">Sign up</a> · <a href="#forgot">Forgot password?</a></p>
</form>
""",
    """
const SIGN_IN_URL = ""; // your server's sign-in address (Firebase Auth, Supabase, your backend)
const pw = document.getElementById("pw"), show = document.getElementById("show");
show.onclick = function () {
  const hidden = pw.type === "password";
  pw.type = hidden ? "text" : "password";
  show.textContent = hidden ? "Hide" : "Show";
};
document.getElementById("login").onsubmit = function (e) {
  e.preventDefault();
  const email = document.getElementById("email").value.trim();
  const problem = document.getElementById("problem");
  if (!/^[^@ ]+@[^@ ]+[.][^@ ]+$/.test(email)) { problem.textContent = "That email doesn't look right."; return; }
  if (pw.value.length < 8) { problem.textContent = "The password needs at least 8 characters."; return; }
  if (!SIGN_IN_URL) { problem.textContent = "Ready: add your sign-in server's address at the top of the script."; return; }
  problem.textContent = "Signing in…";
  fetch(SIGN_IN_URL, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ email: email, password: pw.value }) })
    .then(function (r) { problem.textContent = r.ok ? "Signed in." : "Email or password is wrong."; })
    .catch(function () { problem.textContent = "Couldn't reach the server. Try again."; });
};
""")

app("quiz", r"quiz(?: app| game)?|trivia(?: game)?|multiple[- ]choice (?:test|game)",
    "quiz",
    "asks multiple-choice questions one at a time, says right or wrong, and gives the score at the end",
    "the questions are a list at the top of the script (change them to yours); the page shows one with its choices as buttons, "
    "marks the answer, and counts the score",
    "answered every question right: the score was full marks; started again: it went back to question 1",
    "Quiz",
    """
<h1>Quiz</h1>
<p id="count" class="muted"></p>
<h2 id="question"></h2>
<div id="choices" class="choices"></div>
<p id="said" role="status"></p>
<button id="next">Next</button>
""",
    """
const QUESTIONS = [
  { q: "What is the capital of Nigeria?", choices: ["Lagos", "Abuja", "Kano"], answer: 1 },
  { q: "How many days are in a leap year?", choices: ["365", "366", "364"], answer: 1 },
  { q: "Which planet is closest to the sun?", choices: ["Mercury", "Venus", "Mars"], answer: 0 }
];
let at = 0, score = 0, answered = false;
const next = document.getElementById("next");
function draw() {
  answered = false;
  document.getElementById("said").textContent = "";
  const choices = document.getElementById("choices");
  choices.innerHTML = "";
  if (at >= QUESTIONS.length) {
    document.getElementById("count").textContent = "Done";
    document.getElementById("question").textContent = "You scored " + score + " out of " + QUESTIONS.length + ".";
    next.textContent = "Start again";
    return;
  }
  const item = QUESTIONS[at];
  document.getElementById("count").textContent = "Question " + (at + 1) + " of " + QUESTIONS.length;
  document.getElementById("question").textContent = item.q;
  next.textContent = "Next";
  next.disabled = true;
  item.choices.forEach(function (c, i) {
    const b = document.createElement("button");
    b.textContent = c; b.className = "ghost";
    b.onclick = function () {
      if (answered) return;
      answered = true;
      const right = i === item.answer;
      if (right) score++;
      b.className = right ? "right" : "wrong";
      document.getElementById("said").textContent = right ? "Right!" : "Not quite: it's " + item.choices[item.answer] + ".";
      next.disabled = false;
    };
    choices.append(b);
  });
}
next.onclick = function () {
  if (at >= QUESTIONS.length) { at = 0; score = 0; } else { at++; }
  draw();
};
draw();
""",
    "h2{font-size:1.1rem}.choices{display:grid;gap:8px;margin-bottom:12px}.right{background:#16a34a}.wrong{background:#dc2626}"
    "button:disabled{opacity:.5}")

app("bill_split", r"tip calculator|bill split(?:ter)?|split (?:the|a) bill|tip app",
    "tip and bill-splitting calculator",
    "works out the tip and what each person pays, from the bill, the tip percentage and how many people share it",
    "three boxes (the bill, the tip %, how many people) and the answer worked out as you type (the input event), rounded to money",
    "a bill of 10,000 with a 10% tip shared by 4 people: tip 1,000.00, total 11,000.00, each 2,750.00",
    "Split the bill",
    """
<h1>Split the bill</h1>
<label for="bill">Bill</label><input id="bill" type="number" min="0" step="0.01" inputmode="decimal" value="0">
<label for="tip">Tip (%)</label><input id="tip" type="number" min="0" step="1" value="10">
<label for="people">People</label><input id="people" type="number" min="1" step="1" value="1">
<dl class="sums"><dt>Tip</dt><dd id="tipped">0.00</dd><dt>Total</dt><dd id="total">0.00</dd><dt>Each pays</dt><dd id="each">0.00</dd></dl>
""",
    """
function money(n) { return n.toLocaleString("en", { minimumFractionDigits: 2, maximumFractionDigits: 2 }); }
function work() {
  const bill = Math.max(0, parseFloat(document.getElementById("bill").value) || 0);
  const tip = Math.max(0, parseFloat(document.getElementById("tip").value) || 0);
  const people = Math.max(1, Math.floor(parseFloat(document.getElementById("people").value) || 1));
  const tipped = bill * tip / 100, total = bill + tipped;
  document.getElementById("tipped").textContent = money(tipped);
  document.getElementById("total").textContent = money(total);
  document.getElementById("each").textContent = money(total / people);
}
document.querySelectorAll("input").forEach(function (i) { i.oninput = work; });
work();
""",
    ".sums{display:grid;grid-template-columns:1fr auto;gap:8px;margin-top:16px}.sums dd{margin:0;font-weight:700;text-align:right}")


class _Check(html.parser.HTMLParser):
    VOID = {"meta", "input", "br", "img", "link", "hr"}

    def __init__(self):
        super().__init__()
        self.open = []

    def handle_starttag(self, tag, attrs):
        if tag not in self.VOID:
            self.open.append(tag)

    def handle_endtag(self, tag):
        assert self.open and self.open[-1] == tag, "</%s> doesn't close <%s>" % (tag, self.open[-1] if self.open else "")
        self.open.pop()


def check(a):
    id, html_text = a[0], a[6]
    assert "\\" not in html_text, id + ": no backslashes in the HTML (new lines are written as \\n)"
    assert "\t" not in "".join(a), id + ": no tabs"
    re.compile(a[1])
    c = _Check()
    c.feed(html_text)
    assert not c.open, id + ": not closed: " + str(c.open)
    script = html_text.split("<script>\n", 1)[1].rsplit("</script>", 1)[0]
    with tempfile.NamedTemporaryFile("w", suffix=".js", delete=False) as f:
        f.write(script)
    try:
        subprocess.run(["node", "--check", f.name], check=True)
    finally:
        os.unlink(f.name)


def main():
    for a in APPS:
        assert len(a) == 7, a[0]
        check(a)
    # every app opened in a real browser and used, before any is written
    with tempfile.TemporaryDirectory() as d:
        for a in APPS:
            with open(os.path.join(d, a[0] + ".html"), "w") as f:
                f.write(a[6])
        subprocess.run(["node", os.path.join(HERE, "test_web_apps.js"), d] + [a[0] for a in APPS], check=True)
    lines = ["# Small web apps Ibiko v1 hands over whole, each tested in a browser (tools/build_web_apps.py): "
             "id, named by, name, does, how it works, tested, html"]
    for a in APPS:
        lines.append("\t".join(a[:6] + (a[6].replace("\n", "\\n"),)))
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w") as f:
        f.write("\n".join(lines) + "\n")
    subprocess.run([sys.executable, os.path.join(HERE, "encode_text.py"), OUT, "--remove"], check=True)
    print("%d web apps, each tested in a browser" % len(APPS))


if __name__ == "__main__":
    main()
