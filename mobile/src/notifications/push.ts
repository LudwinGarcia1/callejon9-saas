import Constants from 'expo-constants';
import * as Device from 'expo-device';
import { isRunningInExpoGo } from 'expo';
import * as Notifications from 'expo-notifications';
import { Platform } from 'react-native';
import { ANDROID_CHANNEL_ID, parseRequestNotificationData, toEpochMillis, type RequestNotificationData } from './messages';

/** Resultado de intentar registrar el dispositivo en el servicio push. */
export type PushRegistration =
  | { status: 'granted'; token: string }
  | { status: 'denied'; canAskAgain: boolean }
  | { status: 'unavailable'; reason: string };

/** Una notificación ya validada, lista para mostrarse en la app. */
export interface InboxItem {
  id: string;
  title: string;
  body: string;
  data: RequestNotificationData;
  receivedAt: number;
  /** foreground: llegó con la app abierta; opened: el usuario la tocó. */
  origin: 'foreground' | 'opened';
}

/**
 * Decide qué hacer con una notificación que llega con la app en primer plano.
 * Sin este handler, iOS y Android la descartan en silencio.
 */
export function configureForegroundPresentation() {
  if (Platform.OS === 'web') return;
  Notifications.setNotificationHandler({
    handleNotification: async () => ({
      shouldShowBanner: true,
      shouldShowList: true,
      shouldPlaySound: true,
      shouldSetBadge: false,
    }),
  });
}

function resolveProjectId(): string | undefined {
  return (
    Constants.expoConfig?.extra?.eas?.projectId ??
    Constants.easConfig?.projectId ??
    process.env.EXPO_PUBLIC_EAS_PROJECT_ID ??
    undefined
  );
}

/**
 * Pide permiso y obtiene el token push de Expo.
 *
 * 1. En Android crea el canal antes de pedir permiso (Android 13+ solo muestra
 *    el diálogo si existe al menos un canal).
 * 2. Consulta el permiso actual y solo lo pide si el sistema aún lo permite.
 * 3. Con permiso, solicita el token a Expo usando el projectId de EAS.
 */
export async function registerForPushNotifications(): Promise<PushRegistration> {
  if (Platform.OS === 'web') {
    return { status: 'unavailable', reason: 'La versión web no recibe notificaciones push nativas.' };
  }

  if (Platform.OS === 'android') {
    await Notifications.setNotificationChannelAsync(ANDROID_CHANNEL_ID, {
      name: 'Seguimiento de solicitudes',
      description: 'Avisos cuando una solicitud cambia de estado',
      importance: Notifications.AndroidImportance.HIGH,
      vibrationPattern: [0, 250, 250, 250],
      lockscreenVisibility: Notifications.AndroidNotificationVisibility.PRIVATE,
    });
  }

  const current = await Notifications.getPermissionsAsync();
  let permission = current;
  if (!current.granted && current.canAskAgain) {
    permission = await Notifications.requestPermissionsAsync({
      ios: { allowAlert: true, allowBadge: true, allowSound: true },
    });
  }
  if (!permission.granted) {
    return { status: 'denied', canAskAgain: permission.canAskAgain };
  }

  // Un emulador de Android con Google Play sí recibe FCM; el simulador de iOS no es confiable para APNs.
  if (!Device.isDevice && Platform.OS === 'ios') {
    return { status: 'unavailable', reason: 'El simulador de iOS no recibe push remotos: usa un iPhone físico.' };
  }
  if (isRunningInExpoGo() && Platform.OS === 'android') {
    return {
      status: 'unavailable',
      reason: 'Expo Go en Android ya no admite push remotos (SDK 53+). Usa una development build.',
    };
  }
  const projectId = resolveProjectId();
  if (!projectId) {
    return { status: 'unavailable', reason: 'Falta EXPO_PUBLIC_EAS_PROJECT_ID en el archivo .env.' };
  }

  try {
    const { data } = await Notifications.getExpoPushTokenAsync({ projectId });
    return { status: 'granted', token: data };
  } catch (cause) {
    const detail = cause instanceof Error ? cause.message : String(cause);
    return { status: 'unavailable', reason: `No se pudo obtener el token: ${detail}` };
  }
}

/** Convierte una notificación del sistema en un elemento del buzón, o null si no es válida. */
export function toInboxItem(notification: Notifications.Notification, origin: InboxItem['origin']): InboxItem | null {
  const { content, identifier } = notification.request;
  const data = parseRequestNotificationData(content.data);
  if (!data) return null;
  return {
    id: identifier,
    title: content.title ?? 'Actualización de solicitud',
    body: content.body ?? '',
    data,
    receivedAt: toEpochMillis(notification.date),
    origin,
  };
}

interface Listeners {
  onReceived: (item: InboxItem) => void;
  onOpened: (item: InboxItem) => void;
}

/**
 * Suscribe la app a los dos eventos de expo-notifications:
 * - recepción con la app abierta (el sistema no la muestra solo);
 * - respuesta del usuario al tocar la notificación, incluso si la app estaba
 *   cerrada (getLastNotificationResponse recupera ese toque al arrancar).
 * Devuelve la función que cancela ambas suscripciones.
 */
export function subscribeToNotifications({ onReceived, onOpened }: Listeners): () => void {
  if (Platform.OS === 'web') return () => undefined;

  const received = Notifications.addNotificationReceivedListener((notification) => {
    const item = toInboxItem(notification, 'foreground');
    if (item) onReceived(item);
  });

  const handleResponse = (response: Notifications.NotificationResponse) => {
    if (response.actionIdentifier !== Notifications.DEFAULT_ACTION_IDENTIFIER) return;
    const item = toInboxItem(response.notification, 'opened');
    if (item) onOpened(item);
  };
  const responses = Notifications.addNotificationResponseReceivedListener(handleResponse);

  const coldStart = Notifications.getLastNotificationResponse();
  if (coldStart) {
    handleResponse(coldStart);
    Notifications.clearLastNotificationResponse();
  }

  return () => {
    received.remove();
    responses.remove();
  };
}
