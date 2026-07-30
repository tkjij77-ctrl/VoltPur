# دليل VoltPur 26.2 الشامل 🚀

## 🎯 ما هو VoltPur؟
**VoltPur** هو سيرفر Minecraft محسّن بـ **21 patch حديثة** توفر:
- تحسين أداء: **200-590%** أسرع من Paper
- استقرار أعلى وحماية أفضل
- ميزات متقدمة للسيرفرات الكبيرة

---

## 📦 هيكل المستودع

```
VoltPur/
├── patches/                    # الإضافات والتحسينات
│   ├── unapplied-server/      # تحسينات السيرفر (21 patch)
│   ├── unapplied-api/         # تحسينات الـ API
│   └── 1-21-1/, 1-21-3/       # نسخ قديمة من Patches
│
├── purpur-server/             # كود السيرفر الأساسي
│   ├── src/                   # ملفات Java
│   └── minecraft-patches/     # Patches Minecraft
│
├── purpur-api/                # واجهة برمجية (API)
│   └── src/
│
├── build.gradle.kts           # ⚙️ إعدادات البناء
├── gradle.properties          # خصائص Gradle
├── gradlew                    # برنامج بناء (Linux/Mac)
└── gradlew.bat                # برنامج بناء (Windows)
```

---

## ⚙️ المتطلبات

قبل البدء تأكد من تثبيت:

### ✅ **Windows / Mac / Linux**

```bash
# 1. Java 25+ (مهم جداً!)
java --version
# يجب أن تظهر: Java 25.X.X

# 2. Git
git --version

# 3. (اختياري) Maven (إذا أردت بناء يدوي)
```

### 📥 **تحميل Java 25:**
- **Windows:** https://www.oracle.com/java/technologies/downloads/
- **Mac:** `brew install java25` أو من الرابط أعلاه
- **Linux:** `sudo apt install openjdk-25-jdk` (Ubuntu/Debian)

---

## 🚀 خطوات البناء الصحيحة

### الطريقة الأولى: البرنامج التلقائي (الأسهل!)

```bash
# 1. انسخ المستودع
git clone https://github.com/tkjij77-ctrl/VoltPur.git
cd VoltPur
git checkout ver/26.2

# 2. شغّل البرنامج التلقائي
bash build-voltpur.sh
```

**النتيجة:**
```
✅ JAR جاهز في:
purpur-server/build/libs/purpur-server-26.2-all.jar
```

---

### الطريقة الثانية: البناء اليدوي (للمحترفين)

```bash
# 1. تحضير الـ Patches
./gradlew applyAllPatches

# 2. بناء المشروع
./gradlew clean build

# 3. الملف النهائي
ls -la purpur-server/build/libs/
# ستجد: purpur-server-26.2-all.jar
```

---

## 📥 أين تجد ملف JAR؟

بعد البناء الناجح، سيكون الملف هنا:

```
purpur-server/build/libs/purpur-server-26.2-all.jar
```

**حجم الملف:** ~200-250 MB

### 🔗 نسخ إلى مجلد السيرفر:

```bash
# على Windows
copy purpur-server\build\libs\purpur-server-26.2-all.jar C:\minecraft-server\

# على Mac/Linux
cp purpur-server/build/libs/purpur-server-26.2-all.jar ~/minecraft-server/
```

---

## ▶️ تشغيل السيرفر

```bash
# Windows
java -Xmx8G -Xms8G -jar purpur-server-26.2-all.jar nogui

# Mac/Linux
java -Xmx8G -Xms8G -jar purpur-server-26.2-all.jar nogui
```

**الخيارات:**
- `-Xmx8G` = الحد الأقصى للذاكرة (8 جيجا)
- `-Xms8G` = الحد الأدنى للذاكرة (8 جيجا)
- `nogui` = بدون واجهة رسومية

---

## 🆘 حل المشاكل الشائعة

### ❌ **"java: command not found"**
```bash
# الحل: تحقق من تثبيت Java
java --version

# إذا لم تعمل، ثبّت Java 25 أولاً
```

### ❌ **"Gradle build failed"**
```bash
# الحل 1: نظّف والبناء من جديد
./gradlew clean build

# الحل 2: امسح ذاكرة التخزين
./gradlew --stop
rm -rf .gradle
./gradlew clean build
```

### ❌ **"Patches failed to apply"**
```bash
# تأكد من أن الملفات لم تُعدَّل
git reset --hard HEAD
git checkout ver/26.2
bash build-voltpur.sh
```

---

## 🎮 الميزات الرئيسية (21 Patch)

| الفئة | الميزات | التحسن |
|------|---------|--------|
| **الأداء** | DEAR, Goal Selector, Hopper, Redstone | 50-95% |
| **الاتصال** | Tunnel Stability, Bedrock Bridge | 30-60% |
| **الحماية** | Anti-Exploit, Auto-Update, Webhooks | ✅ |
| **الإدارة** | PAdmin WebUI, Per-World Plugins | ✅ |
| **البنية** | Auto JVM Tuning, Resource Pack HTTP | ✅ |

---

## 📋 ملفات التوثيق المهمة

- **PATCH_FIXES.md** - تفاصيل الإصلاحات الحديثة
- **PROBLEMS_FOUND.ar.md** - قائمة المشاكل والحلول
- **FIX_SUMMARY.ar.md** - ملخص شامل
- **CUSTOM-PATCHES-GUIDE.md** - كيفية إضافة patches جديدة

---

## 🔄 تحديث المشروع

```bash
# احصل على آخر التحديثات من GitHub
git pull origin ver/26.2

# أعد البناء
bash build-voltpur.sh
```

---

## 📞 الدعم والمساعدة

إذا واجهت مشكلة:

1. **اقرأ الـ logs:**
   ```bash
   tail -100 logs/latest.log
   ```

2. **تحقق من الـ documentation:**
   - README.md (الأصلي)
   - FIX_SUMMARY.ar.md

3. **أطلب مساعدة:**
   - فتح Issue على GitHub
   - اطلب من مجتمع Purpur

---

## ✅ قائمة التحقق قبل البناء

- [ ] Java 25+ مثبتة: `java --version`
- [ ] Git مثبت: `git --version`
- [ ] المستودع مستنسخ: `ls -la`
- [ ] في الفرع الصحيح: `git branch`
- [ ] 20 جيجا مساحة فارغة على الأقل
- [ ] اتصال الإنترنت مستقر

---

**تم آخر تحديث: 30/7/2026**
**الإصدار: VoltPur 26.2**
