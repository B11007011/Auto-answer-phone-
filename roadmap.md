### **🚀 Prompt: Auto Answer Call App for Xiaomi F22 Pro (Flutter & Android Accessibility Service)**  

#### **📌 Project Description**  
I want to develop a **Flutter-based Android application** that **automatically answers incoming calls** and **enables speaker mode** on Xiaomi F22 Pro and other Android devices.  

The app should:
- Detect an **incoming call**.
- **Automatically pick up** the call.
- **Enable speaker mode** once the call is answered.
- **Work on Android 10+**, especially handling MIUI-specific restrictions.
- Provide an option for the user to enable/disable **Auto Answer Mode** in settings.
- Handle **MIUI's custom dialer UI**, which may change button text and layouts.

---

### **⚙️ Setup Instructions**  

1️⃣ **Create a Flutter project**  
```sh
flutter create auto_answer_app
cd auto_answer_app
```

2️⃣ **Install dependencies** in `pubspec.yaml`  
```yaml
dependencies:
  flutter:
    sdk: flutter
  phone_state: ^2.0.2
  permission_handler: ^11.0.1
  flutter_foreground_task: ^3.10.2
```

3️⃣ **Modify `AndroidManifest.xml`**  
- Add necessary permissions inside `<manifest>`:  
  ```xml
  <uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE"/>
  <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>
  ```
  
- Register Accessibility Service inside `<application>`:  
  ```xml
  <service
      android:name=".AutoAnswerService"
      android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
      <intent-filter>
          <action android:name="android.accessibilityservice.AccessibilityService" />
      </intent-filter>

      <meta-data
          android:name="android.accessibilityservice"
          android:resource="@xml/accessibility_service_config" />
  </service>
  ```

4️⃣ **Create `accessibility_service_config.xml`** in `android/app/src/main/res/xml/`  
```xml
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service
    android:description="@string/accessibility_service_description"
    android:packageNames="com.android.dialer"
    android:accessibilityEventTypes="typeWindowContentChanged|typeViewClicked"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:notificationTimeout="100"
    android:canRetrieveWindowContent="true"
    android:accessibilityFlags="flagDefault" />
```

---

### **🛠️ Technologies Used**
- **Flutter** (for UI & logic)
- **Dart** (main programming language)
- **Java/Kotlin** (for Android native call handling)
- **Android Accessibility Service** (to bypass call restrictions on Android 10+)
- **Foreground Services** (to keep the app running in the background)
- **MIUI-Specific Adjustments** (Handling MIUI's call interface)

---

### **📲 Auto Answer Service (Handles Call Answering & Speaker Mode)**
Create `AutoAnswerService.java` inside `android/app/src/main/java/com/example/autoanswerapp/`:

```java
package com.example.autoanswerapp;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.media.AudioManager;
import android.util.Log;

public class AutoAnswerService extends AccessibilityService {

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            AccessibilityNodeInfo nodeInfo = getRootInActiveWindow();
            if (nodeInfo != null) {
                findAndClickAnswerButton(nodeInfo);
            }
        }
    }

    private void findAndClickAnswerButton(AccessibilityNodeInfo nodeInfo) {
        if (nodeInfo.getChildCount() == 0) {
            if (nodeInfo.getText() != null) {
                String buttonText = nodeInfo.getText().toString().toLowerCase();
                if (buttonText.contains("answer") || buttonText.contains("accept") || buttonText.contains("接听") || buttonText.contains("trả lời")) {
                    nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK);
                    enableSpeakerphone();
                    Log.d("AutoAnswerService", "Answered Call and Enabled Speaker");
                }
            }
        } else {
            for (int i = 0; i < nodeInfo.getChildCount(); i++) {
                findAndClickAnswerButton(nodeInfo.getChild(i));
            }
        }
    }

    private void enableSpeakerphone() {
        AudioManager audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setSpeakerphoneOn(true);
        }
    }

    @Override
    public void onInterrupt() {
    }
}
```

---

### **📲 Flutter Code to Enable Accessibility Service**
In `main.dart`, add a button to open **Accessibility Settings**:

```dart
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(MyApp());
}

class MyApp extends StatelessWidget {
  static const platform = MethodChannel('call_control');

  Future<void> openAccessibilitySettings() async {
    try {
      await platform.invokeMethod('openAccessibility');
    } on PlatformException catch (e) {
      print("Failed to open Accessibility settings: '${e.message}'.");
    }
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(title: Text('Auto Answer Call')),
        body: Center(
          child: ElevatedButton(
            onPressed: openAccessibilitySettings,
            child: Text('Enable Accessibility Service'),
          ),
        ),
      ),
    );
  }
}
```

---

### **⚠️ MIUI-Specific Adjustments (Xiaomi F22 Pro)**
#### **1️⃣ Enable Auto Start Permission**  
- Xiaomi restricts auto-starting apps.  
  - **Settings > Apps > Manage Apps > Auto Answer App > Auto Start > Enable**

#### **2️⃣ Disable MIUI Battery Optimization**  
- MIUI kills background services aggressively.  
  - **Settings > Battery & Performance > Battery Saver > Auto Answer App > No Restrictions**

#### **3️⃣ Force Accessibility Service to Stay Active**  
- MIUI frequently **disables accessibility services**. Users must:  
  - **Settings > Additional Settings > Accessibility**  
  - **Enable Auto Answer Service**
  - **Lock the app in Recent Apps (Swipe up > Hold App > Tap Lock 🔒)**

#### **4️⃣ Ensure MIUI Call UI is Supported**
- MIUI may modify the **"Answer" button text**, so we handle:
  - `"answer"`, `"accept"`, `"接听"` (Chinese), `"trả lời"` (Vietnamese)

---

### **✅ Final Checklist**
✅ **Flutter UI**
   - [ ] Home screen with an **Enable Auto Answer** button  
   - [ ] Toggle switch for **Auto Answer Mode**  

✅ **MIUI Compatibility**
   - [ ] Ask the user to **manually enable Auto Start**  
   - [ ] Display a guide for **disabling Battery Optimization**  
   - [ ] Show a pop-up to **keep Accessibility Service active**  

✅ **Phone Call Handling**
   - [ ] Detect incoming calls using **phone_state**  
   - [ ] Enable **Accessibility Service** for auto-answering calls  
   - [ ] Automatically **click the Answer button** (Handle MIUI variations)  
   - [ ] Enable **speaker mode** once the call is picked up  

✅ **Testing (Xiaomi F22 Pro)**
   - [ ] Test with **Google Dialer**  
   - [ ] Test with **MIUI Phone App**  
   - [ ] Ensure **Accessibility Service** remains active  

---

### **🚀 How to Run**
1️⃣ **Run the app**  
```sh
flutter run
```
2️⃣ **Enable Auto Start & Battery Permissions** (User must do manually)  
3️⃣ **Keep Accessibility Service Active**  
4️⃣ **Test on Xiaomi F22 Pro** and tweak UI detection if needed  

🔥 **Now your Auto Answer Call App is optimized for Xiaomi MIUI!** 🚀