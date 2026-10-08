# Acceso privado y salida a internet de Jenkins en Azure

- **Estado:** aceptada
- **Fecha:** 2026-10-07
- **Decide:** Ludwin Garcia, en la sesión de validación de CAL-48
- **Issues:** CAL-48 (infraestructura), CAL-86 (Jenkins)

## Contexto

CAL-86 exige Jenkins «sin exposición pública», con acceso administrativo «por red privada/VPN o mecanismo equivalente» y con «evidencia de acceso/costo mínimo». CAL-48 debe entregar a CAL-86 «red, identidad, almacenamiento y endpoint privado».

Hechos que condicionan la decisión:

- Para la API de Azure publicada después del 31 de marzo de 2026, las subredes de VNets nuevas son privadas por defecto y una VM necesita un método explícito de salida (NAT Gateway, IP pública en la NIC, load balancer con reglas de salida o firewall). Fuente: [Default outbound access in Azure](https://learn.microsoft.com/en-us/azure/virtual-network/ip-services/default-outbound-access), actualizado el 2026-09-10.
- Jenkins necesita salida a GitHub, Maven Central, el registro de npm y Docker Hub para construir y para Testcontainers.
- Bastion Developer no tiene cobro por hora, pero no admite conexiones desde Azure CLI (native client); solo SSH desde el portal y una VM a la vez. El túnel al puerto web de Jenkins requiere Standard o Premium, que tienen cobro por hora. Fuente: [Choose the right Azure Bastion SKU](https://learn.microsoft.com/en-us/azure/bastion/bastion-sku-comparison), actualizado el 2026-07-01.
- La suscripción es Azure for Students, región Mexico Central.

## Opciones consideradas

| Opción | Acceso al panel | Salida | Consecuencia |
|---|---|---|---|
| Tailscale + IP pública sin entrada | Tailnet privada | IP pública estándar en la NIC | Menor costo; depende de un tercero y de una cuenta de Tailscale |
| Bastion Standard + NAT Gateway | `az network bastion tunnel` | NAT Gateway | Todo nativo de Azure; ambos recursos cobran por hora mientras existen |
| Bastion Developer + NAT Gateway | Sin panel; SSH desde el portal | NAT Gateway | Sin cobro de Bastion; Jenkins solo administrable como código |

## Decisión

Tailscale para el acceso administrativo e IP pública estándar asociada a la NIC como método explícito de salida.

- El NSG de la subred niega todo tráfico entrante desde Internet; no hay ningún puerto público abierto. La IP pública solo existe para la salida.
- La clave de registro de Tailscale no vive en el repositorio ni en Terraform: se guarda en Key Vault y la VM la lee con su identidad administrada (CAL-86).
- GitHub no puede llamar a Jenkins, así que el disparo de builds es por SCM polling, como ya anticipa la nota de CAL-66 del 2026-09-21.

## Consecuencias

- CAL-48 crea la IP pública, el NSG con negación explícita de entrada, la identidad administrada, Key Vault con RBAC y el disco de datos para `JENKINS_HOME`.
- CAL-86 instala Tailscale y Jenkins en la VM y adjunta la IP y el disco.
- El acceso depende de la disponibilidad de Tailscale; si falla, la vía de emergencia es `az vm run-command`, que no requiere red entrante.

## Pendiente de verificar

- Que Azure for Students permita en Mexico Central el tamaño de VM y los recursos declarados. No se pudo comprobar sin una sesión `az login`; se confirma con el primer `terraform plan`.
- El costo mensual real. Se registra con la evidencia de CAL-86, no se estima aquí.

## Señales para revisar la decisión

- Que el costo de la IP pública supere el presupuesto educativo.
- Que la política de la organización o de la asignatura prohíba servicios de terceros para el acceso administrativo.
