export const environment = {
  production: true,
  localhost: `${window.location.protocol}//${window.location.host}`,
  ws: `${window.location.protocol === 'http:' ? 'ws:' : 'wss:'}//${window.location.host}`,
  version: '3.6.0'
};
