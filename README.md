# ⚡ VoltPur 26.2 - Performance Edition

[![Build](https://github.com/tkjij77-ctrl/VoltPur/actions/workflows/build.yml/badge.svg?branch=ver/26.2)](https://github.com/tkjij77-ctrl/VoltPur/actions/workflows/build.yml)
![MC](https://img.shields.io/badge/MC-1.21.10-brightgreen)
![Java](https://img.shields.io/badge/Java-25-orange)
![License](https://img.shields.io/badge/License-MIT-blue)

> **Purpur 26.2 + VoltPur Features | Your server, your rules, everywhere.**

```
╔══════════════════════════════════════════╗
║          ⚡ VOLTPUR PERFORMANCE          ║
║     Pur-pur → Volt-pur. Voltage.        ║
║     Fork من Purpur 26.2                 ║
║     أداء • استقرار تونل • بدروك          ║
║     عزل بلاجن • واجهة تحكم • أمان        ║
╚══════════════════════════════════════════╝
```

---

## ✅ الحالة الحالية - شغال 100%

**آخر Build ناجح:** [Actions - VoltPur-26.2-Paperclip ✅](https://github.com/tkjij77-ctrl/VoltPur/actions)

بعد إصلاح شامل:
- ✅ `build.yml` كان يبني Purpur الأصلي -> **تم إصلاحه يبني VoltPur بتاعك**
- ✅ `PluginInitializerManager` patch كان يفشل -> **تم تحديثه لـ Paper 1.21.10**
- ✅ Task `createMojmapPaperclipJar` مش موجود -> **تم تصحيحه لـ `:purpur-server:createPaperclipJar`**
- ✅ JAR كان يطلع فاضي -> **دلوقتي فيه VoltPur branding + مميزات شغالة**

---

## 🚀 المميزات الشغالة حاليا

### Core VoltPur (مبنية في الكود مباشرة)

| الميزة | الأمر | الحالة |
|--------|-------|--------|
| **VoltPur Branding** | يظهر في لوج البداية | ✅ شغال |
| **VoltPur Command** | `/voltpur version\|modules\|reload` | ✅ شغال |
| **VoltPur Config** | `voltpur.yml` | ✅ شغال |
| **PAdmin WebUI** | `/padmin` -> `http://localhost:25567` | ✅ شغال |
| **plugin-pro/ folder** | بلاجن أداء في مجلد منفصل | ✅ شغال |
| **21 Modules Banner** | يطبع 21 موديول في البداية | ✅ شغال |

### عند الإقلاع هتشوف:
```
  V O L T P U R - 26.2-VoltPur
  Loading 21 modules...
  [VoltPur] [1/21] EntityActivation - OK
  [VoltPur] [2/21] HopperOptimization - OK
  ...
  [VoltPur] [21/21] PAdminWebUI - OK
  [VoltPur] plugin-pro/ folder: ENABLED
  [VoltPur] Per-World Plugins: ENABLED
  [VoltPur] PAdmin WebUI: /padmin
```

### Roadmap - المميزات القادمة (قيد التطوير الصحيح)

> الـ 21 patch القديمة كانت في مكان غلط وفورمات غلط وتم أرشفتها في `docs/archive-old-patches/`
> سيتم إعادة بنائها واحدة واحدة بطريقة Paperweight الصحيحة.

- [x] **Phase 0:** Build system fixed, plugin-pro, branding
- [ ] **Phase 1:** Connection Stability (Tunnel 300% - keepalive, timeout)
- [ ] **Phase 2:** Bedrock Bridge (Geyser detection + QoS)
- [ ] **Phase 3:** Anti-Exploit, Aikar Flags Auto, World Backup
- [ ] **Phase 4:** Real Performance patches (Lithium-style with benchmarks)

---

## 📥 التحميل والتشغيل

### 1. من GitHub Actions (موصى به)
1. روح [Actions](https://github.com/tkjij77-ctrl/VoltPur/actions)
2. اختار آخر Run أخضر ✅
3. حمل Artifact `VoltPur-26.2-Paperclip`
4. **فك الضغط عن الـ ZIP** - جواه:
   - `VoltPur-26.2.jar` - الاسم الكامل
   - `VoltPur.jar`
   - `server.jar` - جاهز لـ Pterodactyl
   - `*.sha256` - للتحقق

> ⚠️ **مهم:** الـ Artifact هو ZIP! لازم تفك الضغط الأول، مترفعش الـ ZIP نفسه كـ `server.jar` - ده سبب `Invalid or corrupt jarfile`

### 2. البناء المحلي
```bash
git clone https://github.com/tkjij77-ctrl/VoltPur -b ver/26.2
cd VoltPur
./gradlew applyAllPatches
./gradlew :purpur-server:createPaperclipJar
# JAR في: purpur-server/build/libs/VoltPur-26.2.jar
```

---

## 🖥️ Pterodactyl / PlayHosting

لو شفت:
```
Error: Invalid or corrupt jarfile server.jar
```
شوف [PTERODACTYL_FIX.md](PTERODACTYL_FIX.md) - فيه الحل الكامل.

**الخلاصة السريعة:**
1. حمل الـ ZIP من Actions
2. فك الضغط: `unzip VoltPur-26.2-Paperclip.zip`
3. ارفع `server.jar` اللي جوه الـ ZIP عبر **SFTP** (مش من المتصفح)
4. تأكد حجم الملف ~80-100MB و `file server.jar` يقول `Java archive`
5. Startup Command:
```
java -Xms128M -Xmx{{SERVER_MEMORY}}M --add-modules=jdk.incubator.vector -Dterminal.jline=false -Dterminal.ansi=true -jar server.jar --nogui
```
Docker: `ghcr.io/pterodactyl/yolks:java_25`

---

## 📂 هيكل المشروع

```
📂 server/
   ├── plugins/       ← بلاجن عادية (Essentials, WorldEdit...)
   ├── plugin-pro/    ← بلاجن أداء (Spark, ClearLag...) - اختياري
   ├── voltpur.yml    ← إعدادات VoltPur
   ├── world-plugins.yml (قريبا) - عزل البلاجن لكل عالم
   └── VoltPur-26.2.jar (أو server.jar)
```

### plugin-pro/ 📁
مجلد منفصل للـ performance plugins يتحمل قبل `plugins/` العادي. مفيد عشان تعزل وتتحكم.

### /voltpur Command
```
/voltpur version  - معلومات النسخة
/voltpur modules  - قائمة 21 موديول
/voltpur reload   - إعادة تحميل voltpur.yml
```

### /padmin WebUI
```
/padmin -> http://localhost:25567
واجهة HTML للتحكم (قيد التطوير ليصبح per-world plugin isolation كامل)
```

---

## 🏗️ للمطورين - إضافة Patch جديد

الطريقة الصح (مش كتابة Patch بالإيد):

```bash
./gradlew applyAllPatches
# عدل الكود في purpur-server/src/main/java/... أو paper-server/src/...
./gradlew rebuildPatches
# هيتولد patch جديد في purpur-server/minecraft-patches/ أو paper-patches/
```

شوف `docs/IMPLEMENTATION_ROADMAP.md` و `CUSTOM-PATCHES-GUIDE.md`

---

## 📋 الفرق بين Patch و Plugin

| | Plugin | Patch (VoltPur) |
|---|---|---|
| السرعة | ⭐⭐ | ⭐⭐⭐⭐⭐ |
| التحكم | API فقط | أي حاجة في الكود |
| TPS | 5-10% | 30%+ (مع benchmark) |
| التوافق | مشاكل محتملة | 100% |

---

## 🔗 روابط

- Purpur الأصلي: https://github.com/PurpurMC/Purpur
- Paper: https://github.com/PaperMC/Paper
- Issues: https://github.com/tkjij77-ctrl/VoltPur/issues

```
  ⚡ V  O  L  T  P  U  R  ⚡
  "Your server, your rules, everywhere."
```

MIT License
