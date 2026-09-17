# ⚡ VoltPur — تعديلات التصليب (Phase 0 + 1)

الوثيقة تشرح **ماذا تغيّر في هذه الجلسة، ولماذا، وكيف تتحقق**. تفاصيل كاملة في [CHANGELOG.md](CHANGELOG.md).

## أهم 10 تغييرات

1. **رجّعنا كل شغل التصليب الذي مُسح في `f718a8c`** (مصادقة PAdmin، تحقق SHA-256، النسخ الاحتياطي قبل تعديل الملفات، تدوير اللوج، `SECURITY.md`، استثناء الأسعار في `.gitignore`، إصلاح `build-fork.sh`).
2. **PAdmin**: loopback فقط + Basic Auth إجباري + باسورد غير فارغ + مقارنة ثابتة الزمن + GET فقط + قراءة-فقط + لقطة من المين ثريد (كان على `0.0.0.0` بلا مصادقة).
3. **المُحدِّث**: `/vo up <n>` أصبح **تحميل + تحقق + خطة**، و`/vo up confirm` هو التنفيذ. إضافة **`/vo rollback`**، وكشف **مسار الجار المُشغَّل فعليًا**.
4. **لا مسح شامل**: `update.clean-reinstall=false` افتراضيًا (كان يمسح `plugins/`, `server.properties`, `ops.json`, `whitelist.json`, `backups/`... كلها).
5. **`update.auto-backup` بقى حقيقيًا**: يعمل باك أب متسق للعوالم قبل استبدال الجار.
6. **`VoltPurWorldCheck`** لم يعد يكتب `server.properties` (كان يكتب `online-mode=false`!).
7. **`ItemLimiter`**: opt-in + لا يحذف إلا العناصر القديمة (60s+) وغير المسمّاة/غير المُسحّرة + يعلن في الشات قبل الحذف.
8. **`DynamicOptimizer`**: opt-in + فترة سماح بعد الإقلاع + تأكيد استمرار الضغط + **رجوع تلقائي** للقيم الأصلية.
9. **`VoltPurGuard`**: نظام يجعل فشل الموديولات مرئيًا (`runs/fails/last`) بدل 30 كتلة `catch` فارغة. وظهر في `/voltpur modules`.
10. **كشف الحاويات (cgroup)**: التوصية بـ `-Xmx` لم تعد تأخذ نصف ذاكرة **المضيف** (كانت توصي بـ 32GB داخل حاوية 2GB ⇒ قتل OOM).

## كيف تتحقق (3 أوامر)
```
/voltpur modules     → كل موديول + صحته الحقيقية (runs / fails) + الوضع الأمني الحالي
/vo up list          → قائمة البناءات (مخزنة على القرص)، ثم /vo up 1 يعرض الخطة قبل أي تنفيذ
/voltpur hardware    → هل داخل حاوية؟ ما حصتها الفعلية؟ ما الأعلام المناسبة؟
```

## التحقق المنفَّذ فعليًا (وليس ادعاءً)
```
tools/verify/run-verify.sh
== 1/3 compiling 48 files (JDK 21) ==   compile OK (62 classes)
== 2/3 core behaviour checks ==         43 PASS / 0 FAIL
== 3/3 updater / downloader checks ==   20 PASS / 0 FAIL
RESULT: PASS
```
الـ harness موجود داخل المستودع (`tools/verify/`) لأي حد يقدر يعيده: يترجم ملفات VoltPur الحقيقية مقابل stubs للواجهة، ثم يفحص السلوك (افتراضات آمنة، كومنتات `server.properties`، قائمة discord، عدّادات Guard، رفض روابط SSRF، تحقق الأرشيف المُنزَّل، SHA-256 معروف).
**وأمسك خللين في كودي الجديد قبل الكوميت:** عدّاد `Guard.runs()` لم يكن يزيد أبدًا، ومسار فشل تحميل البلجن كان صامتًا. التفاصيل في `CHANGELOG.md` (بندان 16 و17).

## تحقق بالبناء الحقيقي (CI، Java 25)
الفرع `fix/phase-0-1-hardening` مدفوع على المستودع، والبناء الرسمي اشتغل عليه:
| البند | القيمة |
|---|---|
| Run | **#66** — `workflow_dispatch` على `1cbaa68e` |
| النتيجة | `completed / success` (~7.5 دقيقة، 15:38→15:45 UTC) |
| الخطوات | Checkout ✓ · Setup Java 25 ✓ · Apply Patches ✓ · Build Paperclip JAR ✓ · Prepare Artifacts ✓ · Upload JAR ✓ |
| **Create Release** | **skipped** ✅ (الخطوة مقيّدة بـ `refs/heads/ver/26.2` — لم يُنشأ إصدار من فرع مراجعة) |
| الناتج | `VoltPur-26.2.jar` = **62 MiB** · `server.jar` = 62 MiB · `VoltPur-26.2.jar.sha256` · `file` يقول: `Java archive data (JAR)` |
| الـ artifact | `VoltPur-26.2-Paperclip` (185.0 MiB — يشمل 3 نسخ + الجار الأصلي) |
| `ver/26.2` | **لم يُلمس** — لسه `f718a8c` |

الخلاصة: الشجرة المُصلَّبة **تبني فعليًا** بجافا 25 وتنتج JAR سليمًا، وليس فقط «تترجم مقابل stubs».

## ما لم يُنفَّذ في هذه الجلسة (بوضوح)
- **تشغيل سيرفر حقيقي / smoke test إقلاع**: لم يُشغَّل بعد (لا يتحقق منه البناء وحده). الـ harness لا يعوّض ذلك.
- CI smoke test (إقلاع headless في Actions) — مقترح في المرحلة 2.
- `Claim → Probe` (توليد الأرقام آليًا في CI) — المرحلة 2.
- تحويل الأدوات إلى بلجن مستقل `VoltPur.jar` — قرار استراتيجي (المرحلة 3).
- أي patch على NMS (الهوبر/التصادم/الذاكرة) — **لم ندّعِها ولن ندّعيها قبل قياس**.
