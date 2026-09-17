# 📜 VoltPur / VoltCore — Changelog الكامل

> كل ما بُني وتم التحقق منه عبر هذه المراحل. الأرقام تُقاس عبر `/voltpur benchmark` — لا أرقام وهمية.

## 🛡️ v26.2.0-rc2 — «التصليب: عمليات آمنة + صدق قابل للتحقق»

> جلسة كاملة مبنية على تدقيق كود مستقل. الهدف: إزالة المخاطر التي كانت تُنافي سياسة المشروع.

### 🔴 إصلاح مخاطر حرجة
| # | ما كان | ما صار |
|---|---|---|
| 1 | `PAdmin` بلا مصادقة على `0.0.0.0` (بعد أن أُلغي التصليب في `f718a8c`) | loopback فقط + Basic Auth إجباري + مقارنة ثابتة الزمن + GET فقط + لقطة من المين ثريد (Paper API ليست thread-safe) |
| 2 | المُحدِّث بلا تحقق SHA-256 (الفجر > 1MB فقط) | تحقّق إجباري من الـ checksum المنشور + فحص zip/manifest/versions.list + رفض عند عدم التطابق |
| 3 | `/vo up` يمسح كل الملفات (plugins, server.properties, ops.json, whitelist.json, backups) بلا نسخة | تحديث **على مرحلتين** (`<n>` ثم `confirm`) + استبدال جار غير مدمِّر + `server.jar.bak-*` + `/vo rollback` |
| 4 | `update.auto-backup: true` مكتوب ولا كود يقرأه | يعمل فعليًا: باك أب متسق قبل استبدال الجار |
| 5 | `WorldCheck` يكتب `server.properties` بـ `online-mode=false` ويمسح الكومنتات | الدالة أُزيلت نهائيًا - القراءة فقط |
| 6 | `ItemLimiter` يحذف غرض اللاعبين افتراضيًا | opt-in + عمر أدنى 60s + استثناء المسمّى/المُسحّر + إعلان في الشات قبل الحذف |
| 7 | `DynamicOptimizer` يغيّر السبون/المحاكاة نهائيًا ومفعّل افتراضيًا | opt-in + فترة سماح بعد الإقلاع + تأكيد استمرار الضغط + **رجوع تلقائي** |
| 8 | 30 كتلة `catch` فارغة تُخفي فشل الموديولات | `VoltPurGuard`: عدّاد runs/fails/last + لوج محدّد بمرة/دقيقة + يظهر في `/voltpur modules` |
| 9 | توصية `-Xmx = نصف ذاكرة المضيف` (قتل OOM داخل الحاويات) | قراءة cgroup v2/v1: التوصية مقيّدة بحصة الحاوية فعليًا |
| 10 | منطق أعلام JVM مقلوب (`Class.forName` للتحقق من موديول غير محمّل) | `ModuleFinder.ofSystem()` + `ModuleLayer.boot()` |
| 11 | `VoltPurResourcePack` يقرأ الملف كاملًا في الذاكرة لكل طلب | بث متدفق + مسار عشوائي `pack-<token>.zip` + طباعة `resource-pack-sha1` |
| 12 | `VoltPurBackup` يضغط عوالم شغّالة (باك أب غير متسق) + أسماء عوالم ثابتة | `save-off → save-all flush → zip → save-on` + أسماء من `Bukkit.getWorlds()` + تحقق من الأرشيف |
| 13 | `VoltPurTuning` يكتب ملفات بلا نسخة ويمسح الكومنتات ورسالة «Applied» غير دقيقة | نسخة `.bak-<ts>` + تحرير سطر-بسطر يحفظ الكومنتات + «يحتاج ريستارت» + تسجيل قبل → بعد |
| 14 | المهام مملوكة لبلجن طرف ثالث (تموت عند تعطيله) | المالك دائمًا داخلي (`MinecraftInternalPlugin`) - نفس أسلوب Purpur |
| 15 | شغل تصليب مُسح في `f718a8c` (SECURITY.md، تدوير اللوج، `.gitignore`، `build-fork.sh`) | مُسترجَع كاملًا + `POLICY.md` يوثّق الضمانات مع طريقة التحقق منها |

