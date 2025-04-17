import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'dart:async';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Auto Answer Call',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const PasswordCheckWrapper(),
    );
  }
}

class PasswordCheckWrapper extends StatefulWidget {
  const PasswordCheckWrapper({super.key});

  @override
  State<PasswordCheckWrapper> createState() => _PasswordCheckWrapperState();
}

class _PasswordCheckWrapperState extends State<PasswordCheckWrapper> {
  bool _isPasswordRequired = false;
  bool _isPasswordVerified = false;
  static const String _defaultPassword = "autoanswer2024";

  @override
  void initState() {
    super.initState();
    _checkPasswordRequirement();
  }

  Future<void> _checkPasswordRequirement() async {
    final prefs = await SharedPreferences.getInstance();
    final firstUseTime = prefs.getInt('first_use_time');
    final isVerified = prefs.getBool('is_permanently_verified') ?? false;

    if (firstUseTime == null) {
      // First time using the app
      await prefs.setInt(
          'first_use_time', DateTime.now().millisecondsSinceEpoch);
      setState(() {
        _isPasswordVerified = true;
      });
      return;
    }

    if (isVerified) {
      setState(() {
        _isPasswordVerified = true;
      });
      return;
    }

    final fifteenDaysInMillis =
        15 * 24 * 60 * 60 * 1000; // 15 days in milliseconds
    final now = DateTime.now().millisecondsSinceEpoch;
    final timeSinceFirstUse = now - firstUseTime;

    if (timeSinceFirstUse >= fifteenDaysInMillis) {
      setState(() {
        _isPasswordRequired = true;
      });
    } else {
      setState(() {
        _isPasswordVerified = true;
      });
    }
  }

  Future<void> _verifyPassword(String password) async {
    if (password == _defaultPassword) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool('is_permanently_verified', true);
      setState(() {
        _isPasswordVerified = true;
        _isPasswordRequired = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_isPasswordRequired && !_isPasswordVerified) {
      return PasswordScreen(onVerify: _verifyPassword);
    }
    return const MyHomePage();
  }
}

class PasswordScreen extends StatefulWidget {
  final Function(String) onVerify;

  const PasswordScreen({super.key, required this.onVerify});

  @override
  State<PasswordScreen> createState() => _PasswordScreenState();
}

class _PasswordScreenState extends State<PasswordScreen> {
  final _passwordController = TextEditingController();
  String _errorText = '';

