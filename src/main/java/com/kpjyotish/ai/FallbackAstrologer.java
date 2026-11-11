package com.kpjyotish.ai;

import com.kpjyotish.ai.AiTypes.*;
import com.kpjyotish.kp.Chart;
import com.kpjyotish.store.Entities.ChatMessage;
import com.kpjyotish.store.Entities.User;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based KP astrologer used when no AI key is configured (or AI fails).
 * Runs a short interview: why are you consulting -> topic-specific (incl. psychological /
 * aptitude) questions -> answer from the KP chart + the client's answers.
 */
@Component
public class FallbackAstrologer {

    /** Mutable interview state (persisted in ChatSession by the caller). */
    public static class State {
        public String topicKey, topic;
        public int stage;
        public boolean ready;
    }

    record Topic(String key, String label, int cusp, Set<Integer> good, Set<Integer> bad, List<String> words, List<String> questions, List<String> qShort) {}

    static final List<Topic> TOPICS = List.of(
        new Topic("education", "Education & stream choice", 4, Set.of(4, 9, 11), Set.of(3, 8, 12),
            List.of("10th", "12th", " inter ", "intermediate", "mpc", "bipc", "mec", "cec", "hec", "stream", "study", "studies", "exam", "school", "college", "degree", "course", "neet", "jee", "eamcet", "engineering", "medicine", "mbbs", "student", "class", "marks", "education", "subject"),
            List.of("Which subjects do you enjoy the most and score best in — Maths & Physics, or Biology & Chemistry? Tell me your recent marks if you remember.",
                    "Imagine a free Sunday. Would you rather build or fix something, solve a puzzle or try coding — or read about the human body, plants and animals, or help someone who is unwell? Answer honestly, not what others expect from you.",
                    "How do you handle pressure — long study hours and competitive exams like JEE / NEET / EAMCET? Do you feel calm, or do you get anxious? And is anyone at home expecting a particular stream for you?",
                    "One astrology question to fine-tune your chart: when did your 10th exams start (month and year), and is your birth time taken from a birth certificate or from family memory?"),
            List.of("favourite subjects & marks", "natural interests", "pressure & family expectations", "exam date & birth-time source")),
        new Topic("career", "Career & profession", 10, Set.of(2, 6, 10, 11), Set.of(5, 8, 12),
            List.of("job", "jobs", "career", "carrer", "carear", "carrear", "carier", "carreer", "creer", "work", "naukri", "udyogam", "promotion", "business", "office", "salary", "profession", "government", "govt", "startup", "software", "company", "transfer", "interview", "unemployed", "employment"),
            List.of("Tell me about your current situation — are you studying, working (in which field and for how long), or looking for a job?",
                    "What kind of work makes you lose track of time — working with people, with numbers and systems, creating things, or leading and taking decisions? Do you prefer a stable job or the risk and freedom of business?",
                    "When you face a setback at work — a rejection, criticism or a missed promotion — how do you usually react: try again immediately, step back and rethink, or feel low for a while? What keeps you going?",
                    "One astrology question to verify your chart: when did you start your first job or finish your studies (month and year)? And is your birth time from a birth certificate?"),
            List.of("current situation", "work style & risk appetite", "how you handle setbacks", "first job date & birth-time source")),
        new Topic("marriage", "Marriage & relationships", 7, Set.of(2, 7, 11), Set.of(1, 6, 10, 12),
            List.of("marriage", "mariage", "marrage", "marraige", "pelli", "shaadi", "shadi", "vivah", "marry", "wedding", "spouse", "love", "relationship", "partner", "husband", "wife", "match", "alliance", "divorce", "engagement"),
            List.of("Are you looking for marriage timing, or is this about an existing relationship / proposal?",
                    "What matters most to you in a life partner — and is it a love or an arranged marriage that you are hoping for?",
                    "How do you usually handle disagreements with people close to you — do you talk it out, go quiet, or give in to keep the peace? Do you find it easy to share your feelings?",
                    "One astrology question: has any proposal, engagement or serious relationship happened before — roughly when (month and year)? And is your birth time from a certificate?"),
            List.of("marriage question", "partner expectations", "conflict & feelings", "past relationship date & birth-time source")),
        new Topic("health", "Health & well-being", 1, Set.of(1, 5, 11), Set.of(6, 8, 12),
            List.of("health", "helth", "arogyam", "disease", "illness", "sick", "stress", "anxiety", "sleep", "hospital", "surgery", "pain", "depression", "tension", "fever", "sugar", "diabetes"),
            List.of("I'm sorry you're going through this. What is troubling you — physical health, stress / sleep, or worry about a family member?",
                    "How are your daily routine, sleep and food habits these days? Do you get time to rest?",
                    "When you feel low or worried, what do you usually do — talk to someone, keep it inside, or distract yourself? Do you have someone you trust to talk to?",
                    "One astrology question: when did this problem start (month and year)? And is your birth time from a birth certificate?"),
            List.of("health concern", "routine & habits", "emotional support", "start date & birth-time source")),
        new Topic("finance", "Money, wealth & investments", 2, Set.of(2, 6, 11), Set.of(5, 8, 12),
            List.of("money", "finance", "financial", "wealth", "welth", "dabbu", "paisa", "rich", "loan", "debt", "investment", "invest", "savings", "income", "profit", "loss", "stock", "shares"),
            List.of("What is your money question — income growth, clearing debts, or an investment decision?",
                    "Are you more of a saver or a spender? How do you feel when you take a financial risk — excited or nervous?",
                    "What does financial success mean to you — security for family, freedom, status, or something else? What worries you most about money right now?",
                    "One astrology question: when did you have your biggest gain or loss so far (month and year)? And is your birth time from a certificate?"),
            List.of("money question", "saver or spender", "what money means to you", "past gain/loss date & birth-time source")),
        new Topic("foreign", "Foreign travel & settlement", 12, Set.of(3, 9, 12), Set.of(4, 11),
            List.of("abroad", "foreign", "visa", "usa", " uk ", "canada", "australia", "germany", "settle", "onsite", "ms ", "immigration", "travel"),
            List.of("Is it for studies, a job, or settling abroad — and which country are you considering?",
                    "How do you feel about living far from family for a long time — excited, or worried?",
                    "How do you usually adapt to new places and people — quickly, or does it take you time to feel at home?",
                    "One astrology question: have you applied for a visa or travelled abroad before — when (month and year)? And is your birth time from a certificate?"),
            List.of("purpose & country", "feelings about living abroad", "adapting to change", "visa/travel date & birth-time source")),
        new Topic("property", "Property, house & vehicles", 4, Set.of(4, 11, 12), Set.of(3, 5),
            List.of("property", "house", "home", "land", "flat", "plot", "construction", "vehicle", " car ", "apartment"),
            List.of("Are you planning to buy, build, or sell — and roughly when?",
                    "Will this be funded mainly by savings or a loan? How comfortable are you with a long loan commitment?",
                    "What does owning this property mean to you and your family — security, comfort, investment, or a dream?",
                    "One astrology question: did you move house or buy any property / vehicle earlier — when (month and year)? And is your birth time from a certificate?"),
            List.of("property plan", "funding & comfort with loans", "what the property means", "past move date & birth-time source")),
        new Topic("children", "Children & progeny", 5, Set.of(2, 5, 11), Set.of(1, 4, 10),
            List.of("child", "children", "baby", "pregnancy", "pregnant", "kids", " son ", "daughter", "conceive", "progeny"),
            List.of("Is your question about having a child, or about your children's education / future?",
                    "How long have you been hoping for this, and how are you and your partner coping emotionally?",
                    "Who supports you most through this — your partner, family or friends? Do you feel pressure from others?",
                    "One astrology question: when did you get married (month and year)? And is your birth time from a certificate?"),
            List.of("children question", "time & emotions", "support & pressure", "marriage date & birth-time source"))
    );
    static final Topic GENERAL = new Topic("general", "General life reading", 1, Set.of(1, 2, 10, 11), Set.of(6, 8, 12), List.of(),
            List.of("Tell me a little about yourself — what do you do now (study, job, business, home) and what is happening in your life these days?",
                    "What would you most like clarity on in the next two years — and what is one thing you feel is holding you back?",
                    "When things don't go your way, how do you usually feel and react? What gives you strength in difficult times?",
                    "One astrology question to verify your chart: tell me one important event in your life with its month and year (job, marriage, move, exam). Is your birth time from a certificate?"),
            List.of("current life", "what you want clarity on", "how you handle difficulty", "life event & birth-time source"));

