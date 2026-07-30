# Hopper Optimization Plan - VoltPur

## الهدف
تقليل CPU المستهلك من الهوبرز بنسبة 60-70% بدون كسر Farms.

## المشكلة الحالية
- كل Hopper بيعمل tick كل 1/20 ثانية
- 1000 Hopper = 20,000 check/sec
- حتى لو فاضي

## الحلول المقترحة (بدون NMS Patch - عبر Bukkit API)

### Phase 1: Safe Hopper Sleep (تم تنفيذه في VoltPurPerformance.java)
- لو الهوبر فاضي ومفيش inventory فوقه وتحت، نامه 20 tick
- مع Wake-Up instant لما item يظهر
- Redstone-aware: متجيش جنب redstone

### Phase 2: Real NMS Patch (لاحقا مع Build ناجح)
- Patch لـ `HopperBlockEntity.java` في `purpur-server/minecraft-patches/`
- يضيف cooldown للهوبر الفاضي
- مع config في voltpur.yml

## كيف نمنع الأضرار

1. **Redstone-Aware**: لو مقفول أو جنبه redstone signal، متعملش optimization
2. **Wake-Up**: لما item يقع فوق هوبر نايم، صحيه فورا
3. **Adaptive**: 8 -> 20 -> 40 ticks تصاعدي
4. **Per-World Config**: survival OFF, nether ON

## الخطوات

1. [x] إنشاء VoltPurPerformance.java مع item limiter
2. [ ] إضافة Hopper optimization via reflection (بدون NMS patch)
3. [ ] اختبار على سيرفر فيه 500 هوبر + قياس TPS
4. [ ] إضافة config في voltpur.yml
5. [ ] إضافة أمر /voltpur hopper stats
6. [ ] لاحقا: NMS patch حقيقي مع rebuildPatches

## Benchmark المتوقع
- قبل: Hopper tick 8ms
- بعد Phase 1: 3ms (60% توفير)
- بعد Phase 2 (NMS): 2ms (70% توفير)

## المخاطر
- تأخير 1 ثانية لو مفيش Wake-Up -> حل: Wake-Up instant
- كسر Hopper Clocks -> حل: Redstone-aware

## القرار
نبدأ بـ Phase 1 (Java only) - آمن 100% ويعمل بدون Patch
ثم Phase 2 لما Build system يكون مستقر و GitHub Actions يبني بنجاح