  void _submitPassword() {
    final password = _passwordController.text.trim();
    if (password.isEmpty) {
      setState(() {
        _errorText = 'Password cannot be empty';
      });
      return;
    }
    widget.onVerify(password);
    if (mounted && password != "autoanswer2024") {
      setState(() {
        _errorText = 'Incorrect password';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Password Required'),
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Text(
              'This app requires password verification after 15 days of use.',
              textAlign: TextAlign.center,
              style: TextStyle(fontSize: 16),
            ),
            const SizedBox(height: 20),
            TextField(
              controller: _passwordController,
              obscureText: true,
              decoration: InputDecoration(
                labelText: 'Enter Password',
                errorText: _errorText.isNotEmpty ? _errorText : null,
                border: const OutlineInputBorder(),
              ),
              onSubmitted: (_) => _submitPassword(),
            ),
            const SizedBox(height: 20),
            ElevatedButton(
              onPressed: _submitPassword,
              child: const Text('Verify Password'),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    _passwordController.dispose();
    super.dispose();
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({super.key});

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  bool _isServiceEnabled = false;
  bool _isPhonePermissionGranted = false;
  bool _autoAnswerEnabled = false;
  bool _speakerEnabled = false;
  bool _isXiaomiDevice = false;
  final MethodChannel _channel = const MethodChannel('call_control');

  @override
  void initState() {
    super.initState();
    _checkPermissions();
    _checkAccessibilityService();
    _checkIsXiaomiDevice();
    _loadSavedSettings();
  }

  Future<void> _checkIsXiaomiDevice() async {
    final deviceInfo = await getDeviceInfo();
    setState(() {
      _isXiaomiDevice = deviceInfo.contains('xiaomi') || 
                        deviceInfo.contains('redmi') || 
                        deviceInfo.contains('poco');
    });
    debugPrint("Device info: $deviceInfo, isXiaomi: $_isXiaomiDevice");
  }

  Future<String> getDeviceInfo() async {
    try {
      final androidInfo = await _channel.invokeMethod('getDeviceInfo');
      return androidInfo?.toString().toLowerCase() ?? '';
    } catch (e) {
      debugPrint("Failed to get device info: '${e.toString()}'.");
      return '';
    }
  }

  Future<void> _loadSavedSettings() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      setState(() {
        _autoAnswerEnabled = prefs.getBool('auto_answer_enabled') ?? false;
        _speakerEnabled = prefs.getBool('speaker_enabled') ?? false;
      });
    } catch (e) {
      debugPrint("Failed to load settings: '${e.toString()}'.");
    }
  }

  Future<void> _checkAccessibilityService() async {
    try {
      final bool isEnabled =
          await _channel.invokeMethod('isAccessibilityServiceEnabled');
      setState(() {
        _isServiceEnabled = isEnabled;
      });
    } on PlatformException catch (e) {
      debugPrint("Failed to check accessibility service: '${e.message}'.");
    }
  }

  Future<void> _checkPermissions() async {
    // Check phone permission
    final phoneStatus = await Permission.phone.status;
    final phoneCallStatus = await Permission.phone.request();

    setState(() {
      _isPhonePermissionGranted =
          phoneStatus.isGranted && phoneCallStatus.isGranted;
    });

    if (!_isPhonePermissionGranted) {
      final result = await Permission.phone.request();
      setState(() {
        _isPhonePermissionGranted = result.isGranted;
      });
    }
  }

  Future<void> _openAccessibilitySettings() async {
    try {
      await _channel.invokeMethod('openAccessibility');
    } on PlatformException catch (e) {
      debugPrint("Failed to open Accessibility settings: '${e.message}'.");
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
                'Failed to open Accessibility settings. Please open them manually.'),
            duration: Duration(seconds: 3),
          ),
        );
      }
    }
  }

  void openAccessibilitySettings() {
    const platform = MethodChannel('com.example.auto_answer_app/accessibility');
    platform.invokeMethod('openAccessibilitySettings');
  }

  Future<void> _saveSettings() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('auto_answer_enabled', _autoAnswerEnabled);
    await prefs.setBool('speaker_enabled', _speakerEnabled);

