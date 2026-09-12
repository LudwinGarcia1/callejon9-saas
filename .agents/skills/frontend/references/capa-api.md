# Capa API cliente

`frontend/src/lib/api.ts` envuelve `fetch`, serializa JSON, incluye credenciales y convierte RFC 7807 a `ApiError`. En navegador usa base vacía; en servidor usa `BACKEND_ORIGIN`.

`endpoints.ts` concentra constructores de rutas. `query-keys.ts` concentra claves e incluye parámetros que cambian resultados. `types.ts` modela requests/responses.

Al agregar una consulta:

1. confirma controller, método, roles y forma del DTO;
2. agrega endpoint y tipos centrales;
3. define una query key estable con todos los parámetros;
4. maneja `ApiError` y estados de UI;
5. invalida solo recursos afectados por mutaciones;
6. verifica que no se exponga token ni `tenant_id` autoritativo.
