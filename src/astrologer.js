// Built-in KP astrologer: interview (situation -> 2 psychological questions -> 1 astrology question)
// -> answer from the KP chart + the client's answers. Also writes the report's consultation content.
import { housesSignified, planetOf } from './kp/engine.js';

const T = (key, label, cusp, good, bad, words, questions, qShort) => ({ key, label, cusp, good, bad, words, questions, qShort });

export const TOPICS = [
  T('education', 'Education & stream choice', 4, [4, 9, 11], [3, 8, 12],
    ['10th', '12th', ' inter ', 'intermediate', 'mpc', 'bipc', 'mec', 'cec', 'hec', 'stream', 'study', 'studies', 'exam', 'school', 'college', 'degree', 'course', 'neet', 'jee', 'eamcet', 'engineering', 'medicine', 'mbbs', 'student', 'class', 'marks', 'education', 'subject'],
    ['Which subjects do you enjoy the most and score best in — Maths & Physics, or Biology & Chemistry? Tell me your recent marks if you remember.',
      'Imagine a free Sunday. Would you rather build or fix something, solve a puzzle or try coding — or read about the human body, plants and animals, or help someone who is unwell? Answer honestly, not what others expect from you.',
      'How do you handle pressure — long study hours and competitive exams like JEE / NEET / EAMCET? Do you feel calm, or do you get anxious? And is anyone at home expecting a particular stream for you?',
      'One astrology question to fine-tune your chart: when did your 10th exams start (month and year), and is your birth time taken from a birth certificate or from family memory?'],
    ['favourite subjects & marks', 'natural interests', 'pressure & family expectations', 'exam date & birth-time source']),
  T('career', 'Career & profession', 10, [2, 6, 10, 11], [5, 8, 12],
    ['job', 'jobs', 'career', 'carrer', 'carear', 'carrear', 'carier', 'carreer', 'creer', 'work', 'naukri', 'udyogam', 'promotion', 'business', 'office', 'salary', 'profession', 'government', 'govt', 'startup', 'software', 'company', 'transfer', 'interview', 'unemployed', 'employment'],
    ['Tell me about your current situation — are you studying, working (in which field and for how long), or looking for a job?',
      'What kind of work makes you lose track of time — working with people, with numbers and systems, creating things, or leading and taking decisions? Do you prefer a stable job or the risk and freedom of business?',
      'When you face a setback at work — a rejection, criticism or a missed promotion — how do you usually react: try again immediately, step back and rethink, or feel low for a while? What keeps you going?',
      'One astrology question to verify your chart: when did you start your first job or finish your studies (month and year)? And is your birth time from a birth certificate?'],
    ['current situation', 'work style & risk appetite', 'how you handle setbacks', 'first job date & birth-time source']),
  T('marriage', 'Marriage & relationships', 7, [2, 7, 11], [1, 6, 10, 12],
    ['marriage', 'mariage', 'marrage', 'marraige', 'pelli', 'shaadi', 'shadi', 'vivah', 'marry', 'wedding', 'spouse', 'love', 'relationship', 'partner', 'husband', 'wife', 'match', 'alliance', 'divorce', 'engagement'],
    ['Are you looking for marriage timing, or is this about an existing relationship / proposal?',
      'What matters most to you in a life partner — and is it a love or an arranged marriage that you are hoping for?',
      'How do you usually handle disagreements with people close to you — do you talk it out, go quiet, or give in to keep the peace? Do you find it easy to share your feelings?',
      'One astrology question: has any proposal, engagement or serious relationship happened before — roughly when (month and year)? And is your birth time from a certificate?'],
    ['marriage question', 'partner expectations', 'conflict & feelings', 'past relationship date & birth-time source']),
  T('health', 'Health & well-being', 1, [1, 5, 11], [6, 8, 12],
    ['health', 'helth', 'arogyam', 'disease', 'illness', 'sick', 'stress', 'anxiety', 'sleep', 'hospital', 'surgery', 'pain', 'depression', 'tension', 'fever', 'sugar', 'diabetes'],
    ["I'm sorry you're going through this. What is troubling you — physical health, stress / sleep, or worry about a family member?",
      'How are your daily routine, sleep and food habits these days? Do you get time to rest?',
      'When you feel low or worried, what do you usually do — talk to someone, keep it inside, or distract yourself? Do you have someone you trust to talk to?',
      'One astrology question: when did this problem start (month and year)? And is your birth time from a birth certificate?'],
    ['health concern', 'routine & habits', 'emotional support', 'start date & birth-time source']),
  T('finance', 'Money, wealth & investments', 2, [2, 6, 11], [5, 8, 12],
    ['money', 'finance', 'financial', 'wealth', 'welth', 'dabbu', 'paisa', 'rich', 'loan', 'debt', 'investment', 'invest', 'savings', 'income', 'profit', 'loss', 'stock', 'shares'],
    ['What is your money question — income growth, clearing debts, or an investment decision?',
      'Are you more of a saver or a spender? How do you feel when you take a financial risk — excited or nervous?',
      'What does financial success mean to you — security for family, freedom, status, or something else? What worries you most about money right now?',
      'One astrology question: when did you have your biggest gain or loss so far (month and year)? And is your birth time from a certificate?'],
    ['money question', 'saver or spender', 'what money means to you', 'past gain/loss date & birth-time source']),
  T('foreign', 'Foreign travel & settlement', 12, [3, 9, 12], [4, 11],
    ['abroad', 'foreign', 'visa', 'usa', ' uk ', 'canada', 'australia', 'germany', 'settle', 'onsite', 'ms ', 'immigration', 'travel'],
    ['Is it for studies, a job, or settling abroad — and which country are you considering?',
      'How do you feel about living far from family for a long time — excited, or worried?',
      'How do you usually adapt to new places and people — quickly, or does it take you time to feel at home?',
      'One astrology question: have you applied for a visa or travelled abroad before — when (month and year)? And is your birth time from a certificate?'],
    ['purpose & country', 'feelings about living abroad', 'adapting to change', 'visa/travel date & birth-time source']),
  T('property', 'Property, house & vehicles', 4, [4, 11, 12], [3, 5],
    ['property', 'house', 'home', 'land', 'flat', 'plot', 'construction', 'vehicle', ' car ', 'apartment'],
    ['Are you planning to buy, build, or sell — and roughly when?',
      'Will this be funded mainly by savings or a loan? How comfortable are you with a long loan commitment?',
      'What does owning this property mean to you and your family — security, comfort, investment, or a dream?',
      'One astrology question: did you move house or buy any property / vehicle earlier — when (month and year)? And is your birth time from a certificate?'],
    ['property plan', 'funding & comfort with loans', 'what the property means', 'past move date & birth-time source']),
  T('children', 'Children & progeny', 5, [2, 5, 11], [1, 4, 10],
    ['child', 'children', 'baby', 'pregnancy', 'pregnant', 'kids', ' son ', 'daughter', 'conceive', 'progeny'],
    ["Is your question about having a child, or about your children's education / future?",
      'How long have you been hoping for this, and how are you and your partner coping emotionally?',
      'Who supports you most through this — your partner, family or friends? Do you feel pressure from others?',
      'One astrology question: when did you get married (month and year)? And is your birth time from a certificate?'],
    ['children question', 'time & emotions', 'support & pressure', 'marriage date & birth-time source']),
];
export const GENERAL = T('general', 'General life reading', 1, [1, 2, 10, 11], [6, 8, 12], [],
  ['Tell me a little about yourself — what do you do now (study, job, business, home) and what is happening in your life these days?',
    'What would you most like clarity on in the next two years — and what is one thing you feel is holding you back?',
    "When things don't go your way, how do you usually feel and react? What gives you strength in difficult times?",
    'One astrology question to verify your chart: tell me one important event in your life with its month and year (job, marriage, move, exam). Is your birth time from a certificate?'],
  ['current life', 'what you want clarity on', 'how you handle difficulty', 'life event & birth-time source']);

