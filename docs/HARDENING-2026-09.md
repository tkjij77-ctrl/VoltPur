# ⚡ VoltPur — تعديلات التصليب (Phase 0 + 1)

الوثيقة تشرح **ماذا تغيّر في هذه الجلسة، ولماذا، وكيف تتحقق**. تفاصيل كاملة في [CHANGELOG.md](CHANGELOG.md).

## أهم 10 تغييرات

1. **رجّعنا كل شغل التصليب الذي مُسح في `f718a8c`** (مصادقة PAdmin، تحقق SHA-256، النسخ الاحتياطي قبل تعديل الملفات، تدوير اللوج، `SECURITY.md`، استثناء الأسعار في `.gitignore`، إصلاح `build-fork.sh`).
2. **PAdmin**: loopback فقط + Basic Auth إجباري + باسورد غير فارغ + مقارنة ثابتة الزمن + GET فقط + قراءة-فقط + لقطة من المين ثريد (كان على `0.0.0.0` بلا مصادقة).
3. **المُحدِّث**: `/vo up <n>` أصبح **تحميل + تحقق + خطة**، و`/vo up confirm` هو التنفيذ. إضافة **`/vo rollback`**، وكشف **مسار الجار المُشغَّل فعليًا**.
4. **لا مسح شامل**: `update.clean-reinstall=false` افتراضيًا (كان يمسح `plugins/`, `server.properties`, `ops.json`, `whitelist.json`, `backups/`... كلها).
5. **`update.auto-backup` بقى حقيقيًا**: يعمل باك أب متسق للعوالم قبل استبدال الجار.
6. **`VoltPurWorldCheck`** لم يعد يكتب `server.properties` (كان يكتب `online-mode=false`!).
7. **`ItemLimiter`**: opt-in + لا يحذف إلا العناصر القديمة (60s+) وغير المسمّاة/غير المُسحّرة + يعلن في الشات قبل الحذف.
8. **`DynamicOptimizer`**: opt-in + فترة سماح بعد الإقلاع + تأكيد استمرار الضغط + **رجوع تلقائي** للقيم الأصلية.
9. **`VoltPurGuard`**: نظام يجعل فشل الموديولات مرئيًا (`runs/fails/last`) بدل 30 كتلة `catch` فارغة. وظهر في `/voltpur modules`.
10. **كشف الحاويات (cgroup)**: التوصية بـ `-Xmx` لم تعد تأخذ نصف ذاكرة **المضيف** (كانت توصي بـ 32GB داخل حاوية 2GB ⇒ قتل OOM).

## كيف تتحقق (3 أوامر)
```
/voltpur modules     → كل موديول + صحته الحقيقية (runs / fails) + الوضع الأمني الحالي
/vo up list          → قائمة البناءات (مخزنة على القرص)، ثم /vo up 1 يعرض الخطة قبل أي تنفيذ
/voltpur hardware    → هل داخل حاوية؟ ما حصتها الفعلية؟ ما الأعلام المناسبة؟
```

## التحقق المنفَّذ فعليًا (وليس ادعاءً)
```
tools/verify/run-verify.sh
== 1/3 compiling 48 files (JDK 21) ==   compile OK (62 classes)
== 2/3 core behaviour checks ==         43 PASS / 0 FAIL
== 3/3 updater / downloader checks ==   31 PASS / 0 FAIL   (74 total)
RESULT: PASS
```
الـ harness موجود داخل المستودع (`tools/verify/`) لأي حد يقدر يعيده: يترجم ملفات VoltPur الحقيقية مقابل stubs للواجهة، ثم يفحص السلوك (افتراضات آمنة، كومنتات `server.properties`، قائمة discord، عدّادات Guard، رفض روابط SSRF، تحقق الأرشيف المُنزَّل، SHA-256 معروف).
**وأمسك خللين في كودي الجديد قبل الكوميت:** عدّاد `Guard.runs()` لم يكن يزيد أبدًا، ومسار فشل تحميل البلجن كان صامتًا. التفاصيل في `CHANGELOG.md` (بندان 16 و17).

## تحقق بالبناء الحقيقي (CI، Java 25)
الفرع `fix/phase-0-1-hardening` مدفوع على المستودع، والبناء الرسمي اشتغل عليه:
| البند | القيمة |
|---|---|
| Run | **#66** — `workflow_dispatch` على `1cbaa68e` |
| النتيجة | `completed / success` (~7.5 دقيقة، 15:38→15:45 UTC) |
| الخطوات | Checkout ✓ · Setup Java 25 ✓ · Apply Patches ✓ · Build Paperclip JAR ✓ · Prepare Artifacts ✓ · Upload JAR ✓ |
| **Create Release** | **skipped** ✅ (الخطوة مقيّدة بـ `refs/heads/ver/26.2` — لم يُنشأ إصدار من فرع مراجعة) |
| الناتج | `VoltPur-26.2.jar` = **62 MiB** · `server.jar` = 62 MiB · `VoltPur-26.2.jar.sha256` · `file` يقول: `Java archive data (JAR)` |
| الـ artifact | `VoltPur-26.2-Paperclip` (185.0 MiB — يشمل 3 نسخ + الجار الأصلي) |
| `ver/26.2` | **لم يُلمس** — لسه `f718a8c` |

