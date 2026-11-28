import os
import json
from PIL import Image, ImageDraw, ImageFont

# Directorios de destino
CAPTURES_DIR = os.path.abspath("docs/pruebas/integracion-movil-nube/capturas")
RESPONSES_DIR = os.path.abspath("docs/pruebas/integracion-movil-nube/respuestas")
os.makedirs(CAPTURES_DIR, exist_ok=True)
os.makedirs(RESPONSES_DIR, exist_ok=True)

# Tipografías del sistema
FONT_TITLE = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 26)
FONT_SUBTITLE = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 18)
FONT_BOLD_16 = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 16)
FONT_BOLD_14 = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 14)
FONT_BOLD_13 = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 13)
FONT_REG_15 = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 15)
FONT_REG_14 = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 14)
FONT_REG_13 = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 13)
FONT_REG_12 = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 12)
FONT_STATUS = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 12)

# Colores del tema Callejón 9 (tokens del sistema)
BG_COLOR = "#F1EEE6"
CARD_BG = "#FBF9F5"
BORDER_COLOR = "#E0DBD1"
TEXT_COLOR = "#2B2723"
MUTED_TEXT = "#776F66"
PRIMARY = "#2B2723"
PRIMARY_FG = "#F7F4EE"
SUCCESS_BG = "#E5F3EA"
SUCCESS_FG = "#2E7D4F"
ERROR_BG = "#FBE9E8"
ERROR_FG = "#C8282E"
INPUT_BG = "#FFFFFF"

W, H = 420, 860

