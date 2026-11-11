// Optional "smart answers" with Claude (Anthropic SDK). Without ANTHROPIC_API_KEY, or if a call
// fails, the built-in KP astrologer (astrologer.js) answers.
import Anthropic from '@anthropic-ai/sdk';
import * as env from './env.js';
import * as astro from './astrologer.js';

export async function enabled() { return (await env.has('ANTHROPIC_API_KEY')) && (await env.get('AI_DISABLED')) !== 'true'; }
export const model = () => env.get('AI_MODEL', 'claude-opus-5-5');

function chartJson(c) {
  return JSON.stringify({
    ascendant: c.ascendant, moonSign: c.moonSign, nakshatra: c.nakshatra, sunSign: c.sunSign,
    planets: c.planets.map((p) => ({ planet: p.name, sign: p.sign, deg: p.dms, house: p.house, retro: p.retrograde, star: p.starLord, sub: p.subLord })),
    cusps: c.cusps.map((p) => ({ cusp: p.house, sign: p.sign, deg: p.dms, star: p.starLord, sub: p.subLord })),
    planetSignifiesHouses: Object.fromEntries(c.significators.map((s) => [s.planet, s.all])),
    rulingPlanets: c.rulingPlanets, mahadashas: c.dasha.periods.map((p) => ({ lord: p.lord, start: p.start, end: p.end })), current: c.currentDasha,
  });
}
function profile(u) {
  const age = Math.floor((Date.now() - Date.parse(u.dob)) / (365.25 * 86400000));
  return `Name: ${u.fullName}\nGender: ${u.gender || 'not given'}\nDate of birth: ${u.dob}  Time: ${u.tob}  (age ${age})\nBirth place: ${u.city}\nToday: ${new Date().toISOString().slice(0, 10)}`;
}
const memoryBlock = (m) => (m.length ? '- ' + m.join('\n- ') : '(nothing yet)');

const RULES = `You are the astrologer of "Astrology Talk", a warm, experienced Krishnamurti Paddhati (KP) astrologer chatting with a client. Never give yourself a personal name and never call yourself an AI, bot or assistant - you are simply "the astrologer at Astrology Talk".
How to consult:
1. First understand WHY they are consulting. If unclear, ask what is on their mind.
2. ALWAYS interview before answering: ask ONE question at a time, 3-4 rounds - their current situation, two psychological questions (feelings, motivation, how they handle setbacks / pressure, what they truly want) and one astrology question (a past life event with month/year for chart verification, and whether the birth time is from a certificate).
3. Then answer clearly: combine their answers with KP principles - judge the relevant cusp sub lords and the houses they signify via their star lords (education 4,5,9,11; career 2,6,10,11; marriage 2,7,11; property 4,11,12; foreign 3,9,12; health 1,5,11 vs 6,8,12; children 2,5,11), and time events with the running and upcoming dasha/antardasha lords. Name the actual planets from the chart.
4. After giving the answer set readyForReport=true and invite them to generate their personalised PDF report. They can keep asking questions.
Use the client's name, remember what you know about them, never re-ask things you already know. Reply in the language the client writes in. Keep replies under ~180 words, plain text. Be encouraging and practical; never make fatalistic predictions; for medical, legal or financial decisions suggest also consulting a qualified professional.`;

async function call(schema, maxTokens, body) {
  const client = new Anthropic({ apiKey: await env.get('ANTHROPIC_API_KEY') });
  const base = { model: await model(), max_tokens: maxTokens, output_config: { format: { type: 'json_schema', schema } }, ...body };
  let msg;
  try {
    msg = await client.beta.messages.create({ ...base, betas: ['server-side-fallback-2026-07-01'], fallbacks: 'default' });
  } catch (e) {
    if (e instanceof Anthropic.BadRequestError) msg = await client.messages.create(base);
    else throw e;
  }
  if (msg.stop_reason === 'refusal') throw new Error('declined');
  if (msg.stop_reason === 'max_tokens') throw new Error('truncated');
  return JSON.parse(msg.content.filter((b) => b.type === 'text').map((b) => b.text).join(''));
}

