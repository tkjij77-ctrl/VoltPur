# ⚠️ أرشيف غير حقيقي — لا تستخدم هذا المجلد

الملفات في هذا المجلد **ليست patches قابلة للتطبيق**، ولم تُطبَّق على السيرفر أبدًا.

## لماذا هي هنا؟
كانت مسوّدات مبكرة (أغسطس 2026) مكتوبة بشكل patch يدوي، وبقيت في المستودع بعد أن تغيّرت الخطة إلى الاعتماد على Paper/Purpur المدمج + أدوات إدارية.

## ما المشكلة فيها؟
1. **هاشات مزيّفة:** `index abc123..def456` / `xyz789..abc012` — ليست هاشات git حقيقية، فـ `git apply` سيفشل.
2. **منطق يكسر اللعبة** (مثال حقيقي من `0003-lithium-collision-optimization.patch`):
   ```java
   if (this.distanceToSqr(Vec3.atCenterOf(pos)) > 64.0) { return false; }   // في isColliding
   ```
   هذا يجعل كل تصادم بعيد عن مركز الكتلة يُرفض ⇒ الكيانات تمر عبر الحواجز.
3. **ادعاءات أداء بلا قياس:** أرقام مثل «30-50% faster collision» كانت مكتوبة يدويًا.

## الحالة الحقيقية لتلك الميزات
مسجّلة في `VoltPurModules` بحالة **PLANNED** (غير منفَّذة). لا توجد أي patch NMS في هذا الفورك.
الأسطر الحقيقية الوحيدة التي يضيفها VoltPur كتعديل على المحرك:

| الملف | الحجم | ماذا يفعل |
|---|---|---|
| `purpur-server/paper-patches/files/.../PluginInitializerManager.java.patch` | 9 أسطر | يسجّل `plugin-pro/` كمصدر بلجنات إضافي |
| `purpur-server/minecraft-patches/sources/net/minecraft/commands/Commands.java.patch` | سطر واحد | يحمي من `NPE` عند تشغيل أمر قبل تحميل العالم |

## القاعدة (انظر `POLICY.md`)
لا تُرفع patch بلا: تطبيق فعلي ناجح (`applyAllPatches`) + قياس (`/voltpur benchmark`) + دليل في المستودع.
