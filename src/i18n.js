// Languages (Indian first, then world) + optional server-side translation
// (Google Cloud Translation key or the smart-answers key). Without a key, the visitor's browser translates.
import Anthropic from '@anthropic-ai/sdk';
import * as env from './env.js';

const L = (code, name, nativeName, font, rtl, indian) => ({ code, name, nativeName, fontFamily: font, rtl, indian });
export const LANGS = [
  L('en', 'English', 'English', 'Noto Sans', false, false),
  L('te', 'Telugu', 'తెలుగు', 'Noto Sans Telugu', false, true), L('hi', 'Hindi', 'हिन्दी', 'Noto Sans Devanagari', false, true),
  L('ta', 'Tamil', 'தமிழ்', 'Noto Sans Tamil', false, true), L('kn', 'Kannada', 'ಕನ್ನಡ', 'Noto Sans Kannada', false, true),
  L('ml', 'Malayalam', 'മലയാളം', 'Noto Sans Malayalam', false, true), L('mr', 'Marathi', 'मराठी', 'Noto Sans Devanagari', false, true),
  L('bn', 'Bengali', 'বাংলা', 'Noto Sans Bengali', false, true), L('gu', 'Gujarati', 'ગુજરાતી', 'Noto Sans Gujarati', false, true),
  L('pa', 'Punjabi', 'ਪੰਜਾਬੀ', 'Noto Sans Gurmukhi', false, true), L('or', 'Odia', 'ଓଡ଼ିଆ', 'Noto Sans Oriya', false, true),
  L('ur', 'Urdu', 'اردو', 'Noto Nastaliq Urdu', true, true), L('as', 'Assamese', 'অসমীয়া', 'Noto Sans Bengali', false, true),
  L('sa', 'Sanskrit', 'संस्कृतम्', 'Noto Sans Devanagari', false, true), L('ne', 'Nepali', 'नेपाली', 'Noto Sans Devanagari', false, true),
  L('gom', 'Konkani', 'कोंकणी', 'Noto Sans Devanagari', false, true), L('mai', 'Maithili', 'मैथिली', 'Noto Sans Devanagari', false, true),
  L('bho', 'Bhojpuri', 'भोजपुरी', 'Noto Sans Devanagari', false, true), L('doi', 'Dogri', 'डोगरी', 'Noto Sans Devanagari', false, true),
  L('sd', 'Sindhi', 'سنڌي', 'Noto Naskh Arabic', true, true), L('mni-Mtei', 'Manipuri (Meitei)', 'ꯃꯤꯇꯩꯂꯣꯟ', 'Noto Sans Meetei Mayek', false, true),
  L('lus', 'Mizo', 'Mizo ṭawng', 'Noto Sans', false, true),
  L('es', 'Spanish', 'Español', 'Noto Sans', false, false), L('fr', 'French', 'Français', 'Noto Sans', false, false), L('de', 'German', 'Deutsch', 'Noto Sans', false, false),
  L('ar', 'Arabic', 'العربية', 'Noto Naskh Arabic', true, false), L('zh-CN', 'Chinese (Simplified)', '简体中文', 'Noto Sans SC', false, false),
  L('zh-TW', 'Chinese (Traditional)', '繁體中文', 'Noto Sans TC', false, false), L('ja', 'Japanese', '日本語', 'Noto Sans JP', false, false),
  L('ko', 'Korean', '한국어', 'Noto Sans KR', false, false), L('ru', 'Russian', 'Русский', 'Noto Sans', false, false), L('pt', 'Portuguese', 'Português', 'Noto Sans', false, false),
  L('it', 'Italian', 'Italiano', 'Noto Sans', false, false), L('nl', 'Dutch', 'Nederlands', 'Noto Sans', false, false), L('tr', 'Turkish', 'Türkçe', 'Noto Sans', false, false),
  L('fa', 'Persian', 'فارسی', 'Noto Naskh Arabic', true, false), L('id', 'Indonesian', 'Bahasa Indonesia', 'Noto Sans', false, false), L('ms', 'Malay', 'Bahasa Melayu', 'Noto Sans', false, false),
  L('th', 'Thai', 'ไทย', 'Noto Sans Thai', false, false), L('vi', 'Vietnamese', 'Tiếng Việt', 'Noto Sans', false, false), L('si', 'Sinhala', 'සිංහල', 'Noto Sans Sinhala', false, false),
  L('my', 'Burmese', 'မြန်မာ', 'Noto Sans Myanmar', false, false), L('km', 'Khmer', 'ខ្មែរ', 'Noto Sans Khmer', false, false), L('lo', 'Lao', 'ລາວ', 'Noto Sans Lao', false, false),
  L('tl', 'Filipino', 'Filipino', 'Noto Sans', false, false), L('sw', 'Swahili', 'Kiswahili', 'Noto Sans', false, false), L('iw', 'Hebrew', 'עברית', 'Noto Sans Hebrew', true, false),
  L('el', 'Greek', 'Ελληνικά', 'Noto Sans', false, false), L('pl', 'Polish', 'Polski', 'Noto Sans', false, false), L('uk', 'Ukrainian', 'Українська', 'Noto Sans', false, false),
  L('ro', 'Romanian', 'Română', 'Noto Sans', false, false), L('hu', 'Hungarian', 'Magyar', 'Noto Sans', false, false), L('cs', 'Czech', 'Čeština', 'Noto Sans', false, false),
  L('sv', 'Swedish', 'Svenska', 'Noto Sans', false, false), L('no', 'Norwegian', 'Norsk', 'Noto Sans', false, false), L('da', 'Danish', 'Dansk', 'Noto Sans', false, false),
  L('fi', 'Finnish', 'Suomi', 'Noto Sans', false, false), L('ka', 'Georgian', 'ქართული', 'Noto Sans Georgian', false, false), L('hy', 'Armenian', 'Հայերեն', 'Noto Sans Armenian', false, false),
  L('am', 'Amharic', 'አማርኛ', 'Noto Sans Ethiopic', false, false), L('af', 'Afrikaans', 'Afrikaans', 'Noto Sans', false, false), L('zu', 'Zulu', 'isiZulu', 'Noto Sans', false, false),
];
export const lang = (code) => LANGS.find((l) => l.code === code) || null;

