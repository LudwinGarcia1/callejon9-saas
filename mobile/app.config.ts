import { existsSync } from 'node:fs';
import type { ConfigContext, ExpoConfig } from 'expo/config';

/**
 * Extiende app.json con valores que dependen del entorno y no se versionan:
 * el projectId de EAS (necesario para obtener el token push de Expo) y el
 * google-services.json de Firebase (necesario para recibir push en Android).
 */
export default ({ config }: ConfigContext): ExpoConfig => {
  const projectId = process.env.EXPO_PUBLIC_EAS_PROJECT_ID || undefined;
  // Solo se usa si el archivo existe: sin él la app compila igual, pero Android no recibe push.
  const servicesPath = process.env.GOOGLE_SERVICES_JSON || './google-services.json';
  const googleServicesFile = servicesPath && existsSync(servicesPath) ? servicesPath : undefined;
  return {
    ...config,
    name: config.name ?? 'RestoHub Meseros',
    slug: config.slug ?? 'callejon9-mobile',
    android: { ...config.android, ...(googleServicesFile ? { googleServicesFile } : {}) },
    extra: { ...config.extra, ...(projectId ? { eas: { projectId } } : {}) },
  };
};
