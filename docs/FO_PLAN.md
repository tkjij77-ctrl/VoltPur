# Fabulously Optimized -> VoltPur Implementation Plan

## تحليل FO
FO = 48 مود، 80% Client (Sodium, Iris...), 20% Server (Lithium, FerriteCore, ModernFix)

### Server-relevant من FO:
- Lithium: Entity, Hopper, Collision, Chunk
- FerriteCore: Memory (BlockState compression)
- ModernFix: Boot time, Registry
- (Krypton, C2ME مش في FO بس مهمة)

## الفلسفة
FO: Vanilla بدون تغيير Gameplay، بس أسرع
VoltPur: نفس الفلسفة + Configurable + Pterodactyl safe

## Roadmap 5 مراحل

### Phase 0: Done ✅
- Build fixed
- Branding
- plugin-pro/
- /vo up
- /voltpur modules

### Phase 1: Lithium - Hopper + Entity Activation (الآن)
**Hopper Optimization:**
- ملف: `purpur-server/minecraft-patches/features/0022-VoltPur-Hopper-Optimization.patch`
- كود: emptyCooldown، ينام 20 tick لو فاضي، يصحى لو item فوقه، redstone-aware
- Config: `voltpur.yml -> hopper-optimization.enabled, cooldown, redstone-aware`
- خطر: منخفض (مع wake-up)
- Benchmark: قبل 8ms/tick -> بعد 2ms (70%)

**Entity Activation:**
- Lithium بيخلي الكائنات البعيدة tick كل 5-20 tick بدل كل tick
- ملف: `0023-VoltPur-Entity-Activation.patch`
- Config: distance 32, 128

### Phase 2: FerriteCore - Memory
- ضغط BlockState palette
- ملف: `0024-VoltPur-FerriteCore-Memory.patch`
- من 3GB RAM -> 1.5GB
- خطر: متوسط، يحتاج اختبار مع WorldEdit

### Phase 3: Krypton + C2ME - Network + Chunk
- Krypton: Netty optimization
- C2ME: Chunk loading multithreaded
- خطر: متوسط-عالي، يحتاج اختبار مع لاعبين كتير

### Phase 4: ModernFix - Boot
- يسرع إقلاع السيرفر 20s -> 12s
- يمسح Mixins غير لازمة

### Phase 5: Connection Stability (FO-inspired + حصري)
- Tunnel stability 300% (playit.gg)
- Bedrock Bridge QoS

## كيف ننفذ بدون كسر
1. كل Patch OFF by default أول مرة
2. كل Patch معه redstone-aware + wake-up
3. كل Patch معه /voltpur <module> stats
4. كل Patch معه Benchmark via Spark
5. كل Patch في ملف منفصل + reversible

## المسارات
- **Hybrid:** plugin-pro/ للـ Paper Plugins السريعة
- **Inspired:** ندرس فكرة FO ونطبقها بطريقتنا، مش كوبي بيست
- **No Direct Port:** كود Fabric ≠ Paper

## البداية
نبدأ Phase 1 - Hopper Optimization كأول Patch حقيقي من FO
