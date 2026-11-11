// Vedic knowledge base + extra calculations (divisional charts, panchang, transits) for the full report.
import { createRequire } from 'module';
// CommonJS build: its ESM build lacks a "type": "module" marker and fails on Vercel's Node
const A = createRequire(import.meta.url)('astronomy-engine');
import { SIGNS, SIGN_LORDS, NAKSHATRAS, kpAyanamsa, tropicalLongitude, norm, planetOf } from './engine.js';

export { SIGNS };

export const SIGN_TRAITS = [
  'Aries is a fiery, movable sign ruled by Mars: pioneering, courageous, direct and quick to act, with a natural urge to lead and start new things.',
  'Taurus is an earthy, fixed sign ruled by Venus: steady, patient, practical and loyal, with a love of comfort, beauty, good food and financial security.',
  'Gemini is an airy, dual sign ruled by Mercury: curious, witty, communicative and versatile, learning fast and enjoying variety and conversation.',
  'Cancer is a watery, movable sign ruled by the Moon: caring, sensitive, protective and intuitive, deeply attached to home, mother and family.',
  'Leo is a fiery, fixed sign ruled by the Sun: generous, dignified, creative and confident, with natural authority and a wish to be respected.',
  'Virgo is an earthy, dual sign ruled by Mercury: analytical, precise, helpful and health-conscious, with a talent for detail, service and improvement.',
  'Libra is an airy, movable sign ruled by Venus: balanced, diplomatic, artistic and fair-minded, valuing partnership, harmony and justice.',
  'Scorpio is a watery, fixed sign ruled by Mars: intense, secretive, determined and research-minded, with great inner strength and transformative power.',
  'Sagittarius is a fiery, dual sign ruled by Jupiter: optimistic, philosophical, honest and freedom-loving, drawn to higher learning, travel and dharma.',
  'Capricorn is an earthy, movable sign ruled by Saturn: disciplined, ambitious, responsible and patient, building success steadily over time.',
  'Aquarius is an airy, fixed sign ruled by Saturn: humanitarian, inventive, independent and future-oriented, thinking for the larger group.',
  'Pisces is a watery, dual sign ruled by Jupiter: compassionate, imaginative, spiritual and selfless, with strong intuition and artistic sensitivity.'];
export const ELEMENT = ['Fire', 'Earth', 'Air', 'Water'];
export const QUALITY = ['Movable', 'Fixed', 'Dual'];

const nak = (deity, symbol, gana, traits) => ({ deity, symbol, gana, traits });
export const NAKS = [
  nak('Ashwini Kumaras', "Horse's head", 'Deva', 'quick, energetic, helpful and fond of healing and new beginnings'),
  nak('Yama', 'Yoni', 'Manushya', 'determined, responsible and able to carry heavy burdens with strong will'),
  nak('Agni', 'Razor / flame', 'Rakshasa', 'sharp, courageous, purifying and outspoken, with leadership drive'),
  nak('Brahma', 'Chariot', 'Manushya', 'attractive, creative, stable and fond of comfort, beauty and growth'),
  nak('Soma', "Deer's head", 'Deva', 'gentle, curious, searching and fond of travel and learning'),
  nak('Rudra', 'Teardrop', 'Manushya', 'intense, intellectual and transformative, growing through challenges'),
  nak('Aditi', 'Bow and quiver', 'Deva', 'kind, optimistic, generous and able to bounce back and renew'),
  nak('Brihaspati', "Cow's udder", 'Deva', 'nurturing, wise, dutiful and spiritually inclined - one of the most auspicious stars'),
  nak('Nagas', 'Coiled serpent', 'Rakshasa', 'perceptive, strategic, persuasive and deeply intuitive'),
  nak('Pitris', 'Royal throne', 'Rakshasa', 'dignified, traditional, proud of lineage and drawn to leadership'),
  nak('Bhaga', 'Front legs of a bed', 'Manushya', 'pleasant, creative, social and fond of relaxation and romance'),
  nak('Aryaman', 'Back legs of a bed', 'Manushya', 'reliable, generous, helpful and good at partnerships and contracts'),
  nak('Savitar', 'Open hand', 'Deva', 'skilful, clever, hard-working and good with the hands and crafts'),
  nak('Vishwakarma', 'Bright jewel', 'Rakshasa', 'artistic, stylish, inventive and fond of design and building'),
  nak('Vayu', 'Young sprout in the wind', 'Deva', 'independent, flexible, diplomatic and business-minded'),
  nak('Indra-Agni', 'Triumphal arch', 'Rakshasa', 'goal-oriented, ambitious and persistent until success is achieved'),
  nak('Mitra', 'Lotus', 'Deva', 'friendly, devoted, cooperative and successful in groups and abroad'),
  nak('Indra', 'Earring / umbrella', 'Rakshasa', 'protective, responsible, resourceful and capable of authority'),
  nak('Nirriti', 'Bunch of roots', 'Rakshasa', 'investigative, philosophical, getting to the root of things'),
  nak('Apas', 'Winnowing basket', 'Manushya', 'confident, persuasive, invincible in spirit and fond of purification'),
  nak('Vishwadevas', 'Elephant tusk', 'Manushya', 'righteous, principled, persistent and achieving lasting victory'),
  nak('Vishnu', 'Ear / three footprints', 'Deva', 'attentive listener, learned, well-connected and respected'),
  nak('Eight Vasus', 'Drum', 'Rakshasa', 'musical, wealthy, energetic and good in group activities'),
  nak('Varuna', 'Empty circle', 'Rakshasa', 'independent, healing, secretive and scientific in thinking'),
  nak('Aja Ekapada', 'Front of a funeral cot', 'Manushya', 'intense, idealistic, passionate and transformative'),
  nak('Ahir Budhnya', 'Back of a funeral cot', 'Manushya', 'wise, calm, disciplined and spiritually deep'),
  nak('Pushan', 'Fish / drum', 'Deva', 'gentle, caring, protective, creative and spiritually evolved')];
