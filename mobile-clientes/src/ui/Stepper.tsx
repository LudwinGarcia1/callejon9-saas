import { Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, radius } from './theme';

interface StepperProps {
  value: number;
  onChange: (next: number) => void;
  label: string;
}

/** Botones − / + para la cantidad de un producto. */
export function Stepper({ value, onChange, label }: StepperProps) {
  if (value === 0) {
    return (
      <Pressable accessibilityRole="button" accessibilityLabel={`Agregar ${label}`} onPress={() => onChange(1)} style={styles.add}>
        <Text style={styles.addText}>Agregar</Text>
      </Pressable>
    );
  }
  return (
    <View style={styles.row}>
      <Pressable accessibilityRole="button" accessibilityLabel={`Quitar uno de ${label}`} onPress={() => onChange(value - 1)} style={styles.step}>
        <Text style={styles.stepText}>−</Text>
      </Pressable>
      <Text style={styles.value} accessibilityLabel={`${value} de ${label}`}>{value}</Text>
      <Pressable accessibilityRole="button" accessibilityLabel={`Agregar uno de ${label}`} onPress={() => onChange(value + 1)} style={styles.step}>
        <Text style={styles.stepText}>+</Text>
      </Pressable>
    </View>
  );
}

const styles = StyleSheet.create({
  add: { borderRadius: radius, borderWidth: 1, borderColor: colors.primary, paddingHorizontal: 14, minHeight: 40, justifyContent: 'center' },
  addText: { color: colors.primary, fontWeight: '600' },
  row: { flexDirection: 'row', alignItems: 'center', gap: 6 },
  step: { width: 40, height: 40, borderRadius: radius, backgroundColor: colors.primary, alignItems: 'center', justifyContent: 'center' },
  stepText: { color: colors.primaryForeground, fontSize: 20, fontWeight: '700' },
  value: { minWidth: 24, textAlign: 'center', fontSize: 16, fontWeight: '700', color: colors.foreground },
});