export const LIFE_AREAS = ['Education & Learning', 'Career & Profession', 'Money, Wealth & Investments', 'Marriage & Relationships', 'Children & Family',
  'Health & Well-being', 'Property, Home & Vehicles', 'Foreign Travel & Settlement', 'Spiritual Growth & Inner Peace'];
const LIFE_AREA_KEYS = ['education', 'career', 'finance', 'marriage', 'children', 'health', 'property', 'foreign', 'general'];

const HOUSE_MEANING = { 1: 'self, health and personality', 2: 'wealth, family and speech', 3: 'courage, siblings and short travel', 4: 'home, mother, property and basic education',
  5: 'children, intelligence, romance and speculation', 6: 'service, competition, debts and health issues', 7: 'marriage and partnerships', 8: 'obstacles, inheritance and sudden events',
  9: 'fortune, father, higher learning and long journeys', 10: 'career, status and karma', 11: 'gains, friends and fulfilment of desires', 12: 'expenses, losses, foreign lands and liberation' };
export const NATURE = { Sun: 'authority, confidence and government links', Moon: 'emotions, the mind and public dealings', Mars: 'energy, technical skill and decisive action',
  Mercury: 'intellect, calculation, trade and communication', Jupiter: 'wisdom, teaching, expansion and blessings', Venus: 'comfort, relationships, art and luxury',
  Saturn: 'discipline, patience, hard work and long-term structure', Rahu: 'ambition, technology, foreign links and unconventional paths', Ketu: 'research, detachment, healing and spiritual insight' };

