import 'dart:io';

import 'package:audio_service/audio_service.dart';
import 'package:encore_core/core/host.dart' as core;
import 'package:encore_core/data/usb_link.dart';
import 'package:encore_core/data/wear_link.dart';
import 'package:encore_core/playback/widget_sync.dart';
import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_riverpod/misc.dart' show Override;

import '../version.dart';
import 'usb_card.dart' as usb;
import 'usb_link.dart';
import 'wear_link.dart';

/// Where this app's releases are (the APKs; the desktop app has its own, d-safro/encore-desktop).
const phoneUpdateRepo = 'd-safro/encore';

const _system = MethodChannel('encore/system');

/// The phone app's pieces for encore_core (see EncoreHost there).
class PhoneHost extends core.EncoreHost {
  const PhoneHost();

  @override
  String get appVersion => encoreVersion;

  @override
  String get updateRepo => phoneUpdateRepo;

  @override
  List<Override> get overrides => [
    usbLinkProvider.overrideWith(PhoneUsbLink.new),
    // a paired Wear OS watch signs in to the same server
    wearLinkProvider.overrideWith((ref) {
      ref.watch(phoneWearLinkProvider);
    }),
  ];

  @override
  Future<void> afterPlayer(AudioHandler handler, ProviderContainer container) async {
    startWidgetSync(handler); // the home-screen widget
  }

  @override
  Widget usbCard() => const usb.UsbCard();

  /// Android keeps a notification up (a foreground service) while songs download, so they carry on with
  /// the app in the background.
  @override
  void downloading(int count) {
    _system.invokeMethod('downloading', {'count': count}).catchError((_) => null);
  }

  @override
  core.UpdateInstaller get updates => const AndroidUpdater();
}

/// Android's package installer for a downloaded APK.
class AndroidUpdater extends core.UpdateInstaller {
  const AndroidUpdater();

  @override
  Future<bool> canInstall() async {
    try {
      return await _system.invokeMethod<bool>('canInstall') ?? false;
    } catch (_) {
      return false;
    }
  }

  /// Android's "Install unknown apps" page for Encore.
  @override
  Future<void> allowInstalls() => _system.invokeMethod('allowInstalls');

  /// Opens Android's installer for the downloaded file (it asks you to confirm).
  @override
  Future<void> install(File file) => _system.invokeMethod('install', {'path': file.path});
}