### ✅ تحقق منفَّذ (قابل للتكرار)
```
tools/verify/run-verify.sh
→ compile OK (62 classes, JDK 21) · core checks: 43 PASS · command checks: 20 PASS
→ RESULT: PASS
```
الـ harness الجديد (`tools/verify/`) يترجم ملفات VoltPur فعليًا مقابل stubs للواجهة، ويشغّل **63 تأكيدًا سلوكيًا** (الافتراضات الآمنة، حفظ كومنتات `server.properties`، قائمة discord المسموح بها، عدّادات Guard، فحص روابط SSRF، تحقق الأرشيف، قيمة SHA-256 معروفة).

**وأمسك خللين حقيقيين في نفس الجلسة قبل الكوميت:**
| # | ما كان | ما صار |
|---|---|---|
| 16 | `VoltPurGuard.Stat.runs()` لا يزيد أبدًا عند النجاح ⇒ `/voltpur modules` يعرض `runs=0` للأبد | العدّاد يزيد في مسار النجاح + اختبار يغطّي «فشل ← شفاء» |
| 17 | `PluginJarInstaller.install` يرمي استثناءً مفحوصًا داخل مهمة غير متزامنة بلا `catch` ⇒ فشل التحميل بصمت | مسار فشل يخبر اللاعب + سطر WARNING (بدون طباعة الرابط لأنه قد يحمل توكن) |

### 📉 إزالة أرقام وكود غير حقيقي
- `docs/BENCHMARKS.md` → نسخة جديدة: منهجية + قائمة **«أرقام مسحوبة»** (62% هوبَر وغيرها) موضّح سبب إلغائها.
- `PTERODACTYL_FIX.md` → حُذفت مخرجات الإقلاع الوهمية (`Loading 21 modules`).
- `docs/archive-old-patches/` → `README.md` يوضّح أن الـ 21 patch غير قابلة للتطبيق (هاشات مزيّفة) وبعضها يكسر اللعبة.
- إعدادات ميتة أُزيلت: `hopper-sleep.*`, `entity-limiter`, `chunk-optimization` (مع رسالة لوج بالاسم عند وجودها في ملف قديم).

### 🧹 نظافة
- حذف `nulcd` (ملف فيه رسالة خطأ ويندوز) و`postman/`.
- استرجاع صلاحية التنفيذ (`100755`) لـ `gradlew` و`scripts/*.sh`.
- `build-fork.sh` أُصلح ليطابق CI (Java 25 + `:purpur-server:createPaperclipJar` + مسار الجار الصحيح).

### ❌ لم يُنفَّذ في هذا الإصدار (بصدق)
- ~~بناء السيرفر الفعلي~~ → **تم**: CI run **#66** على الفرع `fix/phase-0-1-hardening` (`1cbaa68e`) = `success` بجافا 25، والناتج `VoltPur-26.2.jar` **62 MiB** (`Java archive data (JAR)`)، وخطوة الـ Release تخطّتها CI عمدًا لأنها مقيّدة بـ `ver/26.2`.
- تشغيل سيرفر Paper اختباريًا (smoke test إقلاع headless) — مُقترح للمرحلة 2.
- توليد الأرقام آليًا (Claim→Probe) · تحويل الأدوات إلى بلجن مستقل · **أي patch NMS**.

---

## ✅ Phase 1 — «الحقيقة + تشغيل المحرّك الحقيقي»
**الهدف:** جعل كل ادعاء في السوفت وير صادقاً، وتشغيل ما هو موجود فعلاً.

