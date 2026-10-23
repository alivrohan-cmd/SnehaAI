# Sneha AI (স্নেহা) — v0.1.0

Package: `com.sneha.ai` • Kotlin + Jetpack Compose • মস্তিষ্ক: Google Gemini

> সৎ অবস্থা: এই সোর্স কোড লেখা হয়েছে কিন্তু **এখনও কোনো ফোনে বিল্ড বা টেস্ট করা হয়নি**।
> প্রথম বিল্ডে কম্পাইল এরর আসতে পারে — এরর লগ Claude-কে পাঠালে ঠিক করে দেওয়া হবে।

## বিল্ডের পথ (সহজ থেকে কঠিন)

### পথ ১ — GitHub Actions (সবচেয়ে নির্ভরযোগ্য)
1. GitHub-এ একটি **private** রিপো বানান, জিপের সব ফাইল আপলোড করুন (API key কোথাও রাখবেন না)।
2. Actions ট্যাব > "Build APK" > Run workflow।
3. শেষ হলে Artifacts থেকে `sneha-ai-debug-apk` নামান → APK ইনস্টল করুন।

### পথ ২ — Termux (পরীক্ষামূলক, ৬GB+ RAM ও ৫GB+ ফাঁকা জায়গা ভালো)
Termux অবশ্যই F-Droid বা GitHub থেকে নেবেন (Play Store সংস্করণ নয়)।

```bash
termux-setup-storage
cd ~ && cp /sdcard/Download/SnehaAI.zip . && unzip SnehaAI.zip && cd SnehaAI
chmod +x setup-termux.sh build.sh
./setup-termux.sh     # একবার: JDK 17, aapt2, Android SDK (platform 34)
./build.sh            # Gradle 8.5 নামিয়ে assembleDebug চালায়
```
APK-র অবস্থান: `~/SnehaAI/app/build/outputs/apk/debug/app-debug.apk`

```bash
cp app/build/outputs/apk/debug/app-debug.apk /sdcard/Download/SnehaAI.apk
```
তারপর ফাইল ম্যানেজার থেকে ইনস্টল করুন ("অজানা উৎস" অনুমতি লাগবে)।

### Troubleshooting
- `aapt2` এরর: `pkg install aapt2` ও `build.sh` চালান (এটি `-Pandroid.aapt2FromMavenOverride` দেয়)।
- OutOfMemory: `gradle.properties`-এ `-Xmx1536m` কমিয়ে `-Xmx1024m` করুন, অন্য অ্যাপ বন্ধ রাখুন।
- SDK/লাইসেন্স এরর: `setup-termux.sh` আবার চালান।
- Gradle native/library এরর: `gradle.properties`-এ `systemProp.org.gradle.native=false` আছে; তবুও হলে পথ ১ ব্যবহার করুন।
- Termux-এ না হলে অন্য ফোন/PC-তে: `gradle assembleDebug` (JDK 17 + Android SDK লাগবে)।

## প্রথম ব্যবহার
1. অ্যাপ খুলুন → ⚙ সেটিংস → AI Brain → Gemini API key দিন (aistudio.google.com থেকে) → Test Connection। তালিকা থেকে মডেল বাছুন।
2. মাইক বাটন চাপলে মাইক্রোফোন অনুমতি চাইবে।
3. সেটিংস > অটোমেশন থেকে Accessibility চালু করুন (গাইড অ্যাপেই আছে)।

## ফাইল ট্রি
```
SnehaAI/
├─ settings.gradle.kts  build.gradle.kts  gradle.properties
├─ build.sh  setup-termux.sh  .github/workflows/build.yml
└─ app/src/main/
   ├─ AndroidManifest.xml
   ├─ res/ (xml/accessibility_service_config.xml, values/, drawable/)
   └─ java/com/sneha/ai/
      ├─ MainActivity.kt  SnehaApp.kt
      ├─ data/  (Prefs, SecureKeyStore, MemoryStore, ChatStore)
      ├─ ai/    (AiProvider, GeminiProvider, ActionPlan, SystemPrompt)
      ├─ apps/  (AppLauncher)   media/ (YouTubeHandoff)
      ├─ voice/ (SpeechInput, SpeechOutput)
      ├─ automation/ (SnehaAccessibilityService, ActionExecutor)
      ├─ coding/ (ProjectStore, TermuxRunner)
      └─ ui/ (Theme, Orb, MainViewModel, CodingViewModel, HomeScreen, CodingScreen, SettingsScreen)
```

