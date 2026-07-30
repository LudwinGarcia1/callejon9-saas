package com.callejon9.tenancy;

/**
 * Se intento acceder a datos sin un tenant activo. El sistema falla cerrado:
 * ninguna consulta corre sin un restaurante identificado.
 */
public class NoTenantContextException extends RuntimeException {

    public NoTenantContextException() {
        super("No hay un tenant activo en el contexto. "
                + "El acceso a datos requiere un restaurante identificado.");
    }
}