| الملف | الإنجاز |
|-------|---------|
| `VoltPurModules.java` | (جديد) سجلّ موديولات **صادق** (24 موديول، حالة ACTIVE/PARTIAL/PLANNED) بدل "21 ENABLED" الوهمية |
| `VoltPurPerformance.java` | ربط أداء حقيقي (ItemLimiter + TPSMonitor + ChunkCheck يعملون فعلاً)، أُزيلت "logging only" المضلِّلة |
| `VoltPurBenchmark.java` | (جديد) أداة قياس **حيّة**: TPS/MSPT/entities/chunks/heap/hoppers → `logs/voltpur-benchmark.txt` |
| `VoltPurHardware.java` | (جديد) كشف العتاد + تحذيرات توافق + توليد أعلام JVM |
| `VoltPurTuning.java` | (جديد) ضبط opt-in لـ `server.properties` حسب العتاد |
| `VoltPurCommand.java` | أوامر صادقة + `benchmark` |
| `PAdminCommand.java` | بيانات حقيقية (بدل "Modules: 21") + `/api/status` |
| `VoltPurConfig.java` | إزالة الخيارات الميتة (hopper/entity المضللة) |
| `VoltPur.java` | أُزيلت مصفوفة الموديولات الوهمية + صادق في plugin-pro |

## ✅ Phase 2 — «التصحيح الوظيفي والاستقرار»
**الهدف:** إصلاح مشاكل تشغيلية حقيقية اكتُشفت من الـ console.

| الإصلاح | المشكلة التي حلّها |
|---------|-------------------|
| `VoltPurWorldCheck` | كان "fallback / Worlds: 0" + خطأ main-thread → جدولة عبر GlobalRegionScheduler |
| `VoltPurPerformance` | خطأ `Timer-0 failed main thread check` (async world access) → GlobalRegionScheduler (main thread, null-plugin safe) |
| `VoltPurCommand` (/vo up) | `runTaskAsynchronously(null)` كان يفشل على سيرفر بلا plugins → AsyncScheduler |
| `plugin-pro` patch | صيغة `.patch` paperweight صحيحة (بدل ملف كامل) |

## ✅ Phase 3 — «المرحلة النهائية / التكامل»
**الهدف:** سوفت وير متكامل ذاتي التوثيق.

| الإضافة | الوصف |
|---------|-------|
| `VoltPurHelp.java` | (جديد) `/voltpur help` — مرجع كامل لكل الأوامر في مكان واحد |
| `CHANGELOG.md` | هذا الملف |
| README | قسم شرح صادق عن "ماذا يفعل السوفت وير فعلاً" |

---

## 🧠 الموديولات النهائية (حالة صادقة)
| الحالة | الموديولات |
|--------|-----------|
| 🟢 **ACTIVE (8)** | HardwareDetection, HardwareAutoTune, ItemLimiter, TPSMonitor, WorldStability, PterodactylFix, Updater, PAdminWebUI |
| 🟠 **PARTIAL (3)** | AikarFlagsAuto, BedrockBridge, AntiExploit |
| 🟠 **PARTIAL (1)** | EntityActivation (عبر Paper EAR المدمج) |
| 🟡 **PLANNED (بقية)** | HopperOptimization (snippet جاهز), Collision, Memory, Network, Redstone, Chunk, Light, إلخ |

## 📦 مخرجات البناء
- **Build workflow** → يبني Paperclip JAR + ينشئ Release عام (`server.jar`, `VoltPur.jar`, `VoltPur-26.2.jar`, `.sha256`).
- **`/vo up`** → تحديث ذاتي من آخر build (بدون token عبر الـ Release العام).

## ✅ إضافة: تحسين الهوبر الحقيقي (hopper-check=8)
- **البحث المعمّق** أثبت أن Purpur يرث تحسينات Paper/Pufferfish للـ hopper أصلًا.
- القيمة الحقيقية لـ VoltPur = **تطبيق `hopper-check=8`** (بدل 1 = فحص كل tick) في spigot.yml.
- opt-in عبر `modules.hardware.auto-tune: true` أو `/voltpur optimize`.
- يُقاس عبر `/voltpur benchmark` (قبل/بعد).
- **ميزة حقيقية مقاسة، لا ادعاء.**
