# ⚡ VoltPur — Minecraft Server Software

[![Build](https://github.com/tkjij77-ctrl/VoltPur/actions/workflows/build.yml/badge.svg?branch=ver/26.2)](https://github.com/tkjij77-ctrl/VoltPur/actions)
![MC](https://img.shields.io/badge/Minecraft-26.2-brightgreen?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

---

## 📖 ما هو VoltPur؟

**خادم ماينكرافت** مبني على **Purpur 26.2** (وهو فورك من **Paper**)، يضيف فوقه **طبقة تشغيل وتحقق**: أوامر تشخيص، كشف عتاد حقيقي، قياس أداء، تحديثات ذاتية **متحقَّق منها ومُرجَعة (rollback)**، وحماية من الأخطاء التشغيلية على لوحات الاستضافة.

> **بصراحة كاملة:** الأداء الذي تحصل عليه هو أداء **Paper + Purpur** المدمج. VoltPur **لا يضيف أي patch على محرك اللعبة** للأداء (كل تحسينات الهوبر/التصادم/الذاكرة/الشبكة مسجّلة `PLANNED` وليست منفَّذة). قيمتنا الحقيقية هي: **القياس الصادق + العمليات الآمنة**، وهذه هي أول نسخة تفعل ذلك فعليًا.

---

## 🛡️ السلوك الافتراضي: آمن بالتصميم (v26.2.0-rc2)

| الميزة | الافتراضي | لماذا |
|---|---|---|
| `ItemLimiter` (حذف العناصر الزائدة) | **مطفأ** | كان يحذف غرض اللاعبين افتراضيًا وبلا تحذير |
| `DynamicOptimizer` (تعديل السبون/المحاكاة) | **مطفأ** | كان يغيّر قواعد اللعب دائمًا لحد الريستارت |
| `PAdmin` WebUI | **مطفأ** + loopback + Basic Auth | كان يستمع على `0.0.0.0` بلا مصادقة |
| «إعادة تثبيت نظيفة» عند التحديث | **مطفأة** | كان يمسح `plugins/`, `server.properties`, `ops.json`, `whitelist.json`, `backups/` |
| التحقق من SHA-256 للتحديث | **إجباري** | كان الفحص الوحيد «حجم > 1MB» |
| نسخة الجار السابق + `/vo rollback` | **مفعّلة** | مفيش طريقة رجوع كانت موجودة |

القاعدة الكاملة في **[POLICY.md](POLICY.md)** — أي ميزة تُعدّل ملفًا أو تحذف بيانات أو تفتح منفذًا: **مطفأة افتراضيًا + قابلة للرجوع + مقيسة**.

---

## ⌨️ الأوامر

```
/voltpur help         مرجع كل الأوامر + الوضع الأمني الحالي
/voltpur version      الإصدار + بصمة البناء المثبَّت
/voltpur modules      حالة كل موديول + صحته الحقيقية (runs / fails)
/voltpur status       TPS + MSPT + العوالم + الملفات + بصمة البناء
/voltpur worlds       العوالم المحمّلة (كيانات/chunks)
/voltpur hardware     CPU/RAM الفعلي + حصة الحاوية + أعلام JVM
/voltpur flags        أمر البدء الجاهز للنسخ
/voltpur benchmark    قياس حي (OP، كولداون 30 ثانية)
/voltpur optimize     كتابة الإعدادات المحسّنة (OP، يشترط auto-tune=true)
/voltpur reload       إعادة تحميل voltpur.yml (OP)

/vo up list           قائمة البناءات (مخزّنة على القرص)
/vo up <n>            تحميل + تحقّق SHA-256 + عرض الخطة (لا تنفيذ)
/vo up confirm        تنفيذ التحديث   |   /vo up cancel  إلغاء
/vo rollback          الرجوع للجار السابق
/vo in <url> <plugins|plugin-pro>   تنزيل بلجن (يتحقق من الشكل، لا من الثقة)

/padmin start|stop|status            واجهة ويب محلية (read-only)
```

---

## 🚀 التشغيل السريع

```bash
java -Xms2G -Xmx2G -XX:+UseG1GC -XX:+ParallelRefProcEnabled \
  -XX:MaxGCPauseMillis=200 -XX:+AlwaysPreTouch -XX:+ExitOnOutOfMemoryError \
  -jar server.jar --nogui
```
> **لا تنسخ أعلامًا جاهزة من الإنترنت.** شغّل `/voltpur flags` — الأمر يحسب `-Xmx` من **حصة حاويتك الفعلية** (cgroup) لا من ذاكرة المضيف، وهي مشكلة كانت توصي بـ 32GB داخل حاوية 2GB.

---

## 🔬 التحقق قبل أي دمج
```bash
tools/verify/run-verify.sh     # ترجمة فعلية + 136 فحصًا سلوكيًا (JDK 21+)
```
يفشل السكربت إذا كسرت أي تغيير الافتراضات الآمنة أو فحص الروابط أو تحقق الأرشيف. تفاصيل ما يُثبته وما لا يُثبته: `tools/verify/README.md`.

---

## 📦 حالة الموديولات (صادقة)

- **مُنفَّذة (13)** — منها **7 اختيارية مطفأة افتراضيًا**.
- **جزئية (4)** — مثل `AikarFlagsAuto` (توصية فقط)، `EntityActivation` (من Paper أصلًا).
- **مخططة (9)** — `HopperOptimization`, `CollisionOptimization`, `MemoryOptimization`, `NetworkOptimization`, `RedstoneOptimization`, `ChunkLoading`, `LightEngine`, `ConnectionStability`, `PerWorldPlugin` — **لا كود لها**.

> العدد يتغيّر مع كل إصدار، لذلك المصدر الموثوق هو `/voltpur modules` (يعرض الحالة + الصحة اللحظية). وأي رقم مكتوب في التوثيق يجب أن يطابقه.

---

## 🔧 تعديلات VoltPur على المحرك (كلها)

| الملف | الحجم | الوظيفة |
|---|---|---|
| `purpur-server/paper-patches/files/.../PluginInitializerManager.java.patch` | 9 أسطر | تسجيل `plugin-pro/` كمصدر بلجنات إضافي |
| `purpur-server/minecraft-patches/sources/net/minecraft/commands/Commands.java.patch` | سطر واحد | حماية `NPE` عند تنفيذ أمر قبل تحميل العالم |

هذا كل شيء. باقي المشروع (حوالي 2,900 سطر Java) طبقة أدوات داخل السيرفر.

**ملاحظة عن `plugin-pro/`:** الـ JARs بداخله تُكتشف **قبل** `plugins/`، لكن **ترتيب التحميل النهائي تحدده اعتماديات البلجن** (`depend`/`loadbefore`، أو `load: BEFORE|AFTER` في `plugin.yml`/`paper-plugin.yml`) — لا ندّعي غير ذلك.

---

## 📊 القياس

**لا ننشر أرقامًا بلا ملف قياس في المستودع.** للتشغيل:
```
/voltpur benchmark      → يكتب logs/voltpur-benchmark.txt (مع تدوير تلقائي)
```
المنهجية الكاملة + قائمة «الأرقام المسحوبة» القديمة في **[docs/BENCHMARKS.md](docs/BENCHMARKS.md)**.

---

## 📁 العوالم والتحديثات

- VoltPur **لا ينشئ** مجلدات عوالم وهمية، ولا يكتب `server.properties` (Minecraft/Paper يفعلان ذلك بشكل صحيح).
- التحديث الافتراضي **لا يحذف شيئًا**: يتحقق من الجار الجديد ثم يستبدل الجار المُشغَّل، ويحفظ نسخة `.bak-<timestamp>`، ويحفظ العوالم/configs/plugins كما هي.
- الخيار المدمِّر (`update.clean-reinstall`) موجود لمن يريده، ومطفأ افتراضيًا، **ويطبع أسماء ما سيُحذف أولًا**.

---

## 🔗 روابط

- **Releases:** https://github.com/tkjij77-ctrl/VoltPur/releases
- **Actions:** https://github.com/tkjij77-ctrl/VoltPur/actions
- **سياسة المشروع (إلزامية):** [POLICY.md](POLICY.md)
- **الأمان والمخاطر المتبقية:** [SECURITY.md](SECURITY.md)
- **آخر تعديلات التصليب:** [docs/HARDENING-2026-09.md](docs/HARDENING-2026-09.md)
- **المصدر الأصلي:** [Purpur](https://github.com/PurpurMC/Purpur) · [Paper](https://github.com/PaperMC/Paper)

## 📜 الترخيص
MIT License (نفس Purpur/Paper).
