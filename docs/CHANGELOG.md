# 📜 VoltPur / VoltCore — Changelog الكامل

> كل ما بُني وتم التحقق منه عبر هذه المراحل. الأرقام تُقاس عبر `/voltpur benchmark` — لا أرقام وهمية.

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
