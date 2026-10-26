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
لو حجم الـ JAR ~80MB، الرفع من المتصفح ممكن يفشل.

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
- تأكد أن Run ID هو `30522860445` أو أحدث

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
# المفروض يكون ~80-100MB مش 0 bytes ولا بضع KB
```

## الطريقة الصح لـ Pterodactyl

### الإعداد:
**Startup -> Startup Command:**
```
java -Xms128M -Xmx{{SERVER_MEMORY}}M --add-modules=jdk.incubator.vector -Dterminal.jline=false -Dterminal.ansi=true -jar server.jar --nogui
```
(حط `server.jar` مش ` {{SERVER_JARFILE}}` variable لو عندك مشكلة)

**Docker Image:** `ghcr.io/pterodactyl/yolks:java_25`

**تأكد:**
- `server.jar` = الـ VoltPur JAR الجديد (فك الضغط الأول!)
- Java 25
- حجم الملف 70MB+

## اختبار
بعد ما ترفع الصح:
```
[VoltPur]  V O L T P U R - 26.2-VoltPur
[VoltPur]  Loading 21 modules...
[VoltPur]  [1/21] EntityActivation - OK
...
```

## لو لسه المشكلة موجودة
ابعت:
1. `ls -lh server.jar`
2. `file server.jar`
3. `sha256sum server.jar` وقارنها مع `VoltPur-26.2.jar.sha256`
