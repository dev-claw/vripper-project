export interface Settings {
  connectionSettings: ConnectionSettings;
  downloadSettings: DownloadSettings;
  viperSettings: ViperSettings;
  systemSettings: SystemSettings;
}

export interface ConnectionSettings {
  maxConcurrentPerHost: number;
  maxGlobalConcurrent: number;
  timeout: number;
  maxAttempts: number;
}

export interface DownloadSettings {
  downloadPath: string;
  autoStart: boolean;
  autoQueueThreshold: number;
  forceOrder: boolean;
  forumSubDirectory: boolean;
  threadSubLocation: boolean;
  clearCompleted: boolean;
  appendPostId: boolean;
}

export interface ViperSettings {
  login: boolean;
  username: string;
  password: string;
  thanks: boolean;
  host: string;
  requestLimit: number;
  fetchMetadata: boolean;
  watchSubscriptions: boolean;
  watchIntervalMinutes: number;
}

export interface SystemSettings {
  tempPath: string;
  maxEventLog: number;
  enableClipboardMonitoring: boolean;
  clipboardPollingRate: number;
}
