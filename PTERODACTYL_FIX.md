# 🔧 حل مشكلة Invalid or corrupt jarfile في Pterodactyl

## المشكلة
```
Error: Invalid or corrupt jarfile server.jar
```

## الأسباب الشائعة

### 1. حملت ملف ZIP وسميته server.jar (أشهر سبب!)
GitHub Actions بيحملك ملف **ZIP** اسمه `VoltPur-26.2-Paperclip.zip` جواه الـ JAR.

**الحل:**
1. فك الضغط عن الـ ZIP الأول
2. جواه هتلاقي:
   - `VoltPur-26.2.jar`
   - `VoltPur.jar`
   - `server.jar` (جاهز لـ Pterodactyl)
3. ارفع `server.jar` اللي جوه الـ ZIP، مش الـ ZIP نفسه

```bash
# على الكمبيوتر
unzip VoltPur-26.2-Paperclip.zip
# هيطلعلك VoltPur-26.2.jar و server.jar
```

### 2. الرفع عبر متصفح Pterodactyl بيقطع الملف
جار Paperclip بحجم عشرات الميجابايت، والرفع من المتصفح ممكن يفشل أو ينتج ملفًا ناقصًا.

**الحل:** استخدم SFTP:
- Host: IP السيرفر
- Port: 2022 (افتراضي Pterodactyl)
- Username: نفس يوزر الـ panel
- ارفع `server.jar` عبر SFTP

### 3. استخدمت jar قديم من build فاشل
قبل الإصلاح الأخير، الـ builds كانت تفشل وتنتج ملفات ناقصة.

**الحل:**
- روح Actions -> آخر run ناجح (أخضر ✅) بتاريخ اليوم
- حمل الـ Artifact الجديد فقط
- افتح صفحة **Releases** وحمّل من آخر إصدار (لا يحتاج توكن، وهو نفس ما يستخدمه `/vo up`)
- أو من Actions: أي run **أخضر ✅** حديث — لا تعتمد على رقم Run محدد مكتوب في أي دليل

### 4. تأكد الملف JAR فعلا
على السيرفر، شغل:
```bash
file server.jar
# المفروض يطلع: Zip archive data, Java archive
# لو طلع: HTML document -> حملت صفحة مش jar!

java -jar server.jar --help
# المفروض يطلع مساعدة
```

### 5. الصلاحيات
```bash
chmod +x server.jar
ls -lh server.jar
# المفروض عشرات الميجابايت. لو 0 bytes أو بضع KB => الملف/الرفع ناقص
sha256sum server.jar   # وقارنها مع VoltPur-26.2.jar.sha256
```

## الطريقة الصح لـ Pterodactyl

### الإعداد:
**Startup -> Startup Command:**
```
java -Xms128M -Xmx{{SERVER_MEMORY}}M --add-modules=jdk.incubator.vector -Dterminal.jline=false -Dterminal.ansi=true -jar server.jar --nogui
```
(حط `server.jar` مش ` {{SERVER_JARFILE}}` variable لو عندك مشكلة)

> لو ظهر خطأ أن `jdk.incubator.vector` غير موجود، احذف `--add-modules=jdk.incubator.vector` من الأمر.
> هذا العلم يُضاف تلقائيًا في التوصية داخل `/voltpur flags` **فقط** لو الـ JDK يحتويه فعلًا.

**Docker Image:** `ghcr.io/pterodactyl/yolks:java_25`

**تأكد:**
- `server.jar` = الـ VoltPur JAR الجديد (فك الضغط الأول!)
- Java 25
- الحجم عشرات الميجابايت (وليس KB) — القيمة الدقيقة ظاهرة في صفحة الإصدار

## اختبار حقيقي (بدون مخرجات مُختلقة)

شغّل السيرفر وابحث في اللوج عن السطور الفعلية التالية:

```
━━━━━━━━━━━━━━━━ VoltPur ⚡ 26.2.0-rc2 ━━━━━━━━━━━━━━━━
[VoltPur] Modules: N implemented, N partial, N planned - run /voltpur modules for live health
[VoltPur] Safety: item-limiter=off optimizer=off padmin=off destructive-reinstall=off update-checksum=required
[VoltPur-World] Loaded worlds: N (overworld=OK, nether=..., end=...)
[VoltPur] Installed build: build <n> | checksum verified: yes | at <timestamp>   <- يظهر فقط بعد تحديث عبر /vo up
```
(الأرقام `N` تُطبع فعليًا من السجل — لا يوجد عدد ثابت مكتوب في الكود.)
ثم في اللعبة (OP):
```
/voltpur modules     -> لكل موديول: حالته + runs/fails الحقيقية
/voltpur status      -> TPS + MSPT + العوالم + بصمة البناء المثبَّت
```

> **ملاحظة صدق:** كانت النسخة السابقة من هذا الملف تعرض مخرجات مثل
> `[VoltPur] Loading 21 modules... [1/21] EntityActivation - OK` — وهذا **مخرج غير موجود**
> في أي إصدار من الكود (قائمة الـ 21 موديول الوهمية أُزيلت منذ `VoltPurModules`).
> لا تعتمد على أي لوج مكتوب يدويًا في التوثيق؛ استخدم اللوج الفعلي لسيرفرك.

## لو لسه المشكلة موجودة
ابعت:
1. `ls -lh server.jar`
2. `file server.jar`
3. `sha256sum server.jar` وقارنها مع `VoltPur-26.2.jar.sha256`
