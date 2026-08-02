# VoltPur v26.2.0-RC1 - خطة الإطلاق للسوفت وير الحقيقي

## الهدف
الخروج من DEV (26.2-DEV@1970) إلى سوفت وير حقيقي مستقر v26.2.0-RC1

## تعريف RC1
Release Candidate 1 = نسخة مرشحة للإصدار النهائي، جاهزة للاختبار 48 ساعة

## Checklist RC1 (يجب أن يكون كله ✅)

### Code Freeze
- [ ] اختيار آخر Build ناجح ومستقر كـ Base: 7f4e352 (BENCHMARKS + STABILITY)
- [ ] تجميد الكود - لا إضافات جديدة إلا إصلاحات كراش
- [ ] تنظيف Debug Logs الكتير (خليها INFO فقط للـ VoltPur)
- [ ] تغيير VERSION من 26.2-VoltPur إلى 26.2.0-RC1
- [ ] إزالة تاريخ 1970-01-01T00:00:00Z واستبداله بتاريخ البناء الحقيقي

### Files & Worlds
- [ ] server.properties افتراضي مع allow-nether=true, allow-flight=true
- [ ] bukkit.yml, spigot.yml, paper.yml, purpur.yml, voltpur.yml يتولدوا أوتوماتيك
- [ ] world/, world_nether/, world_the_end/ يتحملوا
- [ ] plugin-pro/ + README.txt يتولد
- [ ] logs/voltpur-stability.log

### Commands
- [ ] /voltpur status -> STABLE
- [ ] /voltpur worlds -> 3 worlds
- [ ] /voltpur modules -> 21 ENABLED
- [ ] /voltpur version -> 26.2.0-RC1
- [ ] /vo up <build> -> يحدث عبر نت الاستضافة
- [ ] /padmin -> WebUI

### Performance (حقيقي)
- [ ] Item limiter 500/world
- [ ] Hopper Java check 60%
- [ ] Entity activation check
- [ ] TPS 19.9+ مع 0 لاعب
- [ ] RAM < 2GB أول تشغيل

### Pterodactyl
- [ ] Server marked as running في <20s
- [ ] لا ينهار بعد 60s
- [ ] file server.jar valid
- [ ] Java 25

### Bedrock
- [ ] Geyser + Floodgate يتحملوا
- [ ] Bedrock يدخل

### Documentation
- [ ] README.md محدث برابط مباشر لـ Release (مش Artifact ZIP)
- [ ] PTERODACTYL_FIX.md موجود
- [ ] BENCHMARKS.md بأرقام حقيقية
- [ ] STABILITY_CHECK.md ✅

### Release
- [ ] git tag v26.2.0-RC1
- [ ] GitHub Release مع 3 ملفات: VoltPur-26.2.0-RC1.jar, VoltPur.jar, server.jar + sha256
- [ ] Release Notes: ما تم إصلاحه، ما هو شغال، ما هو قيد العمل
- [ ] رابط مباشر: https://github.com/tkjij77-ctrl/VoltPur/releases/download/v26.2.0-RC1/VoltPur-26.2.0-RC1.jar

### Testing 48h
- [ ] شغل RC1 على Pterodactyl 24 ساعة
- [ ] ادخل Nether و End
- [ ] /spark profiler 300 ثانية
- [ ] /voltpur status كل ساعة
- [ ] لو مستقر 48 ساعة -> حوله لـ Final v26.2.0

## خطوات التنفيذ الآن

1. **اختيار Base:** 7f4e352 (آخر ناجح فيه BENCHMARKS)
2. **تنظيف الكود:**
   - غير VERSION في VoltPur.java من 26.2-VoltPur لـ 26.2.0-RC1
   - شيل Debug Logs الزيادة
   - تأكد أن PurpurVersionFetcher يطبع VoltPur مش Purpur DEV
3. **إنشاء Tag:**
   ```
   git tag -a v26.2.0-RC1 -m "VoltPur v26.2.0-RC1 - Full Software RC1"
   git push origin v26.2.0-RC1
   ```
4. **GitHub Actions يعمل Release أوتوماتيك** (بسبب permissions: contents: write)
5. **تحديث README** برابط مباشر للـ Release

## معيار النجاح
RC1 ناجح لو:
- Build ينجح
- Release يتعمل مع 3 JARs
- سيرفر يقوم في Pterodactyl ويطبع STABLE
- /voltpur status = ALL OK
- لاعب يدخل ويلعب 10 دقايق بدون كراش

بعد 48h -> Final v26.2.0