export const nakIdx = (name) => NAKSHATRAS.indexOf(name);

const pi = (karaka, body, gem, mantra, day, color, number, metal, deity, exalt, debil) => ({ karaka, body, gem, mantra, day, color, number, metal, deity, exalt, debil });
export const PLANET = {
  Sun: pi('soul, father, authority, government, vitality, confidence and reputation', 'heart, bones, eyes, spine', 'Ruby', 'Om Hraam Hreem Hraum Sah Suryaya Namah', 'Sunday', 'Orange / copper red', 1, 'Copper / gold', 'Surya', 'Aries', 'Libra'),
  Moon: pi('mind, emotions, mother, public, comforts, travel and fluids', 'mind, chest, blood, body fluids', 'Pearl', 'Om Shraam Shreem Shraum Sah Chandraya Namah', 'Monday', 'White / silver', 2, 'Silver', 'Chandra / Parvati', 'Taurus', 'Scorpio'),
  Mars: pi('energy, courage, siblings, property, land, engineering, police and surgery', 'muscles, blood, marrow', 'Red Coral', 'Om Kraam Kreem Kraum Sah Bhaumaya Namah', 'Tuesday', 'Red', 9, 'Copper', 'Hanuman / Subrahmanya', 'Capricorn', 'Cancer'),
  Mercury: pi('intellect, speech, education, trade, accounts, writing, communication and friends', 'nervous system, skin, lungs', 'Emerald', 'Om Braam Breem Braum Sah Budhaya Namah', 'Wednesday', 'Green', 5, 'Bronze', 'Vishnu', 'Virgo', 'Pisces'),
  Jupiter: pi('wisdom, teachers, children, wealth, dharma, law, banking and blessings', 'liver, fat, thighs', 'Yellow Sapphire', 'Om Graam Greem Graum Sah Gurave Namah', 'Thursday', 'Yellow', 3, 'Gold', 'Dakshinamurthy / Brihaspati', 'Cancer', 'Capricorn'),
  Venus: pi('marriage, spouse, love, arts, vehicles, luxury, beauty and comforts', 'reproductive system, kidneys, face', 'Diamond / White Sapphire', 'Om Draam Dreem Draum Sah Shukraya Namah', 'Friday', 'White / pastel', 6, 'Silver', 'Lakshmi', 'Pisces', 'Virgo'),
  Saturn: pi('discipline, hard work, longevity, servants, masses, delays, justice and endurance', 'legs, joints, teeth, nerves', 'Blue Sapphire', 'Om Praam Preem Praum Sah Shanaye Namah', 'Saturday', 'Dark blue / black', 8, 'Iron', 'Shani / Hanuman', 'Libra', 'Aries'),
  Rahu: pi('ambition, foreign lands, technology, sudden gains, illusion and unconventional paths', 'skin, nervous system', 'Hessonite (Gomedh)', 'Om Bhraam Bhreem Bhraum Sah Rahave Namah', 'Saturday', 'Smoky / dark', 4, 'Lead', 'Durga', 'Taurus', 'Scorpio'),
  Ketu: pi('spirituality, detachment, research, healing, intuition and liberation', 'nerves, skin, hidden ailments', "Cat's Eye", 'Om Sraam Sreem Sraum Sah Ketave Namah', 'Tuesday', 'Grey / multicolour', 7, 'Iron', 'Ganesha', 'Scorpio', 'Taurus'),
};
const FRIENDS = { Sun: ['Moon', 'Mars', 'Jupiter'], Moon: ['Sun', 'Mercury'], Mars: ['Sun', 'Moon', 'Jupiter'], Mercury: ['Sun', 'Venus'], Jupiter: ['Sun', 'Moon', 'Mars'],
  Venus: ['Mercury', 'Saturn'], Saturn: ['Mercury', 'Venus'], Rahu: ['Mercury', 'Venus', 'Saturn'], Ketu: ['Mars', 'Jupiter'] };

