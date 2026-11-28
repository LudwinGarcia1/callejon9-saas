import os
import docx
from docx.shared import Inches, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_ALIGN_VERTICAL
from docx.oxml import OxmlElement, parse_xml
from docx.oxml.ns import nsdecls, qn

DOC_NAME = "Actividad12_Modulo_Movil_Consulta_Registro_Ludwin_Garcia_Gaytan.docx"
CAPTURES_DIR = os.path.abspath("docs/pruebas/integracion-movil-nube/capturas")
RESPONSES_DIR = os.path.abspath("docs/pruebas/integracion-movil-nube/respuestas")

# Colores institucionales y de diseño
HEX_PRIMARY = "0F4C81"     # Azul UTVT / Callejón 9
HEX_SECONDARY = "365F91"   # Azul medio
HEX_TEXT = "2D3748"        # Gris oscuro texto
HEX_MUTED = "64748B"       # Gris medio
HEX_BORDER = "CBD5E1"      # Borde tablas
HEX_BG_ROW_ALT = "F8FAFC"  # Fila alterna
HEX_SUCCESS_BG = "E5F3EA"  # Verde suave
HEX_SUCCESS_FG = "2E7D4F"  # Verde oscuro
HEX_ERROR_BG = "FBE9E8"    # Rojo suave
HEX_ERROR_FG = "C8282E"    # Rojo oscuro

COLOR_PRIMARY = RGBColor(0x0F, 0x4C, 0x81)
COLOR_SECONDARY = RGBColor(0x36, 0x5F, 0x91)
COLOR_TEXT = RGBColor(0x2D, 0x37, 0x48)
COLOR_MUTED = RGBColor(0x64, 0x74, 0x8B)

def set_cell_background(cell, hex_color):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = parse_xml(f'<w:shd {nsdecls("w")} w:fill="{hex_color}"/>')
    tcPr.append(shd)

def set_cell_margins(cell, top=120, bottom=120, left=150, right=150):
    tcPr = cell._tc.get_or_add_tcPr()
    tcMar = parse_xml(
        f'<w:tcMar {nsdecls("w")}>'
        f'<w:top w:w="{top}" w:type="dxa"/>'
        f'<w:bottom w:w="{bottom}" w:type="dxa"/>'
        f'<w:left w:w="{left}" w:type="dxa"/>'
        f'<w:right w:w="{right}" w:type="dxa"/>'
        f'</w:tcMar>'
    )
    tcPr.append(tcMar)

def set_table_borders(table, color="CBD5E1", sz="4", val="single"):
    tblPr = table._tbl.tblPr
    borders = parse_xml(
        f'<w:tblBorders {nsdecls("w")}>'
        f'<w:top w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:bottom w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:left w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:right w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:insideH w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'<w:insideV w:val="{val}" w:sz="{sz}" w:space="0" w:color="{color}"/>'
        f'</w:tblBorders>'
    )
    tblPr.append(borders)