## ফিচারের অবস্থা
| ফিচার | অবস্থা |
|---|---|
| Gemini চ্যাট, Test Connection, মডেল তালিকা, ব্যবহার পরিসংখ্যান | Implemented but not tested |
| JSON অ্যাকশন প্ল্যান + allow-list ভ্যালিডেটর + নিশ্চিতকরণ | Implemented but not tested |
| ভয়েস ইনপুট/আউটপুট, ভাষা, গতি, ভলিউম, ভয়েস বাছাই, থামানো | Implemented but not tested |
| নারী কণ্ঠ স্বয়ংক্রিয় বাছাই | Not implemented — Android ভয়েসের লিঙ্গ জানায় না; ব্যবহারকারী শুনে বাছে |
| Wake phrase / সবসময় শোনা | Not implemented — ফোরগ্রাউন্ড সার্ভিস + কীওয়ার্ড ইঞ্জিন লাগে |
| ফ্লোটিং বাবল | Not implemented |
| অ্যাপ খোলা, প্রিয় অ্যাপ | Implemented but not tested |
| অ্যাপ Force-stop | Not implemented — সাধারণ অ্যাপে Android অনুমতি দেয় না; শুধু Back/Home |
| Accessibility: ট্যাপ, স্ক্রল, Back/Home, টাইপ, Enter, লেবেল পড়া, STOP | Implemented but not tested |
| YouTube সার্চ হ্যান্ডঅফ | Implemented but not tested |
| YouTube প্রথম ফলাফল চালানো + প্লেব্যাক যাচাই | Partially implemented (UI লেবেল নির্ভর, ইংরেজি "play video" ধরে) |
| মিডিয়া কী (pause/next/prev) | Implemented but not tested |
| কোডিং মোড (লেখা/ব্যাখ্যা/ঠিক করা), কপি, সেভ, এক্সপোর্ট | Implemented but not tested |
| HTML/CSS/JS স্যান্ডবক্স প্রিভিউ | Implemented but not tested |
| Termux দিয়ে Python/C/C++/Java/Node/Bash | Partially implemented (প্রোটোকল ডক্স থেকে লেখা, আসল Termux-এ যাচাই বাকি) |
| Kotlin ইত্যাদি চালানো | Not implemented — রানটাইম নেই |
| মেমরি (দেখা/এডিট/মোছা/এক্সপোর্ট/বন্ধ) | Implemented but not tested |
| Room ডেটাবেস | Not implemented — সহজ JSON ফাইল, Termux বিল্ড হালকা রাখতে |
| Claude/OpenAI/লোকাল মডেল | Not implemented — ইন্টারফেস তৈরি |
| থিম, ফন্ট, রিডিউস-মোশন | Implemented but not tested |
| রিমাইন্ডার | Not implemented |
| অ্যাপ বিল্ড/ইনস্টল | Not verified |

## টেস্ট চেকলিস্ট (ফোনে করে ফলাফল জানান)
- [ ] APK ইনস্টল ও চালু
- [ ] API key সেভ → Test Connection → মডেল তালিকা
- [ ] বাংলা ও ইংরেজি টেক্সট চ্যাট
- [ ] মাইক অনুমতি দিয়ে/না দিয়ে ভয়েস ইনপুট; বাংলা TTS
- [ ] "YouTube খোলো", "Settings-এ নিয়ে যাও", অনুপস্থিত অ্যাপ
- [ ] Accessibility চালু; Back, স্ক্রল, ট্যাপ; STOP বাটন/নোটিফিকেশন
- [ ] YouTube সার্চ হ্যান্ডঅফ; প্রথম ফলাফল চালানো; pause/next
- [ ] ব্যাংকিং অ্যাপে অটোমেশন ব্লক হয় কিনা
- [ ] কোড তৈরি; HTML/JS প্রিভিউ; Termux Python রান (অনুমোদন ডায়ালগসহ)
- [ ] ভুল key / ইন্টারনেট বন্ধ / কোটা ত্রুটির বার্তা
- [ ] মেমরি যোগ/এডিট/মোছা/এক্সপোর্ট/বন্ধ
- [ ] অ্যাপ রিস্টার্টের পর চ্যাট, সেটিংস, মেমরি থাকে কিনা
- [ ] ব্যাটারি/পারফরম্যান্স (মধ্যম মানের ফোনে)

## জানা সীমাবদ্ধতা
- Android ১০+ এ ব্যাকগ্রাউন্ড থেকে অ্যাপ চালু আটকে যেতে পারে; অ্যাপ ফলাফল যাচাই করে জানায়।
- Android ১৩+ এ ইনস্টল করা APK-র Accessibility চালুর আগে "Allow restricted settings" লাগে।
- ডিফল্ট মডেল `gemini-3.6-flash` (ডক্স অনুযায়ী); Test Connection আপনার কী-র মডেল তালিকা থেকে মিলিয়ে নেয়।
- API key APK-তে নেই; ফোনে Keystore-এনক্রিপ্টেড। প্রোডাকশনে সিকিউর ব্যাকএন্ড দরকার।
