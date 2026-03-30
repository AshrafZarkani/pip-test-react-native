import * as React from 'react';
import {
  ActivityIndicator,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  View,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';

import {
  FEATURED_STREAMS,
  FREE_SERIES_CATALOG,
  type StreamDescriptor,
} from './src/data/streams';
import {
  addVideoPlayerListener,
  isPictureInPictureSupported,
  openNativePlayer,
  type VideoPlayerEvent,
} from './src/native/videoPlayer';

type EventTone = 'accent' | 'muted' | 'warn';

type EventLogEntry = {
  id: string;
  label: string;
  tone: EventTone;
  timestamp: string;
};

const seriesEpisodeCount = FREE_SERIES_CATALOG.reduce(
  (count, series) => count + series.episodes.length,
  0,
);

function App() {
  return (
    <SafeAreaProvider>
      <StatusBar barStyle="dark-content" backgroundColor="#f2ede3" />
      <DemoLauncher />
    </SafeAreaProvider>
  );
}

function DemoLauncher() {
  const [events, setEvents] = React.useState<EventLogEntry[]>([]);
  const [launchingStreamId, setLaunchingStreamId] = React.useState<string | null>(
    null,
  );
  const [pipSupported, setPipSupported] = React.useState<boolean | null>(null);

  const pushEvent = React.useEffectEvent(
    (label: string, tone: EventTone = 'muted') => {
      const timestamp = new Date().toLocaleTimeString([], {
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
      });

      setEvents(previous => [
        {
          id: `${Date.now()}-${Math.random()}`,
          label,
          tone,
          timestamp,
        },
        ...previous,
      ].slice(0, 12));
    },
  );

  const handleNativeEvent = React.useEffectEvent((event: VideoPlayerEvent) => {
    switch (event.type) {
      case 'stream_changed':
        pushEvent(`Native player loading: ${event.title}`, 'accent');
        break;
      case 'player_state':
        pushEvent(
          `${event.title ?? 'Player'}: ${event.state}${
            event.isPlaying ? ' (playing)' : ''
          }`,
          'muted',
        );
        break;
      case 'pip_change':
        pushEvent(
          event.isInPictureInPicture
            ? 'Entered native Picture-in-Picture'
            : 'Returned from Picture-in-Picture',
          'accent',
        );
        break;
      case 'error':
        pushEvent(`Native error: ${event.message}`, 'warn');
        break;
      case 'activity_closed':
        pushEvent('Native player closed', 'muted');
        break;
    }
  });

  React.useEffect(() => {
    let isMounted = true;

    const subscription = addVideoPlayerListener(handleNativeEvent);

    isPictureInPictureSupported()
      .then(supported => {
        if (!isMounted) {
          return;
        }

        setPipSupported(supported);
        pushEvent(
          supported
            ? 'Android PiP is available on this device'
            : 'Android PiP is not available on this device',
          supported ? 'accent' : 'warn',
        );
      })
      .catch(error => {
        if (!isMounted) {
          return;
        }

        setPipSupported(false);
        pushEvent(
          `Support check failed: ${error instanceof Error ? error.message : 'unknown error'}`,
          'warn',
        );
      });

    return () => {
      isMounted = false;
      subscription.remove();
    };
  }, []);

  async function handleLaunch(stream: StreamDescriptor) {
    if (!stream.url.trim()) {
      pushEvent(
        `${stream.providerLabel ?? stream.title} needs the exact authorized source before it can be launched.`,
        'warn',
      );
      return;
    }

    setLaunchingStreamId(stream.id);
    pushEvent(`Requesting native playback for ${stream.title}`, 'accent');

    try {
      await openNativePlayer(stream);
    } catch (error) {
      pushEvent(
        error instanceof Error ? error.message : 'Unable to launch native player',
        'warn',
      );
    } finally {
      setLaunchingStreamId(null);
    }
  }

  return (
    <SafeAreaView style={styles.safeArea} edges={['top', 'bottom']}>
      <View style={styles.canvas}>
        <View style={styles.backgroundOrbPrimary} />
        <View style={styles.backgroundOrbSecondary} />

        <ScrollView
          contentInsetAdjustmentBehavior="automatic"
          contentContainerStyle={styles.scrollContent}>
          <View style={styles.heroCard}>
            <Text style={styles.heroEyebrow}>Android Native PiP Proof</Text>
            <Text style={styles.heroTitle}>
              React Native shell, Media3 playback, Picture-in-Picture owned by
              Android.
            </Text>
            <Text style={styles.heroBody}>
              This test app launches a dedicated native Android video activity so
              PiP is handled by Media3 and the Android activity lifecycle rather
              than by a JavaScript video view.
            </Text>

            <View style={styles.statusRow}>
              <View style={styles.statusChipPrimary}>
                <Text style={styles.statusChipPrimaryText}>
                  {FEATURED_STREAMS.length} direct streams plus {FREE_SERIES_CATALOG.length} real series
                </Text>
              </View>
              <View
                style={pipSupported ? styles.statusChipNeutral : styles.statusChipWarn}>
                <Text
                  style={
                    pipSupported
                      ? styles.statusChipNeutralText
                      : styles.statusChipWarnText
                  }>
                  {pipSupported === null
                    ? 'Checking PiP support'
                    : pipSupported
                      ? 'PiP supported'
                      : 'PiP unavailable'}
                </Text>
              </View>
            </View>
          </View>

          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>Quick Streams</Text>
            <Text style={styles.sectionSubtitle}>
              Direct launch titles for fast HLS and MP4 validation against the native Android player.
            </Text>
          </View>

          {FEATURED_STREAMS.map(stream => {
            const isLaunching = launchingStreamId === stream.id;

            return (
              <Pressable
                key={stream.id}
                style={({ pressed }) => [
                  styles.streamCard,
                  pressed ? styles.streamCardPressed : null,
                ]}
                onPress={() => handleLaunch(stream)}>
                <View style={styles.streamCardTopRow}>
                  <View style={styles.streamBadge}>
                    <Text style={styles.streamBadgeText}>
                      {stream.providerLabel ?? 'Native Activity'}
                    </Text>
                  </View>
                  <Text style={styles.streamMime}>{stream.mimeType}</Text>
                </View>

                <Text style={styles.streamTitle}>{stream.title}</Text>
                <Text style={styles.streamDescription}>{stream.description}</Text>

                <View style={styles.streamMetaRow}>
                  <Text style={styles.streamMetaText}>
                    DRM fields wired in the contract: {stream.drmScheme ?? 'none for demo'}
                  </Text>
                </View>

                <View style={styles.streamFooterRow}>
                  <Text style={styles.streamFooterText}>Launch native player</Text>
                  {isLaunching ? (
                    <ActivityIndicator color="#17313e" />
                  ) : (
                    <Text style={styles.streamFooterArrow}>Open</Text>
                  )}
                </View>
              </Pressable>
            );
          })}

          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>Series</Text>
            <Text style={styles.sectionSubtitle}>
              The free long-form content is grouped as a proper series with a season label,
              metadata, and episode-level playback. {seriesEpisodeCount} episodes are currently loaded.
            </Text>
          </View>

          {FREE_SERIES_CATALOG.map(series => (
            <View key={series.id} style={styles.seriesCard}>
              <View style={styles.seriesBanner}>
                <View>
                  <Text style={styles.seriesEyebrow}>{series.providerLabel}</Text>
                  <Text style={styles.seriesTitle}>{series.title}</Text>
                </View>
                <Text style={styles.seriesReleaseText}>{series.releaseLabel}</Text>
              </View>

              <Text style={styles.seriesSynopsis}>{series.synopsis}</Text>

              <View style={styles.seriesMetaRow}>
                <View style={styles.seriesMetaChip}>
                  <Text style={styles.seriesMetaChipText}>{series.genreLabel}</Text>
                </View>
                <View style={styles.seriesMetaChip}>
                  <Text style={styles.seriesMetaChipText}>{series.seasonLabel}</Text>
                </View>
                <View style={styles.seriesMetaChip}>
                  <Text style={styles.seriesMetaChipText}>
                    {series.episodes.length} episodes
                  </Text>
                </View>
              </View>

              <View style={styles.episodeList}>
                {series.episodes.map(episode => {
                  const isLaunching = launchingStreamId === episode.id;

                  return (
                    <Pressable
                      key={episode.id}
                      style={({ pressed }) => [
                        styles.episodeCard,
                        pressed ? styles.episodeCardPressed : null,
                      ]}
                      onPress={() => handleLaunch(episode)}>
                      <View style={styles.episodeHeaderRow}>
                        <Text style={styles.episodeCode}>
                          S{episode.seasonNumber.toString().padStart(2, '0')} E
                          {episode.episodeNumber.toString().padStart(2, '0')}
                        </Text>
                        <Text style={styles.episodeRuntime}>{episode.runtimeLabel}</Text>
                      </View>

                      <Text style={styles.episodeTitle}>{episode.title}</Text>
                      <Text style={styles.episodeDescription}>{episode.description}</Text>

                      <View style={styles.episodeFooterRow}>
                        <Text style={styles.episodeFooterText}>Play episode</Text>
                        {isLaunching ? (
                          <ActivityIndicator color="#17313e" />
                        ) : (
                          <Text style={styles.episodeFooterArrow}>Watch</Text>
                        )}
                      </View>
                    </Pressable>
                  );
                })}
              </View>
            </View>
          ))}

          <View style={styles.sectionHeader}>
            <Text style={styles.sectionTitle}>Event Log</Text>
            <Text style={styles.sectionSubtitle}>
              Native playback and PiP state changes are pushed back into JavaScript.
            </Text>
          </View>

          <View style={styles.logPanel}>
            {events.length === 0 ? (
              <Text style={styles.emptyLogText}>Waiting for native events.</Text>
            ) : (
              events.map(event => (
                <View key={event.id} style={styles.logRow}>
                  <Text
                    style={[
                      styles.logLabel,
                      event.tone === 'accent'
                        ? styles.logLabelAccent
                        : event.tone === 'warn'
                          ? styles.logLabelWarn
                          : styles.logLabelMuted,
                    ]}>
                    {event.label}
                  </Text>
                  <Text style={styles.logTimestamp}>{event.timestamp}</Text>
                </View>
              ))
            )}
          </View>
        </ScrollView>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#f2ede3',
  },
  canvas: {
    flex: 1,
    backgroundColor: '#f2ede3',
  },
  backgroundOrbPrimary: {
    position: 'absolute',
    top: -48,
    right: -24,
    width: 196,
    height: 196,
    borderRadius: 98,
    backgroundColor: '#dce8cf',
  },
  backgroundOrbSecondary: {
    position: 'absolute',
    bottom: 120,
    left: -52,
    width: 144,
    height: 144,
    borderRadius: 72,
    backgroundColor: '#e9c6a4',
  },
  scrollContent: {
    paddingHorizontal: 20,
    paddingTop: 20,
    paddingBottom: 32,
  },
  heroCard: {
    backgroundColor: '#17313e',
    borderRadius: 28,
    padding: 24,
    marginBottom: 22,
  },
  heroEyebrow: {
    color: '#dce8cf',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 1.1,
    textTransform: 'uppercase',
    marginBottom: 14,
  },
  heroTitle: {
    color: '#fcf8ef',
    fontSize: 31,
    lineHeight: 36,
    fontWeight: '700',
    marginBottom: 14,
  },
  heroBody: {
    color: '#d7e2e8',
    fontSize: 16,
    lineHeight: 24,
    marginBottom: 18,
  },
  statusRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  statusChipPrimary: {
    backgroundColor: '#dce8cf',
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 10,
    marginRight: 10,
    marginBottom: 10,
  },
  statusChipPrimaryText: {
    color: '#17313e',
    fontWeight: '700',
  },
  statusChipNeutral: {
    backgroundColor: '#f0e4d5',
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 10,
    marginBottom: 10,
  },
  statusChipNeutralText: {
    color: '#604733',
    fontWeight: '700',
  },
  statusChipWarn: {
    backgroundColor: '#f7d9cc',
    borderRadius: 999,
    paddingHorizontal: 14,
    paddingVertical: 10,
    marginBottom: 10,
  },
  statusChipWarnText: {
    color: '#973b1b',
    fontWeight: '700',
  },
  sectionHeader: {
    marginBottom: 12,
  },
  sectionTitle: {
    color: '#1d2528',
    fontSize: 24,
    fontWeight: '700',
    marginBottom: 4,
  },
  sectionSubtitle: {
    color: '#5a646a',
    fontSize: 15,
    lineHeight: 22,
  },
  streamCard: {
    backgroundColor: '#fcf8ef',
    borderRadius: 24,
    padding: 20,
    marginBottom: 16,
    borderWidth: 1,
    borderColor: '#eadfce',
  },
  streamCardPressed: {
    transform: [{ scale: 0.99 }],
    opacity: 0.96,
  },
  seriesCard: {
    backgroundColor: '#17313e',
    borderRadius: 28,
    padding: 20,
    marginBottom: 18,
  },
  seriesBanner: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 14,
  },
  seriesEyebrow: {
    color: '#dce8cf',
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.7,
    marginBottom: 8,
  },
  seriesTitle: {
    color: '#fcf8ef',
    fontSize: 28,
    lineHeight: 32,
    fontWeight: '700',
  },
  seriesReleaseText: {
    color: '#b9cad4',
    fontSize: 12,
    fontWeight: '700',
  },
  seriesSynopsis: {
    color: '#d7e2e8',
    fontSize: 15,
    lineHeight: 23,
    marginBottom: 14,
  },
  seriesMetaRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginBottom: 16,
  },
  seriesMetaChip: {
    backgroundColor: '#274856',
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginRight: 10,
    marginBottom: 10,
  },
  seriesMetaChipText: {
    color: '#e4edf0',
    fontSize: 12,
    fontWeight: '700',
  },
  episodeList: {
    gap: 12,
  },
  episodeCard: {
    backgroundColor: '#fcf8ef',
    borderRadius: 22,
    padding: 18,
  },
  episodeCardPressed: {
    transform: [{ scale: 0.99 }],
    opacity: 0.96,
  },
  episodeHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 10,
  },
  episodeCode: {
    color: '#5b7b4e',
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 0.8,
    textTransform: 'uppercase',
  },
  episodeRuntime: {
    color: '#6d5950',
    fontSize: 12,
    fontWeight: '700',
  },
  episodeTitle: {
    color: '#1d2528',
    fontSize: 20,
    lineHeight: 26,
    fontWeight: '700',
    marginBottom: 8,
  },
  episodeDescription: {
    color: '#48545a',
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 12,
  },
  episodeFooterRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    borderTopWidth: 1,
    borderTopColor: '#efe5d7',
    paddingTop: 12,
  },
  episodeFooterText: {
    color: '#17313e',
    fontSize: 15,
    fontWeight: '700',
  },
  episodeFooterArrow: {
    color: '#17313e',
    fontSize: 14,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 0.7,
  },
  streamCardTopRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 14,
  },
  streamBadge: {
    backgroundColor: '#e6efdc',
    borderRadius: 999,
    paddingHorizontal: 12,
    paddingVertical: 8,
  },
  streamBadgeText: {
    color: '#305262',
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 0.6,
  },
  streamMime: {
    color: '#56636a',
    fontSize: 12,
    fontWeight: '700',
  },
  streamTitle: {
    color: '#1d2528',
    fontSize: 21,
    fontWeight: '700',
    marginBottom: 8,
  },
  streamDescription: {
    color: '#48545a',
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 12,
  },
  streamMetaRow: {
    borderTopWidth: 1,
    borderTopColor: '#efe5d7',
    paddingTop: 12,
    marginBottom: 14,
  },
  streamMetaText: {
    color: '#6d5950',
    fontSize: 13,
    lineHeight: 20,
  },
  streamFooterRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  streamFooterText: {
    color: '#17313e',
    fontSize: 15,
    fontWeight: '700',
  },
  streamFooterArrow: {
    color: '#17313e',
    fontSize: 14,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 0.7,
  },
  logPanel: {
    backgroundColor: '#fffaf2',
    borderRadius: 24,
    padding: 18,
    borderWidth: 1,
    borderColor: '#eadfce',
  },
  emptyLogText: {
    color: '#6e6761',
    fontSize: 15,
  },
  logRow: {
    borderBottomWidth: 1,
    borderBottomColor: '#efe5d7',
    paddingVertical: 12,
  },
  logLabel: {
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 4,
  },
  logLabelAccent: {
    color: '#17313e',
    fontWeight: '700',
  },
  logLabelMuted: {
    color: '#42525a',
  },
  logLabelWarn: {
    color: '#9c3f24',
    fontWeight: '700',
  },
  logTimestamp: {
    color: '#7e7368',
    fontSize: 12,
  },
});

export default App;