def build_document():
    doc = docx.Document()
    
    # Configurar márgenes a 1 pulgada (estándar APA / Institucional)
    for section in doc.sections:
        section.top_margin = Inches(1.0)
        section.bottom_margin = Inches(1.0)
        section.left_margin = Inches(1.0)
        section.right_margin = Inches(1.0)
        section.page_width = Inches(8.5)
        section.page_height = Inches(11.0)

    # Configuración de estilos base
    style_normal = doc.styles['Normal']
    style_normal.font.name = 'Calibri'
    style_normal.font.size = Pt(11)
    style_normal.font.color.rgb = COLOR_TEXT
    style_normal.paragraph_format.line_spacing = 1.15
    style_normal.paragraph_format.space_after = Pt(6)

    # ==========================================
    # ENCABEZADO INSTITUCIONAL
    # ==========================================
    p_inst = doc.add_paragraph()
    p_inst.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_inst = p_inst.add_run("UNIVERSIDAD TECNOLÓGICA DEL VALLE DE TOLUCA")
    r_inst.font.name = 'Calibri'
    r_inst.font.size = Pt(16)
    r_inst.font.bold = True
    r_inst.font.color.rgb = COLOR_PRIMARY
    p_inst.paragraph_format.space_after = Pt(2)

    p_dir = doc.add_paragraph()
    p_dir.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_dir = p_dir.add_run("DIRECCIÓN DE CARRERA EN TECNOLOGÍAS DE LA INFORMACIÓN\nÁREA DE DESARROLLO DE SOFTWARE MULTIPLATAFORMA")
    r_dir.font.name = 'Calibri'
    r_dir.font.size = Pt(11)
    r_dir.font.bold = True
    r_dir.font.color.rgb = COLOR_MUTED
    p_dir.paragraph_format.space_after = Pt(4)

    p_line = doc.add_paragraph()
    p_line.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_line = p_line.add_run("―――――――――――――――――――――――――――――――――――――――――――――")
    r_line.font.bold = True
    r_line.font.color.rgb = RGBColor(0x25, 0x63, 0xEB)
    p_line.paragraph_format.space_after = Pt(6)

    p_act = doc.add_paragraph()
    p_act.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_act = p_act.add_run("ACTIVIDAD - 12:\nMÓDULO MÓVIL DE CONSULTA Y REGISTRO CON API REMOTA\n(IMPLEMENTACIÓN DE UN MÓDULO CONECTADO A LA NUBE)")
    r_act.font.name = 'Calibri'
    r_act.font.size = Pt(13)
    r_act.font.bold = True
    r_act.font.color.rgb = COLOR_PRIMARY
    p_act.paragraph_format.space_after = Pt(4)

    p_sub = doc.add_paragraph()
    p_sub.alignment = WD_ALIGN_PARAGRAPH.CENTER
    r_sub = p_sub.add_run("Reporte Técnico de Integración Móvil, Consumo REST (GET), Envío Seguro (POST) y Evidencias de Validación")
    r_sub.font.name = 'Calibri'
    r_sub.font.size = Pt(11)
    r_sub.font.italic = True
    r_sub.font.color.rgb = COLOR_TEXT
    p_sub.paragraph_format.space_after = Pt(12)

    # TABLA DE METADATOS DEL ALUMNO Y ASIGNATURA
    t_meta = doc.add_table(rows=5, cols=2)
    t_meta.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(t_meta, "CBD5E1", sz="4")
    
    meta_data = [
        ("Estudiante:", "Ludwin Garcia Gaytan"),
        ("Matrícula:", "222310777"),
        ("Grado y Cuatrimestre:", "Décimo Cuatrimestre - Ingeniería en Desarrollo de Software"),
        ("Docente:", "Prof. R. Vinicio Camacho"),
        ("Fecha de Entrega:", "17 de octubre de 2026")
    ]
    
    for i, (label, val) in enumerate(meta_data):
        row = t_meta.rows[i]
        c0, c1 = row.cells[0], row.cells[1]
        c0.width = Inches(2.2)
        c1.width = Inches(4.3)
        set_cell_background(c0, "F1F5F9")
        set_cell_background(c1, "FFFFFF")
        set_cell_margins(c0, top=60, bottom=60, left=100, right=100)
        set_cell_margins(c1, top=60, bottom=60, left=100, right=100)
        
        p0 = c0.paragraphs[0]
        r0 = p0.add_run(label)
        r0.font.bold = True
        r0.font.size = Pt(10)
        r0.font.color.rgb = COLOR_PRIMARY
        p0.paragraph_format.space_after = Pt(0)
        
        p1 = c1.paragraphs[0]
        r1 = p1.add_run(val)
        r1.font.size = Pt(10)
        r1.font.color.rgb = COLOR_TEXT
        p1.paragraph_format.space_after = Pt(0)

    doc.add_paragraph().paragraph_format.space_after = Pt(8)

    # ==========================================
    # SECCIÓN: CASO DE ESTUDIO Y SITUACIÓN PLANTEADA
    # ==========================================
    h_caso = doc.add_paragraph()
    r = h_caso.add_run("Caso de Estudio y Situación Planteada")
    r.font.size = Pt(13)
    r.font.bold = True
    r.font.color.rgb = COLOR_PRIMARY
    h_caso.paragraph_format.space_after = Pt(4)

    p_caso = doc.add_paragraph()
    p_caso.add_run(
        "Un cliente empresarial de la industria restaurantera solicita que la aplicación móvil operativa "
        "deje de depender exclusivamente de datos locales o estructuras de prueba simuladas (mock data) "
        "y se integre formalmente con su plataforma de servicios en la nube. La solución desarrollada "
        "incorpora un módulo integral que permite consultar información en tiempo real y registrar nuevos "
        "elementos del catálogo mediante peticiones asíncronas hacia la API remota del SaaS Callejón 9, "
        "implementando estrictas validaciones preventivas en el cliente, validación en el servidor "
        "y sólidas garantías de seguridad perimetral y aislamiento multi-inquilino."
    )

    # ==========================================
    # FASE 1: SELECCIÓN Y PREPARACIÓN
    # ==========================================
    h_f1 = doc.add_paragraph()
    r = h_f1.add_run("Fase 1. Selección y Preparación de la Arquitectura")
    r.font.size = Pt(13)
    r.font.bold = True
    r.font.color.rgb = COLOR_PRIMARY
    h_f1.paragraph_format.space_after = Pt(4)

    p_f1_desc = doc.add_paragraph()
    p_f1_desc.add_run(
        "En cumplimiento de los lineamientos de la actividad, no se creó un proyecto aislado desde cero, "
        "sino que se amplió y consolidó la aplicación móvil existente del ecosistema Callejón 9:"
    )

    # Tabla Ficha Técnica de Selección
    t_fase1 = doc.add_table(rows=5, cols=2)
    t_fase1.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(t_fase1, "CBD5E1", sz="4")

    f1_items = [
        ("1. Proyecto Móvil Seleccionado", 
         "Callejón 9 Mobile (App operativa de meseros y administración de salón). Desarrollada en React Native 0.88 con Expo SDK 58, TypeScript 6.0 en modo estricto y arquitectura modular desacoplada en mobile/."),
        ("2. Entidad de Información", 
         "Productos del Catálogo (products). Modelo que representa alimentos y bebidas ofertados en el restaurante. Contiene identificador único (ULID), nombre del producto, precio unitario (MXN), categoría taxonómica, descripción detallada y bandera de estado activo/inactivo."),
        ("3. Servicio Remoto y Alojamiento", 
         "Backend RESTful propio basado en Java 21, Spring Boot 3.4 y PostgreSQL 16 con políticas de Row Level Security (RLS). Alojado en la nube empresarial mediante Azure App Service (https://app-callejon9-api-prod-3878c6.azurewebsites.net/api/v1) con réplica de desarrollo mediante túnel seguro HTTPS."),
        ("4. Especificación de Endpoints y Métodos HTTP", 
         "• GET /api/v1/products: Consulta remota de productos del restaurante autenticado.\n"
         "• GET /api/v1/categories: Consulta remota de categorías para clasificación.\n"
         "• POST /api/v1/products: Registro remoto de nuevos productos con payload JSON (Requiere rol ADMIN).\n"
         "• PUT /api/v1/products/{id}: Actualización integral de información de producto.\n"
         "• PATCH /api/v1/products/{id}: Activación/desactivación lógica sin pérdida de historial.")
    ]

    for i, (crit, desc) in enumerate(f1_items):
        row = t_fase1.rows[i]
        c0, c1 = row.cells[0], row.cells[1]
        c0.width = Inches(2.2)
        c1.width = Inches(4.3)
        set_cell_background(c0, "F8FAFC" if i % 2 == 0 else "FFFFFF")
        set_cell_background(c1, "F8FAFC" if i % 2 == 0 else "FFFFFF")
        set_cell_margins(c0, top=70, bottom=70, left=100, right=100)
        set_cell_margins(c1, top=70, bottom=70, left=100, right=100)

        p0 = c0.paragraphs[0]
        r0 = p0.add_run(crit)
        r0.font.bold = True
        r0.font.size = Pt(9.5)
        r0.font.color.rgb = COLOR_PRIMARY
        p0.paragraph_format.space_after = Pt(0)

        p1 = c1.paragraphs[0]
        r1 = p1.add_run(desc)
        r1.font.size = Pt(9.5)
        r1.font.color.rgb = COLOR_TEXT
        p1.paragraph_format.space_after = Pt(0)

    doc.add_paragraph().paragraph_format.space_after = Pt(6)

    # Matriz de Endpoints HTTP
    p_tbl_title = doc.add_paragraph()
    r = p_tbl_title.add_run("Tabla 1.1: Especificación del Contrato de Intercambio de la API Remota")
    r.font.bold = True
    r.font.size = Pt(10.5)
    r.font.color.rgb = COLOR_SECONDARY
    p_tbl_title.paragraph_format.space_after = Pt(3)

    ep_data = [
        ("Login de Usuario", "POST", "/auth/login", "{\"slug\",\"email\",\"password\"}", "200 OK + Cookie", "Público"),
        ("Consulta Catálogo", "GET", "/products", "Vacío (query param opcional)", "200 OK (Product[])", "Cualquiera autenticado"),
        ("Consulta Categorías", "GET", "/categories", "Vacío", "200 OK (Category[])", "Cualquiera autenticado"),
        ("Registro Producto", "POST", "/products", "{\"name\",\"price\",\"description\",\"categoryId\"}", "201 Created", "ADMIN"),
        ("Edición Integral", "PUT", "/products/{id}", "{\"name\",\"price\",\"description\",\"categoryId\"}", "200 OK", "ADMIN"),
        ("Baja/Alta Lógica", "PATCH", "/products/{id}", "{\"active\": boolean}", "200 OK", "ADMIN")
    ]

    t_endpoints = doc.add_table(rows=len(ep_data) + 1, cols=6)
    t_endpoints.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(t_endpoints, "CBD5E1", sz="4")

    headers_ep = ["Operación", "Método", "Ruta / Endpoint", "Payload Enviado", "Código Esperado", "Rol Requerido"]
    for j, h in enumerate(headers_ep):
        cell = t_endpoints.rows[0].cells[j]
        set_cell_background(cell, HEX_PRIMARY)
        set_cell_margins(cell, top=70, bottom=70, left=60, right=60)
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(h)
        r.font.bold = True
        r.font.size = Pt(9)
        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        p.paragraph_format.space_after = Pt(0)

    for i, row_data in enumerate(ep_data):
        row = t_endpoints.rows[i + 1]
        for j, val in enumerate(row_data):
            cell = row.cells[j]
            set_cell_background(cell, "F8FAFC" if i % 2 == 0 else "FFFFFF")
            set_cell_margins(cell, top=60, bottom=60, left=60, right=60)
            p = cell.paragraphs[0]
            if j in [1, 4, 5]:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            r = p.add_run(val)
            r.font.size = Pt(8.5)
            if j == 1:
                r.font.bold = True
                if val == "GET": r.font.color.rgb = RGBColor(0x16, 0x65, 0x34)
                elif val == "POST": r.font.color.rgb = RGBColor(0x1E, 0x40, 0xAF)
                elif val == "PUT": r.font.color.rgb = RGBColor(0x85, 0x4D, 0x0E)
                elif val == "PATCH": r.font.color.rgb = RGBColor(0x6B, 0x21, 0xA8)
            p.paragraph_format.space_after = Pt(0)

    doc.add_paragraph().paragraph_format.space_after = Pt(8)

    # ==========================================
    # FASE 2: IMPLEMENTACIÓN DE FUNCIONALIDADES
    # ==========================================
    h_f2 = doc.add_paragraph()
    r = h_f2.add_run("Fase 2. Implementación de Funcionalidades Técnicas")
    r.font.size = Pt(13)
    r.font.bold = True
    r.font.color.rgb = COLOR_PRIMARY
    h_f2.paragraph_format.space_after = Pt(4)

    # 2.1 Funcionalidad A
    p_fa = doc.add_paragraph()
    r = p_fa.add_run("2.1 Funcionalidad A: Consulta de Datos Remotos (GET)")
    r.font.bold = True
    r.font.size = Pt(11)
    r.font.color.rgb = COLOR_SECONDARY
    p_fa.paragraph_format.space_after = Pt(2)

    p_fa_desc = doc.add_paragraph()
    p_fa_desc.add_run(
        "La consulta de información remota se centraliza en la función fetchCatalog() (ubicada en src/api/catalog.ts), "
        "la cual invoca el cliente HTTP reutilizable request() en src/api/client.ts. Al ingresar a la pantalla ProductsScreen.tsx, "
        "la aplicación ejecuta una solicitud asíncrona hacia el endpoint GET /api/v1/products. "
        "El flujo de datos implementa las siguientes características clave:\n"
        "• Procesamiento y Deserialización: La respuesta JSON devuelta por el servidor se valida y parsea directamente "
        "a la interfaz tipada Product[], aplicando una ordenación alfabética en memoria respetando las reglas ortográficas en español.\n"
        "• Renderizado Móvil Óptimo: Se emplea el componente nativo FlatList de React Native, optimizado para alto rendimiento "
        "en dispositivos con memoria reducida, agregando soporte de recarga gestual («pull-to-refresh») mediante RefreshControl.\n"
        "• Manejo Exhaustivo de Errores: Si la petición falla por indisponibilidad de red, timeout o rechazo del servidor, "
        "el error es interceptado y traducido por toApiError() a un mensaje en español comprensible para el usuario, "
        "desplegando el componente visual Banner en tono destructivo con un botón secundario para 'Reintentar' la consulta."
    )

    # 2.2 Funcionalidad B
    p_fb = doc.add_paragraph()
    r = p_fb.add_run("2.2 Funcionalidad B: Registro de Información en la Nube (POST)")
    r.font.bold = True
    r.font.size = Pt(11)
    r.font.color.rgb = COLOR_SECONDARY
    p_fb.paragraph_format.space_after = Pt(2)

    p_fb_desc = doc.add_paragraph()
    p_fb_desc.add_run(
        "El alta de nuevos productos se gestiona mediante la pantalla ProductFormScreen.tsx. Diseñada siguiendo las pautas de UX "
        "móvil del sistema Callejón 9, presenta un formulario reactivo contenido en un KeyboardAvoidingView con desplazamiento suave:\n"
        "• Formulario de Captura: Permite ingresar el Nombre del producto (requerido), Precio en moneda nacional (requerido), "
        "selección visual de Categoría mediante Chips táctiles interactivos, y una Descripción detallada opcional.\n"
        "• Validación Preventiva en Cliente (src/validation/product.ts): Antes de generar tráfico de red innecesario, "
        "la función validateProduct() verifica que el nombre no contenga únicamente espacios en blanco (máximo 160 caracteres), "
        "que el precio sea un número estrictamente positivo con máximo 2 cifras decimales, y que la descripción no exceda 500 caracteres. "
        "Si existen inconsistencias, los inputs afectados se marcan con contorno rojo y leyendas de error contextuales que se limpian dinámicamente conforme el usuario corrige el texto.\n"
        "• Envío y Confirmación: Tras validar localmente, se realiza una petición HTTP POST serializando el payload en formato JSON. "
        "Al recibir la confirmación exitosa con código 201 Created, la aplicación regresa al catálogo desplegando un Banner verde con "
        "el mensaje: «El servidor guardó [nombre] a $[precio] y ya aparece en el menú», destacando la nueva fila y asegurando sincronización inmediata."
    )

    # 2.3 Funcionalidad C
    p_fc = doc.add_paragraph()
    r = p_fc.add_run("2.3 Funcionalidad C: Seguridad de la Integración Móvil-Nube")
    r.font.bold = True
    r.font.size = Pt(11)
    r.font.color.rgb = COLOR_SECONDARY
    p_fc.paragraph_format.space_after = Pt(2)

    p_fc_desc = doc.add_paragraph()
    p_fc_desc.add_run(
        "La integración móvil incorpora rigurosos mecanismos de seguridad defensiva tanto en el canal como en el manejo de sesiones:\n"
        "• Configuración Obligatoria de HTTPS: En el archivo src/config.ts, la aplicación inspecciona la variable EXPO_PUBLIC_API_URL. "
        "El cliente móvil se niega terminantemente a iniciar si la dirección de conexión utiliza el protocolo inseguro http://, "
        "exigiendo el canal cifrado TLS 1.3 provisto por Azure con cabecera Strict-Transport-Security (HSTS).\n"
        "• Mitigación de Robo de Tokens (Protección contra XSS Móvil): A diferencia de implementaciones vulnerables que guardan "
        "tokens en AsyncStorage o variables de JavaScript expuestas, Callejón 9 delega el manejo del token JWT a cookies de sesión con flags "
        "httpOnly, Secure y SameSite=Strict. Las cookies son administradas por el almacén nativo del sistema operativo (OkHttp en Android "
        "y NSHTTPCookieStorage en iOS), garantizando que ningún código malicioso o script inyectado en el runtime de JS pueda acceder a las credenciales.\n"
        "• Ciclo de Vida y Renovación Automática: El Access Token emitido por Spring Boot cuenta con una vigencia efímera de 15 minutos. "
        "Ante la recepción de una respuesta HTTP 401 Unauthorized, client.ts dispara transparentemente una solicitud de rotación "
        "hacia POST /api/v1/auth/refresh. Si la sesión expiró de forma definitiva, se purga el estado y se redirige con seguridad al LoginScreen."
    )

    doc.add_paragraph().paragraph_format.space_after = Pt(6)

    # ==========================================
    # FASE 3: PRUEBAS Y VALIDACIÓN
    # ==========================================
    h_f3 = doc.add_paragraph()
    r = h_f3.add_run("Fase 3. Pruebas, Validación y Evidencia de Ejecución")
    r.font.size = Pt(13)
    r.font.bold = True
    r.font.color.rgb = COLOR_PRIMARY
    h_f3.paragraph_format.space_after = Pt(4)

    p_f3_desc = doc.add_paragraph()
    p_f3_desc.add_run(
        "Para validar el comportamiento del módulo en condiciones reales y asegurar que se cumplan las cinco condiciones requeridas "
        "(renderizado correcto, procesamiento de registros en servidor, validación de campos obligatorios, manejo de fallas de red "
        "y confidencialidad de credenciales), se ejecutaron 69 pruebas automatizadas con Vitest y una batería de 7 casos de prueba de integración (M01 a M07):"
    )

    # Tabla de Resultados de Pruebas
    t_tests = doc.add_table(rows=8, cols=5)
    t_tests.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(t_tests, "CBD5E1", sz="4")

    headers_test = ["ID", "Caso de Prueba", "Acción Ejecutada en Móvil", "Resultado Obtenido", "Estatus"]
    for j, h in enumerate(headers_test):
        cell = t_tests.rows[0].cells[j]
        set_cell_background(cell, HEX_PRIMARY)
        set_cell_margins(cell, top=70, bottom=70, left=60, right=60)
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(h)
        r.font.bold = True
        r.font.size = Pt(9)
        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        p.paragraph_format.space_after = Pt(0)

    test_rows = [
        ("M01", "Consulta de Datos (GET)", "Ingreso al módulo Menú con sesión activa", "Obtención exitosa de 8 productos remotos serializados en FlatList con precios en MXN.", "Aprobado (200 OK)"),
        ("M02", "Registro de Información (POST)", "Captura de «Taco de bistec especial» a $55.00", "Petición HTTP POST enviada a Azure; persistencia en PostgreSQL confirmada.", "Aprobado (201 Created)"),
        ("M03", "Persistencia y Reconsulta", "Pull-to-refresh en móvil y verificación en BD", "El nuevo producto permanece en la nube y se propaga a los demás clientes.", "Aprobado (Sincronizado)"),
        ("M04", "Edición y Estado (PUT/PATCH)", "Modificación de precio a $60.00 y desactivación", "Actualización reactiva en tiempo real; el producto inactivo se atenúa en la UI.", "Aprobado (200 OK)"),
        ("M05", "Validación en Cliente", "Envío con nombre vacío y precio negativo (-15)", "Bloqueo preventivo en formulario móvil; no se genera tráfico HTTP.", "Aprobado (Validado)"),
        ("M05b", "Validación en Servidor", "Petición POST con curl alterando el body", "Spring Boot responde 400 Bad Request en formato RFC 7807 con errors.name.", "Aprobado (400 Bad Request)"),
        ("M06", "Tolerancia a Fallas de Red", "Registro de datos con Modo Avión activado", "Banner rojo desplegado: «No se pudo conectar con el servidor»; reintento funcional.", "Aprobado (Controlado)")
    ]

    for i, r_data in enumerate(test_rows):
        row = t_tests.rows[i + 1]
        for j, val in enumerate(r_data):
            cell = row.cells[j]
            set_cell_background(cell, "F8FAFC" if i % 2 == 0 else "FFFFFF")
            set_cell_margins(cell, top=60, bottom=60, left=60, right=60)
            p = cell.paragraphs[0]
            if j in [0, 4]:
                p.alignment = WD_ALIGN_PARAGRAPH.CENTER
            r = p.add_run(val)
            r.font.size = Pt(8.5)
            if j == 4:
                r.font.bold = True
                r.font.color.rgb = RGBColor(0x16, 0x65, 0x34)
            p.paragraph_format.space_after = Pt(0)

    doc.add_paragraph().paragraph_format.space_after = Pt(8)

    # ==========================================
    # FASE 4: EVIDENCIAS VISUALES JUSTIFICADAS
    # ==========================================
    h_f4 = doc.add_paragraph()
    r = h_f4.add_run("Fase 4. Evidencias Visuales de la Aplicación Móvil en Ejecución")
    r.font.size = Pt(13)
    r.font.bold = True
    r.font.color.rgb = COLOR_PRIMARY
    h_f4.paragraph_format.space_after = Pt(4)

    p_f4_intro = doc.add_paragraph()
    p_f4_intro.add_run(
        "A continuación se presentan las capturas de pantalla de la aplicación móvil en ejecución, "
        "demostrando cada una de las funcionalidades desarrolladas y justificando técnicamente el comportamiento observado:"
    )

    # FUNCIÓN AUXILIAR PARA INSERTAR CAPTURA CON TARJETA DE JUSTIFICACIÓN
    def add_screen_evidence(img_name, title, justification, tech_details):
        img_path = os.path.join(CAPTURES_DIR, img_name)
        if not os.path.exists(img_path):
            print(f"Alerta: No se encontró la imagen {img_path}")
            return

        t_card = doc.add_table(rows=1, cols=2)
        t_card.alignment = WD_TABLE_ALIGNMENT.CENTER
        set_table_borders(t_card, "CBD5E1", sz="6")

        c_img = t_card.rows[0].cells[0]
        c_desc = t_card.rows[0].cells[1]
        c_img.width = Inches(2.4)
        c_desc.width = Inches(4.1)

        set_cell_background(c_img, "F1F5F9")
        set_cell_background(c_desc, "FFFFFF")
        set_cell_margins(c_img, top=100, bottom=100, left=100, right=100)
        set_cell_margins(c_desc, top=100, bottom=100, left=120, right=120)

        # Insertar imagen
        p_img = c_img.paragraphs[0]
        p_img.alignment = WD_ALIGN_PARAGRAPH.CENTER
        p_img.paragraph_format.space_after = Pt(0)
        run_img = p_img.add_run()
        run_img.add_picture(img_path, width=Inches(2.2))

        # Insertar texto explicativo
        p_desc = c_desc.paragraphs[0]
        p_desc.paragraph_format.space_after = Pt(4)
        r_t = p_desc.add_run(title)
        r_t.font.bold = True
        r_t.font.size = Pt(11)
        r_t.font.color.rgb = COLOR_PRIMARY

        p_just = c_desc.add_paragraph()
        p_just.paragraph_format.space_after = Pt(6)
        r_jlabel = p_just.add_run("Justificación Funcional:\n")
        r_jlabel.font.bold = True
        r_jlabel.font.size = Pt(9.5)
        r_jlabel.font.color.rgb = COLOR_SECONDARY
        r_jtext = p_just.add_run(justification)
        r_jtext.font.size = Pt(9.5)

        p_tech = c_desc.add_paragraph()
        p_tech.paragraph_format.space_after = Pt(0)
        r_tlabel = p_tech.add_run("Detalle de Integración en la Nube:\n")
        r_tlabel.font.bold = True
        r_tlabel.font.size = Pt(9.5)
        r_tlabel.font.color.rgb = COLOR_SECONDARY
        r_ttext = p_tech.add_run(tech_details)
        r_ttext.font.size = Pt(9)
        r_ttext.font.color.rgb = COLOR_MUTED

        doc.add_paragraph().paragraph_format.space_after = Pt(6)

    # 1. Evidencia M01
    add_screen_evidence(
        "M01-menu.png",
        "📱 Evidencia 1: Consulta Remota del Catálogo (GET /products)",
        "La pantalla 'Menú' renderiza en tiempo real los registros provenientes de la base de datos PostgreSQL alojada en Azure. "
        "Se muestra el listado ordenado alfabéticamente con nombres, precios en formato regional MXN ($45.00), categorías y etiquetas descriptivas. "
        "Para usuarios con privilegios administrativos (ADMIN), se visualizan productos activos e inactivos con opción táctil de edición.",
        "• Protocolo: HTTPS GET /api/v1/products\n"
        "• Procesamiento: Deserialización a Product[] en ProductsScreen.tsx mediante React Native FlatList.\n"
        "• Garantía de Seguridad: El ID de restaurante no se solicita en la petición; PostgreSQL lo aísla mediante RLS."
    )

    # 2. Evidencia M02
    add_screen_evidence(
        "M02-formulario.png",
        "📱 Evidencia 2: Formulario de Registro en la Nube (POST /products)",
        "La pantalla 'Nuevo producto' presenta la interfaz de captura intuitiva con campos estructurados: Nombre obligatorio, "
        "Precio unitario con teclado numérico especializado, selector taxonómico interactivo por Chips (categoría 'Tacos' seleccionada) "
        "y campo multilínea para Descripción detallada de ingredientes.",
        "• Componente: ProductFormScreen.tsx envuelto en KeyboardAvoidingView.\n"
        "• Validación en Tiempo Real: Limpieza automática de estados de error conforme el usuario teclea en los inputs.\n"
        "• Payload: Preparación de objeto tipado CreateProductRequest listo para serializar en JSON."
    )

    # 3. Evidencia M05
    add_screen_evidence(
        "M05-validacion.png",
        "📱 Evidencia 3: Validación Preventiva de Campos Obligatorios",
        "Demostración de la robustez del módulo ante intentos de captura incorrecta. Al pulsar 'Registrar producto' con campos vacíos "
        "o con un precio negativo (-15.00), la capa de validación local (validateProduct) intercepta el flujo e impide la salida de peticiones HTTP. "
        "Los campos defectuosos se destacan con bordes rojos y alertas visuales explícitas: '✕ El nombre es obligatorio' y '✕ Escribe un precio válido'.",
        "• Archivo: mobile/src/validation/product.ts con pruebas unitarias en catalog.test.ts.\n"
        "• Reglas de Negocio: Nombre entre 1 y 160 caracteres; precio > 0 con máximo 2 decimales; descripción <= 500 caracteres.\n"
        "• Eficiencia: Ahorro de ancho de banda y prevención de saturación en el servidor remoto."
    )

    # 4. Evidencia M03
    add_screen_evidence(
        "M03-registrado.png",
        "📱 Evidencia 4: Confirmación de Registro Exitoso y Persistencia",
        "Tras completar el registro válido, el backend en la nube procesa la inserción y retorna HTTP 201 Created. "
        "La pantalla móvil transiciona al catálogo desplegando un Banner de éxito en verde esmeralda confirmando: "
        "«El servidor guardó 'Taco de bistec especial' a $55.00 y ya aparece en el menú en la nube». "
        "El producto recién persistido aparece encabezando la lista con un distintivo visual resaltado.",
        "• Endpoint: POST /api/v1/products → HTTP 201 Created.\n"
        "• Reconsulta Automática: La app vuelve a consultar el servidor para sincronizar la UI con la base de datos.\n"
        "• Estado Global: Notificación estructurada mediante CatalogNotice en App.tsx."
    )

    # 5. Evidencia M06
    add_screen_evidence(
        "M06-sin-red.png",
        "📱 Evidencia 5: Manejo Seguro de Errores de Conexión (Sin Red)",
        "Comprobación de la resiliencia de la app móvil ante caídas de enlace o modo avión activado. "
        "En lugar de sufrir un cierre inesperado (crash), la promesa de red es capturada en client.ts generando una excepción de tipo ApiError. "
        "La interfaz muestra un Banner amigable: '⚠ Error al consultar el menú - No se pudo conectar con el servidor en la nube' "
        "y proporciona un botón directo de 'Reintentar' sin exponer trazas de depuración sensibles.",
        "• Manejador: Función toApiError() con captura de TypeError / AbortError por timeout.\n"
        "• Seguridad de la Información: Cero filtración de endpoints internos, IPs o stack traces de Java.\n"
        "• Recuperación: Restablecimiento transparente una vez reactivada la señal celular o Wi-Fi."
    )

    # 6. Evidencia M07
    add_screen_evidence(
        "M07-mesero.png",
        "📱 Evidencia 6: Control de Acceso y Autorización por Rol (RBAC)",
        "Demostración de la segregación de funciones en la interfaz móvil. Cuando un usuario inicia sesión con rol operativo (WAITER), "
        "la aplicación oculta los botones de '+ Nuevo producto' y las opciones de edición o baja de artículos. "
        "Si un usuario malintencionado intentara ejecutar un POST manual mediante herramientas externas, Spring Security rechaza la acción con HTTP 403 Forbidden.",
        "• Control de Interfaz: Propiedad canManage={session?.role === 'ADMIN'} en ProductsScreen.tsx.\n"
        "• Autorización en Servidor: Anotación @PreAuthorize(\"hasRole('ADMIN')\") en ProductController.java.\n"
        "• Principio de Menor Privilegio: El mesero únicamente dispone de facultades de consulta de comandas."
    )

    # ==========================================
    # SECCIÓN: DESCRIPCIÓN DE 3 MEDIDAS DE SEGURIDAD
    # ==========================================
    h_sec = doc.add_paragraph()
    r = h_sec.add_run("Descripción Detallada de las Tres Medidas de Seguridad Implementadas")
    r.font.size = Pt(13)
    r.font.bold = True
    r.font.color.rgb = COLOR_PRIMARY
    h_sec.paragraph_format.space_after = Pt(4)

    p_sec_intro = doc.add_paragraph()
    p_sec_intro.add_run(
        "En estricto cumplimiento con la rúbrica de la actividad, se detallan las tres medidas de seguridad fundamentales "
        "integradas en la solución para proteger la información en tránsito, el almacenamiento de sesiones y la integridad transaccional:"
    )

    t_sec = doc.add_table(rows=4, cols=3)
    t_sec.alignment = WD_TABLE_ALIGNMENT.CENTER
    set_table_borders(t_sec, "CBD5E1", sz="4")

    headers_sec = ["Medida de Seguridad", "Mecanismo Técnico Aplicado", "Vulnerabilidad Mitigada y Ubicación"]
    for j, h in enumerate(headers_sec):
        cell = t_sec.rows[0].cells[j]
        set_cell_background(cell, HEX_PRIMARY)
        set_cell_margins(cell, top=70, bottom=70, left=70, right=70)
        p = cell.paragraphs[0]
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER
        r = p.add_run(h)
        r.font.bold = True
        r.font.size = Pt(9.5)
        r.font.color.rgb = RGBColor(0xFF, 0xFF, 0xFF)
        p.paragraph_format.space_after = Pt(0)

    sec_data = [
        ("1. Cifrado Forzoso en Tránsito (HTTPS / TLS 1.3)",
         "El cliente móvil inspecciona la URL base del servicio remoto mediante assertHttps() en src/config.ts. La aplicación bloquea su ejecución ante cualquier intento de comunicación en texto plano (http://). Asimismo, el servidor en Azure implementa cabeceras HSTS (Strict-Transport-Security: max-age=31536000) forzando canales criptográficos TLS 1.3.",
         "Previene ataques Man-in-the-Middle (MitM), intercepción de paquetes en redes Wi-Fi públicas y espionaje de payloads JSON.\nUbicación: mobile/src/config.ts y SecurityConfig.java."),
        ("2. Gestión Inmune de Sesiones (Cookies HttpOnly + Tokens Efímeros)",
         "Las credenciales y tokens JWT jamás tocan la memoria de JavaScript ni se almacenan en AsyncStorage. El backend emite cookies de autenticación con directivas httpOnly, Secure y SameSite=Strict, las cuales son gestionadas nativamente por el stack de red del sistema operativo (OkHttp/NSHTTPCookieStorage). El Access Token dura solo 15 minutos y se renueva automáticamente.",
         "Erradica el riesgo de robo o secuestro de tokens mediante Cross-Site Scripting (XSS Móvil) o inyección de scripts en bibliotecas de terceros.\nUbicación: mobile/src/api/client.ts y AuthController.java."),
        ("3. Doble Validación de Integridad y Sanitización RFC 7807",
         "Implementación de validación simétrica: validación preventiva en el cliente (validateProduct) y validación estricta en el servidor mediante Jakarta Bean Validation (@NotBlank, @DecimalMin, @Size). Ante errores, el servidor retorna objetos estandarizados RFC 7807 (ProblemDetail) sanitizados, ocultando trazas de base de datos o stack traces internos.",
         "Previene inyecciones SQL, corrupción de base de datos por datos truncados o negativos, y fuga de información de infraestructura en mensajes de error.\nUbicación: mobile/src/validation/product.ts y GlobalExceptionHandler.java.")
    ]

    for i, (m_title, m_tech, m_mit) in enumerate(sec_data):
        row = t_sec.rows[i + 1]
        c0, c1, c2 = row.cells[0], row.cells[1], row.cells[2]
        c0.width = Inches(2.0)
        c1.width = Inches(2.3)
        c2.width = Inches(2.2)

        set_cell_background(c0, "F8FAFC" if i % 2 == 0 else "FFFFFF")
        set_cell_background(c1, "F8FAFC" if i % 2 == 0 else "FFFFFF")
        set_cell_background(c2, "F8FAFC" if i % 2 == 0 else "FFFFFF")

        set_cell_margins(c0, top=70, bottom=70, left=70, right=70)
        set_cell_margins(c1, top=70, bottom=70, left=70, right=70)
        set_cell_margins(c2, top=70, bottom=70, left=70, right=70)

        p0 = c0.paragraphs[0]
        r0 = p0.add_run(m_title)
        r0.font.bold = True
        r0.font.size = Pt(9)
        r0.font.color.rgb = COLOR_PRIMARY
        p0.paragraph_format.space_after = Pt(0)

        p1 = c1.paragraphs[0]
        r1 = p1.add_run(m_tech)
        r1.font.size = Pt(8.5)
        p1.paragraph_format.space_after = Pt(0)

        p2 = c2.paragraphs[0]
        r2 = p2.add_run(m_mit)
        r2.font.size = Pt(8.5)
        p2.paragraph_format.space_after = Pt(0)

    doc.add_paragraph().paragraph_format.space_after = Pt(8)

    # Medida Adicional de Aislamiento
    p_extra_sec = doc.add_paragraph()
    r = p_extra_sec.add_run("Medida de Seguridad Adicional — Aislamiento Multi-Tenant con PostgreSQL RLS:")
    r.font.bold = True
    r.font.size = Pt(10)
    r.font.color.rgb = COLOR_SECONDARY
    p_extra_sec.paragraph_format.space_after = Pt(2)

    p_extra_desc = doc.add_paragraph()
    p_extra_desc.add_run(
        "Como garantía arquitectónica no negociable del proyecto Callejón 9, la aplicación móvil nunca envía el identificador "
        "del restaurante (tenant_id) como parámetro manipulable en las peticiones de datos. El servidor extrae la identidad del "
        "inquilino exclusivamente desde el payload del JWT firmado y ejecuta set_config('app.tenant_id', ..., true) dentro de la transacción. "
        "A nivel de motor de base de datos, PostgreSQL impone políticas de Row Level Security (RLS) con las directivas "
        "ENABLE ROW LEVEL SECURITY y FORCE ROW LEVEL SECURITY, haciendo físicamente imposible que un usuario consulte o altere productos pertenecientes a otro restaurante."
    )

    # ==========================================
    # SECCIÓN: EXPLICACIÓN DEL FUNCIONAMIENTO POR EL EQUIPO
    # ==========================================
    h_exp = doc.add_paragraph()
    r = h_exp.add_run("Explicación del Funcionamiento por parte de los Integrantes del Equipo")
    r.font.size = Pt(13)
    r.font.bold = True
    r.font.color.rgb = COLOR_PRIMARY
    h_exp.paragraph_format.space_after = Pt(4)

    p_exp = doc.add_paragraph()
    p_exp.add_run(
        "Como estudiante e integrante responsable de la solución (Ludwin Garcia Gaytan), se expone el funcionamiento general "
        "de la integración desarrollada para la defensa técnica del módulo:\n\n"
        "1. Desacoplamiento de la Capa de Red:\n"
        "Toda la comunicación móvil se canaliza a través de un único módulo de transporte (client.ts). "
        "Las pantallas de la interfaz nunca realizan llamadas directas con fetch crudo, sino que consumen funciones de negocio "
        "(fetchCatalog, createProduct). Esto garantiza que parámetros transversales como cabeceras Content-Type, "
        "políticas de timeout, control de cookies nativas y traducción uniforme de errores operen de manera idéntica en toda la aplicación.\n\n"
        "2. Ciclo de Vida de una Consulta (GET):\n"
        "Cuando el usuario accede al Menú o desliza la pantalla hacia abajo para recargar, se activa el hook load('refresh'). "
        "Se realizan en paralelo las solicitudes GET /products y GET /categories. Mientras el backend en la nube procesa la consulta "
        "en PostgreSQL filtrando por RLS, la UI móvil despliega un ActivityIndicator. Al llegar la respuesta JSON, los datos se ordenan, "
        "se mapean con sus categorías y se renderizan reactivamente en el FlatList, formateando los valores numéricos a moneda local.\n\n"
        "3. Ciclo de Vida de un Registro (POST):\n"
        "Al capturar un nuevo producto, el formulario ejecuta primero la validación sincrónica en el cliente. Si los campos son válidos, "
        "se deshabilita el botón de envío para prevenir pulsaciones dobles y se despacha el payload JSON hacia POST /products. "
        "Spring Security comprueba que el JWT posea el rol 'ADMIN'; si el rol es 'WAITER', retorna 403 Forbidden. Si la validación en Spring (@Valid) "
        "es aprobada, la capa de servicio persiste la entidad y devuelve HTTP 201 Created con el objeto creado. La app móvil intercepta el 201, "
        "cierra el formulario y actualiza la lista principal mostrando el aviso verde de confirmación.\n\n"
        "4. Enlace al Repositorio de Código:\n"
        "El código fuente completo de la aplicación móvil, los componentes visuales, el conjunto de pruebas unitarias y las referencias "
        "de integración se encuentran bajo control de versiones Git en el proyecto Callejón 9, ubicados en el subdirectorio /mobile."
    )

    # Conclusión
    p_concl = doc.add_paragraph()
    r = p_concl.add_run("Conclusión:")
    r.font.bold = True
    r.font.size = Pt(10.5)
    r.font.color.rgb = COLOR_PRIMARY
    p_concl.paragraph_format.space_after = Pt(2)

    p_concl_desc = doc.add_paragraph()
    p_concl_desc.add_run(
        "La integración implementada satisface con éxito todos los requerimientos de la Actividad 12, logrando transformar "
        "una aplicación móvil aislada en un cliente cloud reactivo, seguro y de nivel empresarial. La arquitectura adoptada "
        "garantiza alta disponibilidad, protección rigurosa de las credenciales de usuario y consistencia absoluta en el catálogo operativo del cliente."
    )

    doc.save(DOC_NAME)
    print(f"Documento guardado exitosamente: {DOC_NAME}")

if __name__ == "__main__":
    build_document()
