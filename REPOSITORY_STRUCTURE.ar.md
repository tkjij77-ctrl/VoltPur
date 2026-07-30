# شرح هيكل مستودع VoltPur 📁

## 📍 الملفات والمجلدات الرئيسية

### 1️⃣ **ملفات البناء (Build Files)**

```
📄 build.gradle.kts           ← إعدادات البناء الرئيسية ⭐
📄 gradle.properties          ← خصائص البناء (Java version, etc.)
📄 settings.gradle.kts        ← إعدادات Multi-Module
🐚 gradlew                    ← برنامج بناء تلقائي (Linux/Mac)
🐚 gradlew.bat                ← برنامج بناء تلقائي (Windows)
```

**الفائدة:** هذه الملفات تخبر `Gradle` بكيفية بناء المشروع.

---

### 2️⃣ **مجلد Patches (الإضافات والتحسينات)**

```
patches/
├── unapplied-server/        ← ✨ تحسينات السيرفر (21 Patch)
│   ├── 0001-Pufferfish-Server-Changes.patch
│   ├── 0002-Fix-pufferfish-issues.patch
│   ├── ... 19 patches أخرى
│   └── 0021-Advanced-Features.patch
│
├── unapplied-api/           ← تحسينات الـ API
│   └── ...
│
├── 1-21-3/                  ← النسخة القديمة (1.21.3)
├── 1-21-1/                  ← النسخة القديمة (1.21.1)
└── 1-20-6/                  ← النسخة القديمة (1.20.6)
```

**معنى الكلمات:**
- **unapplied** = لم تُطبق بعد (تحتاج `./gradlew applyAllPatches`)
- **Patch** = ملف يحتوي على تعديلات على ملفات Java

---

### 3️⃣ **مجلد purpur-server (كود السيرفر)**

```
purpur-server/
├── src/
│   └── main/
│       ├── java/            ← ملفات Java الأساسية
│       └── resources/       ← ملفات config و yaml
│
├── minecraft-patches/       ← Patches خاصة بـ Minecraft
├── paper-patches/          ← Patches من Paper
└── patches/                ← Patches محلية
```

**المهم:** هنا يُطبّق كل التحسينات والمميزات على كود السيرفر.

---

### 4️⃣ **مجلد purpur-api (واجهة البرمجة)**

```
purpur-api/
├── src/
│   └── main/
│       ├── java/            ← واجهات وأدوات البرمجة
│       └── resources/
│
└── paper-patches/          ← Patches من Paper
```

**الفائدة:** يستخدمها المطورون عند كتابة Plugins.

---

### 5️⃣ **مجلد build-data**

```
build-data/
├── dev-imports.txt         ← قائمة الاستيرادات
└── purpur.at               ← إعدادات Paperweight
```

**الفائدة:** تحتوي على إعدادات بناء متقدمة.

---

### 6️⃣ **مجلد gradle (Gradle Wrapper)**

```
gradle/
└── wrapper/
    ├── gradle-wrapper.jar
    └── gradle-wrapper.properties
```

**الفائدة:** يسمح بتشغيل Gradle بدون تثبيته على النظام.

---

### 7️⃣ **ملفات البرامج (Scripts)**

```
🐚 build-voltpur.sh         ← برنامج بناء تلقائي (محسّن!)
🐚 build-fork.sh            ← برنامج بناء أساسي
🐚 scripts/apatch.sh        ← برنامج تطبيق الـ Patches
```

---

### 8️⃣ **مجلد test-plugin**

```
test-plugin/
├── build.gradle.kts        ← إعدادات plugin الاختبار
└── src/
    └── main/
        └── java/           ← كود plugin اختبار
```

**الفائدة:** لاختبار الـ API الجديدة.

---

### 9️⃣ **ملفات التوثيق (Documentation)**

