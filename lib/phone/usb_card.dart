import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import 'package:encore_core/data/library.dart';
import 'package:encore_core/data/usb_link.dart';
import 'package:encore_core/ui/actions.dart';
import 'package:encore_core/ui/widgets.dart';

/// Downloads screen: copy music over a USB cable from the computer that runs the server.
class UsbCard extends ConsumerWidget {
  const UsbCard({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final wired = ref.watch(usbLinkProvider);
    final dl = ref.watch(downloadsProvider);
    final cs = Theme.of(context).colorScheme;
    final t = Theme.of(context).textTheme;
    if (wired) {
      final all = dl.pins.contains('library');
      return Card(
        color: cs.primaryContainer.withValues(alpha: 0.35),
        margin: const EdgeInsets.fromLTRB(16, 4, 16, 8),
        child: ListTile(
          leading: const Icon(Icons.usb_rounded),
          title: const Text('Connected to your server by cable'),
          subtitle: Text(
            dl.progress.isNotEmpty
                ? '${nSongs(dl.progress.length)} coming through the cable'
                : 'Everything goes through the cable: fast, no Wi-Fi or VPN needed',
          ),
          trailing: all
              ? null
              : FilledButton.tonal(
                  onPressed: () async {
                    final songs = (await ref.read(dbProvider).allSongs()).where((s) => !s.isLookup).toList();
                    await ref.read(downloadsProvider.notifier).pin('library', songs);
                    if (context.mounted) toast(context, 'Copying ${nSongs(songs.length)} over the cable');
                  },
                  child: const Text('Copy all'),
                ),
        ),
      );
    }
    return Card(
      margin: const EdgeInsets.fromLTRB(16, 4, 16, 8),
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        leading: const Icon(Icons.usb_rounded),
        title: const Text('Copy music over a USB cable'),
        subtitle: const Text('Much faster than Wi-Fi, from the computer that runs your server'),
        shape: const Border(),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
        expandedCrossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final (i, step) in const [
            'Once: on the phone, turn on USB debugging (Settings › About phone › tap Build number 7 times, then '
                'Settings › System › Developer options › USB debugging).',
            'Plug the phone into the computer that runs your server, and tap Allow when the phone asks.',
            'On that computer, run encore-usb (tools/encore-usb.sh in Encore\'s repository). It keeps the cable '
                'connected to the server while it runs.',
            'This card turns blue within a few seconds: tap Copy all, or download anything as usual. Everything '
                '(browsing, playing, downloads) goes through the cable while it\'s plugged in, and over the network '
                'when it isn\'t.',
          ].indexed)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(width: 22, child: Text('${i + 1}.', style: t.bodyMedium)),
                  Expanded(child: Text(step, style: t.bodyMedium?.copyWith(color: cs.onSurfaceVariant))),
                ],
              ),
            ),
          Align(
            alignment: Alignment.centerRight,
            child: TextButton(
              onPressed: () => ref.read(usbLinkProvider.notifier).check(),
              child: const Text('Check again'),
            ),
          ),
        ],
      ),
    );
  }
}
