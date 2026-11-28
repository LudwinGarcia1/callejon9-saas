// Metro con los Debug IDs de Sentry: enlaza cada bundle con su source map para
// que los errores de una build de release lleguen con la línea original.
const { getSentryExpoConfig } = require('@sentry/react-native/metro');

module.exports = getSentryExpoConfig(__dirname);
