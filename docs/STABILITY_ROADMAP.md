# خطة الاستقرار الكامل - من تطوير لسوفوير كامل

## المشكلة الحالية (من تقرير المستخدم)
- عالم النظر (Nether) وعالم (The End) مش بيحملوا
- ملفات ناقصة
- عايز يخرج من مرحلة التطوير لمرحلة سوفتوير كامل

## تعريف سوفتوير كامل
سوفتوير كامل يعني:
1. **كل العوالم شغالة:** overworld, nether, end - بتتولد وتتحمل وملفاتها موجودة
2. **كل الملفات موجودة:** 
   - server.properties, bukkit.yml, spigot.yml, paper.yml, purpur.yml, voltpur.yml
   - world/, world_nether/, world_the_end/
   - plugins/, plugin-pro/, logs/
3. **كل الأوامر شغالة:** /voltpur, /padmin, /plugins, /tps, /version
4. **مفيش كراش:** No NPE, No UnknownHostException fatal
5. **Pterodactyl متوافق:** Server marked as running بسرعة قبل 60 ثانية
6. **Bedrock شغال:** Geyser + Floodgate يشتغلوا بدون مشاكل
7. **الأداء مستقر:** TPS 20, MSPT < 30, RAM < 2GB أول تشغيل

## Checklist الاستقرار

### Phase A: Worlds (اللي ناقص حاليا)
- [ ] التأكد أن server.properties فيه `allow-nether=true` و `allow-flight=true`
- [ ] التأكد أن PurpurWorldConfig مش بيمنع Nether/End
- [ ] إضافة VoltPurWorldCheck: عند البداية يتأكد أن 3 عوالم اتحملوا، لو لا ينشئهم
- [ ] إضافة لوج: `[VoltPur] Worlds loaded: overworld (OK), nether (OK), end (OK)`

### Phase B: Files
- [ ] `voltpur.yml` يتولد أوتوماتيك (موجود)
- [ ] `plugin-pro/README.txt` يتولد (موجود)
- [ ] `worlds/` etc يتولدوا
- [ ] إضافة `STABILITY_CHECK.md` في كل مجلد يوضح أن الملفات كاملة

### Phase C: Pterodactyl
- [ ] السيرفر يعمل `Server marked as running` في أول 20 ثانية (حصل فعلا)
- [ ] إضافة `server.properties` افتراضي لو مش موجود

### Phase D: Commands & Features
- [ ] `/voltpur status` - يعرض حالة كل العوالم والملفات
- [ ] `/voltpur worlds` - يعرض العوالم المحملة
- [ ] `/padmin` - WebUI

### Phase E: Performance Real
- [x] Item limiter
- [x] Hopper check (Java)
- [ ] Entity activation (Lithium)
- [ ] Chunk optimization

## التنفيذ الآن

### 1. إصلاح تحميل العوالم
المشكلة قد تكون من:
- PurpurWorldConfig: قد يكون فيه `disable` للـ Nether
- أو من `bukkit.yml -> world-settings` 
- أو أن السيرفر بيتقتل بعد 60 ثانية قبل ما يكمل تحميل العوالم (Pterodactyl health check)

الحل:
- في VoltPur.init(): إضافة check يتأكد أن `Bukkit.getWorlds().size() >= 1` ولو Nether/End مش موجودين يحاول ينشئهم
- إضافة `allow-nether=true` في server.properties لو مش موجود

### 2. ربط كل حاجة
المشكلة: "انت ممكن تكون نفس كل حاجة ولكن انت ممكن ما اكونش رابطها"
يعني الكود موجود بس مش مربوط ببعض (مثلا VoltPurPerformance موجود بس مش بيتنادى)

الحل:
- من VoltPur.init() نادي كل الموديولات:
  - VoltPurConfig.init()
  - VoltPurPerformance.init()
  - VoltPurWorldCheck.init()
  - إلخ

### 3. ملف STABILITY_REPORT
كل ما السيرفر يقوم، يعمل ملف `logs/voltpur-stability.log` فيه:
- وقت الإقلاع
- العوالم المحملة
- الملفات الموجودة
- TPS
- Modules

لو أي حاجة ناقصة، يكتب WARNING ويحاول يصلحها أوتوماتيك.

## مقياس الخروج من التطوير
نخرج من التطوير لما:
- [ ] 5 Builds ناجحة ورا بعض بدون فشل
- [ ] 3 عوالم بيحملوا في كل مرة
- [ ] /voltpur status يقول ALL OK
- [ ] Pterodactyl ميقتلش السيرفر بعد 60 ثانية
- [ ] لاعب يقدر يدخل Nether و End بدون مشاكل
- [ ] Bedrock يدخل عبر Geyser

حالياً: عندنا 2 Builds ناجحة ورا بعض (19edc51 و 7ead1c7) - محتاجين 3 كمان