    /** Life-area chapters of the full report (written by AI when available). */
    public static final List<String> LIFE_AREAS = List.of(
            "Education & Learning", "Career & Profession", "Money, Wealth & Investments", "Marriage & Relationships", "Children & Family",
            "Health & Well-being", "Property, Home & Vehicles", "Foreign Travel & Settlement", "Spiritual Growth & Inner Peace");
    static final List<String> LIFE_AREA_KEYS = List.of("education", "career", "finance", "marriage", "children", "health", "property", "foreign", "general");

    static final Map<Integer, String> HOUSE_MEANING = Map.ofEntries(
            Map.entry(1, "self, health and personality"), Map.entry(2, "wealth, family and speech"), Map.entry(3, "courage, siblings and short travel"),
            Map.entry(4, "home, mother, property and basic education"), Map.entry(5, "children, intelligence, romance and speculation"),
            Map.entry(6, "service, competition, debts and health issues"), Map.entry(7, "marriage and partnerships"), Map.entry(8, "obstacles, inheritance and sudden events"),
            Map.entry(9, "fortune, father, higher learning and long journeys"), Map.entry(10, "career, status and karma"), Map.entry(11, "gains, friends and fulfilment of desires"),
            Map.entry(12, "expenses, losses, foreign lands and liberation"));
    static final Map<String, String> NATURE = Map.of(
            "Sun", "authority, confidence and government links", "Moon", "emotions, the mind and public dealings", "Mars", "energy, technical skill and decisive action",
            "Mercury", "intellect, calculation, trade and communication", "Jupiter", "wisdom, teaching, expansion and blessings", "Venus", "comfort, relationships, art and luxury",
            "Saturn", "discipline, patience, hard work and long-term structure", "Rahu", "ambition, technology, foreign links and unconventional paths",
            "Ketu", "research, detachment, healing and spiritual insight");

