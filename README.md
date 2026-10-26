# ⚡ VoltPur — Minecraft Server Software

[![Build](https://github.com/tkjij77-ctrl/VoltPur/actions/workflows/build.yml/badge.svg?branch=ver/26.2)](https://github.com/tkjij77-ctrl/VoltPur/actions)
![MC](https://img.shields.io/badge/Minecraft-1.21.10-brightgreen?style=for-the-badge)
![Java](https://img.shields.io/badge/Java-25-orange?style=for-the-badge)
![Version](https://img.shields.io/badge/VoltPur-26.2.0-blue?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)

---

## 📖 ما هو VoltPur؟ (الفكرة الأساسية)

**VoltPur هو برنامج خادم ماينكرافت عالي الأداء** (server software) مبني على **Purpur 26.2** (المشتق بدوره من Paper لمجتمع 1.21.10).

يهدف VoltPur إلى تقديم **تحكم هندسي تكيفي حقيقي، أمان مطلق، ومساعد ذكي عملي يستند إلى بيانات الأداء الحية**، بعيداً عن الادعاءات الوهمية أو التعديلات العشوائية.

### 🌟 القيمة الحقيقية المضافة في VoltPur:

| المجال | ماذا يقدّم VoltPur |
|---|---|
| **الأداء التكيفي الحقيقي (Closed-Loop Autotune)** | حلقة تحكم مغلقة تراقب MSPT، تشخص السبب الجذري للضغط، وتتخذ إجراءات وقائية ضمن حدود صلبة آمنة مع ميزة التقييم والتراجع التلقائي (Auto-Rollback). |
| **محدد العناصر الذكي (Smart ItemLimiter)** | مسح خطي بمرور واحد يحذف المقتنيات المرمية الزائدة بدءاً من الأقدم، مع استثناء مقتنيات اللاعبين القريبة والحديثة. |
| **التعديل العميق (`plugin-plus/`)** | بنية تعديل منخفضة المستوى (Fabric-style) تعمل قبل إقلاع كلاسات ماينكرافت لتعديل NMS مع عزل كامل للذاكرة وفحص SHA-256. |
| **طبقة الذكاء الاصطناعي (`/ai`)** | مساعد وطبيب تشخيصي فني للسيرفر يترجم بيانات الأداء إلى حلول مفهومة، بمفاتيح المستخدم (مجاناً أو بسقف ميزانية شهري)، دون أي تعطيل للخادم. |
| **الأمان والاستقرار الشامل** | كاتب إعدادات سطري ذري (`AtomicConfigWriter`) يحافظ 100% على التعليقات، PAdmin محمي عبر localhost و Basic Auth، وفحص SHA-256 للتحديثات. |
| **الشفافية والصدق** | سجل موديولات صادق (13 Active / 5 Partial / 9 Planned)، وبيانات قياس حقيقية عبر `/voltpur benchmark`. |

---

## ✅ سجل الميزات والموديولات (صادق ومُثبت)

### 🟢 نشطة ومُثبتة (ACTIVE — تعمل وتُقاس)
| الميزة / الموديول | الوظيفة | التحقق والأمر |
|---|---|---|
| **ClosedLoopAutotune** | مراقبة MSPT وتعديل التوليف مع التراجع التلقائي | `/voltpur autotune status` |
| **SmartItemLimiter** | تنظيف العناصر الزائدة مع استثناء القريب والحديث | `/voltpur benchmark` |
| **AtomicConfig** | كتابة ذرية آمنة تحافظ على تعليقات الإعدادات | ملفات الإعدادات و `.bak` |
| **HopperOptimization** | نوم الهوبرات الخاملة غير الموصولة بحاويات | `hopper-sleep` في `voltpur.yml` |
| **AiPracticalLayer** | مساعد ذكي وطبيب تشخيص للسيرفر بميزانية محددة | `/ai doctor` و `/ai status` |
| **HardwareDetection** | كشف العتاد وتوليد أعلام JVM المثالية لجهازك | `/voltpur hardware` |
| **HardwareAutoTune** | توليف `server.properties` حسب موارد الجهاز | `/voltpur flags` |
| **Benchmark** | قياسات حية لـ MSPT/TPS/الكيانات/الذاكرة/الهوبرات | `/voltpur benchmark` |
| **TPSMonitor** | مراقبة حية لثبات السيرفر والتنبيه عند الهبوط | سجلات الكنسول |
| **WorldStability** | فحص وحماية عوالم السيرفر وعزل الأعطال | `/voltpur status` |
| **PterodactylFix** | إصلاح مشاكل الإقلاق واستقرار بيئات الاستضافة | ملف التشغيل والبيئة |
| **Updater** | تحديث آمن مع فحص SHA-256 ونسخ احتياطي | `/vo up` |
| **PAdminWebUI** | واجهة ويب محمية محلياً (127.0.0.1 + Basic Auth) | `/padmin` |

### 🟠 ميزات جزئية (PARTIAL)
- **PlusLoader:** محمّل `plugin-plus/` المسبق بمبدأ Child-First ClassLoader وفحص SHA-256.
- **EntityActivation:** نشطة عبر محرّك Paper EAR المدمج.
- **AikarFlagsAuto:** أعلام Aikar الموصى بها تُقترح في السجلات.
- **BedrockBridge / AntiExploit:** طبقات حماية مدمجة جزئياً.

---

## 🤖 المساعد الذكي وطبيب السيرفر (`/ai`)

VoltPur مزود بطبقة ذكاء اصطناعي عملية غير معطلة للـ Main Thread تلتزم بأعلى معايير الأمان والخصوصية:

- **أوامر المساعد:**
  - `/ai <سؤال>`: استفسار تقني حول إعدادات السيرفر مع حقن سياق النظام تلقائياً.
  - `/ai doctor`: فحص تشخيصي شامل لـ MSPT وتاريخ القرارات مع تقديم توصيات عملية.
  - `/ai status`: عرض المزود الحالي، النموذج، الرصيد المستهلك، وسقف الميزانية.
  - `/ai models`: عرض النماذج المجانية والمتاحة لمفتاحك.
  - `/ai test`: فحص سرعة الاتصال بالمزود.
  - `/ai confirm-paid`: تأكيد الموافقة على استخدام النماذج المدفوعة ضمن سقف الميزانية المحدد.
- **المزودات المدعومة تلقائياً:**
  - OpenRouter (`sk-or-...`) — نماذج مجانية افتراضية (`:free`).
  - Google Gemini (`AIza...`) — Free tier سريع.
  - Groq (`gsk_...`) — استجابة فائقة السرعة.
  - OpenAI (`sk-...`) — نماذج مدفوعة محكومة بالميزانية.
  - Custom / Local — دعم كامل لنماذج Ollama و LM Studio عبر `base-url`.
- **خصوصية صارمة:** حجب أسماء اللاعبين، عناوين الـ IP، والإحداثيات افتراضياً.

---

## 🚀 التشغيل السريع

### 1) التحميل
حمّل `server.jar` من صفحة [Releases](https://github.com/tkjij77-ctrl/VoltPur/releases/latest) وضعه في مجلد الخادم.

### 2) الموافقة على EULA
افتح ملف `eula.txt` واضبط القيمة: `eula=true`.

### 3) التشغيل
```bash
java -Xms1G -Xmx{{RAM}}M --add-modules=jdk.incubator.vector \
  -Dterminal.jline=false -Dterminal.ansi=true -jar server.jar --nogui
```

---

## ⌨️ دليل الأوامر الشامل

```bash
# === أوامر الإدارة والتشخيص ===
/voltpur help                   # مرجع كامل لكافة الأوامر
/voltpur status                 # فحص شامل (عوالم/ملفات/MSPT/TPS)
/voltpur modules                # كشف حالة الموديولات الصادقة (Active/Partial/Planned)
/voltpur benchmark              # قياس حي للأداء وتصدير تقرير JSON
/voltpur hardware               # تقرير توافق العتاد وأعلام JVM الموصى بها

# === حلقة التحكم التكيفي (Autotune) ===
/voltpur autotune status        # عرض حالة حلقة التحكم والوضع الحالي (Advisor/Active)
/voltpur autotune history       # سجل آخر 20 قراراً اتخذتها الحلقة مع نتائجها
/voltpur autotune rollback      # التراجع الفوري عن كافة التعديلات إلى نقطة البداية
/voltpur autotune enable        # تفعيل الوضع التلقائي النشط (Active Mode)
/voltpur autotune disable       # التحويل إلى وضع المستشار فقط (Advisor-Only)

# === المساعد الذكي وطبيب السيرفر ===
/ai <سؤال>                      # استشارة المساعد التقني حول الخادم
/ai doctor                      # فحص تشخيصي عميق لمقاييس السيرفر الحالية
/ai status                      # عرض حالة المزود والميزانية الشهرية
/ai models                      # عرض النماذج المدعومة والمجانية
/ai test                        # اختبار الاتصال بالمزود
/ai confirm-paid                # تأكيد تفعيل النماذج المدفوعة

# === التحديث وتنزيل الإضافات ===
/vo up [buildId]                # تحديث آمن للسيرفر مع فحص التجزئة والنسخ الاحتياطي
/vo in <url> <plugins|plugin-pro> # تنزيل إضافات موثوقة وفحص صلاحيتها
/padmin                         # واجهة التحكم عبر الويب (Localhost)
```

---

## 🧪 التحقق والاختبارات الآلية (CI & Testing)

VoltPur يخضع لاختبارات صارمة تضمن عدم انكسار أي ميزة قبل النشر:
- **39 اختبار وحدة (Unit Tests)** تغطي كافة الأنظمة الحساسة (التحكم التكيفي، الذكاء الاصطناعي، الكاتب الذري، والمحمّل).
- **اختبار الإقلاع في بيئة CI غير الرسومية (`scripts/ci-headless-test.sh`)** للتحقق من سلامة الإقلاع والإغلاق النظيف.

لتشغيل الاختبارات محلياً:
```bash
./scripts/ci-headless-test.sh
```

---

## 📜 الترخيص
مرخص تحت رخصة **MIT License** (متوافق مع تراخيص Purpur و Paper).
