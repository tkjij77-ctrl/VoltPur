# 🧠 Phase 2 — خطة التحسين الحقيقي (Entity Activation + Hopper)

> أُعدّت هذه الخطة بعد **فكّ ترجمة المصدر الحقيقي** لـ `HopperBlockEntity` (1.21.10) من server jar مع تطبيق mappings Mojang، ومراجعة نظام Paper/Purpur الحالي. لا تخمين.

---

## 1) خلاصة البحث والنتائج

### أ. Entity Activation Range (EAR) — موجودة أصلًا!
- Paper **يحتوي أصلاً** على `ActivationRange` (تخطيط التفعيل)، و Purpur مفعّله بافتراضياته (يتأكد `ActivationRange.java.patch` الموجود في الفورك + خيارات مثل `squidImmuneToEAR`).
- **الخلاصة:** "Entity Activation" **أداء حقيقي مدمج أصلًا** في Paper/Purpur. لا حاجة لإضافة patch مكرر.
- **الخطأ في الأرشيف** (`0001-entity-activation.patch`): يستهدف `ServerChunkCache.java` وهو **مكان خاطئ** (سيتسبب بمشاكل/لا يعمل). → لن نضيفه، بل **نصحّح الحالة** في سجلّ الموديولات لتعكس أن EAR نشطة عبر Paper.

### ب. Hopper Optimization — الفجوة الحقيقية
- من المصدر الحقيقي (mojang-mapped):
  - `pushItemsTick` يقلّل `cooldownTime` وكل tick يستدعي `tryMoveItems` (الذي يعمل eject + suck).
  - هوبر **فارغ + بلا مصدر فوق + بلا item في منطقة الالتقاط + غير موقوف** ⇒ `tryMoveItems` **لن يفعل شيئاً** (no-op مضمون).
- **أفضل طريقة (اخترتها):** إضافة نوم آمن للهوبر الفارغ في `pushItemsTick`:
  - ننام فقط عندما يكون `isEmpty()` و `getSourceContainer()==null` و `getItemsAtAndAbove().isEmpty()` وغير موقوف.
  - بما أن هذه الحالة تجعل `tryMoveItems` بلا أثر، فالنوم **حافظ للسلوك** (لا يكسر farms).
  - **Opt-in** (مفعل يدوياً) بافتراض آمن، وبـ cooldown قصير قابل للضبط.
- **لاحظنا تحدياً:** لا يمكن توليد ملف patch NMS صحيح بخطوط متطابقة هنا لأن شجرة `net/minecraft` تُولَّد عند `applyAllPatches` (لا يمكن تشغيلها في هذه البيئة). لذلك:
  - الجزء القابل للتحقق (Java layer) نكتبه ونختبر ترجمته هنا.
  - مقتطف NMS الحقيقي نسلّمه جاهزاً + خطوات `applyPatches → عدّل → rebuildPatches` ليكوّن patch صحيح تلقائياً على جهازك.

---

## 2) الخطة القابلة للتنفيذ

### A. طبقة Java (قابلة للتحقق الآن — سأعدّلها وأترجمها)
1. **`VoltPurConfig.java`**: إضافة خيارات `hopper-sleep.enabled` (افتراضي false) + `hopper-sleep.cooldown` (افتراضي 3).
2. **`VoltPurModules.java`**: تصحيح الحالة بصدق:
   - `EntityActivation` → `PARTIAL` (نشط عبر Paper EAR، لا patch مكرر).
   - `HopperOptimization` → `PLANNED` (الجزء NMS بانتظار تطبيق `applyPatches` على جهازك) مع ملاحظة.
3. **`VoltPurPerformance.java`**: تحسين `countSleepableHoppers()` ليطابق معايير النوم الآمنة + دمجها في benchmark.
4. **`VoltPurBenchmark.java`**: إضافة سطر يبلّغ عن (عدد الهوبرات القابلة للنوم + حالة EAR).

### B. طبقة NMS (سلّم مقتطف جاهز — لا أرفع patch غير متحقق)
- مقتطف mojang-mapped لـ `HopperBlockEntity.pushItemsTick` (في `docs/HOPPER_SNIPPET.md`) + خطوات `applyPatches → عدّل → rebuildPatches`.

---

## 3) الأمان و«لا تكسر»
- كل النوم خلف شرط `isEmpty && noSource && noItems && !powered` → no-op مضمون.
- `hopper-sleep.enabled` افتراضي **false** (صفر خطر حتى يُفعَّل يدوياً).
- نبقى صادقين في السجلّ: لا ندّعي ACTIVE لشيء لم يُطبق فعلاً.

---

## 4) القياس (كيف نثبت أنه أداء حقيقي)
- `/voltpur benchmark` قبل/بعد → مقارنة MSPT مع X هوبر فارغ.
- عند تفعيل NMS: قارن عدد هوبرات "لا تفعل شيئاً" قبل/بعد النوم.