    // ================================================================== chat
    public ChatTurn reply(User u, Chart c, List<String> memory, List<ChatMessage> history, State st) {
        String first = u.fullName.split("\\s+")[0];
        String msg = "";
        for (int i = history.size() - 1; i >= 0; i--) if (history.get(i).role.equals("user")) { msg = history.get(i).text; break; }
        String low = " " + msg.toLowerCase(Locale.ROOT) + " ";
        List<String> mem = new ArrayList<>(extractFacts(msg));
        Topic detected = detect(low);

        // New consultation topic (or switching topic after an answer was given)
        if (st.topicKey == null || (st.ready && detected != null && !detected.key().equals(st.topicKey))) {
            Topic t = detected;
            if (t == null && st.topicKey == null && st.stage == 0 && !looksLikeQuestion(low)) {
                st.stage = -1; // asked once
                return new ChatTurn("Thank you for sharing, " + first + ". So that I read the right part of your chart — what would you like guidance on today? For example: studies / which stream to choose, career or job, marriage, health, money, foreign travel, property or children. You can also just tell me what is worrying you.",
                        mem, false, "");
            }
            if (t == null) t = GENERAL;
            st.topicKey = t.key(); st.topic = topicLabel(t, low); st.stage = 0; st.ready = false;
            mem.add("Main concern: " + st.topic);
            Chart.Point cusp = c.cusps.get(t.cusp() - 1);
            String intro = "I understand, " + first + " — you want guidance on " + st.topic.toLowerCase(Locale.ROOT).replace("mpc vs bipc", "MPC vs BiPC")
                    + ". In KP astrology this is judged mainly from your " + ord(t.cusp()) + " cusp — it falls in " + cusp.sign
                    + " with " + cusp.subLord + " as its sub lord, and you are running " + c.currentDasha.mahadasha + " Mahadasha / " + c.currentDasha.antardasha
                    + " Antardasha.\n\nBefore I give my answer, I'd like to understand you better — I'll ask " + t.questions().size() + " short questions.\n\n";
            return new ChatTurn(intro + qLabel(0, t) + t.questions().get(0), mem, false, st.topic);
        }

        Topic t = byKey(st.topicKey);
        if (!st.ready) {
            if (st.stage >= 0 && st.stage < t.qShort().size() && !msg.isBlank())
                mem.add("Said about " + t.qShort().get(st.stage) + ": " + trim(msg, 140));
            st.stage++;
            if (st.stage < t.questions().size()) {
                return new ChatTurn(ack(low) + "\n\n" + qLabel(st.stage, t) + t.questions().get(st.stage), mem, false, st.topic);
            }
            st.ready = true;
            String answers = String.join(" ", userAnswers(history)).toLowerCase(Locale.ROOT);
            String analysis = t.key().equals("education") ? educationAnswer(u, c, answers, memory) : topicAnswer(u, c, t, answers);
            return new ChatTurn(analysis + "\n\nI have saved these points in my memory. Tap \"Generate my report\" to get a personalised PDF built from our conversation — or ask me anything else.", mem, true, st.topic);
        }

        // Follow-up after the answer
        String follow;
        if (low.contains("when") || low.contains("timing") || low.contains("which year") || low.contains("eppudu") || low.contains("kab")) {
            follow = "On timing: " + favourablePeriods(c, t.good(), 3) + ".";
        } else if (low.contains("remedy") || low.contains("remedies") || low.contains("upay") || low.contains("pariharam")) {
            follow = remedies(c);
        } else {
            follow = "Good question, " + first + ". Looking again at your chart for " + st.topic.toLowerCase(Locale.ROOT) + ": " + cuspVerdict(c, t)
                    + " The most supportive periods ahead are " + favourablePeriods(c, t.good(), 2) + ".";
        }
        return new ChatTurn(follow + "\n\nYou can generate your report any time, or book a 30-minute live consultation for deeper questions.", mem, true, st.topic);
    }

