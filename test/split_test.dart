// The phone app's own pieces after the split: its update repository and version, the USB cable link
// replacing encore_core's never-plugged-in one, and the application id existing installs update.
import 'dart:io';

import 'package:encore/phone/host.dart';
import 'package:encore/phone/usb_link.dart';
import 'package:encore/version.dart';
import 'package:encore_core/core/host.dart';
import 'package:encore_core/data/updates.dart';
import 'package:encore_core/data/usb_link.dart';
import 'package:encore_core/data/wear_link.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

String read(String path) => File(path).readAsStringSync();

void main() {
  test('updates still come from d-safro/encore, and the version is the one in pubspec.yaml', () {
    const host = PhoneHost();
    expect(host.updateRepo, 'd-safro/encore');
    final pub = RegExp(r'^version: *([0-9.]+)', multiLine: true).firstMatch(read('pubspec.yaml'))!.group(1);
    expect(encoreVersion, pub);
    encoreHost = host;
    expect(updateRepo, 'd-safro/encore');
    encoreHost = const EncoreHost();
  });

  test("the phone's cable link takes over usbLinkProvider", () {
    final c = ProviderContainer(overrides: const PhoneHost().overrides);
    addTearDown(c.dispose);
    expect(c.read(usbLinkProvider.notifier), isA<PhoneUsbLink>());
    expect(c.read(usbLinkProvider), isFalse); // nothing plugged in (and no server)
  });

  test("the watch sign-in is the phone's own (it does nothing off Android)", () {
    final c = ProviderContainer(overrides: const PhoneHost().overrides);
    addTearDown(c.dispose);
    c.read(wearLinkProvider);
  });

  test('songs through the cable keep their path and login', () {
    final u = UsbLinkNotifier.viaCable(Uri.parse('https://music.example/navidrome/rest/ping?u=a&t=b'));
    expect(u.toString(), 'http://127.0.0.1:4533/navidrome/rest/ping?u=a&t=b');
  });

  test('the same application id, so installed copies update in place', () {
    expect(read('android/app/build.gradle.kts'), contains('applicationId = "dev.safro.encore"'));
  });
}
