# 🛠️ Hopper Sleep — مقتطف NMS الجاهز (يتطلب applyPatches على جهازك)

> **لماذا لا أرفعه كـ patch مباشر؟** ملفات `net/minecraft/*` تُولَّد عند `applyAllPatches` على جهازك (شجرة mojang-mapped). لو كتبت patch بخطوط ثابتة من هنا، قد لا يطابق السياق المولَّد فيفشل البناء. الحل الصحيح في paperweight: **طبّق، عدّل، ثم أعد توليد الـ patch**.

## الخطوات (على جهازك الذي فيه Java 25 + clone git)

```bash
cd فوركك
./gradlew applyAllPatches
# افتح:
#   paper-server/src/main/java/net/minecraft/world/level/block/entity/HopperBlockEntity.java
# وأضف التعديل أدناه، ثم:
./gradlew rebuildPatches
```

## التعديل (mojang-mapped — يطابق ما ستراه بعد applyAllPatches)

في `HopperBlockEntity.pushItemsTick(Level level, BlockPos pos, BlockState state, HopperBlockEntity blockEntity)`:

```java
public static void pushItemsTick(Level level, BlockPos pos, BlockState state, HopperBlockEntity blockEntity) {
    --blockEntity.cooldownTime;
    blockEntity.tickedGameTime = level.getGameTime();

    // VoltPur start - safe empty-hopper rest (opt-in, behavior-preserving)
    // tryMoveItems is a guaranteed no-op when the hopper is empty, has no
    // source container above, and has no items in its pickup zone. Resting
    // under exactly those conditions cannot break farms.
    if (VoltPurConfig.hopperSleepEnabled
            && blockEntity.isEmpty()
            && blockEntity.getSourceContainer(level, blockEntity, pos, state) == null
            && blockEntity.getItemsAtAndAbove(level, blockEntity).isEmpty()) {
        blockEntity.setCooldown(VoltPurConfig.hopperSleepCooldown); // short rest, e.g. 3
        return; // skip the expensive eject/suck checks this tick
    }
    // VoltPur end

    if (!blockEntity.isOnCooldown()) {
        blockEntity.setCooldown(0);
        tryMoveItems(level, pos, state, blockEntity, () -> suckInItems(level, blockEntity));
    }
}
```

> ملاحظة استيراد: أضف `import org.purpurmc.purpur.VoltPurConfig;` أعلى الملف إذا لم تكن موجودة (هي في نفس الـ module).
> `getSourceContainer` و`getItemsAtAndAbove` دوال خاصة بالكلاس نفسه، موجودة في `HopperBlockEntity`.

## التداول (نوم الهوبر)
- **بما أنه no-op مضمون** تحت الشرط، فلا كسر للأفعال أو farms.
- `setCooldown` قصير (افتراضي 3) يحدّ التأخير عند ظهور item لاحقاً.
- فعّل يدوياً: `modules.performance.hopper-sleep.enabled: true` في `voltpur.yml`.

## القياس
قبل/بعد:
```
/voltpur benchmark   # لاحظ MSPT + Hoppers sleepable
```
ضع عدداً كبيراً من الهوبرات الفارغة، قارن MSPT قبل وبعد تفعيل النوم.