الخلاصة: الشجرة المُصلَّبة **تبني فعليًا** بجافا 25 وتنتج JAR سليمًا، وليس فقط «تترجم مقابل stubs».

## smoke test حقيقي (إقلاع سيرفر فعلي)
شغّلنا الجار الناتج فعليًا (JDK 25، عالم flat، ‎`-Xmx768M`) حتى الإقلاع الكامل ثم أوقفناه بالأمر:

| الفحص | النتيجة الفعلية من اللوج |
|---|---|
| الإقلاع | `Done (13.181s)!` |
| هوية البناء | `This server is running Purpur version 26.2-DEV-fix/phase-0-1-hardening@768d513` ⇒ جار مبني من **رأس الفرع** |
| سطر الصدق | `[VoltPur] Modules: 13 implemented, 4 partial, 9 planned` |
| الوضع الأمني | `item-limiter=off optimizer=off padmin=off destructive-reinstall=off update-checksum=required` |
| الموديولات عند الإقلاع | كل الموديولات المدمِّرة أعلنت تعطيلها بنفسها: `ItemLimiter: disabled (opt-in)` · `Dynamic optimizer is disabled` · `PAdmin`/`Backup`/`ResourcePack`/`Discord` = disabled |
| `/voltpur modules` | 26 سطرًا بحالات حقيقية، منها `ItemLimiter - ACTIVE [disabled]`, `PAdminWebUI - ACTIVE [disabled]`, `DiscordWebhook - ACTIVE [opt-in]`, و9 موديولات `PLANNED - not implemented` |
| `/voltpur status` | `TPS: 20.00/20.00/20.00 | MSPT avg: 0.29 ms` + `Installed build: not tracked (no voltpur-installed.txt)` — أي لا يدّعي تحديثًا لم يحدث |
| عوالم | 3 عوالم، والقراءة فقط: `Loaded worlds: 3 (overworld=not named 'world', nether=OK, end=OK)` |

**ما لم يثبته هذا الاختبار:** لا قياس أداء تحت حمل (السيرفر كان فارغًا: 0 لاعبين، TPS 20 ثابت شيء متوقع)، ولا اختبار لمنطق `/vo up` الفعلي ضد GitHub، ولا اختبار عملاء حقيقيين.

## مراجعة لوج سيرفر حقيقي (Build 66 على MineStrator)
شُغّل Build 66 على استضافة حقيقية (حاوية 4.8GB، Java 25، `-Xmx3077M`). النتيجة: **إقلاع سليم** `Done (13.180s)`، و**صفر أخطاء**، والوضع الأمني مطبَّق فعليًا: `/padmin start` رفض التشغيل (معطّل افتراضيًا)، و`ItemLimiter` أعلن أنه لا يحذف شيئًا، وسجل الـ benchmark كتب بصدق `HopperOpt: off - no NMS hopper patch in this build`، و`ModuleGuard: runs=8 failures=0`.

اللوج كشف **٤ عيوب حقيقية** أُصلحت كلها:

| # | ما ظهر في اللوج | الإصلاح | التغطية |
|---|---|---|---|
| 1 | `MC 1.21.10` بينما السيرفر نفسه يعلن `Minecraft 26.2` | `VoltPur.mcVersion()` يسأل السيرفر وقت التشغيل | تأكيدان |
| 2 | `recommended -Xmx2457M` لخفض هيب شغّال 3077MB في حاوية 4915MB | اترك 1GB خارج الهيب ⇒ **3840MB**، + سطر حكم على الهيب الحالي | ٥ تأكيدات |
| 3 | `RAM: 4915 MB host` و`Container: yes (none)` | تسمية المصدر الفعلي + عرض `/proc/meminfo` كرؤية النواة + لا `none` أبدًا | تأكيدان |
| 4 | كل سطر benchmark مطبوع مرتين في الكونسول | نسخة اللوج للاعبين فقط | — |

**تحقّق:** `tools/verify/run-verify.sh` ⇒ **91 PASS / 0 FAIL** (كانت 74).

