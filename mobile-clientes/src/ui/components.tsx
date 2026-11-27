import type { ReactNode } from 'react';
import { ActivityIndicator, Pressable, StyleSheet, Text, View } from 'react-native';
import { colors, radius } from './theme';

interface ButtonProps {
  label: string;
  onPress: () => void;
  loading?: boolean;
  disabled?: boolean;
  variant?: 'primary' | 'secondary';
}

export function Button({ label, onPress, loading = false, disabled = false, variant = 'primary' }: ButtonProps) {
  const isPrimary = variant === 'primary';
  const inactive = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: inactive, busy: loading }}
      onPress={onPress}
      disabled={inactive}
      style={({ pressed }) => [
        styles.button,
        isPrimary ? styles.buttonPrimary : styles.buttonSecondary,
        inactive && styles.buttonDisabled,
        pressed && !inactive && styles.buttonPressed,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={isPrimary ? colors.primaryForeground : colors.foreground} />
      ) : (
        <Text style={[styles.buttonLabel, !isPrimary && styles.buttonLabelSecondary]}>{label}</Text>
      )}
    </Pressable>
  );
}

type Tone = 'error' | 'success' | 'info';

export function Banner({ tone, title, children }: { tone: Tone; title?: string; children: ReactNode }) {
  const palette = {
    error: { bg: colors.destructiveSoft, fg: colors.destructive },
    success: { bg: colors.successSoft, fg: colors.success },
    info: { bg: colors.warningSoft, fg: colors.warning },
  }[tone];
  return (
    <View
      accessibilityRole={tone === 'error' ? 'alert' : undefined}
      accessibilityLiveRegion="polite"
      style={[styles.banner, { backgroundColor: palette.bg, borderColor: palette.fg }]}
    >
      {title ? <Text style={[styles.bannerTitle, { color: palette.fg }]}>{title}</Text> : null}
      <Text style={[styles.bannerText, { color: palette.fg }]}>{children}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: 48,
    borderRadius: radius,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 16,
  },
  buttonPrimary: { backgroundColor: colors.primary },
  buttonSecondary: { backgroundColor: colors.card, borderWidth: 1, borderColor: colors.border },
  buttonDisabled: { opacity: 0.5 },
  buttonPressed: { opacity: 0.85 },
  buttonLabel: { color: colors.primaryForeground, fontSize: 16, fontWeight: '600' },
  buttonLabelSecondary: { color: colors.foreground },
  banner: { borderWidth: 1, borderRadius: radius, padding: 12, gap: 2 },
  bannerTitle: { fontSize: 15, fontWeight: '700' },
  bannerText: { fontSize: 14, lineHeight: 20 },
});
