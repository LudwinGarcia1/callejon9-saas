import { useRef, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { login } from '../api/auth';
import { ApiError } from '../api/client';
import type { LoginResponse } from '../api/types';
import { API_URL, CONFIG_ERROR, IS_SECURE_TRANSPORT } from '../config';
import { Banner, Button } from '../ui/components';
import { colors, radius } from '../ui/theme';

type Field = 'slug' | 'email' | 'password';
type FieldErrors = Partial<Record<Field, string>>;

const SLUG_PATTERN = /^[a-z0-9-]{3,80}$/;
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

/** Mismas reglas que LoginRequest en el backend, para avisar antes de enviar. */
function validate(slug: string, email: string, password: string): FieldErrors {
  const errors: FieldErrors = {};
  if (!slug) errors.slug = 'Ingresa el identificador del restaurante.';
  else if (!SLUG_PATTERN.test(slug)) errors.slug = 'Solo minúsculas, números y guiones (3 a 80).';
  if (!email) errors.email = 'Ingresa tu correo.';
  else if (!EMAIL_PATTERN.test(email)) errors.email = 'Ingresa un correo válido.';
  if (!password) errors.password = 'Ingresa tu contraseña.';
  return errors;
}

interface Props {
  /** Aviso que llega de fuera del formulario, p. ej. sesión expirada o rol sin acceso. */
  notice: string | null;
  onAuthenticated: (user: LoginResponse) => Promise<void>;
}

export function LoginScreen({ notice, onAuthenticated }: Props) {
  const [slug, setSlug] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(CONFIG_ERROR);
  const [submitting, setSubmitting] = useState(false);
  const emailRef = useRef<TextInput>(null);
  const passwordRef = useRef<TextInput>(null);

  async function handleSubmit() {
    const credentials = { slug: slug.trim().toLowerCase(), email: email.trim().toLowerCase(), password };
    const errors = validate(credentials.slug, credentials.email, credentials.password);
    setFieldErrors(errors);
    setError(null);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    // La contraseña se borra del formulario en cuanto sale hacia el servidor:
    // no vuelve a mostrarse, ni siquiera enmascarada, sea cual sea el resultado.
    setPassword('');
    try {
      const user = await login(credentials);
      await onAuthenticated(user);
    } catch (cause) {
      if (cause instanceof ApiError) {
        setError(cause.message);
        setFieldErrors(cause.fieldErrors as FieldErrors);
      } else {
        setError('Ocurrió un error inesperado. Intenta de nuevo.');
      }
      passwordRef.current?.focus();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <View style={styles.header}>
          <Text style={styles.brand}>Callejón 9</Text>
          <Text style={styles.subtitle}>App de meseros · Inicia sesión con tu cuenta del restaurante</Text>
        </View>

        <View style={styles.card}>
          {notice && !error ? <Banner tone="info">{notice}</Banner> : null}
          {error ? <Banner tone="error" title="No se pudo iniciar sesión">{error}</Banner> : null}

          <Labeled label="Restaurante" error={fieldErrors.slug}>
            <TextInput
              style={[styles.input, fieldErrors.slug && styles.inputError]}
              value={slug}
              onChangeText={setSlug}
              placeholder="mi-restaurante"
              placeholderTextColor={colors.mutedForeground}
              autoCapitalize="none"
              autoCorrect={false}
              returnKeyType="next"
              onSubmitEditing={() => emailRef.current?.focus()}
              editable={!submitting}
              accessibilityLabel="Restaurante"
            />
          </Labeled>

          <Labeled label="Correo" error={fieldErrors.email}>
            <TextInput
              ref={emailRef}
              style={[styles.input, fieldErrors.email && styles.inputError]}
              value={email}
              onChangeText={setEmail}
              placeholder="mesero@restaurante.com"
              placeholderTextColor={colors.mutedForeground}
              keyboardType="email-address"
              autoCapitalize="none"
              autoComplete="email"
              textContentType="username"
              autoCorrect={false}
              returnKeyType="next"
              onSubmitEditing={() => passwordRef.current?.focus()}
              editable={!submitting}
              accessibilityLabel="Correo"
            />
          </Labeled>

          <Labeled label="Contraseña" error={fieldErrors.password}>
            <TextInput
              ref={passwordRef}
              style={[styles.input, fieldErrors.password && styles.inputError]}
              value={password}
              onChangeText={setPassword}
              placeholder="Tu contraseña"
              placeholderTextColor={colors.mutedForeground}
              secureTextEntry
              autoCapitalize="none"
              autoComplete="current-password"
              textContentType="password"
              autoCorrect={false}
              returnKeyType="go"
              onSubmitEditing={handleSubmit}
              editable={!submitting}
              accessibilityLabel="Contraseña"
            />
          </Labeled>

          <Button label="Iniciar sesión" onPress={handleSubmit} loading={submitting} disabled={!!CONFIG_ERROR} />
        </View>

        <Text style={styles.transport}>
          {IS_SECURE_TRANSPORT ? '🔒 Conexión cifrada (HTTPS)' : '⚠️ Conexión sin cifrar (solo desarrollo)'}
          {API_URL ? `\n${API_URL.replace(/^https?:\/\//, '')}` : ''}
        </Text>
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

function Labeled({ label, error, children }: { label: string; error?: string; children: React.ReactNode }) {
  return (
    <View style={styles.field}>
      <Text style={styles.label}>{label}</Text>
      {children}
      {error ? <Text style={styles.fieldError}>{error}</Text> : null}
    </View>
  );
}

const styles = StyleSheet.create({
  flex: { flex: 1 },
  container: { flexGrow: 1, justifyContent: 'center', padding: 20, gap: 20 },
  header: { gap: 6 },
  brand: { fontSize: 32, fontWeight: '800', color: colors.foreground },
  subtitle: { fontSize: 15, color: colors.mutedForeground, lineHeight: 21 },
  card: {
    backgroundColor: colors.card,
    borderRadius: radius * 1.4,
    borderWidth: 1,
    borderColor: colors.border,
    padding: 16,
    gap: 14,
  },
  field: { gap: 6 },
  label: { fontSize: 14, fontWeight: '600', color: colors.foreground },
  input: {
    minHeight: 48,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius,
    paddingHorizontal: 12,
    fontSize: 16,
    color: colors.foreground,
    backgroundColor: '#FFFFFF',
  },
  inputError: { borderColor: colors.destructive },
  fieldError: { fontSize: 13, color: colors.destructive },
  transport: { textAlign: 'center', fontSize: 13, color: colors.mutedForeground, lineHeight: 19 },
});