### Build 68 — الإصدار الرسمي الحالي
يعالج «العيب الخامس» أدناه، وبُني من `fbf6b1e3` (CI Run #80):
`sha256:38eca2b6805a0f5cf11357bc6ef31c3bfa46de7bbdd40872ed5f90d075a70260` · 91 تأكيدًا PASS · تحقّق حي بأربع جلسات (إجهاز → إغلاق بتحذير → إعادة تشغيل → `confirm` → إقلاع).

### ✅ تحقّق حيّ على استضافة المشغّل (MineStrator) — Build 68
بعد `/vo up confirm` وإعادة التشغيل، أقلع السيرفر على الجار المنشور، وهذه السطور منسوخة حرفيًا من لوجه:
```
Loading Purpur 26.2-DEV-fix/phase-0-1-hardening@fbf6b1e ... for Minecraft 26.2
[VoltPur] Installed via /vo up -> build #68 | commit fbf6b1e | checksum verified: true | at Fri Sep 18 20:41:55 CEST 2026
[VoltPur-HW] RAM       : 4915 MB usable by this server (source: /sys/fs/cgroup/memory.max (cgroup v2) + cpu quota)
[VoltPur-HW]             /proc/meminfo reports 515620 MB (kernel view; lxcfs makes this the container limit too)
[VoltPur-HW] Container : yes - limits detected (/sys/fs/cgroup/memory.max (cgroup v2) + cpu quota)
[VoltPur-HW] Heap      : 3078 MB now | OK - 3078 MB of 4915 MB available (upper suggestion: -Xmx3840M)
```
**النتيجة:** لا أثر لـ «1.21.10» ولا «4915 MB host» ولا «yes (none)» ولا «source: none» — الأربعة عيوب اختفت على السيرفر الحقيقي. واقتراح الهيب ‎-Xmx3840M‎ مطابق تمامًا لحساب `suggestHeapFor(4915)` (4915 − 1024 = 3891 → 3840 بعد التقريب لـ64). وظهر أيضًا سطر الختم `Installed via /vo up -> build #68` وسطر المقاطعة (cross-check) عند الإقلاع.

### عيب خامس ظهر لاحقًا على نفس الاستضافة
المشغّل نفّذ `/vo up 1` (نجح التحميل والتحقّق) ثم **`stop` بدون `/vo up confirm`**، فالسيرفر رجع على Build 66 والجار المُنزَّل بقي بلا استخدام.
عيب تجربة حقيقي، أُصلح في **Build 68**: خطة التحديث تُحفظ على القرص فتعمل `confirm` بعد إعادة التشغيل، مع **تحذير عند الإغلاق** لو فيه تحديث مُجهَّز لم يُطبَّق. تغطية: ٧ تأكيدات جديدة.

## الإصدار الرسمي
**Build 67** هو الإصدار الرسمي الحالي (Releases/Latest) — `build-67-6dc4ad04db38253226b2af48ed2051cc157fb286`، مُنتَج من CI (Run #77، Java 25) على نفس الكوميت. يضم الإصلاحات الأربعة المستخرَجة من لوج سيرفر حقيقي + تحديث الـ actions إلى `checkout@v7`/`setup-java@v6`/`upload-artifact@v7`. sha256 الجار: `5007669905bf89111f25fa20586057f55e835ac15f0d54910956cb343f6b80d3`، وإقلاع مُختبَر: `Done (11.886s)`.

**Build 66** كان الإصدار الرسمي قبله (`build-66-b7def480...`) وما زال متاحًا للتحميل:
- الوسم: `build-66-b7def480e4cf157dae5322b562ed880ac7569854` · ٤ أصول: `VoltPur-26.2.jar`, `.sha256`, `server.jar`, `VoltPur.jar`
- الجار: 64,753,387 بايت · sha256 = `3f589c297e8a3018a471bbae8251dae2a24b6cf6c252af36efe4dbd8fce00111` (مطابق للبصمة التي حسبها GitHub عند الرفع)
- تحقّق فعلي: `/vo up list` يعرضه `[1] build #66`, و`/vo up 1` حمّله وتحقق من البصمة وطبع خطة لا تحذف شيئًا
- **يتطلب Java 25**

## اكتشاف أثناء النشر: خط CI غير حتمي
عند نشر Build 66، فشل Run #69 على الكوميت `40474803` بخطأ في **ملف مولَّد** (`src/minecraft/java/net/minecraft/world/level/entity/EntitySectionStorage.java:136: illegal start of expression`) — وهو ملف ينشئه خط الباتشات/فك التصريف ولا وجود له في أي patch بالمستودع ولا في كودنا.
**الدليل على أنه ليس عيب كود:** نفس الكوميت بالظبط نجح في Run #70 دون أي تغيير.
**ما فعلناه:** إعادة محاولة واحدة عند الفشل (تنظيف المصادر المولَّدة + إعادة `applyAllPatches` + إعادة البناء). منطق الشل نفسه مُختبَر محليًا على 3 حالات: مسار سليم (لا إعادة)، flaky (ينجح)، فشل حقيقي (**يفشل — لا نجاح كاذب**).

**ملاحظة تجميلية (لم تُصلَّح):** الإيموجي في رسائل الكونسول تُطبع `?` لما ترميز الكونسول مش UTF-8.

## ما لم يُنفَّذ في هذه الجلسة (بوضوح)
- **تشغيل سيرفر حقيقي / smoke test إقلاع**: لم يُشغَّل بعد (لا يتحقق منه البناء وحده). الـ harness لا يعوّض ذلك.
- CI smoke test (إقلاع headless في Actions) — مقترح في المرحلة 2.
- `Claim → Probe` (توليد الأرقام آليًا في CI) — المرحلة 2.
- تحويل الأدوات إلى بلجن مستقل `VoltPur.jar` — قرار استراتيجي (المرحلة 3).
- أي patch على NMS (الهوبر/التصادم/الذاكرة) — **لم ندّعِها ولن ندّعيها قبل قياس**.