export const ordSuffix = (n) => (n % 100 >= 11 && n % 100 <= 13 ? 'th' : ({ 1: 'st', 2: 'nd', 3: 'rd' }[n % 10] || 'th'));
export const ord = (n) => n + ordSuffix(n);
const trim = (s, n) => { s = String(s).replace(/\s+/g, ' ').trim(); return s.length <= n ? s : s.slice(0, n - 1) + '…'; };
export const joinHouses = (h) => (h.length ? [...h].sort((a, b) => a - b).join(', ') : 'none');
const containsAny = (s, ...w) => w.some((x) => s.includes(x));
const firstName = (u) => String(u.fullName || '').split(/\s+/)[0];
const cap = (s) => s.charAt(0).toUpperCase() + s.slice(1);

const byKey = (key) => TOPICS.find((t) => t.key === key) || GENERAL;
const looksLikeQuestion = (low) => low.includes('?') || low.length > 60;
const topicLabel = (t, low) => (t.key === 'education' && (low.includes('mpc') || low.includes('bipc') || low.includes('10th')) ? 'Education: which stream after 10th (MPC vs BiPC)' : t.label);
const qLabel = (i, t) => `Question ${i + 1} of ${t.questions.length} (${i === t.questions.length - 1 ? 'astrology question' : i === 0 ? 'about you' : 'about how you think & feel'}): `;

function lev(a, b) {
  let prev = Array.from({ length: b.length + 1 }, (_, j) => j), cur = new Array(b.length + 1);
  for (let i = 1; i <= a.length; i++) {
    cur[0] = i;
    for (let j = 1; j <= b.length; j++) cur[j] = Math.min(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + (a[i - 1] === b[j - 1] ? 0 : 1));
    [prev, cur] = [cur, prev];
  }
  return prev[b.length];
}

/** Topic from keywords, tolerant of spelling mistakes (e.g. "carrear", "marrige", "helth"). */
export function detect(low) {
  let best = null, bestN = 0;
  const words = low.replace(/[^a-z0-9 ]/g, ' ').trim().split(/\s+/);
  for (const t of TOPICS) {
    let n = 0;
    for (const kw of t.words) {
      if (low.includes(kw)) { n += 2; continue; }
      const k = kw.trim();
      if (k.length < 5 || k.includes(' ')) continue;
      if (words.some((w) => w.length >= 4 && Math.abs(w.length - k.length) <= 2 && lev(w, k) <= (k.length >= 7 ? 2 : 1))) n += 1;
    }
    if (n > bestN) { bestN = n; best = t; }
  }
  return best;
}

function ack(low) {
  if (containsAny(low, "don't know", 'dont know', 'not sure', 'confused')) return "That's completely okay — being unsure is normal, and it is exactly why we are talking.";
  if (containsAny(low, 'stress', 'worried', 'scared', 'sad', 'anxious')) return 'Thank you for sharing that honestly — those feelings are natural, and we will find a way forward together.';
  if (low.length < 15) return 'Noted, thank you.';
  return 'Thank you, that helps me understand you much better.';
}

const userAnswers = (history) => history.filter((m) => m.role === 'user').map((m) => m.text);