```
📄 README.md                ← التوثيق الأصلي (الإنجليزية)
📄 README_AR_SETUP.md       ← دليل البناء (العربية) ← اقرأ هذا! ⭐
📄 PATCH_FIXES.md           ← الإصلاحات الحديثة (الإنجليزية)
📄 FIX_SUMMARY.ar.md        ← ملخص الإصلاحات (العربية)
📄 PROBLEMS_FOUND.ar.md     ← المشاكل والحلول (العربية)
📄 CUSTOM-PATCHES-GUIDE.md  ← كيفية إضافة patches (الإنجليزية)
📄 CONTRIBUTING.md          ← كيفية المساهمة (الإنجليزية)
📄 LICENSE                  ← الترخيص
```

---

## 🔄 دورة حياة البناء

```
1️⃣ المستودع الخام
         ⬇️
2️⃣ تطبيق الـ Patches (applyAllPatches)
         ⬇️
3️⃣ تجميع كود Java (compilation)
         ⬇️
4️⃣ بناء JAR النهائي
         ⬇️
5️⃣ ملف JAR جاهز للتشغيل
   purpur-server/build/libs/purpur-server-26.2-all.jar
```

---

## 📊 الملفات المهمة جداً ⭐⭐⭐

| الملف | الأهمية | الوصف |
|------|---------|-------|
| `build.gradle.kts` | 🔴 حرجة | إعدادات البناء الرئيسية |
| `gradle.properties` | 🔴 حرجة | إصدار Java والإعدادات |
| `patches/unapplied-server/` | 🔴 حرجة | التحسينات (21 patch) |
| `purpur-server/src/` | 🔴 حرجة | كود السيرفر |
| `build-voltpur.sh` | 🟠 مهمة | برنامج البناء المحسّن |
| `README_AR_SETUP.md` | 🟠 مهمة | دليل البناء (اقرأه أولاً!) |

---

## ❌ الملفات والمجلدات التي تُتجاهل

```
.gradle/            ← ذاكرة التخزين (احذفها إذا حصلت مشاكل)
build/              ← نتائج البناء (تُعاد بناؤها تلقائياً)
*.jar               ← الملفات الـ JAR المؤقتة
.git/               ← بيانات Git
```

---

## 🚀 الأوامر المهمة

```bash
# عرض هيكل المستودع
tree -L 2 -I '.gradle|build'

# عرض الملفات الكبيرة (إذا أخذت مساحة كبيرة)
du -sh *

# حساب عدد الـ Patches
ls patches/unapplied-server/*.patch | wc -l

# حذف البناء القديم (للبدء من جديد)
rm -rf build .gradle
./gradlew clean build
```

---

## 📝 خريطة ذهنية سريعة

```
VoltPur Repository
│
├── 🛠️ Build System
│   ├── build.gradle.kts
│   ├── gradle.properties
│   └── gradlew/gradlew.bat
│
├── ✨ Patches (التحسينات)
│   ├── unapplied-server/ (21 patch حديثة)
│   ├── unapplied-api/
│   └── versions (1-20-6, 1-21-1, 1-21-3)
│
├── 💻 Source Code
│   ├── purpur-server/ (السيرفر الرئيسي)
│   └── purpur-api/ (الواجهة البرمجية)
│
├── 📚 Documentation (اقرأ هذه أولاً!)
│   ├── README_AR_SETUP.md ⭐
│   ├── README.md
│   └── FIX_SUMMARY.ar.md
│
└── 🔧 Tools & Utilities
    ├── build-voltpur.sh
    └── scripts/
```

---

## ✅ قبل أن تبدأ البناء

- [ ] فهمت هيكل المستودع ✓
- [ ] قرأت `README_AR_SETUP.md`
- [ ] تثبيت Java 25+
- [ ] لديك 20 جيجا مساحة فارغة على الأقل
- [ ] أنت في الفرع الصحيح: `git checkout ver/26.2`

**الآن جاهز للبناء! 🚀**

```bash
bash build-voltpur.sh
```

---

**تم آخر تحديث: 30/7/2026**
