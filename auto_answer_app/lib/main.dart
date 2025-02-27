import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:shared_preferences/shared_preferences.dart';

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
      home: const MyHomePage(),
    );
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
  final MethodChannel _channel = const MethodChannel('call_control');

  @override
  void initState() {
    super.initState();
    _checkPermissions();
    _checkAccessibilityService();
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
