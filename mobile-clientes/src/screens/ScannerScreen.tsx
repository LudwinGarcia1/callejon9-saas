import { CameraView, useCameraPermissions } from 'expo-camera';
import { useRef, useState } from 'react';
import { StyleSheet, Text, View } from 'react-native';
import { parseTableLink, type TableRef } from '../domain/tableLink';
import { Banner, Button } from '../ui/components';
import { colors, radius } from '../ui/theme';

interface ScannerScreenProps {
  onScanned: (ref: TableRef) => void;
  onCancel: () => void;
}

/**
 * Lee el QR impreso en la mesa (https://…/m/{slug}/{mesa}). La cámara solo se
 * usa mientras esta pantalla está abierta y no se guarda ninguna imagen.
 */
export function ScannerScreen({ onScanned, onCancel }: ScannerScreenProps) {
  const [permission, requestPermission] = useCameraPermissions();
  const [error, setError] = useState<string | null>(null);
  // La cámara entrega el mismo código varias veces por segundo: solo se atiende el primero válido.
  const handled = useRef(false);
  const lastInvalid = useRef<string | null>(null);

  function handleScan(data: string) {
    if (handled.current) return;
    const ref = parseTableLink(data);
    if (ref?.tableNumber) {
      handled.current = true;
      onScanned(ref);
      return;
    }
    if (lastInvalid.current !== data) {
      lastInvalid.current = data;
      setError(
        ref
          ? 'Este QR es del restaurante, pero no indica la mesa. Escanea el QR de tu mesa.'
          : 'Este QR no es de una mesa de RestoHub.',
      );
    }
  }

  if (!permission) {
    return <View style={styles.container} />;
  }

  if (!permission.granted) {
    return (
      <View style={[styles.container, styles.padded]}>
        <Text style={styles.title}>Escanear el QR de la mesa</Text>
        <Text style={styles.lead}>
          Necesitamos la cámara solo para leer el código. No tomamos fotos ni guardamos imágenes.
        </Text>
        {permission.canAskAgain ? (
          <Button label="Permitir cámara" onPress={() => void requestPermission()} />
        ) : (
          <Banner tone="error">
            El permiso de cámara está bloqueado. Actívalo en Ajustes del teléfono o escribe el código del restaurante a mano.
          </Banner>
        )}
        <Button label="Volver" variant="secondary" onPress={onCancel} />
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <CameraView
        style={StyleSheet.absoluteFill}
        facing="back"
        barcodeScannerSettings={{ barcodeTypes: ['qr'] }}
        onBarcodeScanned={({ data }) => handleScan(data)}
      />
      <View style={styles.overlay} pointerEvents="box-none">
        <Text style={styles.hint}>Apunta al QR de tu mesa</Text>
        <View style={styles.frame} />
        <View style={styles.bottom}>
          {error ? <Banner tone="error">{error}</Banner> : null}
          <Button label="Cancelar" variant="secondary" onPress={onCancel} />
        </View>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },
  padded: { padding: 20, paddingTop: 48, gap: 16 },
  title: { color: colors.foreground, fontSize: 24, fontWeight: '800' },
  lead: { color: colors.mutedForeground, fontSize: 15, lineHeight: 22 },
  overlay: { flex: 1, justifyContent: 'space-between', alignItems: 'center', padding: 20 },
  hint: {
    marginTop: 24,
    color: '#FFFFFF',
    fontSize: 17,
    fontWeight: '700',
    backgroundColor: 'rgba(0,0,0,0.55)',
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 999,
    overflow: 'hidden',
  },
  frame: { width: 240, height: 240, borderWidth: 3, borderColor: '#FFFFFF', borderRadius: radius * 2 },
  bottom: { alignSelf: 'stretch', gap: 10 },
});