    public ChatTurn greeting(User u, Chart c, List<String> memory) {
        String first = u.fullName.split("\\s+")[0];
        String lastConcern = null;
        for (String m : memory) if (m.startsWith("Main concern: ")) lastConcern = m.substring(14);
        String remembered = lastConcern != null ? " Last time we discussed " + lastConcern + " — I still remember what you told me."
                : memory.isEmpty() ? "" : " I remember what you shared with me earlier.";
        return new ChatTurn("Namaste " + first + " Welcome to Astrology Talk. I have prepared your KP chart: " + c.ascendant.sign + " ascendant, Moon in "
                + c.nakshatra + " nakshatra, and you are running " + c.currentDasha.mahadasha + " Mahadasha / " + c.currentDasha.antardasha + " Antardasha." + remembered
                + "\n\nTell me — why are you consulting the astrologer today? What is on your mind? (For example: studies, career, marriage, health, money, foreign travel, property or children.)", List.of(), false, "");
    }

    static String qLabel(int i, Topic t) {
        String kind = i == t.questions().size() - 1 ? "astrology question" : i == 0 ? "about you" : "about how you think & feel";
        return "Question " + (i + 1) + " of " + t.questions().size() + " (" + kind + "): ";
    }

    /** Topic from keywords, tolerant of spelling mistakes (e.g. "carrear", "marrige", "helth"). */
    static Topic detect(String low) {
        Topic best = null; int bestN = 0;
        String[] words = low.replaceAll("[^a-z0-9 ]", " ").trim().split("\\s+");
        for (Topic t : TOPICS) {
            int n = 0;
            for (String kw : t.words()) {
                if (low.contains(kw)) { n += 2; continue; }
                String k = kw.trim();
                if (k.length() < 5 || k.contains(" ")) continue;
                for (String w : words) if (w.length() >= 4 && Math.abs(w.length() - k.length()) <= 2 && lev(w, k) <= (k.length() >= 7 ? 2 : 1)) { n += 1; break; }
            }
            if (n > bestN) { bestN = n; best = t; }
        }
        return best;
    }

