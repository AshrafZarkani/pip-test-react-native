import { NativeEventEmitter, NativeModules, Platform } from 'react-native';

import type { StreamDescriptor } from '../data/streams';

type NativeVideoPlayerModule = {
  openPlayer(stream: StreamDescriptor): Promise<boolean>;
  isPictureInPictureSupported(): Promise<boolean>;
  addListener(eventName: string): void;
  removeListeners(count: number): void;
};

type RawVideoPlayerEvent = {
  type?: string;
  streamId?: string;
  title?: string;
  state?: string;
  isPlaying?: boolean;
  isInPictureInPicture?: boolean;
  message?: string;
};

export type VideoPlayerEvent =
  | {
      type: 'stream_changed';
      streamId: string;
      title: string;
    }
  | {
      type: 'player_state';
      state: string;
      isPlaying: boolean;
      streamId?: string;
      title?: string;
    }
  | {
      type: 'pip_change';
      isInPictureInPicture: boolean;
    }
  | {
      type: 'error';
      message: string;
    }
  | {
      type: 'activity_closed';
      streamId?: string;
      title?: string;
    };

export type VideoPlayerSubscription = {
  remove: () => void;
};

const EVENT_NAME = 'VideoPlayerEvent';
const LINKING_ERROR =
  'VideoPlayerModule is unavailable. Build the Android app after the native module is added.';

const nativeModule =
  NativeModules.VideoPlayerModule as NativeVideoPlayerModule | undefined;

const nativeEventEmitter = nativeModule
  ? new NativeEventEmitter(nativeModule as never)
  : null;

export async function openNativePlayer(
  stream: StreamDescriptor,
): Promise<boolean> {
  if (Platform.OS !== 'android') {
    throw new Error('This demo launches a native Android player activity only.');
  }

  if (!nativeModule) {
    throw new Error(LINKING_ERROR);
  }

  return nativeModule.openPlayer(stream);
}

export async function isPictureInPictureSupported(): Promise<boolean> {
  if (Platform.OS !== 'android') {
    return false;
  }

  if (!nativeModule) {
    throw new Error(LINKING_ERROR);
  }

  return nativeModule.isPictureInPictureSupported();
}

export function addVideoPlayerListener(
  listener: (event: VideoPlayerEvent) => void,
): VideoPlayerSubscription {
  if (!nativeEventEmitter) {
    return {
      remove() {},
    };
  }

  const subscription = nativeEventEmitter.addListener(
    EVENT_NAME,
    (event: RawVideoPlayerEvent) => {
      listener(normalizeEvent(event));
    },
  );

  return {
    remove() {
      subscription.remove();
    },
  };
}

function normalizeEvent(event: RawVideoPlayerEvent): VideoPlayerEvent {
  switch (event.type) {
    case 'stream_changed':
      return {
        type: 'stream_changed',
        streamId: event.streamId ?? 'unknown',
        title: event.title ?? 'Untitled stream',
      };
    case 'pip_change':
      return {
        type: 'pip_change',
        isInPictureInPicture: Boolean(event.isInPictureInPicture),
      };
    case 'error':
      return {
        type: 'error',
        message: event.message ?? 'Unknown native playback error',
      };
    case 'activity_closed':
      return {
        type: 'activity_closed',
        streamId: event.streamId,
        title: event.title,
      };
    case 'player_state':
    default:
      return {
        type: 'player_state',
        state: event.state ?? 'unknown',
        isPlaying: Boolean(event.isPlaying),
        streamId: event.streamId,
        title: event.title,
      };
  }
}