function extractFacts(msg) {
  const f = [], low = msg.toLowerCase();
  let m = msg.match(/(\d{1,2})\s*(st|nd|rd|th)?\s*(class|standard|std|grade)/i);
  if (m) f.push(`Is studying in ${m[1]}${ordSuffix(Number(m[1]))} class`);
  m = msg.match(/\b(\d{1,2})\s*(years? old|yrs|years)\b/i);
  if (m) f.push(`Is ${m[1]} years old`);
  m = msg.match(/\b(?:i am|i'm|im)\s+(?:a|an)\s+([a-z ]{3,40})/i);
  if (m) f.push('Describes themself as a ' + m[1].trim().split(/\s+(and|but|who|from)\s+/)[0]);
  if (low.includes('mpc')) f.push('Is considering MPC (Maths, Physics, Chemistry)');
  if (low.includes('bipc')) f.push('Is considering BiPC (Biology, Physics, Chemistry)');
  return f;
}

// ================================================================== analysis
function cuspVerdict(c, t) {
  const cusp = c.cusps[t.cusp - 1];
  const sig = housesSignified(c, cusp.subLord);
  const good = sig.filter((h) => t.good.includes(h)), bad = sig.filter((h) => t.bad.includes(h));
  const strength = good.length >= 2 ? 'clearly promised' : good.length === 1 ? 'promised, with steady effort' : 'possible, but it needs patience and the right timing';
  return `your ${ord(t.cusp)} cusp sub lord ${cusp.subLord} signifies houses ${joinHouses(sig)}, so success here is ${strength}` + (bad.length ? `; its link with house ${joinHouses(bad)} shows some delays or extra effort along the way.` : '.');
}

/** Upcoming antardashas (next ~10 years) whose lords signify the good houses. */
export function favourablePeriods(c, good, max) {
  const today = new Date().toISOString().slice(0, 10), limit = new Date(Date.now() + 10 * 365.25 * 86400000).toISOString().slice(0, 10);
  const out = [];
  for (const md of c.dasha.periods) {
    if (md.end < today || md.start > limit) continue;
    for (const ad of md.antardashas) {
      if (ad.end < today || ad.start > limit) continue;
      const adHits = housesSignified(c, ad.lord).filter((h) => good.includes(h)).length;
      const hits = adHits + housesSignified(c, md.lord).filter((h) => good.includes(h)).length;
      if (hits >= 2 && adHits > 0) out.push(`${md.lord}–${ad.lord} (${ad.start.slice(0, 7)} to ${ad.end.slice(0, 7)})`);
      if (out.length >= max) return out.join(', ');
    }
  }
  return out.length ? out.join(', ') : `the periods of ${c.currentDasha.mahadasha} after ${c.currentDasha.adEnd}`;
}

function careerFit(a) {
  const fit = [];
  if (containsAny(a, 'problem', 'system', 'logic', 'software', 'tester', 'testing', 'coding', 'computer', 'it company', 'data', 'numbers'))
    fit.push(a.includes('test') ? 'moving from testing towards automation, development or data roles' : 'technical, analytical or data-driven roles');
  if (containsAny(a, 'people', 'talk', 'teach', 'help', 'customer', 'team')) fit.push('people-facing roles such as training, HR, sales or client management');
  if (containsAny(a, 'creat', 'design', 'art', 'write', 'music', 'content')) fit.push('creative roles such as design, content or media');
  if (containsAny(a, 'lead', 'decision', 'manage', 'own business', 'business')) fit.push('leadership or business roles');
  const stable = containsAny(a, 'stable job', 'stable', 'secure', 'government') ? ' Since you prefer stability, aim for a well-established company or a government / PSU path rather than a risky start-up.'
    : containsAny(a, 'business', 'risk', 'freedom', 'own') ? ' Since you value freedom, a side business or freelancing alongside your job is a safe way to test it.' : '';
  return (fit.length ? 'From what you told me, your best direction is ' + fit.join(' or ') + '.' : 'Choose roles that match the work style you described.') + stable;
}

function psychAdvice(key, a) {
  const stress = containsAny(a, 'stress', 'pressure', 'scared', 'fear', 'worried', 'anxious', 'tension', 'difficult', "can't", 'cannot');
  const family = containsAny(a, 'expect', 'wants me', 'want me', 'force', 'forcing', 'insist', 'pressure from') && containsAny(a, 'father', 'mother', 'parents', 'family', 'dad', 'mom', 'amma', 'nanna');
  const lowAfter = containsAny(a, 'feel low', 'feel sad', 'discouraged', 'give up', 'depressed', 'upset');
  const base = {
    education: 'choose the stream you enjoy studying every day — interest sustains you through two demanding years. Keep a simple daily timetable, revise weekly, practise previous papers, and protect your sleep before exams.',
    career: careerFit(a) + ' Upgrade one concrete skill in the next 3 months and make your big moves (job change, interviews, promotion talks) in the favourable periods above.',
    marriage: 'be clear about your top three values in a partner, communicate openly with family, and give extra weight to proposals that come during the favourable periods.',
    health: 'keep a fixed sleep and meal routine, take a short walk daily, and please do consult a doctor for any physical symptoms — astrology supports, it does not replace medical care.',
    finance: 'build an emergency fund first, avoid speculation in weak periods, and plan major investments in the favourable periods.',
    foreign: 'prepare documents and exams early so you are ready to act when the favourable period opens.',
    property: 'verify documents carefully and finalise registration in a favourable period.',
    children: "stay hopeful, support each other emotionally, and follow your doctor's advice alongside these timings.",
  }[key] || 'focus on one clear goal at a time and act during the favourable periods.';
  return base
    + (lowAfter ? ' You said setbacks make you feel low for a while — that is human; what matters is that you get up again, and you do. Keep a small list of your past wins to read on difficult days.' : '')
    + (stress ? ' You mentioned feeling pressure — that is normal. Break big goals into small daily steps and talk to someone you trust when it feels heavy.' : '')
    + (family ? ' Since family expectations are involved, share your feelings with them calmly; a decision you believe in will carry you further.' : '');
}

function topicAnswer(u, c, t, answers) {
  const cusp = c.cusps[t.cusp - 1];
  const v = cuspVerdict(c, t);
  return `Here is what your chart says, ${firstName(u)}.\n\nKP view: ${cap(v)} ${cusp.subLord} brings the qualities of ${NATURE[cusp.subLord]} into matters of ${HOUSE_MEANING[t.cusp]}.\n\n`
    + `Timing: the most supportive periods ahead are ${favourablePeriods(c, t.good, 3)}.\n\nGuidance: ${psychAdvice(t.key, answers)}`;
}

const MPC_WORDS = ['math', 'physics', 'number', 'calculat', 'puzzle', 'build', 'fix', 'machine', 'computer', 'coding', 'code', 'engineer', 'technolog', 'robot', 'jee', 'iit', 'logic', 'electronics'];
const BIPC_WORDS = ['biology', 'bio ', 'botany', 'zoology', 'doctor', 'medicine', 'mbbs', 'neet', 'human body', 'plant', 'animal', 'nature', 'help', 'care', 'nurse', 'health', 'pharma', 'life science', 'hospital'];
function interestScore(a, words) {
  let s = 0;
  for (const w of words) {
    let i = a.indexOf(w);
    while (i >= 0) {
      const before = a.slice(Math.max(0, i - 14), i);
      s += containsAny(before, 'not ', "don't", 'dont', 'hate', 'weak', 'no ', 'bad at') ? -1 : 1;
      i = a.indexOf(w, i + w.length);
    }
  }
  return s;
}

function educationAnswer(u, c, answers) {
  const mpcPl = ['Mercury', 'Mars', 'Saturn', 'Rahu', 'Sun'], bipcPl = ['Moon', 'Venus', 'Jupiter', 'Ketu'];
  let chartMpc = 0, chartBipc = 0;
  const why = [];
  for (const h of [4, 5, 9, 10]) {
    const cusp = c.cusps[h - 1];
    for (const p of [cusp.subLord, planetOf(c, cusp.subLord).starLord]) { if (mpcPl.includes(p)) chartMpc++; if (bipcPl.includes(p)) chartBipc++; }
    why.push(`${ord(h)} sub lord ${cusp.subLord}`);
  }
  for (const h of [4, 9]) { const sig = housesSignified(c, c.cusps[h - 1].subLord); if (sig.includes(6) && (sig.includes(8) || sig.includes(12))) chartBipc++; }
  const intMpc = interestScore(answers, MPC_WORDS), intBipc = interestScore(answers, BIPC_WORDS);
  const mpc = chartMpc + 1.5 * intMpc, bipc = chartBipc + 1.5 * intBipc;
  let rec;
  if (Math.abs(mpc - bipc) < 1.5 && intMpc <= 0 && intBipc <= 0) {
    rec = "Your chart supports both science streams fairly evenly, and your answers don't show a strong pull either way. In that case choose the subjects you genuinely enjoy studying every day — and it is also worth considering MEC/CEC (commerce) if numbers and business interest you more than science.";
  } else {
    const isMpc = mpc >= bipc;
    const chartFor = isMpc ? chartMpc : chartBipc, chartAgainst = isMpc ? chartBipc : chartMpc, interestFor = isMpc ? intMpc : intBipc;
    const chartPart = chartFor === chartAgainst
      ? `Your chart is evenly balanced (${chartFor} technical and ${chartAgainst} life-science indications from the education sub lords), so your clear interest is the deciding factor.`
      : chartFor > chartAgainst
        ? `Your chart supports this: the education sub lords and their star lords give ${chartFor} ${isMpc ? 'technical / analytical' : 'healing / life-science'} indications against ${chartAgainst}.`
        : `Your chart also shows ${chartAgainst} ${isMpc ? 'life-science' : 'technical'} indications against ${chartFor}, so you have real ability on both sides — but in KP the native's genuine interest during a supportive dasha decides the stream, and your interest is clear.`;
    const interestPart = interestFor > 0
      ? (isMpc ? 'Your answers show a natural pull towards Maths, Physics, building and problem-solving.' : 'Your answers show genuine interest in Biology and in caring for others.')
      : (isMpc ? 'With steady practice in Maths you can do very well here.' : 'With good teachers, Biology can become your strength.');
    rec = `My recommendation: ${isMpc ? 'MPC' : 'BiPC'}. ${interestPart} ${chartPart} ` + (isMpc ? 'Engineering, computer science, technology, architecture or data-related careers suit you.' : 'Medicine, pharmacy, biotechnology, agriculture or allied health careers suit you.');
  }
  const c4 = c.cusps[3], s4 = housesSignified(c, c4.subLord);
  const success = s4.includes(11) || s4.includes(9) ? `Your 4th cusp sub lord ${c4.subLord} connects to houses ${joinHouses(s4)}, which promises good results in studies.`
    : `Your 4th cusp sub lord ${c4.subLord} signifies houses ${joinHouses(s4)} — results come through consistent effort rather than last-minute study.`;
  return `Thank you, ${firstName(u)}. Here is my reading, combining your chart (${why.join(', ')}) with what you told me:\n\n${rec}\n\n${success}\n\nTiming: supportive study and exam periods ahead are ${favourablePeriods(c, [4, 9, 11], 3)}.\n\nGuidance: ${psychAdvice('education', answers)}`;
}

export const remedies = (c) => `Simple KP remedies for you: strengthen ${c.ascendant.subLord} (your ascendant sub lord) and ${c.rulingPlanets.moonStarLord} (your Moon's star lord) through discipline and charity on their weekdays, respect elders and teachers for Jupiter's grace, and practise a few minutes of meditation daily to steady the Moon (the mind).`;

// ================================================================== chat
/** state: { topicKey, topic, stage, ready } (mutated). Returns { reply, newMemories, readyForReport, topic }. */
export function reply(u, c, memory, history, st) {
  const first = firstName(u);
  let msg = '';
  for (let i = history.length - 1; i >= 0; i--) if (history[i].role === 'user') { msg = history[i].text; break; }
  const low = ' ' + msg.toLowerCase() + ' ';
  const mem = extractFacts(msg);
  const detected = detect(low);

  if (!st.topicKey || (st.ready && detected && detected.key !== st.topicKey)) {
    let t = detected;
    if (!t && !st.topicKey && st.stage === 0 && !looksLikeQuestion(low)) {
      st.stage = -1;
      return { reply: `Thank you for sharing, ${first}. So that I read the right part of your chart — what would you like guidance on today? For example: studies / which stream to choose, career or job, marriage, health, money, foreign travel, property or children. You can also just tell me what is worrying you.`, newMemories: mem, readyForReport: false, topic: '' };
    }
    if (!t) t = GENERAL;
    st.topicKey = t.key; st.topic = topicLabel(t, low); st.stage = 0; st.ready = false;
    mem.push('Main concern: ' + st.topic);
    const cusp = c.cusps[t.cusp - 1];
    const intro = `I understand, ${first} — you want guidance on ${st.topic.toLowerCase().replace('mpc vs bipc', 'MPC vs BiPC')}. In KP astrology this is judged mainly from your ${ord(t.cusp)} cusp — it falls in ${cusp.sign} with ${cusp.subLord} as its sub lord, and you are running ${c.currentDasha.mahadasha} Mahadasha / ${c.currentDasha.antardasha} Antardasha.\n\nBefore I give my answer, I'd like to understand you better — I'll ask ${t.questions.length} short questions.\n\n`;
    return { reply: intro + qLabel(0, t) + t.questions[0], newMemories: mem, readyForReport: false, topic: st.topic };
  }

  const t = byKey(st.topicKey);
  if (!st.ready) {
    if (st.stage >= 0 && st.stage < t.qShort.length && msg.trim()) mem.push(`Said about ${t.qShort[st.stage]}: ${trim(msg, 140)}`);
    st.stage++;
    if (st.stage < t.questions.length) return { reply: ack(low) + '\n\n' + qLabel(st.stage, t) + t.questions[st.stage], newMemories: mem, readyForReport: false, topic: st.topic };
    st.ready = true;
    const answers = userAnswers(history).join(' ').toLowerCase();
    const analysis = t.key === 'education' ? educationAnswer(u, c, answers) : topicAnswer(u, c, t, answers);
    return { reply: analysis + '\n\nI have saved these points in my memory. Tap "Generate my report" to get a personalised PDF built from our conversation — or ask me anything else.', newMemories: mem, readyForReport: true, topic: st.topic };
  }

  let follow;
  if (containsAny(low, 'when', 'timing', 'which year', 'eppudu', 'kab')) follow = `On timing: ${favourablePeriods(c, t.good, 3)}.`;
  else if (containsAny(low, 'remedy', 'remedies', 'upay', 'pariharam')) follow = remedies(c);
  else follow = `Good question, ${first}. Looking again at your chart for ${st.topic.toLowerCase()}: ${cuspVerdict(c, t)} The most supportive periods ahead are ${favourablePeriods(c, t.good, 2)}.`;
  return { reply: follow + '\n\nYou can generate your report any time, or book a 30-minute live consultation for deeper questions.', newMemories: mem, readyForReport: true, topic: st.topic };
}

export function greeting(u, c, memory) {
  const last = [...memory].reverse().find((m) => m.startsWith('Main concern: '));
  const remembered = last ? ` Last time we discussed ${last.slice(14)} — I still remember what you told me.` : memory.length ? ' I remember what you shared with me earlier.' : '';
  return `Namaste ${firstName(u)} Welcome to Astrology Talk. I have prepared your KP chart: ${c.ascendant.sign} ascendant, Moon in ${c.nakshatra} nakshatra, and you are running ${c.currentDasha.mahadasha} Mahadasha / ${c.currentDasha.antardasha} Antardasha.${remembered}\n\nTell me — why are you consulting the astrologer today? What is on your mind? (For example: studies, career, marriage, health, money, foreign travel, property or children.)`;
}

// ================================================================== report content
function lifeArea(c, key, first, answers) {
  const t = key === 'general' ? T('general', 'Spiritual growth', 12, [5, 9, 12], [2, 11], [], [], []) : byKey(key);
  const cusp = c.cusps[t.cusp - 1];
  const occ = c.planets.filter((p) => p.house === t.cusp).map((p) => p.name);
  const hs = c.houseSignificators[t.cusp - 1];
  const sigs = [...new Set([...hs.A, ...hs.B, ...hs.C, ...hs.D])];
  return `For ${t.label.toLowerCase()}, KP looks at the ${ord(t.cusp)} cusp together with houses ${joinHouses(t.good)}. Your ${ord(t.cusp)} cusp falls in ${cusp.sign} (${cusp.dms}), in the star of ${cusp.starLord}. ${cap(cuspVerdict(c, t))}\n\n`
    + `${cusp.subLord} brings ${NATURE[cusp.subLord]} into this area of life. `
    + (occ.length ? `${occ.join(', ')} ${occ.length > 1 ? 'occupy' : 'occupies'} the house, adding ${occ.map((o) => NATURE[o]).join('; ')}.` : 'No planet occupies the house itself, so results flow through the cusp sub lord and the significators.')
    + ` The planets signifying this house are ${sigs.length ? sigs.join(', ') : 'few'} — their dashas and bhuktis activate it.\n\n`
    + `Timing: the most supportive periods in the coming years are ${favourablePeriods(c, t.good, 3)}.\n\nGuidance for you, ${first}: ${psychAdvice(t.key, answers)}`;
}

export function cuspText(c, h) {
  const cusp = c.cusps[h - 1], sub = cusp.subLord, houses = housesSignified(c, sub);
  const good = houses.filter((x) => [1, 2, 3, 5, 6, 9, 10, 11].includes(x)), hard = houses.filter((x) => x === 8 || x === 12);
  const occ = c.planets.filter((p) => p.house === h).map((p) => p.name);
  return `The ${ord(h)} cusp falls in ${cusp.sign} at ${cusp.dms}, ruled by ${cusp.signLord}, in the star of ${cusp.starLord} and the sub of ${sub}. In KP, the cusp sub lord is the deciding factor: ${sub} brings ${NATURE[sub]} into matters of ${HOUSE_MEANING[h]}.\n\n`
    + `${sub} signifies houses ${joinHouses(houses)}. `
    + (good.length ? `Its connection with houses ${joinHouses(good)} is supportive, especially during the dasha or bhukti of ${sub} and of planets in its star. ` : 'It has limited connection with the houses of gain, so results come through patience and steady effort. ')
    + (hard.length ? `The link with house ${joinHouses(hard)} asks for planning and a measured approach at times.` : '') + '\n\n'
    + (occ.length ? `${occ.join(', ')} ${occ.length > 1 ? 'occupy' : 'occupies'} this house, adding ${occ.map((o) => NATURE[o]).join('; ')}.` : 'No planet occupies this house, so results flow mainly through the cusp sub lord and sign lord.');
}

/** Report content: { hook, summary, consultation[], sections[] } */
export function report(u, c, memory, transcript, topic) {
  const first = firstName(u);
  const lowTopic = String(topic || '').toLowerCase();
  const t = TOPICS.find((x) => lowTopic.includes(x.key)) || (topic ? detect(' ' + lowTopic + ' ') : null) || GENERAL;
  const answers = userAnswers(transcript).join(' ').toLowerCase();
  const lbl = topic && topic.trim() ? topic : 'General life reading';
  let known = `You consulted about: ${lbl}.\n\n`;
  if (memory.length) known += 'What you shared with me:\n' + memory.map((m) => '• ' + m).join('\n') + '\n\n';
  known += `This report combines your answers with a precise KP reading of your chart, so the guidance below is specific to you, ${first}.`;
  const lastAnswer = [...transcript].reverse().find((m) => m.role === 'assistant' && m.text.length > 200)?.text || '';
  const consultation = [
    { title: 'Your Question: ' + lbl, content: known },
    { title: 'KP Analysis & Recommendation', content: t.key === 'education' ? educationAnswer(u, c, answers) : lastAnswer || topicAnswer(u, c, t, answers) },
    { title: 'Timing of Results', content: `In KP, events happen when the dasha, bhukti and antara lords signify the relevant houses. For ${lbl.toLowerCase()} the key houses are ${joinHouses(t.good)}.\n\nYou are running ${c.currentDasha.mahadasha} Mahadasha / ${c.currentDasha.antardasha} Antardasha until ${c.currentDasha.adEnd}. The most favourable periods ahead are: ${favourablePeriods(c, t.good, 4)}. Plan important steps — applications, exams, proposals, purchases — inside these windows.` },
    { title: 'Practical Guidance For You', content: psychAdvice(t.key, answers) + '\n\n' + remedies(c) },
  ];
  const sections = LIFE_AREAS.map((title, i) => ({ title, content: lifeArea(c, LIFE_AREA_KEYS[i], first, answers) }));
  const cusp = c.cusps[t.cusp - 1];
  const hook = {
    headline: `${first}, your ${ord(t.cusp)} cusp holds the answer to your question`,
    teaser: `You asked about ${lbl}. In KP astrology the deciding factor is the sub lord of your ${ord(t.cusp)} cusp — ${cusp.subLord}, sitting in ${cusp.sign}. Combined with your ${c.currentDasha.mahadasha} period and what you told me about yourself, it points to a clear direction — and a specific window of time when things move fastest. The full report reveals exactly what it is, and when...`,
    secrets: ['My clear recommendation for your question — with the KP reasoning', 'The exact dasha windows when results come fastest', 'The planet that controls your success — and how to strengthen it', 'Practical, personal guidance based on your own answers'],
  };
  const summary = `${u.fullName} has ${c.ascendant.sign} rising, Moon in ${c.moonSign} (${c.nakshatra}) and Sun in ${c.sunSign}, calculated with Placidus cusps and the KP ayanamsa (${c.ayanamsa.dms}). This report focuses on: ${lbl}.`;
  return { hook, summary, consultation, sections };
}
