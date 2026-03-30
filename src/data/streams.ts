export type StreamProvider =
  | 'public-demo'
  | 'free-series';

export type StreamDescriptor = {
  id: string;
  title: string;
  description: string;
  url: string;
  mimeType: string;
  provider?: StreamProvider;
  providerLabel?: string;
  drmScheme?: 'widevine' | 'playready' | 'clearkey';
  drmLicenseUrl?: string;
  headers?: Record<string, string>;
};

export type EpisodeDescriptor = StreamDescriptor & {
  seriesId: string;
  seriesTitle: string;
  seasonNumber: number;
  episodeNumber: number;
  runtimeLabel: string;
};

export type SeriesDescriptor = {
  id: string;
  title: string;
  synopsis: string;
  providerLabel: string;
  releaseLabel: string;
  genreLabel: string;
  seasonLabel: string;
  episodes: EpisodeDescriptor[];
};

export const FEATURED_STREAMS: StreamDescriptor[] = [
  {
    id: 'apple-bipbop-ts',
    title: 'Apple BipBop Advanced Example',
    description:
      'A public HLS VOD sample with multiple renditions. This is useful for verifying stable playback, Home-button PiP entry, and seamless return to fullscreen.',
    url: 'https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8',
    mimeType: 'application/x-mpegURL',
    provider: 'public-demo',
    providerLabel: 'Public Demo',
  },
  {
    id: 'mux-test-hls',
    title: 'Mux Test HLS Stream',
    description:
      'A second public HLS test asset that currently responds reliably and is useful for switching streams into the same native player activity.',
    url: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
    mimeType: 'application/x-mpegURL',
    provider: 'public-demo',
    providerLabel: 'Public Demo',
  },
];

const LUCY_SHOW_EPISODES: EpisodeDescriptor[] = [
  {
    id: 'lucy-show-little-old-lucy',
    title: 'Little Old Lucy',
    description:
      'Lucy tries to prove she can still pass for elderly and spins the whole situation into classic sitcom chaos.',
    url: 'https://archive.org/download/TheLucyShowLittleOldLucy/The%20Lucy%20Show%20-%20Little%20Old%20Lucy.mp4',
    mimeType: 'video/mp4',
    provider: 'free-series',
    providerLabel: 'Free Series',
    seriesId: 'the-lucy-show',
    seriesTitle: 'The Lucy Show',
    seasonNumber: 1,
    episodeNumber: 1,
    runtimeLabel: '25 min',
  },
  {
    id: 'lucy-show-palm-springs',
    title: 'Lucy and Carol in Palm Springs',
    description:
      'Lucy and Carol chase a getaway in Palm Springs and end up with a bigger mess than the holiday they planned.',
    url: 'https://archive.org/download/TheLucyShowLittleOldLucy/The%20Lucy%20Show%20-%20Lucy%20and%20Carol%20in%20Palm%20Springs.mp4',
    mimeType: 'video/mp4',
    provider: 'free-series',
    providerLabel: 'Free Series',
    seriesId: 'the-lucy-show',
    seriesTitle: 'The Lucy Show',
    seasonNumber: 1,
    episodeNumber: 2,
    runtimeLabel: '25 min',
  },
];

export const FREE_SERIES_CATALOG: SeriesDescriptor[] = [
  {
    id: 'the-lucy-show',
    title: 'The Lucy Show',
    synopsis:
      'A classic sitcom presented here as an actual series entry with grouped episodes, season framing, and episode-level playback instead of separate unrelated links.',
    providerLabel: 'Free Series',
    releaseLabel: '1962 to 1968',
    genreLabel: 'Sitcom',
    seasonLabel: 'Season 1 Sampler',
    episodes: LUCY_SHOW_EPISODES,
  },
];

export const DEMO_STREAMS: StreamDescriptor[] = [
  ...FEATURED_STREAMS,
  ...LUCY_SHOW_EPISODES,
];