    static int lev(String a, String b) {
        int[] prev = new int[b.length() + 1], cur = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            cur[0] = i;
            for (int j = 1; j <= b.length(); j++)
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] t = prev; prev = cur; cur = t;
        }
        return prev[b.length()];
    }

    static Topic byKey(String key) { return TOPICS.stream().filter(t -> t.key().equals(key)).findFirst().orElse(GENERAL); }

    static boolean looksLikeQuestion(String low) { return low.contains("?") || low.length() > 60; }

    static String topicLabel(Topic t, String low) {
        if (t.key().equals("education") && (low.contains("mpc") || low.contains("bipc") || low.contains("10th"))) return "Education: which stream after 10th (MPC vs BiPC)";
        return t.label();
    }

    static String ack(String low) {
        if (low.contains("don't know") || low.contains("dont know") || low.contains("not sure") || low.contains("confused")) return "That's completely okay — being unsure is normal, and it is exactly why we are talking.";
        if (low.contains("stress") || low.contains("worried") || low.contains("scared") || low.contains("sad") || low.contains("anxious")) return "Thank you for sharing that honestly — those feelings are natural, and we will find a way forward together.";
        if (low.length() < 15) return "Noted, thank you.";
        return "Thank you, that helps me understand you much better.";
    }

    static List<String> userAnswers(List<ChatMessage> history) {
        List<String> l = new ArrayList<>();
        for (ChatMessage m : history) if (m.role.equals("user")) l.add(m.text);
        return l;
    }

    static final Pattern CLASS = Pattern.compile("(\\d{1,2})\\s*(st|nd|rd|th)?\\s*(class|standard|std|grade)", Pattern.CASE_INSENSITIVE);
    static final Pattern AGE = Pattern.compile("\\b(\\d{1,2})\\s*(years? old|yrs|years)\\b", Pattern.CASE_INSENSITIVE);
    static final Pattern IAM = Pattern.compile("\\b(?:i am|i'm|im)\\s+(?:a|an)\\s+([a-z ]{3,40})", Pattern.CASE_INSENSITIVE);

    static List<String> extractFacts(String msg) {
        List<String> f = new ArrayList<>();
        String low = msg.toLowerCase(Locale.ROOT);
        Matcher m = CLASS.matcher(msg);
        if (m.find()) f.add("Is studying in " + m.group(1) + ordSuffix(Integer.parseInt(m.group(1))) + " class");
        m = AGE.matcher(msg);
        if (m.find()) f.add("Is " + m.group(1) + " years old");
        m = IAM.matcher(msg);
        if (m.find()) f.add("Describes themself as a " + m.group(1).trim().split("\\s+(and|but|who|from)\\s+")[0]);
        if (low.contains("mpc")) f.add("Is considering MPC (Maths, Physics, Chemistry)");
        if (low.contains("bipc")) f.add("Is considering BiPC (Biology, Physics, Chemistry)");
        return f;
    }

    // ================================================================== analysis
    static String ord(int n) { return n + ordSuffix(n); }
    static String ordSuffix(int n) { return (n % 100 >= 11 && n % 100 <= 13) ? "th" : switch (n % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; }; }
    static String trim(String s, int n) { s = s.replaceAll("\\s+", " ").trim(); return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }
    static String joinHouses(Collection<Integer> h) { return h.isEmpty() ? "none" : String.join(", ", h.stream().sorted().map(String::valueOf).toList()); }

    static String cuspVerdict(Chart c, Topic t) {
        Chart.Point cusp = c.cusps.get(t.cusp() - 1);
        List<Integer> sig = c.housesSignified(cusp.subLord);
        Set<Integer> good = new TreeSet<>(sig); good.retainAll(t.good());
        Set<Integer> bad = new TreeSet<>(sig); bad.retainAll(t.bad());
        String strength = good.size() >= 2 ? "clearly promised" : good.size() == 1 ? "promised, with steady effort" : "possible, but it needs patience and the right timing";
        return "your " + ord(t.cusp()) + " cusp sub lord " + cusp.subLord + " signifies houses " + joinHouses(sig) + ", so success here is " + strength
                + (bad.isEmpty() ? "." : "; its link with house " + joinHouses(bad) + " shows some delays or extra effort along the way.");
    }

    /** Upcoming antardashas (next ~10 years) whose lords signify >= 2 of the good houses. */
    static String favourablePeriods(Chart c, Set<Integer> good, int max) {
        String today = LocalDate.now().toString(), limit = LocalDate.now().plusYears(10).toString();
        List<String> out = new ArrayList<>();
        for (Chart.Period md : c.dasha.periods) {
            if (md.end.compareTo(today) < 0 || md.start.compareTo(limit) > 0) continue;
            for (Chart.Period ad : md.antardashas) {
                if (ad.end.compareTo(today) < 0 || ad.start.compareTo(limit) > 0) continue;
                long hits = c.housesSignified(ad.lord).stream().filter(good::contains).count()
                        + c.housesSignified(md.lord).stream().filter(good::contains).count();
                if (hits >= 2 && c.housesSignified(ad.lord).stream().anyMatch(good::contains)) out.add(md.lord + "–" + ad.lord + " (" + ad.start.substring(0, 7) + " to " + ad.end.substring(0, 7) + ")");
                if (out.size() >= max) return String.join(", ", out);
            }
        }
        return out.isEmpty() ? "the periods of " + c.currentDasha.mahadasha + " after " + c.currentDasha.adEnd : String.join(", ", out);
    }

    static String topicAnswer(User u, Chart c, Topic t, String answers) {
        String first = u.fullName.split("\\s+")[0];
        StringBuilder s = new StringBuilder();
        s.append("Here is what your chart says, ").append(first).append(".\n\n");
        s.append("KP view: ").append(Character.toUpperCase(cuspVerdict(c, t).charAt(0))).append(cuspVerdict(c, t).substring(1));
        Chart.Point cusp = c.cusps.get(t.cusp() - 1);
        s.append(" ").append(cusp.subLord).append(" brings the qualities of ").append(NATURE.get(cusp.subLord)).append(" into matters of ").append(HOUSE_MEANING.get(t.cusp())).append(".\n\n");
        s.append("Timing: the most supportive periods ahead are ").append(favourablePeriods(c, t.good(), 3)).append(".\n\n");
        s.append("Guidance: ").append(psychAdvice(t.key(), answers));
        return s.toString();
    }

    static String psychAdvice(String key, String a) {
        boolean stress = containsAny(a, "stress", "pressure", "scared", "fear", "worried", "anxious", "tension", "difficult", "can't", "cannot");
        boolean family = containsAny(a, "expect", "wants me", "want me", "force", "forcing", "insist", "pressure from")
                && containsAny(a, "father", "mother", "parents", "family", "dad", "mom", "amma", "nanna");
        boolean lowAfterSetback = containsAny(a, "feel low", "feel sad", "discouraged", "give up", "depressed", "upset");
        String base = switch (key) {
            case "education" -> "choose the stream you enjoy studying every day — interest sustains you through two demanding years. Keep a simple daily timetable, revise weekly, practise previous papers, and protect your sleep before exams.";
            case "career" -> careerFit(a) + " Upgrade one concrete skill in the next 3 months and make your big moves (job change, interviews, promotion talks) in the favourable periods above.";
            case "marriage" -> "be clear about your top three values in a partner, communicate openly with family, and give extra weight to proposals that come during the favourable periods.";
            case "health" -> "keep a fixed sleep and meal routine, take a short walk daily, and please do consult a doctor for any physical symptoms — astrology supports, it does not replace medical care.";
            case "finance" -> "build an emergency fund first, avoid speculation in weak periods, and plan major investments in the favourable periods.";
            case "foreign" -> "prepare documents and exams early so you are ready to act when the favourable period opens.";
            case "property" -> "verify documents carefully and finalise registration in a favourable period.";
            case "children" -> "stay hopeful, support each other emotionally, and follow your doctor's advice alongside these timings.";
            default -> "focus on one clear goal at a time and act during the favourable periods.";
        };
        return base + (lowAfterSetback ? " You said setbacks make you feel low for a while — that is human; what matters is that you get up again, and you do. Keep a small list of your past wins to read on difficult days." : "")
                + (stress ? " You mentioned feeling pressure — that is normal. Break big goals into small daily steps and talk to someone you trust when it feels heavy." : "")
                + (family ? " Since family expectations are involved, share your feelings with them calmly; a decision you believe in will carry you further." : "");
    }

    /** Career direction from what the client said about their work style. */
    static String careerFit(String a) {
        List<String> fit = new ArrayList<>();
        if (containsAny(a, "problem", "system", "logic", "software", "tester", "testing", "coding", "computer", "it company", "data", "numbers"))
            fit.add(a.contains("test") ? "moving from testing towards automation, development or data roles" : "technical, analytical or data-driven roles");
        if (containsAny(a, "people", "talk", "teach", "help", "customer", "team")) fit.add("people-facing roles such as training, HR, sales or client management");
        if (containsAny(a, "creat", "design", "art", "write", "music", "content")) fit.add("creative roles such as design, content or media");
        if (containsAny(a, "lead", "decision", "manage", "own business", "business")) fit.add("leadership or business roles");
        String stable = containsAny(a, "stable job", "stable", "secure", "government") ? " Since you prefer stability, aim for a well-established company or a government / PSU path rather than a risky start-up."
                : containsAny(a, "business", "risk", "freedom", "own") ? " Since you value freedom, a side business or freelancing alongside your job is a safe way to test it." : "";
        return (fit.isEmpty() ? "Choose roles that match the work style you described." : "From what you told me, your best direction is " + String.join(" or ", fit) + ".") + stable;
    }

    static boolean containsAny(String s, String... w) { for (String x : w) if (s.contains(x)) return true; return false; }

    static final List<String> MPC_WORDS = List.of("math", "physics", "number", "calculat", "puzzle", "build", "fix", "machine", "computer", "coding", "code", "engineer", "technolog", "robot", "jee", "iit", "logic", "electronics");
    static final List<String> BIPC_WORDS = List.of("biology", "bio ", "botany", "zoology", "doctor", "medicine", "mbbs", "neet", "human body", "plant", "animal", "nature", "help", "care", "nurse", "health", "pharma", "life science", "hospital");

    static int interestScore(String a, List<String> words) {
        int s = 0;
        for (String w : words) {
            int i = a.indexOf(w);
            while (i >= 0) {
                String before = a.substring(Math.max(0, i - 14), i);
                boolean neg = before.contains("not ") || before.contains("don't") || before.contains("dont") || before.contains("hate") || before.contains("weak") || before.contains("no ") || before.contains("bad at");
                s += neg ? -1 : 1;
                i = a.indexOf(w, i + w.length());
            }
        }
        return s;
    }

    static String educationAnswer(User u, Chart c, String answers, List<String> memory) {
        String first = u.fullName.split("\\s+")[0];
        Set<String> mpcPl = Set.of("Mercury", "Mars", "Saturn", "Rahu", "Sun"), bipcPl = Set.of("Moon", "Venus", "Jupiter", "Ketu");
        int chartMpc = 0, chartBipc = 0;
        List<String> why = new ArrayList<>();
        for (int h : new int[]{4, 5, 9, 10}) {
            Chart.Point cusp = c.cusps.get(h - 1);
            for (String p : List.of(cusp.subLord, c.planet(cusp.subLord).starLord)) {
                if (mpcPl.contains(p)) chartMpc++;
                if (bipcPl.contains(p)) chartBipc++;
            }
            why.add(ord(h) + " sub lord " + cusp.subLord);
        }
        // Medicine needs 6/8/12 links (hospital, healing) with the 4th / 9th sub lords
        for (int h : new int[]{4, 9}) {
            List<Integer> sig = c.housesSignified(c.cusps.get(h - 1).subLord);
            if (sig.contains(6) && (sig.contains(8) || sig.contains(12))) chartBipc++;
        }
        int intMpc = interestScore(answers, MPC_WORDS), intBipc = interestScore(answers, BIPC_WORDS);
        double mpc = chartMpc + 1.5 * intMpc, bipc = chartBipc + 1.5 * intBipc;
        String rec;
        if (Math.abs(mpc - bipc) < 1.5 && intMpc <= 0 && intBipc <= 0)
            rec = "Your chart supports both science streams fairly evenly, and your answers don't show a strong pull either way. In that case choose the subjects you genuinely enjoy studying every day — and it is also worth considering MEC/CEC (commerce) if numbers and business interest you more than science.";
        else {
            boolean isMpc = mpc >= bipc;
            int chartFor = isMpc ? chartMpc : chartBipc, chartAgainst = isMpc ? chartBipc : chartMpc;
            int interestFor = isMpc ? intMpc : intBipc;
            String chartPart = chartFor == chartAgainst
                    ? "Your chart is evenly balanced (" + chartFor + " technical and " + chartAgainst + " life-science indications from the education sub lords), so your clear interest is the deciding factor."
                    : chartFor > chartAgainst
                    ? "Your chart supports this: the education sub lords and their star lords give " + chartFor + " " + (isMpc ? "technical / analytical" : "healing / life-science") + " indications against " + chartAgainst + "."
                    : "Your chart also shows " + chartAgainst + " " + (isMpc ? "life-science" : "technical") + " indications against " + chartFor + ", so you have real ability on both sides — but in KP the native's genuine interest during a supportive dasha decides the stream, and your interest is clear.";
            String interestPart = interestFor > 0
                    ? (isMpc ? "Your answers show a natural pull towards Maths, Physics, building and problem-solving." : "Your answers show genuine interest in Biology and in caring for others.")
                    : (isMpc ? "With steady practice in Maths you can do very well here." : "With good teachers, Biology can become your strength.");
            rec = "My recommendation: " + (isMpc ? "MPC" : "BiPC") + ". " + interestPart + " " + chartPart + " "
                    + (isMpc ? "Engineering, computer science, technology, architecture or data-related careers suit you." : "Medicine, pharmacy, biotechnology, agriculture or allied health careers suit you.");
        }
        Chart.Point c4 = c.cusps.get(3);
        List<Integer> s4 = c.housesSignified(c4.subLord);
        String success = s4.contains(11) || s4.contains(9) ? "Your 4th cusp sub lord " + c4.subLord + " connects to houses " + joinHouses(s4) + ", which promises good results in studies." :
                "Your 4th cusp sub lord " + c4.subLord + " signifies houses " + joinHouses(s4) + " — results come through consistent effort rather than last-minute study.";
        return "Thank you, " + first + ". Here is my reading, combining your chart (" + String.join(", ", why) + ") with what you told me:\n\n" + rec + "\n\n" + success
                + "\n\nTiming: supportive study and exam periods ahead are " + favourablePeriods(c, Set.of(4, 9, 11), 3) + ".\n\nGuidance: " + psychAdvice("education", answers);
    }

    static String remedies(Chart c) {
        return "Simple KP remedies for you: strengthen " + c.ascendant.subLord + " (your ascendant sub lord) and " + c.rulingPlanets.get("moonStarLord")
                + " (your Moon's star lord) through discipline and charity on their weekdays, respect elders and teachers for Jupiter's grace, and practise a few minutes of meditation daily to steady the Moon (the mind).";
    }

    // ================================================================== report
    public ReportContent report(User u, Chart c, List<String> memory, List<ChatMessage> transcript, String topic) {
        String first = u.fullName.split("\\s+")[0];
        Topic t = TOPICS.stream().filter(x -> topic != null && topic.toLowerCase(Locale.ROOT).contains(x.key())).findFirst()
                .orElseGet(() -> topic == null ? GENERAL : Optional.ofNullable(detect(" " + topic.toLowerCase(Locale.ROOT) + " ")).orElse(GENERAL));
        String answers = String.join(" ", userAnswers(transcript)).toLowerCase(Locale.ROOT);
        List<Section> consult = new ArrayList<>();
        String label = topic == null || topic.isBlank() ? "General life reading" : topic;
        StringBuilder known = new StringBuilder("You consulted about: " + label + ".\n\n");
        if (!memory.isEmpty()) known.append("What you shared with me:\n").append(String.join("\n", memory.stream().map(m -> "• " + m).toList())).append("\n\n");
        known.append("This report combines your answers with a precise KP reading of your chart, so the guidance below is specific to you, ").append(first).append(".");
        consult.add(new Section("Your Question: " + label, known.toString()));
        String lastAnswer = "";
        for (int i = transcript.size() - 1; i >= 0; i--) if (transcript.get(i).role.equals("assistant") && transcript.get(i).text.length() > 200) { lastAnswer = transcript.get(i).text; break; }
        consult.add(new Section("KP Analysis & Recommendation", t.key().equals("education") ? educationAnswer(u, c, answers, memory) : (!lastAnswer.isEmpty() ? lastAnswer : topicAnswer(u, c, t, answers))));
        consult.add(new Section("Timing of Results", "In KP, events happen when the dasha, bhukti and antara lords signify the relevant houses. For " + label.toLowerCase(Locale.ROOT)
                + " the key houses are " + joinHouses(t.good()) + ".\n\nYou are running " + c.currentDasha.mahadasha + " Mahadasha / " + c.currentDasha.antardasha + " Antardasha until " + c.currentDasha.adEnd
                + ". The most favourable periods ahead are: " + favourablePeriods(c, t.good(), 4) + ". Plan important steps — applications, exams, proposals, purchases — inside these windows."));
        consult.add(new Section("Practical Guidance For You", psychAdvice(t.key(), answers) + "\n\n" + remedies(c)));

        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < LIFE_AREAS.size(); i++) sections.add(new Section(LIFE_AREAS.get(i), lifeArea(c, LIFE_AREA_KEYS.get(i), first, answers)));

        Chart.Point cusp = c.cusps.get(t.cusp() - 1);
        Hook hook = new Hook(first + ", your " + ord(t.cusp()) + " cusp holds the answer to your question",
                "You asked about " + label + ". In KP astrology the deciding factor is the sub lord of your " + ord(t.cusp()) + " cusp — " + cusp.subLord + ", sitting in " + cusp.sign
                        + ". Combined with your " + c.currentDasha.mahadasha + " period and what you told me about yourself, it points to a clear direction — and a specific window of time when things move fastest. The full report reveals exactly what it is, and when...",
                List.of("My clear recommendation for your question — with the KP reasoning", "The exact dasha windows when results come fastest",
                        "The planet that controls your success — and how to strengthen it", "Practical, personal guidance based on your own answers"));
        String summary = u.fullName + " has " + c.ascendant.sign + " rising, Moon in " + c.moonSign + " (" + c.nakshatra + ") and Sun in " + c.sunSign
                + ", calculated with Placidus cusps and the KP ayanamsa (" + c.ayanamsa.dms + "). This report focuses on: " + label + ".";
        return new ReportContent(hook, summary, consult, sections);
    }

    static String lifeArea(Chart c, String key, String first, String answers) {
        Topic t = key.equals("general") ? new Topic("general", "Spiritual growth", 12, Set.of(5, 9, 12), Set.of(2, 11), List.of(), List.of(), List.of()) : byKey(key);
        Chart.Point cusp = c.cusps.get(t.cusp() - 1);
        List<String> occ = c.planets.stream().filter(p -> p.house == t.cusp()).map(p -> p.name).toList();
        Chart.HouseSignificator hs = c.houseSignificators.get(t.cusp() - 1);
        Set<String> sigs = new LinkedHashSet<>(); sigs.addAll(hs.A); sigs.addAll(hs.B); sigs.addAll(hs.C); sigs.addAll(hs.D);
        String v = cuspVerdict(c, t);
        return "For " + t.label().toLowerCase(Locale.ROOT) + ", KP looks at the " + ord(t.cusp()) + " cusp together with houses " + joinHouses(t.good())
                + ". Your " + ord(t.cusp()) + " cusp falls in " + cusp.sign + " (" + cusp.dms + "), in the star of " + cusp.starLord + ". "
                + Character.toUpperCase(v.charAt(0)) + v.substring(1) + "\n\n"
                + cusp.subLord + " brings " + NATURE.get(cusp.subLord) + " into this area of life. "
                + (occ.isEmpty() ? "No planet occupies the house itself, so results flow through the cusp sub lord and the significators."
                   : String.join(", ", occ) + (occ.size() > 1 ? " occupy" : " occupies") + " the house, adding " + String.join("; ", occ.stream().map(NATURE::get).toList()) + ".")
                + " The planets signifying this house are " + (sigs.isEmpty() ? "few" : String.join(", ", sigs)) + " — their dashas and bhuktis activate it.\n\n"
                + "Timing: the most supportive periods in the coming years are " + favourablePeriods(c, t.good(), 3) + ".\n\n"
                + "Guidance for you, " + first + ": " + psychAdvice(t.key(), answers);
    }

    public static String cuspText(Chart c, int h, String first) {
        Chart.Point cusp = c.cusps.get(h - 1);
        String sub = cusp.subLord;
        List<Integer> houses = c.housesSignified(sub);
        List<Integer> good = houses.stream().filter(x -> Set.of(1, 2, 3, 5, 6, 9, 10, 11).contains(x)).toList();
        List<Integer> hard = houses.stream().filter(x -> x == 8 || x == 12).toList();
        List<String> occ = c.planets.stream().filter(p -> p.house == h).map(p -> p.name).toList();
        return "The " + ord(h) + " cusp falls in " + cusp.sign + " at " + cusp.dms + ", ruled by " + cusp.signLord + ", in the star of " + cusp.starLord + " and the sub of " + sub
                + ". In KP, the cusp sub lord is the deciding factor: " + sub + " brings " + NATURE.get(sub) + " into matters of " + HOUSE_MEANING.get(h) + ".\n\n"
                + sub + " signifies houses " + joinHouses(houses) + ". "
                + (good.isEmpty() ? "It has limited connection with the houses of gain, so results come through patience and steady effort. "
                   : "Its connection with houses " + joinHouses(good) + " is supportive, especially during the dasha or bhukti of " + sub + " and of planets in its star. ")
                + (hard.isEmpty() ? "" : "The link with house " + joinHouses(hard) + " asks for planning and a measured approach at times.") + "\n\n"
                + (occ.isEmpty() ? "No planet occupies this house, so results flow mainly through the cusp sub lord and sign lord."
                   : String.join(", ", occ) + (occ.size() > 1 ? " occupy" : " occupies") + " this house, adding " + String.join("; ", occ.stream().map(NATURE::get).toList()) + ".");
    }
}
