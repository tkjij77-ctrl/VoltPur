# إصلاح VoltPur - ليه المميزات مش كانت شغالة

## المشاكل التي تم إصلاحها

1. **build.yml كان بيبني PurpurMC/Purpur مش ريبو بتاعك** -> تم إصلاحه ليعمل checkout لنفس الريبو

2. **مجلد performance غير مقروء** -> تم نقله لـ docs/archive-old-patches/ لأنه فورماته غلط وغير متوافق مع Paperweight

3. **الـ JAR الصح هو paperclip** -> تم تغيير build لـ createMojmapPaperclipJar

## المميزات الحالية الشغالة بعد الإصلاح

- `plugin-pro/` folder -> من خلال patch صحيح في `purpur-server/paper-patches/files/.../PluginInitializerManager.java.patch`

## الخطوات الجاية لإضافة الـ 21 patch بشكل صحيح

كل patch لازم يتعمل بالطريقة دي:

```bash
./gradlew applyAllPatches
# عدل الكود في purpur-server/src/main/java/... أو paper-server/...
./gradlew rebuildPatches
# هيتولد patch جديد في purpur-server/minecraft-patches/ أو paper-patches/
git add الت patch الجديد
```

مثال: عايز تضيف Connection Stability:
1. applyAllPatches
2. عدل `net/minecraft/server/network/ServerCommonPacketListenerImpl.java` في `purpur-server/build/...` أو `purpur-server/src/...`
3. rebuildPatches

## إزاي تختبر

```bash
./gradlew applyAllPatches
./gradlew createMojmapPaperclipJar
java -jar purpur-server/build/libs/*-paperclip.jar --nogui
```

هتلاقي في اللوج:
- "VoltPur" branding
- مجلد plugin-pro بيتقري لو موجود

## ملاحظة عن أداء Lithium, FerriteCore, Krypton

دي مودات Fabric، مش Paper patches. مينفعش تنسخها كوبي بيست. محتاجة إعادة كتابة كاملة لنظام Paper.

ابدأ بواحدة واحدة، كل patch صغير وقابل للاختبار.
