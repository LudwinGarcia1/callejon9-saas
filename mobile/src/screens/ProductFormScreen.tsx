import { useEffect, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { createProduct, fetchCategories } from '../api/catalog';
import { ApiError } from '../api/client';
import type { Category, Product } from '../api/types';
import { Banner, Button } from '../ui/components';
import { colors, radius } from '../ui/theme';

type Field = 'name' | 'price' | 'description';
type FieldErrors = Partial<Record<Field, string>>;

const NAME_MAX = 160;
const DESCRIPTION_MAX = 500;
/** numeric(10,2) en PostgreSQL: hasta 8 enteros y 2 decimales. */
const PRICE_PATTERN = /^\d{1,8}([.,]\d{1,2})?$/;

/** Mismas reglas que CreateProductRequest en el backend, para avisar antes de enviar. */
function validate(name: string, price: string, description: string): FieldErrors {
  const errors: FieldErrors = {};
  if (!name) errors.name = 'El nombre es obligatorio.';
  else if (name.length > NAME_MAX) errors.name = `Máximo ${NAME_MAX} caracteres.`;
  if (!price) errors.price = 'El precio es obligatorio.';
  else if (!PRICE_PATTERN.test(price)) errors.price = 'Escribe un precio válido, p. ej. 45 o 45.50.';
  if (description.length > DESCRIPTION_MAX) errors.description = `Máximo ${DESCRIPTION_MAX} caracteres.`;
  return errors;
}

interface Props {
  onCancel: () => void;
  onCreated: (product: Product) => void;
}

/** Registro de información: formulario que envía POST /api/v1/products. */
export function ProductFormScreen({ onCancel, onCreated }: Props) {
  const [name, setName] = useState('');
  const [price, setPrice] = useState('');
  const [description, setDescription] = useState('');
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [categories, setCategories] = useState<Category[]>([]);
  const [categoriesError, setCategoriesError] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  /** Al corregir un campo, su aviso desaparece sin esperar al siguiente envío. */
  function edit(field: Field, setter: (value: string) => void) {
    return (value: string) => {
      setter(value);
      setFieldErrors((current) => ({ ...current, [field]: undefined }));
    };
  }

  useEffect(() => {
    fetchCategories()
      .then((data) => setCategories([...data].sort((a, b) => a.sortOrder - b.sortOrder)))
      .catch(() => setCategoriesError(true));
  }, []);

  async function handleSubmit() {
    const values = { name: name.trim(), price: price.trim(), description: description.trim() };
    const errors = validate(values.name, values.price, values.description);
    setFieldErrors(errors);
    setError(null);
    if (Object.keys(errors).length > 0) return;

    setSubmitting(true);
    try {
      const product = await createProduct({
        name: values.name,
        price: Number(values.price.replace(',', '.')),
        description: values.description || undefined,
        categoryId: categoryId ?? undefined,
      });
      onCreated(product);
    } catch (cause) {
      if (cause instanceof ApiError) {
        setError(cause.message);
        setFieldErrors(cause.fieldErrors as FieldErrors);
      } else {
        setError('Ocurrió un error inesperado. Intenta de nuevo.');
      }
      setSubmitting(false);
    }
  }

  return (
    <KeyboardAvoidingView style={styles.flex} behavior={Platform.OS === 'ios' ? 'padding' : undefined}>
      <ScrollView contentContainerStyle={styles.container} keyboardShouldPersistTaps="handled">
        <View style={styles.header}>
          <Pressable accessibilityRole="button" onPress={onCancel} hitSlop={8} disabled={submitting}>
            <Text style={styles.back}>‹ Menú</Text>
          </Pressable>
          <Text style={styles.title}>Nuevo producto</Text>
          <Text style={styles.meta}>Los campos con * son obligatorios.</Text>
        </View>

        <View style={styles.card}>
          {error ? (
            <Banner tone="error" title="El producto no se registró">
              {error}
            </Banner>
          ) : null}

          <Labeled label="Nombre *" error={fieldErrors.name}>
            <TextInput
              style={[styles.input, fieldErrors.name && styles.inputError]}
              value={name}
              onChangeText={edit('name', setName)}
              placeholder="Taco de bistec"
              placeholderTextColor={colors.mutedForeground}
              maxLength={NAME_MAX + 20}
              editable={!submitting}
              accessibilityLabel="Nombre"
            />
          </Labeled>

          <Labeled label="Precio (MXN) *" error={fieldErrors.price}>
            <TextInput
              style={[styles.input, fieldErrors.price && styles.inputError]}
              value={price}
              onChangeText={edit('price', setPrice)}
              placeholder="45.00"
              placeholderTextColor={colors.mutedForeground}
              keyboardType="decimal-pad"
              editable={!submitting}
              accessibilityLabel="Precio"
            />
          </Labeled>

          <View style={styles.field}>
            <Text style={styles.label}>Categoría</Text>
            {categoriesError ? (
              <Text style={styles.meta}>No se pudieron cargar las categorías; puedes guardarlo sin categoría.</Text>
            ) : (
              <View style={styles.chips}>
                {categories.map((category) => {
                  const selected = category.id === categoryId;
                  return (
                    <Pressable
                      key={category.id}
                      accessibilityRole="button"
                      accessibilityState={{ selected }}
                      onPress={() => setCategoryId(selected ? null : category.id)}
                      disabled={submitting}
                      style={[styles.chip, selected && styles.chipSelected]}
                    >
                      <Text style={[styles.chipLabel, selected && styles.chipLabelSelected]}>{category.name}</Text>
                    </Pressable>
                  );
                })}
              </View>
            )}
          </View>

          <Labeled label="Descripción" error={fieldErrors.description}>
            <TextInput
              style={[styles.input, styles.multiline, fieldErrors.description && styles.inputError]}
              value={description}
              onChangeText={edit('description', setDescription)}
              placeholder="Opcional"
              placeholderTextColor={colors.mutedForeground}
              multiline
              editable={!submitting}
              accessibilityLabel="Descripción"
            />
          </Labeled>

          <Button label="Registrar producto" onPress={handleSubmit} loading={submitting} />
        </View>
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
  container: { padding: 16, gap: 16 },
  header: { gap: 4 },
  back: { fontSize: 16, fontWeight: '600', color: colors.mutedForeground },
  title: { fontSize: 26, fontWeight: '800', color: colors.foreground },
  meta: { fontSize: 14, color: colors.mutedForeground },
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
  multiline: { minHeight: 80, paddingTop: 12, textAlignVertical: 'top' },
  inputError: { borderColor: colors.destructive },
  fieldError: { fontSize: 13, color: colors.destructive },
  chips: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  chip: {
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: '#FFFFFF',
  },
  chipSelected: { backgroundColor: colors.primary, borderColor: colors.primary },
  chipLabel: { fontSize: 14, color: colors.foreground },
  chipLabelSelected: { color: colors.primaryForeground, fontWeight: '600' },
});