export const hasWords = (t) => /[A-Za-z]{2,}/.test(t || '');

export async function serverTranslationAvailable() { return (await env.has('GOOGLE_TRANSLATE_API_KEY')) || (await env.has('ANTHROPIC_API_KEY')); }

/** Translate unique texts; returns { original: translated }. */
export async function translate(texts, target) {
  const todo = [...new Set(texts)].filter(hasWords);
  const out = {};
  if (!todo.length) return out;
  if (await env.has('GOOGLE_TRANSLATE_API_KEY')) {
    const key = await env.get('GOOGLE_TRANSLATE_API_KEY');
    for (let i = 0; i < todo.length;) {
      const batch = []; let chars = 0;
      while (i < todo.length && batch.length < 100 && (batch.length === 0 || chars + todo[i].length <= 25000)) { chars += todo[i].length; batch.push(todo[i++]); }
      const form = new URLSearchParams({ target, source: 'en', format: 'text' });
      batch.forEach((t) => form.append('q', t));
      const r = await fetch('https://translation.googleapis.com/language/translate/v2?key=' + encodeURIComponent(key), { method: 'POST', body: form, signal: AbortSignal.timeout(90000) });
      if (!r.ok) throw new Error(`Translation failed (${r.status}): ${await r.text()}`);
      const arr = (await r.json()).data.translations;
      batch.forEach((t, k) => { if (arr[k]) out[t] = arr[k].translatedText; });
    }
    return out;
  }
  if (await env.has('ANTHROPIC_API_KEY')) {
    const l = lang(target);
    const client = new Anthropic({ apiKey: await env.get('ANTHROPIC_API_KEY') });
    for (let i = 0; i < todo.length;) {
      const batch = []; let chars = 0;
      while (i < todo.length && batch.length < 60 && (batch.length === 0 || chars + todo[i].length <= 12000)) { chars += todo[i].length; batch.push(todo[i++]); }
      const prompt = `Translate each of the following ${batch.length} English texts from a KP astrology report into ${l.name} (${l.nativeName}). Keep numbers, dates, degree values and abbreviations like Su, Mo, KP unchanged; use natural, respectful astrology vocabulary. Return exactly ${batch.length} translations in the same order.\n\n` + batch.map((t, k) => `[${k + 1}] ${t}`).join('\n');
      const msg = await client.messages.create({
        model: await env.get('AI_MODEL', 'claude-opus-5-5'), max_tokens: 16000,
        output_config: { format: { type: 'json_schema', schema: { type: 'object', additionalProperties: false, required: ['translations'], properties: { translations: { type: 'array', items: { type: 'string' } } } } } },
        messages: [{ role: 'user', content: prompt }],
      });
      const text = msg.content.filter((b) => b.type === 'text').map((b) => b.text).join('');
      const res = JSON.parse(text).translations || [];
      batch.forEach((t, k) => { if (res[k]) out[t] = res[k]; });
    }
    return out;
  }
  throw new Error('No translation key configured');
}
