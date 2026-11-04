# CAL-16: paginación de órdenes y ventas

`GET /api/v1/orders` y `GET /api/v1/sales` dejaron de devolver colecciones completas. Ahora responden páginas acotadas con un contrato común, ordenamiento en lista blanca y un tamaño máximo. Es un cambio de contrato incompatible: el backend, el frontend web y la app móvil se entregan en el mismo PR y se despliegan de forma coordinada.

## Contrato

`PageResponse<T>` (`backend/.../shared/web/PageResponse.java`):

```jsonc
{ "content": [ /* filas */ ], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3 }
```

| Parámetro | Valor | Por defecto |
|---|---|---|
| `page` | Desde 0 | `0` |
| `size` | De 1 a 100 | `20` |
| `sort` | `campo` o `campo,asc` / `campo,desc` | La fila más reciente primero |

- **Campos de orden:** en `/orders`, `openedAt`, `folio` y `total`; en `/sales`, `createdAt` y `total`. Cualquier otro campo responde `400`.
- **Desempate:** `PageQuery` agrega siempre `id` como desempate. Así, dos filas con el mismo instante no se repiten ni se saltan entre páginas.
- **Parámetros inválidos:** un `size` fuera de 1–100, una página negativa o un `sort` desconocido responden `400` Problem Detail con `title: "Paginacion invalida"`. No se usa el resolver de `Pageable` de Spring Data, porque recorta el `size` en silencio y deja ordenar por cualquier propiedad de la entidad.
- **`/orders`:** acepta `?status=` (el filtro previo) o `?open=true`, que deja solo `NEW`, `SENT` y `READY`. Los dos juntos responden `400`.
- **`/sales`:** `sales` es la página; `summary` y `paymentMix` siguen cubriendo el rango completo, que es lo que el cajero concilia.

`/users` y `/products` quedan como arreglos: el issue los dejaba condicionados a la estimación, y su tamaño lo acota el plan (usuarios) o el catálogo de un restaurante.

## Clientes

**Frontend web:**
- Mesero, caja, la barra lateral de la comanda y los contadores de navegación comparten el hook `useOpenOrders`. Pide `open=true&size=100` con la clave `["orders", { open: true }]`. Las mutaciones ya invalidaban el prefijo `["orders"]`, así que la cubren sin cambios.
- El hook expone `isTruncated` por si un restaurante llegara a tener más de 100 órdenes abiertas.
- El historial de ventas usa un paginador explícito (`PaginationBar`). Es un `nav` con nombre accesible y la posición se anuncia con `aria-live`.
- La clave de la consulta incluye rango, página y tamaño. Cambiar el rango regresa a la página 0.
- Con `keepPreviousData`, la tabla no vuelve al esqueleto al cambiar de página. La venta seleccionada conserva su ticket aunque deje de estar en la página visible.
- Administración ya no descarga todas las órdenes para sumar las del día. Lee el resumen de `/sales` de hoy, por lo que «Cobrado hoy» suma ventas cobradas. Antes sumaba el total de cualquier orden abierta hoy, incluso sin pagar o cancelada.

**App móvil:** `fetchOpenOrders()` pide `/orders?open=true&size=100` y devuelve `content`. La usa `OrderScreen` para encontrar la orden abierta de una mesa.

## Evidencia con 10 000 filas (2026-10-28)

El dataset se generó con `scripts/evidencia-paginacion-seed.sql`:
- 3 restaurantes con 10 000 órdenes cada uno: 30 abiertas y 9 970 pagadas.
- Una venta por orden pagada.

Los planes se tomaron con `scripts/evidencia-paginacion-explain.sql` como `callejon9_app`, en PostgreSQL 16.15, levantado desde el `docker-compose.yml` de la rama. Se midieron con `EXPLAIN (ANALYZE, BUFFERS)` y caché caliente.

| Consulta (restaurante `escala-a`) | Acceso | Filas leídas | Filas devueltas | Ejecución |
|---|---|---|---|---|
| `/orders?open=true&size=100` | Bitmap sobre `idx_orders_tenant_status (tenant_id, status)` | 30 | 30 | 0.25 ms |
| Conteo de abiertas | Mismo índice | 30 | 1 | 0.36 ms |
| `/orders?size=20` (todas) | Bitmap sobre `idx_orders_tenant_status` por `tenant_id` + top-N heapsort | 10 000 | 20 | 3.9 ms |
| Conteo de todas | Mismo índice | 10 000 | 1 | 1.7 ms |
| `/sales`, 30 días, `size=25` | Index Scan sobre `idx_sales_tenant_created (tenant_id, created_at DESC)` + Incremental Sort | 26 | 25 | 0.61 ms |
| Conteo de ventas del rango | Bitmap sobre `idx_sales_tenant_created` | 2 513 | 1 | 0.92 ms |

- **Aislamiento:** RLS limita lo visible a las 10 000 órdenes y 9 970 ventas del restaurante; las otras 20 000 filas de cada tabla no entran en ningún conteo.
- **Índices:** ninguna consulta usa un barrido secuencial de `orders` o `sales`. Todas entran por un índice que empieza en `tenant_id`.
- **Filas transferidas:** antes, `GET /orders` devolvía las 10 000 órdenes del restaurante para que cada pantalla filtrara las 30 abiertas. Ahora mesero, caja y móvil reciben 30, y cualquier página queda limitada a `size`.

**Pendiente para CAL-34:** el listado de *todas* las órdenes, ordenado por `opened_at`, todavía lee las 10 000 filas del restaurante y ordena en memoria (top-N, 26 kB). Un índice `(tenant_id, opened_at DESC)` lo volvería un Index Scan con `LIMIT`. Se deja a CAL-34, que según el issue revisa índices una vez fijadas las consultas reales. Ninguna pantalla pide hoy ese listado sin filtro.

Son tiempos de una máquina de desarrollo con caché caliente. No se proponen como objetivo de rendimiento; lo que se verifica es el tipo de acceso y la cantidad de filas.

## Pruebas

- **Backend:**
  - `PageQueryTest` cubre los valores por defecto, la lista blanca, la dirección y los límites de `size` y `page`.
  - `OrderPaginationTest` comprueba los metadatos, que las páginas no tengan huecos ni repetidos, el filtro de abiertas, el orden, los `400` y que los conteos excluyan otros restaurantes.
  - `SalesControllerTest` comprueba la página y que el resumen cubra el rango completo, el orden por `total`, los `400` y el `totalElements` por restaurante.
- **Frontend:** `pagination-bar.test.tsx`, `history-pagination.test.tsx` (página, regreso a 0 al cambiar el rango, selección conservada), `use-nav-counts.test.tsx` (petición `open=true&size=100`) y `query-keys.test.ts`.
- **Móvil:** `waiter.test.ts`.