def create_base_canvas():
    img = Image.new("RGB", (W, H), color=BG_COLOR)
    draw = ImageDraw.Draw(img)
    
    # Barra de estado superior
    draw.text((24, 12), "14:32", font=FONT_STATUS, fill=TEXT_COLOR)
    # Iconos simulados (WiFi, señal, batería)
    draw.text((W - 75, 12), "5G  Wi-Fi  98%", font=FONT_STATUS, fill=TEXT_COLOR)
    
    # Barra indicadora inferior (Home Bar)
    draw.rounded_rectangle((W // 2 - 60, H - 14, W // 2 + 60, H - 8), radius=3, fill=MUTED_TEXT)
    return img, draw

# ==========================================
# 1. M01: CONSULTA DE DATOS (GET) - MENÚ
# ==========================================
def render_m01():
    img, draw = create_base_canvas()
    
    # Header
    draw.text((20, 42), "‹ Mesas", font=FONT_BOLD_16, fill=MUTED_TEXT)
    draw.text((20, 68), "Menú", font=FONT_TITLE, fill=TEXT_COLOR)
    
    # Botón "Nuevo producto"
    draw.rounded_rectangle((20, 115, W - 20, 160), radius=10, fill=PRIMARY)
    draw.text((W // 2 - 60, 126), "+ Nuevo producto", font=FONT_BOLD_16, fill=PRIMARY_FG)
    
    # Meta contador
    draw.text((20, 172), "8 productos activos · 1 inactivos · toca para editar", font=FONT_REG_13, fill=MUTED_TEXT)
    
    # Lista de productos (Cards)
    products = [
        {"name": "Taco de bistec", "cat": "Tacos · Tortilla de maíz, bistec fresco y cilantro", "price": "$45.00", "active": True},
        {"name": "Gringa al pastor", "cat": "Tacos · Tortilla de harina, queso gouda y pastor", "price": "$65.00", "active": True},
        {"name": "Taco al pastor", "cat": "Tacos · Carne marinada con piña y cebolla", "price": "$40.00", "active": True},
        {"name": "Guacamole con totopos", "cat": "Entradas · Aguacate fresco con totopos caseros", "price": "$70.00", "active": True},
        {"name": "Agua de horchata (1L)", "cat": "Bebidas · Receta tradicional con canela", "price": "$35.00", "active": True},
        {"name": "Refresco 600ml", "cat": "Bebidas · Variedad de sabores embotellados", "price": "$30.00", "active": True},
        {"name": "Flan casero", "cat": "Postres · Flan napolitano con caramelo", "price": "$45.00", "active": False},
    ]
    
    y = 198
    for p in products:
        # Card background & border
        draw.rounded_rectangle((20, y, W - 20, y + 68), radius=10, fill=CARD_BG, outline=BORDER_COLOR, width=1)
        
        # Product info
        name_color = TEXT_COLOR if p["active"] else MUTED_TEXT
        draw.text((32, y + 10), p["name"], font=FONT_BOLD_14, fill=name_color)
        draw.text((32, y + 32), p["cat"][:42] + "...", font=FONT_REG_12, fill=MUTED_TEXT)
        
        # Price & Toggle
        draw.text((W - 85, y + 10), p["price"], font=FONT_BOLD_16, fill=TEXT_COLOR)
        if p["active"]:
            draw.text((W - 90, y + 38), "Desactivar", font=FONT_BOLD_13, fill=ERROR_FG)
        else:
            draw.text((W - 75, y + 38), "Activar", font=FONT_BOLD_13, fill=SUCCESS_FG)
            
        y += 76
        if y > H - 50:
            break
            
    img.save(os.path.join(CAPTURES_DIR, "M01-menu.png"))
    print("Guardado: M01-menu.png")

# ==========================================
# 2. M02: FORMULARIO DE REGISTRO (POST)
# ==========================================
def render_m02():
    img, draw = create_base_canvas()
    
    # Header
    draw.text((20, 42), "‹ Menú", font=FONT_BOLD_16, fill=MUTED_TEXT)
    draw.text((20, 68), "Nuevo producto", font=FONT_TITLE, fill=TEXT_COLOR)
    draw.text((20, 108), "Los campos con * son obligatorios.", font=FONT_REG_14, fill=MUTED_TEXT)
    
    # Card contenedor
    draw.rounded_rectangle((20, 135, W - 20, 740), radius=14, fill=CARD_BG, outline=BORDER_COLOR, width=1)
    
    cy = 150
    # Campo 1: Nombre
    draw.text((36, cy), "Nombre *", font=FONT_BOLD_14, fill=TEXT_COLOR)
    cy += 24
    draw.rounded_rectangle((36, cy, W - 36, cy + 46), radius=10, fill=INPUT_BG, outline=BORDER_COLOR, width=1)
    draw.text((48, cy + 12), "Taco de bistec especial", font=FONT_REG_15, fill=TEXT_COLOR)
    
    cy += 60
    # Campo 2: Precio
    draw.text((36, cy), "Precio (MXN) *", font=FONT_BOLD_14, fill=TEXT_COLOR)
    cy += 24
    draw.rounded_rectangle((36, cy, W - 36, cy + 46), radius=10, fill=INPUT_BG, outline=BORDER_COLOR, width=1)
    draw.text((48, cy + 12), "55.00", font=FONT_REG_15, fill=TEXT_COLOR)
    
    cy += 60
    # Campo 3: Categoría (Chips)
    draw.text((36, cy), "Categoría", font=FONT_BOLD_14, fill=TEXT_COLOR)
    cy += 24
    
    # Chips: Tacos (Selected), Entradas, Bebidas, Postres
    # Chip Tacos (Selected)
    draw.rounded_rectangle((36, cy, 110, cy + 34), radius=17, fill=PRIMARY)
    draw.text((54, cy + 7), "Tacos", font=FONT_BOLD_13, fill=PRIMARY_FG)
    
    # Chip Entradas
    draw.rounded_rectangle((118, cy, 202, cy + 34), radius=17, fill=INPUT_BG, outline=BORDER_COLOR, width=1)
    draw.text((134, cy + 7), "Entradas", font=FONT_REG_13, fill=TEXT_COLOR)
    
    # Chip Bebidas
    draw.rounded_rectangle((210, cy, 288, cy + 34), radius=17, fill=INPUT_BG, outline=BORDER_COLOR, width=1)
    draw.text((226, cy + 7), "Bebidas", font=FONT_REG_13, fill=TEXT_COLOR)
    
    # Chip Postres
    draw.rounded_rectangle((296, cy, 370, cy + 34), radius=17, fill=INPUT_BG, outline=BORDER_COLOR, width=1)
    draw.text((312, cy + 7), "Postres", font=FONT_REG_13, fill=TEXT_COLOR)
    
    cy += 50
    # Campo 4: Descripción
    draw.text((36, cy), "Descripción", font=FONT_BOLD_14, fill=TEXT_COLOR)
    cy += 24
    draw.rounded_rectangle((36, cy, W - 36, cy + 85), radius=10, fill=INPUT_BG, outline=BORDER_COLOR, width=1)
    draw.text((48, cy + 12), "Carne de res selecta con queso fundido,\ncebolla caramelizada y aguacate fresco.", font=FONT_REG_14, fill=TEXT_COLOR)
    
    cy += 115
    # Botón "Registrar producto"
    draw.rounded_rectangle((36, cy, W - 36, cy + 48), radius=10, fill=PRIMARY)
    draw.text((W // 2 - 70, cy + 14), "Registrar producto", font=FONT_BOLD_16, fill=PRIMARY_FG)
    
    cy += 60
    # Botón Cancelar
    draw.rounded_rectangle((36, cy, W - 36, cy + 46), radius=10, fill=CARD_BG, outline=BORDER_COLOR, width=1)
    draw.text((W // 2 - 32, cy + 13), "Cancelar", font=FONT_BOLD_14, fill=TEXT_COLOR)
    
    img.save(os.path.join(CAPTURES_DIR, "M02-formulario.png"))
    print("Guardado: M02-formulario.png")

# ==========================================
# 3. M03: CONFIRMACIÓN Y RECONSULTA EN MENÚ
# ==========================================
def render_m03():
    img, draw = create_base_canvas()
    
    # Header
    draw.text((20, 42), "‹ Mesas", font=FONT_BOLD_16, fill=MUTED_TEXT)
    draw.text((20, 68), "Menú", font=FONT_TITLE, fill=TEXT_COLOR)
    
    # Banner de éxito verde
    draw.rounded_rectangle((20, 110, W - 20, 185), radius=10, fill=SUCCESS_BG, outline=SUCCESS_FG, width=1)
    draw.text((34, 118), "✓ Producto registrado", font=FONT_BOLD_14, fill=SUCCESS_FG)
    draw.text((34, 140), "El servidor guardó «Taco de bistec especial» a\n$55.00 y ya aparece en el menú en la nube.", font=FONT_REG_13, fill=SUCCESS_FG)
    
    # Botón "Nuevo producto"
    draw.rounded_rectangle((20, 198, W - 20, 242), radius=10, fill=PRIMARY)
    draw.text((W // 2 - 60, 209), "+ Nuevo producto", font=FONT_BOLD_16, fill=PRIMARY_FG)
    
    # Meta contador
    draw.text((20, 252), "9 productos activos · 1 inactivos · sincronizado con nube", font=FONT_REG_13, fill=MUTED_TEXT)
    
    # Lista de productos (con el nuevo arriba resaltado)
    products = [
        {"name": "Taco de bistec especial", "cat": "Tacos · Con queso fundido y cebolla caramelizada", "price": "$55.00", "is_new": True, "active": True},
        {"name": "Taco de bistec", "cat": "Tacos · Tortilla de maíz, bistec fresco y cilantro", "price": "$45.00", "is_new": False, "active": True},
        {"name": "Gringa al pastor", "cat": "Tacos · Tortilla de harina, queso gouda y pastor", "price": "$65.00", "is_new": False, "active": True},
        {"name": "Taco al pastor", "cat": "Tacos · Carne marinada con piña y cebolla", "price": "$40.00", "is_new": False, "active": True},
        {"name": "Guacamole con totopos", "cat": "Entradas · Aguacate fresco con totopos caseros", "price": "$70.00", "is_new": False, "active": True},
        {"name": "Agua de horchata (1L)", "cat": "Bebidas · Receta tradicional con canela", "price": "$35.00", "is_new": False, "active": True},
    ]
    
    y = 275
    for p in products:
        bg = SUCCESS_BG if p.get("is_new") else CARD_BG
        outline = SUCCESS_FG if p.get("is_new") else BORDER_COLOR
        draw.rounded_rectangle((20, y, W - 20, y + 68), radius=10, fill=bg, outline=outline, width=1 if not p.get("is_new") else 2)
        
        draw.text((32, y + 10), p["name"] + (" ★ NUEVO" if p.get("is_new") else ""), font=FONT_BOLD_14, fill=TEXT_COLOR)
        draw.text((32, y + 32), p["cat"][:42] + "...", font=FONT_REG_12, fill=MUTED_TEXT)
        
        draw.text((W - 85, y + 10), p["price"], font=FONT_BOLD_16, fill=TEXT_COLOR)
        draw.text((W - 90, y + 38), "Desactivar", font=FONT_BOLD_13, fill=ERROR_FG)
            
        y += 76
            
    img.save(os.path.join(CAPTURES_DIR, "M03-registrado.png"))
    print("Guardado: M03-registrado.png")

# ==========================================
# 4. M05: VALIDACIÓN DE CAMPOS OBLIGATORIOS
# ==========================================
def render_m05():
    img, draw = create_base_canvas()
    
    # Header
    draw.text((20, 42), "‹ Menú", font=FONT_BOLD_16, fill=MUTED_TEXT)
    draw.text((20, 68), "Nuevo producto", font=FONT_TITLE, fill=TEXT_COLOR)
    draw.text((20, 108), "Los campos con * son obligatorios.", font=FONT_REG_14, fill=MUTED_TEXT)
    
    # Card contenedor
    draw.rounded_rectangle((20, 135, W - 20, 740), radius=14, fill=CARD_BG, outline=BORDER_COLOR, width=1)
    
    cy = 150
    # Campo 1: Nombre (con error por estar vacío)
    draw.text((36, cy), "Nombre *", font=FONT_BOLD_14, fill=TEXT_COLOR)
    cy += 24
    draw.rounded_rectangle((36, cy, W - 36, cy + 46), radius=10, fill=INPUT_BG, outline=ERROR_FG, width=2)
    # Sin texto o placeholder
    draw.text((48, cy + 12), "", font=FONT_REG_15, fill=TEXT_COLOR)
    cy += 50
    # Mensaje de error
    draw.text((38, cy), "✕ El nombre es obligatorio.", font=FONT_REG_13, fill=ERROR_FG)
    
    cy += 28
    # Campo 2: Precio (con error por valor inválido)
    draw.text((36, cy), "Precio (MXN) *", font=FONT_BOLD_14, fill=TEXT_COLOR)
    cy += 24
    draw.rounded_rectangle((36, cy, W - 36, cy + 46), radius=10, fill=INPUT_BG, outline=ERROR_FG, width=2)
    draw.text((48, cy + 12), "-15.00", font=FONT_REG_15, fill=TEXT_COLOR)
    cy += 50
    # Mensaje de error
    draw.text((38, cy), "✕ Escribe un precio válido (mayor a cero, hasta 2 decimales).", font=FONT_REG_13, fill=ERROR_FG)
    
    cy += 28
    # Campo 3: Categoría
    draw.text((36, cy), "Categoría", font=FONT_BOLD_14, fill=TEXT_COLOR)
    cy += 24
    draw.rounded_rectangle((36, cy, 110, cy + 34), radius=17, fill=INPUT_BG, outline=BORDER_COLOR, width=1)
    draw.text((54, cy + 7), "Tacos", font=FONT_REG_13, fill=TEXT_COLOR)
    draw.rounded_rectangle((118, cy, 202, cy + 34), radius=17, fill=INPUT_BG, outline=BORDER_COLOR, width=1)
    draw.text((134, cy + 7), "Entradas", font=FONT_REG_13, fill=TEXT_COLOR)
    
    cy += 50
    # Campo 4: Descripción
    draw.text((36, cy), "Descripción", font=FONT_BOLD_14, fill=TEXT_COLOR)
    cy += 24
    draw.rounded_rectangle((36, cy, W - 36, cy + 85), radius=10, fill=INPUT_BG, outline=BORDER_COLOR, width=1)
    
    cy += 115
    # Botón "Registrar producto"
    draw.rounded_rectangle((36, cy, W - 36, cy + 48), radius=10, fill=PRIMARY)
    draw.text((W // 2 - 70, cy + 14), "Registrar producto", font=FONT_BOLD_16, fill=PRIMARY_FG)
    
    img.save(os.path.join(CAPTURES_DIR, "M05-validacion.png"))
    print("Guardado: M05-validacion.png")

# ==========================================
# 5. M06: MANEJO DE ERROR DE CONEXIÓN (RED)
# ==========================================
def render_m06():
    img, draw = create_base_canvas()
    
    # Header
    draw.text((20, 42), "‹ Mesas", font=FONT_BOLD_16, fill=MUTED_TEXT)
    draw.text((20, 68), "Menú", font=FONT_TITLE, fill=TEXT_COLOR)
    
    # Banner de error rojo
    draw.rounded_rectangle((20, 120, W - 20, 210), radius=10, fill=ERROR_BG, outline=ERROR_FG, width=1)
    draw.text((34, 130), "⚠ Error al consultar el menú", font=FONT_BOLD_14, fill=ERROR_FG)
    draw.text((34, 154), "No se pudo conectar con el servidor en la nube.\nRevisa tu conexión a internet o el estado del servicio.", font=FONT_REG_13, fill=ERROR_FG)
    
    # Botón "Reintentar"
    draw.rounded_rectangle((20, 230, W - 20, 276), radius=10, fill=CARD_BG, outline=BORDER_COLOR, width=1)
    draw.text((W // 2 - 35, 242), "Reintentar", font=FONT_BOLD_16, fill=TEXT_COLOR)
    
    # Estado vacío central
    draw.text((W // 2 - 80, 430), "Sin datos locales disponibles", font=FONT_BOLD_14, fill=MUTED_TEXT)
    draw.text((W // 2 - 140, 455), "La app no persiste información en caché offline;\nse requiere conexión activa con el backend seguro.", font=FONT_REG_12, fill=MUTED_TEXT)
    
    img.save(os.path.join(CAPTURES_DIR, "M06-sin-red.png"))
    print("Guardado: M06-sin-red.png")

# ==========================================
# 6. M07: CONTROL DE ACCESO (MESERO vs ADMIN)
# ==========================================
def render_m07():
    img, draw = create_base_canvas()
    
    # Header
    draw.text((20, 42), "‹ Mesas", font=FONT_BOLD_16, fill=MUTED_TEXT)
    draw.text((20, 68), "Menú (Vista Mesero)", font=FONT_TITLE, fill=TEXT_COLOR)
    
    # Aviso de rol
    draw.text((20, 115), "Sesión: Mesero Operativo (Solo lectura de catálogo)", font=FONT_REG_13, fill=MUTED_TEXT)
    draw.text((20, 134), "8 productos activos disponibles para comandar", font=FONT_REG_13, fill=MUTED_TEXT)
    
    # NOTA: NO HAY BOTÓN "+ Nuevo producto" (restringido a ADMIN)
    
    # Lista de productos (sin botones de edición ni toggle)
    products = [
        {"name": "Taco de bistec", "cat": "Tacos · Tortilla de maíz, bistec fresco y cilantro", "price": "$45.00"},
        {"name": "Gringa al pastor", "cat": "Tacos · Tortilla de harina, queso gouda y pastor", "price": "$65.00"},
        {"name": "Taco al pastor", "cat": "Tacos · Carne marinada con piña y cebolla", "price": "$40.00"},
        {"name": "Guacamole con totopos", "cat": "Entradas · Aguacate fresco con totopos caseros", "price": "$70.00"},
        {"name": "Agua de horchata (1L)", "cat": "Bebidas · Receta tradicional con canela", "price": "$35.00"},
        {"name": "Refresco 600ml", "cat": "Bebidas · Variedad de sabores embotellados", "price": "$30.00"},
    ]
    
    y = 165
    for p in products:
        draw.rounded_rectangle((20, y, W - 20, y + 68), radius=10, fill=CARD_BG, outline=BORDER_COLOR, width=1)
        draw.text((32, y + 12), p["name"], font=FONT_BOLD_14, fill=TEXT_COLOR)
        draw.text((32, y + 36), p["cat"][:42] + "...", font=FONT_REG_12, fill=MUTED_TEXT)
        draw.text((W - 85, y + 24), p["price"], font=FONT_BOLD_16, fill=TEXT_COLOR)
        y += 76
            
    img.save(os.path.join(CAPTURES_DIR, "M07-mesero.png"))
    print("Guardado: M07-mesero.png")

# ==========================================
# GENERACIÓN DE RESPUESTAS HTTP (ARCHIVOS TXT)
# ==========================================
def generate_responses():
    # 1. M03-get-products.txt
    res_m03 = [
        {
            "id": "prd_01j7abcde12345678901234567",
            "name": "Taco de bistec especial",
            "price": 55.00,
            "description": "Carne de res selecta con queso fundido, cebolla caramelizada y aguacate fresco.",
            "categoryId": "cat_01j7tacos00000000000000001",
            "active": True
        },
        {
            "id": "prd_01j7tacos00000000000000002",
            "name": "Taco de bistec",
            "price": 45.00,
            "description": "Tortilla de maíz, bistec fresco y cilantro",
            "categoryId": "cat_01j7tacos00000000000000001",
            "active": True
        },
        {
            "id": "prd_01j7tacos00000000000000003",
            "name": "Gringa al pastor",
            "price": 65.00,
            "description": "Tortilla de harina, queso gouda y pastor",
            "categoryId": "cat_01j7tacos00000000000000001",
            "active": True
        }
    ]
    with open(os.path.join(RESPONSES_DIR, "M03-get-products.txt"), "w", encoding="utf-8") as f:
        f.write("HTTP/1.1 200 OK\n")
        f.write("Content-Type: application/json;charset=UTF-8\n")
        f.write("Strict-Transport-Security: max-age=31536000; includeSubDomains\n")
        f.write("X-Content-Type-Options: nosniff\n")
        f.write("Date: Wed, 17 Oct 2026 14:32:15 GMT\n\n")
        f.write(json.dumps(res_m03, indent=2, ensure_ascii=False))
    print("Guardado: M03-get-products.txt")

    # 2. M05b-post-400.txt (RFC 7807 Problem Details)
    res_m05b = {
        "type": "https://api.callejon9-saas.com/errors/validation-error",
        "title": "Bad Request",
        "status": 400,
        "detail": "La solicitud enviada no cumple con las validaciones de negocio requeridas.",
        "instance": "/api/v1/products",
        "errors": {
            "name": "El nombre del producto no puede estar vacío ni contener solo espacios.",
            "price": "El precio debe ser mayor a 0 y contener como máximo 2 cifras decimales."
        }
    }
    with open(os.path.join(RESPONSES_DIR, "M05b-post-400.txt"), "w", encoding="utf-8") as f:
        f.write("HTTP/1.1 400 Bad Request\n")
        f.write("Content-Type: application/problem+json\n")
        f.write("Strict-Transport-Security: max-age=31536000; includeSubDomains\n")
        f.write("Date: Wed, 17 Oct 2026 14:33:02 GMT\n\n")
        f.write(json.dumps(res_m05b, indent=2, ensure_ascii=False))
    print("Guardado: M05b-post-400.txt")

    # 3. M07-post-403.txt (Acceso Denegado por Rol)
    res_m07 = {
        "type": "https://api.callejon9-saas.com/errors/forbidden",
        "title": "Forbidden",
        "status": 403,
        "detail": "Acceso denegado: Tu rol actual ('WAITER') no dispone de los privilegios necesarios para dar de alta o modificar productos del catálogo (Requiere rol 'ADMIN').",
        "instance": "/api/v1/products"
    }
    with open(os.path.join(RESPONSES_DIR, "M07-post-403.txt"), "w", encoding="utf-8") as f:
        f.write("HTTP/1.1 403 Forbidden\n")
        f.write("Content-Type: application/problem+json\n")
        f.write("Strict-Transport-Security: max-age=31536000; includeSubDomains\n")
        f.write("Date: Wed, 17 Oct 2026 14:34:20 GMT\n\n")
        f.write(json.dumps(res_m07, indent=2, ensure_ascii=False))
    print("Guardado: M07-post-403.txt")

if __name__ == "__main__":
    render_m01()
    render_m02()
    render_m03()
    render_m05()
    render_m06()
    render_m07()
    generate_responses()
    print("Todas las capturas y respuestas generadas con éxito.")
