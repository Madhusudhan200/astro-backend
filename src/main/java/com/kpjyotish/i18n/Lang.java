package com.kpjyotish.i18n;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Supported languages: Indian languages first, then world languages. Codes follow Google Translate. */
public record Lang(String code, String name, String nativeName, String fontFamily, boolean rtl, boolean indian) {

    private static final Map<String, Lang> ALL = new LinkedHashMap<>();

    private static void in(String code, String name, String nat, String font) { ALL.put(code, new Lang(code, name, nat, font, false, true)); }
    private static void inRtl(String code, String name, String nat, String font) { ALL.put(code, new Lang(code, name, nat, font, true, true)); }
    private static void w(String code, String name, String nat, String font) { ALL.put(code, new Lang(code, name, nat, font, false, false)); }
    private static void wRtl(String code, String name, String nat, String font) { ALL.put(code, new Lang(code, name, nat, font, true, false)); }

    static {
        w("en", "English", "English", "NotoSans");
        // ---- Indian languages ----
        in("te", "Telugu", "తెలుగు", "NotoSansTelugu");
        in("hi", "Hindi", "हिन्दी", "NotoSansDevanagari");
        in("ta", "Tamil", "தமிழ்", "NotoSansTamil");
        in("kn", "Kannada", "ಕನ್ನಡ", "NotoSansKannada");
        in("ml", "Malayalam", "മലയാളം", "NotoSansMalayalam");
        in("mr", "Marathi", "मराठी", "NotoSansDevanagari");
        in("bn", "Bengali", "বাংলা", "NotoSansBengali");
        in("gu", "Gujarati", "ગુજરાતી", "NotoSansGujarati");
        in("pa", "Punjabi", "ਪੰਜਾਬੀ", "NotoSansGurmukhi");
        in("or", "Odia", "ଓଡ଼ିଆ", "NotoSansOriya");
        inRtl("ur", "Urdu", "اردو", "NotoNaskhArabic");
        in("as", "Assamese", "অসমীয়া", "NotoSansBengali");
        in("sa", "Sanskrit", "संस्कृतम्", "NotoSansDevanagari");
        in("ne", "Nepali", "नेपाली", "NotoSansDevanagari");
        in("gom", "Konkani", "कोंकणी", "NotoSansDevanagari");
        in("mai", "Maithili", "मैथिली", "NotoSansDevanagari");
        in("bho", "Bhojpuri", "भोजपुरी", "NotoSansDevanagari");
        in("doi", "Dogri", "डोगरी", "NotoSansDevanagari");
        inRtl("sd", "Sindhi", "سنڌي", "NotoNaskhArabic");
        in("mni-Mtei", "Manipuri (Meitei)", "ꯃꯤꯇꯩꯂꯣꯟ", "NotoSansMeeteiMayek");
        in("lus", "Mizo", "Mizo ṭawng", "NotoSans");
        // ---- World languages ----
        w("es", "Spanish", "Español", "NotoSans");
        w("fr", "French", "Français", "NotoSans");
        w("de", "German", "Deutsch", "NotoSans");
        wRtl("ar", "Arabic", "العربية", "NotoNaskhArabic");
        w("zh-CN", "Chinese (Simplified)", "简体中文", "NotoSansSC");
        w("zh-TW", "Chinese (Traditional)", "繁體中文", "NotoSansTC");
        w("ja", "Japanese", "日本語", "NotoSansJP");
        w("ko", "Korean", "한국어", "NotoSansKR");
        w("ru", "Russian", "Русский", "NotoSans");
        w("pt", "Portuguese", "Português", "NotoSans");
        w("it", "Italian", "Italiano", "NotoSans");
        w("nl", "Dutch", "Nederlands", "NotoSans");
        w("tr", "Turkish", "Türkçe", "NotoSans");
        wRtl("fa", "Persian", "فارسی", "NotoNaskhArabic");
        w("id", "Indonesian", "Bahasa Indonesia", "NotoSans");
        w("ms", "Malay", "Bahasa Melayu", "NotoSans");
        w("th", "Thai", "ไทย", "NotoSansThai");
        w("vi", "Vietnamese", "Tiếng Việt", "NotoSans");
        w("si", "Sinhala", "සිංහල", "NotoSansSinhala");
        w("my", "Burmese", "မြန်မာ", "NotoSansMyanmar");
        w("km", "Khmer", "ខ្មែរ", "NotoSansKhmer");
        w("lo", "Lao", "ລາວ", "NotoSansLao");
        w("tl", "Filipino", "Filipino", "NotoSans");
        w("sw", "Swahili", "Kiswahili", "NotoSans");
        wRtl("iw", "Hebrew", "עברית", "NotoSansHebrew");
        w("el", "Greek", "Ελληνικά", "NotoSans");
        w("pl", "Polish", "Polski", "NotoSans");
        w("uk", "Ukrainian", "Українська", "NotoSans");
        w("ro", "Romanian", "Română", "NotoSans");
        w("hu", "Hungarian", "Magyar", "NotoSans");
        w("cs", "Czech", "Čeština", "NotoSans");
        w("sv", "Swedish", "Svenska", "NotoSans");
        w("no", "Norwegian", "Norsk", "NotoSans");
        w("da", "Danish", "Dansk", "NotoSans");
        w("fi", "Finnish", "Suomi", "NotoSans");
        w("ka", "Georgian", "ქართული", "NotoSansGeorgian");
        w("hy", "Armenian", "Հայերեն", "NotoSansArmenian");
        w("am", "Amharic", "አማርኛ", "NotoSansEthiopic");
        w("af", "Afrikaans", "Afrikaans", "NotoSans");
        w("zu", "Zulu", "isiZulu", "NotoSans");
    }

    public static Lang get(String code) { return code == null ? null : ALL.get(code); }

    public static List<Lang> all() { return List.copyOf(ALL.values()); }
}
