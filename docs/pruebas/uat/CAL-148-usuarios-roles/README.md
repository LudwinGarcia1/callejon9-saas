# CAL-148 · UAT-03 Usuarios y roles

Escenario de aceptación pendiente con una persona administradora en Azure. Usar un restaurante de prueba, tres cuentas ficticias y sesiones separadas de navegador. La [SIT de usuarios y roles](../../sit/CAL-135-usuarios-roles/README.md) aporta evidencia técnica previa.

1. **C01 · Alta y permisos.** Desde **Administración** (`/admin`), abrir **Usuarios** y crear una cuenta por rol. Iniciar sesión con cada cuenta y comprobar que ve únicamente los módulos autorizados.

   | Rol | Pantalla inicial | Módulos autorizados |
   |---|---|---|
   | Mesero | `/waiter` | Mesas |
   | Cocina | `/kitchen` | Cocina e Inventario |
   | Cajero | `/cashier` | Caja, Turnos de caja e Historial |

   Abrir `/admin` en cada sesión operativa y comprobar que se deniega el acceso. Si «solo ve su pantalla» se interpreta como una sola sección para Cocina o Caja, registrar esa diferencia de alcance como observación del caso.
2. **C02 · Desactivación.** Mantener una cuenta operativa abierta en una sesión aparte y desactivarla desde **Usuarios**. Provocar la siguiente petición protegida en la sesión activa y comprobar que se rechaza y se pierde el acceso. Volver a intentar el inicio de sesión y registrar lo que se observa en pantalla, incluido el texto mostrado.
3. **C03 · Contraseña débil.** En **Nuevo usuario**, intentar una contraseña de menos de 10 caracteres y comprobar que el formulario explica el requisito y mantiene abierto el diálogo. Pedir al participante que describa qué cambiaría antes de corregir la contraseña y continuar.

Completar el [registro de aceptación](registro-aceptacion.md) con resultados, nombre de rol del participante, fecha, versión y evidencia anonimizada. Si hay un defecto, anotar severidad y enlace al issue. Retirar las cuentas temporales del entorno al terminar.

La prueba de interfaz `pnpm test src/test/create-user-dialog.test.tsx` cubre el mensaje de contraseña corta. Las suites de autorización y revocación del backend aportan verificación técnica adicional.