export function dignity(planet, sign, signLord) {
  const p = PLANET[planet];
  if (p.exalt === sign) return 'Exalted';
  if (p.debil === sign) return 'Debilitated';
  if (signLord === planet) return 'Own sign';
  if (FRIENDS[planet].includes(signLord)) return 'Friendly sign';
  return 'Neutral / enemy sign';
}

export function dignityText(d) {
  switch (d) {
    case 'Exalted': return 'It is exalted here - at its strongest, giving excellent and generous results of its significations.';
    case 'Debilitated': return 'It is debilitated here - its results come with effort and learning; remedies and patience turn this into hard-won strength.';
    case 'Own sign': return 'It is in its own sign - comfortable and strong, giving reliable results.';
    case 'Friendly sign': return 'It sits in a friendly sign - supported and able to give good results.';
    default: return 'It sits in a neutral or challenging sign - results depend more on its star lord and sub lord, as KP teaches.';
  }
}

export const HOUSE_NAME = ['Tanu Bhava (Self)', 'Dhana Bhava (Wealth)', 'Sahaja Bhava (Courage)', 'Sukha Bhava (Happiness)', 'Putra Bhava (Children)', 'Ripu Bhava (Service & Health)',
  'Kalatra Bhava (Marriage)', 'Ayur Bhava (Longevity)', 'Dharma Bhava (Fortune)', 'Karma Bhava (Career)', 'Labha Bhava (Gains)', 'Vyaya Bhava (Expenses & Moksha)'];
export const HOUSE_DETAIL = [
  'body, health, appearance, personality, temperament, fame, overall strength and the direction of life',
  'family, accumulated wealth, speech, food habits, face, right eye, early education and values',
  'courage, younger siblings, communication, writing, media, short journeys, hands, neighbours and self-effort',
  'mother, home, happiness, vehicles, land and property, basic education, heart and inner peace',
  "children, intelligence, creativity, romance, speculation, mantras, past-life merit and higher studies' foundation",
  'service, job, competition, enemies, debts, loans, diseases and their cure, pets and maternal relatives',
  'marriage, spouse, partnerships, business dealings, public relations, contracts and travel',
  'longevity, sudden events, inheritance, insurance, research, occult knowledge and transformation',
  'fortune, father, guru, dharma, higher learning, long-distance travel, pilgrimage and luck',
  'career, profession, status, authority, karma, government recognition and public life',
  'gains, income, friends, elder siblings, fulfilment of desires, networks and success',
  'expenses, losses, foreign lands, hospitals, bed pleasures, charity, sleep and liberation (moksha)'];

export const DASHA_THEME = {
  Sun: 'self-confidence, recognition, dealings with authority and government, and focus on health and father',
  Moon: 'emotional growth, public dealings, travel, home and mother, and changing circumstances',
  Mars: 'energy, ambition, property matters, competition, technical work and decisive action',
  Mercury: 'learning, communication, business, writing, analysis and networking',
  Jupiter: 'wisdom, expansion, children, finances, teachers, spirituality and good fortune',
  Venus: 'relationships, marriage, comforts, vehicles, arts, luxury and financial growth',
  Saturn: 'hard work, responsibility, structure, patience and long-lasting, slowly-built achievements',
  Rahu: 'ambition, foreign links, technology, sudden changes and unconventional opportunities',
  Ketu: 'introspection, research, spirituality, detachment and letting go of the old' };

