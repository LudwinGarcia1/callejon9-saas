# Pipeline de pruebas UT y SIT para demostración

`ci/Jenkinsfile.pruebas-demo` corre, en un solo job de Jenkins, las pruebas cuya evidencia está en `docs/pruebas/`:

| Etapa | Pruebas | Contra qué |
|---|---|---|
| UT · backend | `ClientIpTest`, `RefreshTokenSessionExpiryTest` (CAL-120); `OrderServiceItemsTest`, `OrderRequestLimitsTest` (CAL-125) | Código del commit, sin base de datos |
| UT · frontend | `login-form.test.tsx` (CAL-120), `order-cart.test.tsx` (CAL-125) | Código del commit |
| UT · app móvil | Suite completa de `mobile/` (CAL-126) | Código del commit |
| SIT · CAL-138 | Abrir, agregar y enviar a cocina; 403 de otro mesero; 400 con cantidad 100 | Frontend y API de Azure |
| SIT · CAL-139 | Paridad web: mesas y productos que ve el mesero | Frontend y API de Azure |

**Fuera de la pipeline:**

- **CAL-133:** sus casos provocan logins fallidos y, mientras CAL-167 siga abierto, bloquean el login de todo el entorno durante 15 minutos.
- **Caso «cocina» de CAL-139:** necesita enviar el pedido desde el teléfono.

Las SIT corren los mismos scripts de la evidencia (`docs/pruebas/sit/*/playwright/`) dentro de la imagen oficial `mcr.microsoft.com/playwright:v1.56.1-noble`, y escriben en `salida-sit/`, que se archiva como artefacto del build. No tocan la carpeta de evidencia.

## Qué decide si una SIT pasó

Los scripts de evidencia solo registran lo que ocurre. `verificar.mjs` revisa las respuestas guardadas y falla si alguna no es la esperada:

| Comprobación | Esperado |
|---|---|
| CAL-138 C01: abrir orden, agregar productos y enviar a cocina | 201 y dos 200 |
| CAL-138 C02: otro mesero agrega o cancela desde la interfaz | Dos 403 y ningún 2xx |
| CAL-138 C02: otro mesero envía a cocina por la API | 403 |
| CAL-138 C03: una línea con cantidad 100 | 400 |
| CAL-139 C02: mesas y productos por el proxy | 200 |

## Limpieza del entorno

Cada corrida abre dos órdenes en el restaurante `pruebas`:

- **Orden B:** la cancela la etapa `limpieza` del script de evidencia.
- **Orden A:** la cancela `cerrar-orden-a.mjs` como administrador, porque el caso C01 la deja enviada a cocina.

La limpieza corre aunque falle algún caso, así que las mesas quedan como estaban. `casos` necesita **dos mesas libres**: si el job falla con «Se necesitan dos mesas libres», alguien dejó órdenes abiertas. Revisa el tablero antes de cancelar órdenes ajenas.

## Puesta en marcha en Jenkins

1. **Credencial.** Las SIT leen la contraseña de las cuentas de prueba de la credencial `c9-test-password` (Secret text). Con el plugin de Key Vault se crea así:

   ```powershell
   az keyvault secret set --vault-name <vault> --name c9-test-password --value <contraseña> --tags type=secret-text
   ```

   La contraseña no se escribe en el repositorio ni en la configuración del job.
2. **Job.** *New Item → Pipeline*, nombre `restohub-pruebas-demo`. En *Pipeline → Definition* elige *Pipeline script from SCM*: Git, el repositorio, rama `*/main` y *Script Path* `ci/Jenkinsfile.pruebas-demo`.
3. **Antes de cada corrida con SIT**, enciende la API:

   ```powershell
   .\scripts\azure-backend-power.ps1 -Action Start
   ```

   La etapa «API de Azure encendida» espera hasta 5 minutos a que responda `UP` y, si no, falla con ese aviso.
4. **Ejecuta** *Build with Parameters*. Con `RUN_SIT` desmarcado solo corren las UT, sin Azure.

Al terminar, los resultados de las UT aparecen en *Test Result* y las capturas y respuestas de las SIT en *Artifacts*.

## Correr las SIT en local

Con Docker y desde la raíz del repositorio. En Git Bash, antepón `MSYS_NO_PATHCONV=1` y usa rutas `C:/...`.

```bash
export C9_TEST_PASSWORD='<contraseña>'   # o léela de tu archivo local sin mostrarla
docker run --rm --ipc=host -e C9_TEST_PASSWORD \
  -v "$PWD":/repo:ro -v "$PWD/salida-sit":/out \
  mcr.microsoft.com/playwright:v1.56.1-noble bash /repo/scripts/pruebas-demo/run-sit.sh
```
