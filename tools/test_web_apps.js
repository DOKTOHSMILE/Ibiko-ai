// Opens each web app from tools/build_web_apps.py in a real browser (Chromium, through Playwright) and uses it the way a
// person would. Any failure stops the build: an app that doesn't work is never written to Ibiko's knowledge.
// Usage: node tools/test_web_apps.js <dir> <id>...
const { chromium } = require("playwright");
const path = require("path");

const dir = process.argv[2];
const ids = process.argv.slice(3);

function expect(ok, what) {
  if (!ok) throw new Error(what);
}

const TESTS = {
  async todo(page) {
    await page.fill("#text", "Buy bread");
    await page.click("#add button");
    await page.fill("#text", "Call mum");
    await page.click("#add button");
    expect((await page.locator("#list li").count()) === 2, "two tasks added");
    await page.locator("#list li").nth(0).locator("input").check();
    await page.locator("#list li").nth(1).locator("button").click();
    await page.reload();
    expect((await page.locator("#list li").count()) === 1, "one task left after deleting, kept after reloading");
    expect(await page.locator("#list li input").isChecked(), "the ticked task is still ticked after reloading");
    expect((await page.textContent("#left")).startsWith("0 left"), "nothing left to do");
  },
  async calculator(page) {
    const press = async (keys) => {
      for (const k of keys) await page.locator("#keys button", { hasText: new RegExp("^" + k.replace(/[()+.]/g, "[$&]") + "$") }).click();
    };
    await press(["1", "2", "+", "3", "×", "4", "="]);
    expect((await page.textContent("#screen")) === "24", "12 + 3 × 4 = 24, got " + (await page.textContent("#screen")));
    await press(["C", "(", "2", "+", "3", ")", "×", "4", "="]);
    expect((await page.textContent("#screen")) === "20", "(2 + 3) × 4 = 20");
    await press(["C", "7", "÷", "0", "="]);
    expect((await page.textContent("#screen")) === "Can't divide by zero", "dividing by zero is said, not shown as Infinity");
  },
  async countdown(page) {
    const set = async (ms) => page.evaluate((ms) => {
      const d = new Date(Date.now() + ms);
      d.setMinutes(d.getMinutes() - d.getTimezoneOffset());
      const i = document.getElementById("when");
      i.value = d.toISOString().slice(0, 16);
      i.dispatchEvent(new Event("input"));
    }, ms);
    await set(2 * 864e5 + 5 * 60e3);
    const days = Number(await page.textContent("#d"));
    expect(days === 2 || days === 1, "two days ahead shows 1 or 2 days, got " + days);
    await set(-60e3);
    expect((await page.textContent("#done")) === "The time has come!", "a time in the past: the time has come");
  },
  async stopwatch(page) {
    await page.click("#go");
    await page.waitForTimeout(250);
    await page.click("#go");
    const stopped = await page.textContent("#time");
    expect(stopped !== "00:00.00", "the time moved");
    await page.waitForTimeout(150);
    expect((await page.textContent("#time")) === stopped, "stopped means it stands still");
    await page.click("#lap");
    expect((await page.locator("#laps li").count()) === 1, "a lap marked");
    await page.click("#reset");
    expect((await page.textContent("#time")) === "00:00.00" && (await page.locator("#laps li").count()) === 0, "reset");
  },
  async contact_form(page) {
    await page.evaluate(() => { window.open = () => null; });
    await page.click("button[value=whatsapp]");
    const problem = await page.textContent("#problem");
    expect(problem.includes("your name") && problem.includes("a message"), "empty: says what is missing, got " + problem);
    await page.fill("#name", "Ada");
    await page.fill("#reach", "ada@example.com");
    await page.fill("#msg", "Do you deliver?");
    await page.click("button[value=whatsapp]");
    const link = await page.getAttribute("#contact", "data-sent");
    expect(link && link.startsWith("https://wa.me/") && decodeURIComponent(link).includes("I'm Ada") && decodeURIComponent(link).includes("Do you deliver?"),
      "a WhatsApp link with the message, got " + link);
  },
  async login(page) {
    await page.fill("#email", "not-an-email");
    await page.fill("#pw", "longenough");
    await page.click("#login button:not(#show)");
    expect((await page.textContent("#problem")).includes("email"), "a bad email is said");
    await page.fill("#email", "ada@example.com");
    await page.fill("#pw", "short");
    await page.click("#login button:not(#show)");
    expect((await page.textContent("#problem")).includes("8 characters"), "a short password is said");
    await page.click("#show");
    expect((await page.getAttribute("#pw", "type")) === "text", "Show shows the password");
    await page.click("#show");
    expect((await page.getAttribute("#pw", "type")) === "password", "and Hide hides it");
  },
  async quiz(page) {
    const answers = await page.evaluate(() => QUESTIONS.map((q) => q.choices[q.answer]));
    for (const a of answers) {
      await page.locator("#choices button", { hasText: a }).click();
      expect((await page.textContent("#said")) === "Right!", "the right answer is marked right");
      await page.click("#next");
    }
    expect((await page.textContent("#question")) === "You scored " + answers.length + " out of " + answers.length + ".", "full marks");
    await page.click("#next");
    expect((await page.textContent("#count")) === "Question 1 of " + answers.length, "starts again");
  },
  async bill_split(page) {
    await page.fill("#bill", "10000");
    await page.fill("#tip", "10");
    await page.fill("#people", "4");
    expect((await page.textContent("#tipped")) === "1,000.00", "tip");
    expect((await page.textContent("#total")) === "11,000.00", "total");
    expect((await page.textContent("#each")) === "2,750.00", "each");
  },
};

(async () => {
  const browser = await chromium.launch();
  let failed = 0;
  for (const id of ids) {
    const test = TESTS[id];
    const page = await browser.newPage({ viewport: { width: 390, height: 800 } });
    const errors = [];
    page.on("pageerror", (e) => errors.push(e.message));
    try {
      if (!test) throw new Error("no test for it: nothing is shipped untested");
      await page.goto("file://" + path.resolve(dir, id + ".html"));
      await test(page);
      const wide = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
      expect(!wide, "fits a phone's width");
      expect(errors.length === 0, "no script errors: " + errors.join("; "));
      console.log("ok   " + id);
    } catch (e) {
      failed++;
      console.log("FAIL " + id + ": " + e.message);
    }
    await page.close();
  }
  await browser.close();
  process.exit(failed ? 1 : 0);
})();
