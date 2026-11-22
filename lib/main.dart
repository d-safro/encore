import 'package:encore_core/run.dart';

import 'phone/host.dart';

/// Encore for Android. Everything both apps share is in encore_core; PhoneHost adds the phone's own
/// pieces (the USB cable, Android's download notification and installer, the home-screen widget).
Future<void> main(List<String> args) => runEncore(const PhoneHost(), args);
