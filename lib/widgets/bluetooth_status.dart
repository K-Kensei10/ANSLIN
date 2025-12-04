import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:app_settings/app_settings.dart';

class BluetoothStateModal extends StatefulWidget {
  final Widget child;
  const BluetoothStateModal({super.key, required this.child});

  @override
  State<BluetoothStateModal> createState() => _BluetoothStateModalState();
}

class _BluetoothStateModalState extends State<BluetoothStateModal> {
  // Bluetooth状態を受け取るEventChannel
  static const _eventChannel = EventChannel('bluetoothStatus');
  StreamSubscription? _bluetoothStateSubscription;
  bool _isShowingModal = false;

  @override
  void initState() {
    super.initState();

    // Bluethooth状態監視
    _bluetoothStateSubscription = _eventChannel
      .receiveBroadcastStream()
      .cast<bool>()
      .listen(
        (bool isBluetoothOn) {
          if (!isBluetoothOn) {
            _showBluetoothModal();
          } else {
            // BluetoothがONになったらモーダルを閉じる
            if (_isShowingModal && mounted) {
              Navigator.of(context).pop();
              _isShowingModal = false;
            }
          }
        },
        onError: (error) {
          _showBluetoothModal();
        },
      );
  }

  void _showBluetoothModal() {
    if (!_isShowingModal && mounted) {
      _isShowingModal = true;
      showModalBottomSheet(
        context: context,
        isScrollControlled: true,
        isDismissible: false,
        builder: (BuildContext ctx) {
          return FractionallySizedBox(
            widthFactor: 1.0,
            child: Container(
              padding: const EdgeInsets.all(24),
              height: 450,
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  const Text(
                    "Bluetoothをオンにしてください",
                    style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                    textAlign: TextAlign.center,
                  ),
                  const SizedBox(height: 16),
                  const Icon(Icons.bluetooth_disabled, size: 128, color: Colors.blue),
                  ElevatedButton(
                    child: const Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text("設定画面へ"),
                        SizedBox(width: 8,),
                        Icon(Icons.open_in_new)
                      ]
                    ),
                    onPressed: () {
                      AppSettings.openAppSettings(type: AppSettingsType.bluetooth);
                    },
                  ),
                ],
              )
            ),
          );
        }
      ).whenComplete(() {
        _isShowingModal = false;
      });
    }
  }

  @override
  void dispose() {
    _bluetoothStateSubscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return widget.child;
  }
}