export const VARGAS = [
  [1, 'D1', 'Rasi', 'the physical body, overall life and every area - the root chart'],
  [2, 'D2', 'Hora', 'wealth, assets and the capacity to sustain the family'],
  [3, 'D3', 'Drekkana', 'siblings, courage, initiative and teamwork'],
  [4, 'D4', 'Chaturthamsa', 'property, home, vehicles and fixed assets - your fortune in settled comforts'],
  [7, 'D7', 'Saptamsa', 'children, grandchildren and creative output'],
  [9, 'D9', 'Navamsa', 'marriage, spouse, dharma and the inner strength of every planet - the most important varga'],
  [10, 'D10', 'Dasamsa', 'career, profession, status and achievements in the world'],
  [12, 'D12', 'Dwadasamsa', 'parents, ancestry and inherited traits'],
  [16, 'D16', 'Shodasamsa', 'vehicles, comforts and happiness from conveyances'],
  [20, 'D20', 'Vimsamsa', 'spiritual progress, worship and religious inclination'],
  [24, 'D24', 'Chaturvimsamsa', 'education, learning and academic achievement'],
  [27, 'D27', 'Saptavimsamsa', 'physical and mental strength, stamina and weaknesses'],
  [30, 'D30', 'Trimsamsa', 'misfortunes, challenges and how you overcome them'],
  [40, 'D40', 'Khavedamsa', 'maternal lineage and auspicious / inauspicious effects in life'],
  [45, 'D45', 'Akshavedamsa', 'paternal lineage, character and general conduct'],
  [60, 'D60', 'Shashtiamsa', 'past-life karma and the finest details of the whole chart'],
].map(([n, code, name, governs]) => ({ n, code, name, governs }));

/** Sign index (0-11) of a sidereal longitude in a divisional chart (Parashara rules). */
export function vargaSign(lon, n) {
  const s = Math.floor(lon / 30), d = lon - s * 30, odd = s % 2 === 0, q = s % 3, el = s % 4;
  const part = Math.floor(d / (30 / n));
  switch (n) {
    case 1: return s;
    case 2: return odd ? (d < 15 ? 4 : 3) : (d < 15 ? 3 : 4);
    case 3: return (s + part * 4) % 12;
    case 4: return (s + part * 3) % 12;
    case 7: return ((odd ? s : s + 6) + part) % 12;
    case 9: return ([0, 9, 6, 3][el] + part) % 12;
    case 10: return ((odd ? s : s + 8) + part) % 12;
    case 12: return (s + part) % 12;
    case 16: return ([0, 4, 8][q] + part) % 12;
    case 20: return ([0, 8, 4][q] + part) % 12;
    case 24: return ((odd ? 4 : 3) + part) % 12;
    case 27: return ([0, 3, 6, 9][el] + part) % 12;
    case 30: return odd ? (d < 5 ? 0 : d < 10 ? 10 : d < 18 ? 8 : d < 25 ? 2 : 6) : (d < 5 ? 1 : d < 12 ? 5 : d < 20 ? 11 : d < 25 ? 9 : 7);
    case 40: return ((odd ? 0 : 6) + part) % 12;
    case 45: return ([0, 4, 8][q] + part) % 12;
    default: return (s + part) % 12;
  }
}
export function vargaGrid(chart, n) {
  const g = Array.from({ length: 12 }, () => []);
  for (const p of chart.planets) g[vargaSign(p.longitude, n)].push(p.abbr);
  return g;
}
export const vargaAsc = (chart, n) => vargaSign(chart.cusps[0].longitude, n);

const TITHI = ['Pratipada', 'Dwitiya', 'Tritiya', 'Chaturthi', 'Panchami', 'Shashthi', 'Saptami', 'Ashtami', 'Navami', 'Dashami', 'Ekadashi', 'Dwadashi', 'Trayodashi', 'Chaturdashi'];
const YOGA = ['Vishkumbha', 'Priti', 'Ayushman', 'Saubhagya', 'Shobhana', 'Atiganda', 'Sukarma', 'Dhriti', 'Shula', 'Ganda', 'Vriddhi', 'Dhruva', 'Vyaghata', 'Harshana', 'Vajra', 'Siddhi', 'Vyatipata', 'Variyana', 'Parigha', 'Shiva', 'Siddha', 'Sadhya', 'Shubha', 'Shukla', 'Brahma', 'Indra', 'Vaidhriti'];
const KARANA = ['Bava', 'Balava', 'Kaulava', 'Taitila', 'Gara', 'Vanija', 'Vishti'];
const WEEKDAY = ['Sunday (Ravivar)', 'Monday (Somvar)', 'Tuesday (Mangalvar)', 'Wednesday (Budhvar)', 'Thursday (Guruvar)', 'Friday (Shukravar)', 'Saturday (Shanivar)'];

