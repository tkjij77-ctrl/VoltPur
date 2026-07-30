# بدء سريع - VoltPur 26.2 ⚡

## 3 خطوات فقط:

### 1️⃣ تثبيت Java 25
```bash
java --version  # يجب تظهر Java 25 أو أعلى
```
إذا لم تثبت: https://www.oracle.com/java/technologies/downloads/

### 2️⃣ استنساخ وتجهيز المشروع
```bash
git clone https://github.com/tkjij77-ctrl/VoltPur.git
cd VoltPur
git checkout ver/26.2
```

### 3️⃣ بناء المشروع
```bash
bash build-voltpur.sh
```

**انتظر 10-30 دقيقة...**

---

## ✅ النتيجة النهائية

الملف الذي تحتاجه:
```
purpur-server/build/libs/purpur-server-26.2-all.jar
```

---

## ▶️ تشغيل السيرفر

```bash
java -Xmx8G -Xms8G -jar purpur-server-26.2-all.jar nogui
```

---

## 🔗 الملفات المهمة

| الملف | الفائدة |
|------|---------|
| `README_AR_SETUP.md` | 📖 شرح تفصيلي |
| `REPOSITORY_STRUCTURE.ar.md` | 📁 شرح هيكل المستودع |
| `build-voltpur.sh` | 🔧 برنامج البناء |

---

**هذا كل شيء! 🎉**
