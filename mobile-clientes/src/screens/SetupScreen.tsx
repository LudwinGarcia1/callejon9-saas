import { useState } from 'react';
import { KeyboardAvoidingView, Platform, ScrollView, StyleSheet, Text, TextInput, View } from 'react-native';
import { parseTableLink, parseTableNumber } from '../domain/tableLink';
import { Banner, Button } from '../ui/components';
import { colors, radius } from '../ui/theme';

interface SetupScreenProps {
  initialSlug: string;
  initialTable: string;
  error: string | null;
  loading: boolean;
  onSubmit: (slug: string, tableNumber: number) => void;
  onScan: () => void;
}

export function SetupScreen({ initialSlug, initialTable, error, loading, onSubmit, onScan }: SetupScreenProps) {
  const [restaurant, setRestaurant] = useState(initialSlug);
  const [table, setTable] = useState(initialTable);
  const [formError, setFormError] = useState<string | null>(null);

  function submit() {
    const ref = parseTableLink(restaurant);
    if (!ref) {
      setFormError('Escribe el código del restaurante (por ejemplo, callejon-9) o pega el enlace del QR de tu mesa.');
      return;
    }
    const tableNumber = ref.tableNumber ?? parseTableNumber(table);
    if (tableNumber === null) {
      setFormError('Escribe el número de tu mesa.');
      return;
    }
    setFormError(null);
    setRestaurant(ref.slug);
    setTable(String(tableNumber));
    onSubmit(ref.slug, tableNumber);
  }

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <Text style={styles.eyebrow}>RestoHub · Menú y pedidos</Text>
        <Text style={styles.title}>¿Dónde estás comiendo?</Text>
        <Text style={styles.lead}>
          Necesitas conexión solo la primera vez, para descargar el menú. Después puedes verlo y armar tu pedido aunque
          no tengas señal; se envía a cocina en cuanto vuelva la conexión.
        </Text>

        <Button label="Escanear el QR de la mesa" onPress={onScan} disabled={loading} />
        <Text style={styles.divider}>o escribe los datos</Text>

        <View style={styles.field}>
          <Text style={styles.label}>Restaurante</Text>
          <TextInput
            value={restaurant}
            onChangeText={setRestaurant}
            placeholder="callejon-9 o enlace del QR"
            autoCapitalize="none"
            autoCorrect={false}
            style={styles.input}
            placeholderTextColor={colors.mutedForeground}
          />
        </View>
        <View style={styles.field}>
          <Text style={styles.label}>Número de mesa</Text>
          <TextInput
            value={table}
            onChangeText={setTable}
            placeholder="4"
            keyboardType="number-pad"
            maxLength={4}
            style={styles.input}
            placeholderTextColor={colors.mutedForeground}
            onSubmitEditing={submit}
          />
        </View>

        {formError ? <Banner tone="error">{formError}</Banner> : null}
        {error ? <Banner tone="error">{error}</Banner> : null}

        <Button label="Ver el menú" variant="secondary" onPress={submit} loading={loading} />
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  container: { padding: 20, gap: 16, paddingTop: 48 },
  eyebrow: { color: colors.warning, fontWeight: '700', textTransform: 'uppercase', fontSize: 12, letterSpacing: 1 },
  title: { color: colors.foreground, fontSize: 28, fontWeight: '800' },
  lead: { color: colors.mutedForeground, fontSize: 15, lineHeight: 22 },
  field: { gap: 6 },
  divider: { textAlign: 'center', color: colors.mutedForeground, fontSize: 13 },
  label: { color: colors.foreground, fontWeight: '600' },
  input: {
    backgroundColor: colors.card,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius,
    paddingHorizontal: 12,
    minHeight: 48,
    fontSize: 16,
    color: colors.foreground,
  },
});