const CHAT_SCHEMA = { type: 'object', additionalProperties: false, required: ['reply', 'newMemories', 'readyForReport', 'topic'],
  properties: { reply: { type: 'string' }, newMemories: { type: 'array', items: { type: 'string' } }, readyForReport: { type: 'boolean' }, topic: { type: 'string' } } };

export async function chat(u, c, memory, history, state) {
  if (!(await enabled())) return astro.reply(u, c, memory, history, state);
  try {
    const turns = [{ role: 'user', content: '[The client opened the consultation chat.]' }];
    for (const m of history) {
      const last = turns[turns.length - 1];
      if (last.role === m.role) last.content += '\n\n' + m.text; else turns.push({ role: m.role, content: m.text });
    }
    return await call(CHAT_SCHEMA, 4000, {
      system: RULES + '\n\n=== CLIENT PROFILE ===\n' + profile(u) + '\n\n=== WHAT YOU REMEMBER ===\n' + memoryBlock(memory) + '\n\n=== KP CHART ===\n' + chartJson(c),
      messages: turns,
    });
  } catch (e) {
    console.warn('smart answer failed, using built-in astrologer:', e.message);
    return astro.reply(u, c, memory, history, state);
  }
}

const SECTION = { type: 'object', additionalProperties: false, required: ['title', 'content'], properties: { title: { type: 'string' }, content: { type: 'string' } } };
const REPORT_SCHEMA = { type: 'object', additionalProperties: false, required: ['hook', 'summary', 'consultation', 'sections'], properties: {
  hook: { type: 'object', additionalProperties: false, required: ['headline', 'teaser', 'secrets'], properties: { headline: { type: 'string' }, teaser: { type: 'string' }, secrets: { type: 'array', items: { type: 'string' } } } },
  summary: { type: 'string' }, consultation: { type: 'array', items: SECTION }, sections: { type: 'array', items: SECTION } } };

/** Report content { hook, summary, consultation, sections } + source. */
export async function report(u, c, memory, transcript, topic) {
  if (await enabled()) {
    try {
      const tx = transcript.map((m) => (m.role === 'user' ? 'CLIENT: ' : 'ASTROLOGER: ') + m.text).join('\n');
      const prompt = `Write a personalised KP horoscope report for this client, built around their consultation.\n\n=== CLIENT PROFILE ===\n${profile(u)}\n\n=== MEMORY ===\n${memoryBlock(memory)}\n\n=== CONSULTATION TOPIC ===\n${topic || 'General life reading'}\n\n=== TRANSCRIPT ===\n${tx || '(no chat - general reading)'}\n\n=== KP CHART ===\n${chartJson(c)}\n\n`
        + `Rules: use KP (cusp sub lords, star lords, significators, Vimshottari dasha/antardasha with dates). The 'consultation' sections (3-4, 220-320 words each) must directly answer the client's questions using the chart and what they told you, with a clear recommendation, reasoning, timing and practical next steps. The 'sections' are life-area chapters with exactly these titles in order: ${astro.LIFE_AREAS.join('; ')} (300-450 words each). The hook: a gripping headline (max 14 words), a ~90-word teaser ending on a cliff-hanger, and 4 one-line teasers of what the locked pages reveal. Plain text paragraphs separated by blank lines, no markdown, no fatalistic claims.`;
      const rc = await call(REPORT_SCHEMA, 16000, { messages: [{ role: 'user', content: prompt }] });
      if (rc.sections?.length) return { content: rc, source: 'ai:' + (await model()) };
    } catch (e) { console.warn('smart report failed, using built-in report:', e.message); }
  }
  return { content: astro.report(u, c, memory, transcript, topic), source: 'rule-based' };
}
