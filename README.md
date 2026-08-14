# ⚡ VoltPur — Minecraft Server Software

[![Build](https://github.com/tkjij77-ctrl/VoltPur/actions/workflows/build.yml/badge.svg?branch=ver/26.2)](https://github.com/tkjij77-ctrl/VoltPur/actions)
![MC](https://img.shields.io/badge/Minecraft-1.21.10-brightgreen?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge)
![Version](https://img.shields.io/badge/VoltPur-26.2.0-blue?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

---

## 📖 ما هو VoltPur؟ (الفكرة الأساسية)

**VoltPur هو برنامج خادم ماينكرافت** (server software) مبني على **Purpur 26.2** — الذي هو بدوره فورك من **Paper**، أشهر برامج الخوادم أداءً.

**القيمة التي يضيفها VoltPur فوق Purpur:**

| المجال | ماذا يفعل VoltPur |
|--------|-------------------|
| **قياس وتشخيص** | أوامر مدمجة تقيس أداء الخادم حيًّا (TPS/الذواكر/العوالم) |
| **كشف العتاد** | يكتشف CPU/RAM/العمارة ويقترح أعلام JVM المثلى لجهازك |
| **استقرار** | يصلح مشاكل تشغيلية على لوحات الاستضافة (مثل أخطاء threads) |
| **شفافية** | يعرض حالة كل ميزة بصدق (يعمل / جزئي / مخطط له) |
| **تحسين آمن** | يطبّق إعدادات أداء مثبتة (مثل تحسين الهوبر) قابلة للتفعيل |

> **بصراحة:** VoltPur **ليس** إعادة اختراع. هو يعتمد على أداء Paper/Purpur المدمج (وهو ممتاز أصلًا)، ويضيف فوقه **أدوات تحكم وقياس وتشخيص + تحسينات آمنة قابلة للتفعيل**. هذه هي قيمته الحقيقية.

---

## ✅ حالة الميزات (صادقة)

### 🟢 نشطة ومُثبتة (تعمل وتُقاس)
| الميزة | ماذا تفعل | تحقق منها |
|--------|-----------|-----------|
| **HardwareDetection** | يكتشف العتاد + أعلام JVM الموصى بها | `/voltpur hardware` |
| **Benchmark** | يقيس TPS/MSPT/الكيانات/الكانكس/الذاكرة | `/voltpur benchmark` |
| **ItemLimiter** | يحدّ العناصر المرمية الزائدة | `/voltpur benchmark` |
| **TPSMonitor** | يحذّر عند انخفاض TPS | اللوج |
| **WorldStability** | يفحص/يجهّز مجلدات العوالم الثلاثة | `/voltpur status` |
| **Updater** | تحديث ذاتي `server.jar` | `/vo up` |
| **PAdminWebUI** | واجهة تحكم ويب | `/padmin` |
| **HopperOpt** | (opt-in) يطبق `hopper-check=8` في spigot.yml | `/voltpur optimize` |

### 🟠 جزئية / 🟡 مخطط لها (لا ندّعي غير الحقيقة)
- **EntityActivation** → 🟠 نشطة عبر Paper EAR المدمج (لا patch مكرر).
- **HopperOptimization** → 🟡 تحسين NMS أعمق مخطط له (المقتطف في `docs/HOPPER_SNIPPET.md`).
- Collision / Memory / Network / Redstone / Chunk / Light → 🟡 مخطط لها، لم تُطبَّق بعد.

---

## 🚀 التشغيل السريع

### 1) التحميل
حمّل `server.jar` من [Releases](https://github.com/tkjij77-ctrl/VoltPur/releases/latest) وضعه في مجلد الخادم.

### 2) الموافقة على EULA
افتح `eula.txt` واجعل `eula=true`.

### 3) التشغيل
```bash
java -Xms128M -Xmx{{RAM}}M --add-modules=jdk.incubator.vector \
  -Dterminal.jline=false -Dterminal.ansi=true -jar server.jar --nogui
```
> أضف `--add-modules=jdk.incubator.vector` لتفعيل SIMD على المعالجات الداعمة (يزيل تحذير الكنسول).

---

## ⌨️ الأوامر

```
/voltpur help         مرجع كامل لكل الأوامر
/voltpur version      معلومات الإصدار
/voltpur modules      حالة الميزات الصادقة
/voltpur status       فحص شامل (عوالم/ملفات/TPS)
/voltpur worlds       قائمة العوالم
/voltpur hardware     توافق العتاد + أعلام JVM
/voltpur flags        أمر بدء JVM الموصى به
/voltpur benchmark    قياس حي للأداء
/voltpur optimize     تطبيق التحسينات (OP)
/voltpur reload       إعادة تحميل الإعدادات (OP)
/vo up [buildId]      تحديث server.jar
/padmin               واجهة ويب
```

---

## 📦 مخرجات البناء (Actions)
- يبني **Paperclip JAR** وينشئ **Release عام** تلقائيًا عند كل push إلى `ver/26.2`.
- المخرجات: `server.jar`، `VoltPur.jar`، `VoltPur-26.2.jar`، `.sha256`.
- `server.jar` جاهز للرفع على Pterodactyl/أي استضافة (ليس ZIP).

---

## 📁 مجلدات العوالم
عند أول تشغيل، VoltPur **يجهّز مجلدات** `world/`, `world_nether/`, `world_the_end/` في جذر الخادم (بيانات العوالم يولّدها ماينكرافت نفسه عند التحميل). تظهر في الروت جنب بقية الملفات كما في أي سوفت وير.

---

## 📊 القياس
**لا ننشر أرقامًا بلا قياس.** لقياس أداء الخادم:
```
/voltpur benchmark
```
يكتب النتيجة في `logs/voltpur-benchmark.txt`. قارن قبل/بعد أي تحسين.

---

## 🔗 روابط
- **Releases:** https://github.com/tkjij77-ctrl/VoltPur/releases
- **Actions:** https://github.com/tkjij77-ctrl/VoltPur/actions
- **Changelog:** [docs/CHANGELOG.md](docs/CHANGELOG.md)
- **المنهجية:** [docs/التفكر الصحيح.md](docs/التفكر الصحيح.md)
- **المصدر الأصلي:** [Purpur](https://github.com/PurpurMC/Purpur) · [Paper](https://github.com/PaperMC/Paper)

---

## 📜 الترخيص
MIT License (نفس Purpur/Paper).