export function panchang(c) {
  const sun = planetOf(c, 'Sun').longitude, moon = planetOf(c, 'Moon').longitude;
  const diff = (((moon - sun) % 360) + 360) % 360;
  const t = Math.floor(diff / 12);
  const tithi = t === 14 ? 'Purnima' : t === 29 ? 'Amavasya' : TITHI[t % 15];
  const y = Math.floor(((sun + moon) % 360) / (360 / 27));
  const k = Math.floor(diff / 6);
  const karana = k === 0 ? 'Kimstughna' : k >= 57 ? ['Shakuni', 'Chatushpada', 'Naga'][k - 57] : KARANA[(k - 1) % 7];
  const wd = ['Sun', 'Moon', 'Mars', 'Mercury', 'Jupiter', 'Venus', 'Saturn'].indexOf(c.rulingPlanets.dayLord);
  const nk = NAKS[nakIdx(c.nakshatra)];
  return [
    ['Weekday (Vaar)', WEEKDAY[wd]], ['Tithi', tithi + ', ' + (t < 15 ? 'Shukla Paksha (waxing)' : 'Krishna Paksha (waning)')],
    ['Nakshatra', c.nakshatra + ' pada ' + planetOf(c, 'Moon').pada], ['Yoga', YOGA[y]], ['Karana', karana], ['Moon sign (Rasi)', c.moonSign],
    ['Sun sign (sidereal)', c.sunSign], ['Lagna (Ascendant)', c.ascendant.sign + ' ' + c.ascendant.dms], ['Gana', nk.gana], ['Nakshatra deity', nk.deity],
    ['Birth star lord', planetOf(c, 'Moon').starLord]];
}

/** Sidereal sign index of a slow planet on a date (for yearly forecasts). */
export function transitSign(bodyName, date) {
  const time = A.MakeTime(date);
  const trop = tropicalLongitude(A.Body[bodyName], time);
  const ayan = kpAyanamsa(time.ut + 2451545.0);
  return Math.floor(norm(trop - ayan) / 30);
}

export function jupiterTransit(f) {
  if ([2, 5, 7, 9, 11].includes(f)) return 'favourable - Jupiter blesses growth, income, family events and good decisions';
  if (f === 1) return 'mixed - new responsibilities and changes of place; stay grounded';
  if ([3, 6, 10].includes(f)) return 'moderate - progress needs more effort; avoid over-commitment';
  return 'demanding - be careful with expenses, health and big risks';
}
export function saturnTransit(f) {
  if ([3, 6, 11].includes(f)) return 'favourable - Saturn rewards hard work with stable gains and victory over obstacles';
  if ([12, 1, 2].includes(f)) return 'Sade Sati phase - a period of maturity, responsibility and restructuring; patience and discipline bring lasting results';
  if ([4, 8].includes(f)) return 'Dhaiya (Kantaka) phase - slow down, look after health and home, avoid shortcuts';
  return 'neutral - steady effort gives steady results';
}

export const GLOSSARY = [
  ['KP (Krishnamurti Paddhati)', 'A precise system of Vedic astrology developed by Prof. K. S. Krishnamurti, based on star lords and sub lords.'],
  ['Ayanamsa', 'The difference between the tropical and sidereal zodiacs. KP uses the Krishnamurti ayanamsa.'],
  ['Cusp', 'The starting point of a house. KP uses Placidus cusps calculated for the exact birth time and place.'],
  ['Sign lord', 'The planet ruling the zodiac sign in which a planet or cusp falls.'],
  ['Star lord (Nakshatra lord)', 'The planet ruling the nakshatra. In KP a planet gives the results of its star lord.'],
  ['Sub lord', 'The lord of the 1/9th unequal division of a nakshatra. The deciding factor in KP: it tells whether an event will happen.'],
  ['Sub-sub lord', 'A finer division of the sub, used for fine-tuning.'],
  ['Significator', 'A planet connected to a house through occupation, ownership or its star lord. Significators deliver the results of that house.'],
  ['Ruling planets', 'The planets ruling the moment of judgement (day lord, ascendant and Moon sign/star/sub lords) - used to confirm timing.'],
  ['Vimshottari dasha', 'A 120-year planetary period system based on the Moon nakshatra at birth, used to time events.'],
  ['Mahadasha / Antardasha / Pratyantar', 'Major period, sub-period and sub-sub-period of the Vimshottari system.'],
  ['Divisional chart (Varga)', 'A chart made by dividing each sign into parts, giving detail on a specific life area (e.g. D9 for marriage, D10 for career).'],
  ['Sade Sati', 'The roughly 7.5-year transit of Saturn through the 12th, 1st and 2nd signs from the natal Moon.'],
  ['Retrograde (R)', 'A planet appearing to move backwards; it gives intensified, internalised or delayed results.']];

export { SIGN_LORDS };