    // Notify native side of settings change
    await _channel.invokeMethod('updateSettings', {
      'autoAnswer': _autoAnswerEnabled,
      'speaker': _speakerEnabled,
    });
  }

  Future<void> _forceSpeakerOn() async {
    try {
      await _channel.invokeMethod('forceSpeakerOn');
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Speaker mode forced on'),
          duration: Duration(seconds: 2),
        ),
      );
    } catch (e) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Failed to force speaker mode: $e'),
          backgroundColor: Colors.red,
          duration: const Duration(seconds: 3),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: const Text('Auto Answer Call'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              const Text(
                'Auto Answer Service Status:',
                style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 20),
              Icon(
                _isServiceEnabled && _isPhonePermissionGranted
                    ? Icons.check_circle
                    : Icons.error,
                color: _isServiceEnabled && _isPhonePermissionGranted
                    ? Colors.green
                    : Colors.red,
                size: 48,
              ),
              const SizedBox(height: 16),
              Text(
                _isPhonePermissionGranted
                    ? 'Phone Permission: Granted'
                    : 'Phone Permission: Not Granted',
                style: TextStyle(
                  fontSize: 14,
                  color: _isPhonePermissionGranted ? Colors.green : Colors.red,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 8),
              Text(
                _isServiceEnabled
                    ? 'Accessibility Service: Enabled'
                    : 'Accessibility Service: Disabled',
                style: TextStyle(
                  fontSize: 14,
                  color: _isServiceEnabled ? Colors.green : Colors.red,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 20),
              if (!_isServiceEnabled)
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton.icon(
                    onPressed: _openAccessibilitySettings,
                    icon: const Icon(Icons.settings_accessibility, size: 20),
                    label: const Text('Enable Accessibility Service'),
                    style: ElevatedButton.styleFrom(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                    ),
                  ),
                ),
              if (!_isPhonePermissionGranted)
                Padding(
                  padding: const EdgeInsets.only(top: 8.0),
                  child: SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: _checkPermissions,
                      icon: const Icon(Icons.phone, size: 20),
                      label: const Text('Grant Phone Permission'),
                      style: ElevatedButton.styleFrom(
                        padding: const EdgeInsets.symmetric(vertical: 8),
                      ),
                    ),
                  ),
                ),
              const SizedBox(height: 20),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.grey[100],
                  borderRadius: BorderRadius.circular(8),
                ),
                child: const Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(Icons.info_outline, color: Colors.blue, size: 24),
                    SizedBox(height: 8),
                    Text(
                      'Please enable both the Phone Permission and Auto Answer Service in your phone\'s Accessibility Settings to use this app.',
                      textAlign: TextAlign.center,
                      style: TextStyle(color: Colors.grey, fontSize: 12),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16.0),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Settings',
                        style: TextStyle(
                            fontSize: 18, fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 16),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Auto Answer Calls'),
                          Switch(
                            value: _autoAnswerEnabled,
                            onChanged:
                                _isServiceEnabled && _isPhonePermissionGranted
                                    ? (value) {
                                        setState(() {
                                          _autoAnswerEnabled = value;
                                        });
                                        _saveSettings().then((_) {
                                          ScaffoldMessenger.of(context)
                                              .showSnackBar(
                                            const SnackBar(
                                              content: Text(
                                                  'Settings saved successfully'),
                                              duration: Duration(seconds: 2),
                                            ),
                                          );
                                        }).catchError((error) {
                                          ScaffoldMessenger.of(context)
                                              .showSnackBar(
                                            SnackBar(
                                              content: Text(
                                                  'Failed to save settings: $error'),
                                              backgroundColor: Colors.red,
                                              duration: Duration(seconds: 3),
                                            ),
                                          );
                                        });
                                      }
                                    : null,
                          ),
                        ],
                      ),
                      const SizedBox(height: 8),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          const Text('Enable Speaker'),
                          Switch(
                            value: _speakerEnabled,
                            onChanged:
                                _isServiceEnabled && _isPhonePermissionGranted
                                    ? (value) {
                                        setState(() {
                                          _speakerEnabled = value;
                                        });
                                        _saveSettings().then((_) {
                                          ScaffoldMessenger.of(context)
                                              .showSnackBar(
                                            const SnackBar(
                                              content: Text(
                                                  'Settings saved successfully'),
                                              duration: Duration(seconds: 2),
                                            ),
                                          );
                                        }).catchError((error) {
                                          ScaffoldMessenger.of(context)
                                              .showSnackBar(
                                            SnackBar(
                                              content: Text(
                                                  'Failed to save settings: $error'),
                                              backgroundColor: Colors.red,
                                              duration: Duration(seconds: 3),
                                            ),
                                          );
                                        });
                                      }
                                    : null,
                          ),
                        ],
                      ),
                      
                      // Xiaomi-specific additional option
                      if (_isXiaomiDevice && _speakerEnabled)
                        Padding(
                          padding: const EdgeInsets.only(top: 16.0),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              const Divider(),
                              const SizedBox(height: 8),
                              const Text(
                                'Xiaomi Device Detected',
                                style: TextStyle(
                                  fontSize: 16,
                                  fontWeight: FontWeight.bold,
                                  color: Colors.blue,
                                ),
                              ),
                              const SizedBox(height: 8),
                              const Text(
                                'If speaker mode is not working during calls, try using the Force Speaker button below.',
                                style: TextStyle(fontSize: 12),
                              ),
                              const SizedBox(height: 12),
                              SizedBox(
                                width: double.infinity,
                                child: ElevatedButton.icon(
                                  onPressed: _forceSpeakerOn,
                                  icon: const Icon(Icons.volume_up, size: 20),
                                  label: const Text('Force Speaker Mode'),
                                  style: ElevatedButton.styleFrom(
                                    backgroundColor: Colors.blue,
                                    foregroundColor: Colors.white,
                                    padding: const EdgeInsets.symmetric(vertical: 8),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
