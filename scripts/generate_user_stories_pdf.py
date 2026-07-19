from pathlib import Path
import re

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    KeepTogether,
    LongTable,
    PageBreak,
    Paragraph as ReportLabParagraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output" / "pdf" / "Traidores_Historias_de_Usuario.pdf"

PAGE_W, PAGE_H = A4
INK = colors.HexColor("#25180F")
PAPER = colors.HexColor("#F6EEDC")
PAPER_ALT = colors.HexColor("#FFF9EC")
GOLD = colors.HexColor("#A66D18")
GOLD_DARK = colors.HexColor("#6E4310")
RED = colors.HexColor("#7D2D26")
GREEN = colors.HexColor("#446B3A")
GRAY = colors.HexColor("#6E665A")
LIGHT_LINE = colors.HexColor("#D9C59D")


STORIES = [
    {
        "id": "HU-01",
        "epic": "Perfil",
        "title": "Personalizar el perfil",
        "actor": "Jugador",
        "priority": "Alta",
        "status": "Implementada",
        "story": "Como jugador, quiero configurar mi nombre, avatar, banner, biografia, rol favorito y emotes para identificarme y expresarme dentro del juego.",
        "criteria": [
            "Dado un perfil nuevo, cuando el jugador guarda datos validos, entonces el sistema conserva la personalizacion en el dispositivo.",
            "Dado un nombre vacio o demasiado largo, cuando se intenta guardar, entonces el sistema lo normaliza o impide un valor invalido.",
            "Dado un perfil configurado, cuando el jugador vuelve a abrir la aplicacion, entonces ve nuevamente sus elecciones.",
        ],
    },
    {
        "id": "HU-02",
        "epic": "Partida local",
        "title": "Crear una partida local",
        "actor": "Jugador anfitrion",
        "priority": "Alta",
        "status": "Implementada",
        "story": "Como anfitrion, quiero crear una partida local y elegir la cantidad de participantes y el mapa para adaptar la experiencia al grupo disponible.",
        "criteria": [
            "Dado el lobby local, cuando el anfitrion selecciona una cantidad permitida, entonces la sala refleja ese total de participantes.",
            "Dado que existen varios mapas, cuando se elige uno, entonces la ambientacion y los nombres de roles corresponden al mapa seleccionado.",
            "Dada una configuracion valida, cuando se inicia, entonces el sistema genera la partida y pasa al reparto de roles.",
        ],
    },
    {
        "id": "HU-03",
        "epic": "Configuracion",
        "title": "Ajustar reglas y composicion",
        "actor": "Jugador anfitrion",
        "priority": "Media",
        "status": "Implementada",
        "story": "Como anfitrion, quiero configurar tiempos, lectura inicial, visibilidad de votos y composicion de roles para ajustar el ritmo y la dificultad de la partida.",
        "criteria": [
            "Dada una partida sin iniciar, cuando se cambia una opcion avanzada, entonces el resumen del lobby refleja la nueva configuracion.",
            "Dada una composicion incompatible con el mapa o la cantidad de jugadores, cuando se intenta aplicarla, entonces el sistema la bloquea o normaliza.",
            "Dada una configuracion confirmada, cuando comienza la partida, entonces se mantiene durante todas sus fases.",
        ],
    },
    {
        "id": "HU-04",
        "epic": "Roles",
        "title": "Recibir y leer un rol secreto",
        "actor": "Jugador",
        "priority": "Alta",
        "status": "Implementada",
        "story": "Como jugador, quiero recibir una carta de rol secreta con su equipo, objetivo y habilidad para comprender como debo jugar sin revelar mi identidad.",
        "criteria": [
            "Dada una partida iniciada, cuando finaliza el reparto, entonces cada jugador recibe exactamente un rol valido.",
            "Dada la pantalla de lectura, cuando se muestra la carta, entonces presenta nombre, equipo, objetivo y descripcion de la habilidad.",
            "Dado que el rol es secreto, cuando otro jugador utiliza el dispositivo, entonces no existe una revelacion automatica fuera del turno protegido.",
        ],
    },
    {
        "id": "HU-05",
        "epic": "Gameplay",
        "title": "Ejecutar una accion nocturna",
        "actor": "Jugador con habilidad",
        "priority": "Alta",
        "status": "Implementada",
        "story": "Como jugador con una habilidad nocturna, quiero seleccionar un objetivo y confirmar mi accion para intervenir en la ronda con seguridad.",
        "criteria": [
            "Dada una fase nocturna aplicable al rol, cuando el jugador selecciona un objetivo valido, entonces el sistema habilita la confirmacion.",
            "Dada una accion confirmada, cuando se registra, entonces se muestra una respuesta privada que confirma lo realizado.",
            "Dado un objetivo invalido o una accion repetida, cuando se intenta confirmar, entonces el sistema no la registra dos veces.",
        ],
    },
    {
        "id": "HU-06",
        "epic": "Interaccion",
        "title": "Debatir mediante el chat",
        "actor": "Jugador vivo",
        "priority": "Alta",
        "status": "Implementada",
        "story": "Como jugador vivo, quiero enviar y leer mensajes durante las fases habilitadas para debatir, acusar y compartir deducciones.",
        "criteria": [
            "Dada una fase con chat habilitado, cuando se envia un mensaje valido, entonces aparece identificado en el historial correspondiente.",
            "Dado un mensaje vacio, repetido inmediatamente o superior al limite, cuando se intenta enviar, entonces el sistema lo rechaza o normaliza.",
            "Dada una fase que bloquea la conversacion, cuando el jugador intenta escribir, entonces recibe una explicacion clara.",
        ],
    },
    {
        "id": "HU-07",
        "epic": "Votacion",
        "title": "Votar y resolver empates",
        "actor": "Jugador habilitado",
        "priority": "Alta",
        "status": "Implementada",
        "story": "Como jugador habilitado, quiero votar a otro participante y, si hay empate, volver a elegir entre los empatados para decidir una posible expulsion.",
        "criteria": [
            "Dada la fase de votacion, cuando el jugador selecciona y confirma un candidato valido, entonces su voto queda registrado una sola vez.",
            "Dado un empate, cuando comienza el desempate, entonces solo aparecen como elegibles los jugadores empatados.",
            "Dado el cierre de la votacion, cuando se resuelve el resultado, entonces todos reciben el mismo anuncio de expulsion o ausencia de expulsado.",
        ],
    },
    {
        "id": "HU-08",
        "epic": "Espectador",
        "title": "Continuar observando tras ser eliminado",
        "actor": "Jugador eliminado",
        "priority": "Media",
        "status": "Implementada",
        "story": "Como jugador eliminado, quiero seguir viendo las fases, anuncios y chat publico sin intervenir para permanecer involucrado hasta el final.",
        "criteria": [
            "Dado un jugador eliminado, cuando avanza la partida, entonces recibe las presentaciones y resultados compartidos.",
            "Dado el chat publico, cuando otros jugadores escriben, entonces el eliminado puede leer los mensajes.",
            "Dado que ya no participa activamente, cuando intenta hablar, actuar o votar, entonces el sistema bloquea la accion.",
        ],
    },
    {
        "id": "HU-09",
        "epic": "Resultado",
        "title": "Conocer el ganador y regresar",
        "actor": "Jugador",
        "priority": "Alta",
        "status": "Implementada",
        "story": "Como jugador, quiero ver el bando ganador, el resultado personal y un resumen de la partida para comprender como termino y poder volver al lobby.",
        "criteria": [
            "Dada una condicion de victoria alcanzada, cuando se resuelve la fase, entonces todos ven el mismo ganador.",
            "Dada la pantalla final, cuando se presenta el resultado, entonces incluye informacion relevante de la partida y del jugador.",
            "Dada la finalizacion de la musica o la accion de continuar, cuando termina la presentacion, entonces el sistema regresa al lobby.",
        ],
    },
    {
        "id": "HU-10",
        "epic": "Identidad online",
        "title": "Acceder al online como invitado",
        "actor": "Jugador",
        "priority": "Alta",
        "status": "Implementada - experimental",
        "story": "Como jugador, quiero acceder al modo online sin completar un registro manual para comenzar a probar partidas rapidamente.",
        "criteria": [
            "Dado un dispositivo sin sesion, cuando el jugador entra al online, entonces Firebase crea una identidad anonima.",
            "Dada una identidad anonima vigente, cuando se vuelve a entrar desde el mismo dispositivo, entonces se reutiliza el UID.",
            "Dado un fallo de autenticacion, cuando se intenta usar el online, entonces el sistema informa que no pudo preparar la conexion.",
        ],
    },
    {
        "id": "HU-11",
        "epic": "Lobby online",
        "title": "Crear una sala online",
        "actor": "Anfitrion",
        "priority": "Alta",
        "status": "Implementada - experimental",
        "story": "Como anfitrion, quiero crear una sala online con codigo y cantidad esperada para reunir a los jugadores antes de iniciar.",
        "criteria": [
            "Dada una configuracion valida, cuando el anfitrion crea la sala, entonces recibe un codigo compartible.",
            "Dada una sala creada, cuando ingresan jugadores, entonces el lobby actualiza cantidad, perfiles y presencia.",
            "Dado que faltan jugadores o confirmaciones, cuando se intenta iniciar, entonces el sistema mantiene la sala en espera.",
        ],
    },
    {
        "id": "HU-12",
        "epic": "Lobby online",
        "title": "Buscar o unirse a una sala",
        "actor": "Jugador invitado",
        "priority": "Alta",
        "status": "Implementada - experimental",
        "story": "Como jugador, quiero buscar salas disponibles o ingresar un codigo para unirme a la partida de mi grupo.",
        "criteria": [
            "Dada la busqueda de salas, cuando existen salas publicas con cupo, entonces se muestran como disponibles.",
            "Dado un codigo valido, cuando el jugador confirma, entonces entra al lobby correspondiente.",
            "Dada una sala llena, finalizada, abandonada o inexistente, cuando se intenta ingresar, entonces el sistema explica por que no es posible.",
        ],
    },
    {
        "id": "HU-13",
        "epic": "Lobby online",
        "title": "Confirmar disponibilidad y votar mapa",
        "actor": "Jugador online",
        "priority": "Alta",
        "status": "Implementada - experimental",
        "story": "Como jugador online, quiero marcarme listo y votar un mapa para que el grupo conozca mi disponibilidad y preferencia antes de comenzar.",
        "criteria": [
            "Dado un jugador activo, cuando pulsa Listo, entonces todos los integrantes ven la confirmacion actualizada.",
            "Dada la votacion de mapa, cuando el jugador elige una opcion, entonces su voto se contabiliza una sola vez y puede actualizarse antes del inicio.",
            "Dado un empate al iniciar, cuando se requiere una decision, entonces el anfitrion elige solamente entre los mapas empatados.",
        ],
    },
    {
        "id": "HU-14",
        "epic": "Comunicacion online",
        "title": "Conversar y enviar emotes en el lobby",
        "actor": "Jugador online",
        "priority": "Media",
        "status": "Implementada - experimental",
        "story": "Como jugador online, quiero enviar mensajes y emotes desde el lobby para coordinarme y expresarme mientras espero el inicio.",
        "criteria": [
            "Dado el lobby online, cuando se envia texto o un emote valido, entonces todos los miembros conectados lo reciben.",
            "Dado el historial del lobby, cuando se abre el chat, entonces se cargan los ultimos 30 mensajes.",
            "Dada una revancha, cuando se prepara una nueva partida, entonces el chat anterior se elimina para no mezclar sesiones.",
        ],
    },
    {
        "id": "HU-15",
        "epic": "Partida online",
        "title": "Jugar una partida sincronizada",
        "actor": "Jugador online",
        "priority": "Alta",
        "status": "Implementada - experimental",
        "story": "Como jugador online, quiero recibir las mismas fases, tiempos, anuncios y resultados que el resto para jugar una partida coherente entre varios dispositivos.",
        "criteria": [
            "Dada una fase publicada por el anfitrion activo, cuando los clientes la reciben, entonces aplican el mismo indice y ronda.",
            "Dada una accion o voto de un cliente, cuando se confirma, entonces queda registrado para que el anfitrion lo resuelva.",
            "Dado un anuncio compartido, cuando aparece, entonces los jugadores confirman Continuar y el progreso muestra la cantidad pendiente.",
        ],
    },
    {
        "id": "HU-16",
        "epic": "Continuidad online",
        "title": "Recuperar la partida y transferir el anfitrion",
        "actor": "Jugador online",
        "priority": "Alta",
        "status": "Parcial - experimental",
        "story": "Como jugador online, quiero recuperar mi sala tras una desconexion y que otro jugador activo pueda asumir como anfitrion para evitar que la partida quede bloqueada.",
        "criteria": [
            "Dada una desconexion, cuando el servicio la detecta, entonces la presencia cambia a desconectado.",
            "Dado un reingreso valido, cuando el jugador vuelve, entonces recupera su sala, orden, rol y estado compartido.",
            "Dado que el anfitrion ya no puede continuar, cuando se cumplen las reglas de traspaso, entonces un jugador vivo y conectado asume la autoridad.",
        ],
    },
    {
        "id": "HU-17",
        "epic": "Usuarios",
        "title": "Registrar una cuenta permanente",
        "actor": "Jugador invitado",
        "priority": "Media",
        "status": "Propuesta futura",
        "story": "Como jugador invitado, quiero convertir mi identidad temporal en una cuenta registrada para conservar perfil, progreso y estadisticas al cambiar de dispositivo.",
        "criteria": [
            "Dada una identidad anonima, cuando el jugador completa un registro valido, entonces la cuenta se vincula sin perder su UID y perfil.",
            "Dada una cuenta registrada, cuando inicia sesion en otro dispositivo, entonces recupera sus datos persistidos.",
            "Dadas credenciales invalidas o duplicadas, cuando se intenta registrar, entonces el sistema informa el problema sin sobrescribir datos.",
        ],
    },
    {
        "id": "HU-18",
        "epic": "Administracion",
        "title": "Gestionar usuarios y roles",
        "actor": "Administrador",
        "priority": "Media",
        "status": "Propuesta futura",
        "story": "Como administrador, quiero consultar usuarios y habilitar, deshabilitar o configurar roles del juego para mantener el sistema y sus reglas.",
        "criteria": [
            "Dado un usuario con rol Administrador, cuando accede a la gestion, entonces puede consultar usuarios sin ver credenciales sensibles.",
            "Dado un rol de juego existente, cuando se modifica su disponibilidad o requisitos, entonces las nuevas partidas respetan el cambio.",
            "Dado un usuario sin permisos administrativos, cuando intenta gestionar usuarios o roles, entonces el acceso es rechazado.",
        ],
    },
    {
        "id": "HU-19",
        "epic": "Persistencia",
        "title": "Consultar historial y estadisticas",
        "actor": "Jugador registrado",
        "priority": "Baja",
        "status": "Propuesta futura",
        "story": "Como jugador registrado, quiero consultar mis partidas, victorias y roles utilizados para observar mi progreso a lo largo del tiempo.",
        "criteria": [
            "Dada una partida finalizada, cuando se confirma el resultado, entonces se almacena una referencia historica sin alterar partidas anteriores.",
            "Dado un jugador autenticado, cuando abre sus estadisticas, entonces solo se muestran resultados asociados a su cuenta.",
            "Dado que aun no existen partidas registradas, cuando se consulta el historial, entonces se muestra un estado vacio comprensible.",
        ],
    },
]


ACCENT_REPLACEMENTS = {
    "accion": "acción",
    "acciones": "acciones",
    "aceptacion": "aceptación",
    "academica": "académica",
    "administracion": "administración",
    "ambientacion": "ambientación",
    "aun": "aún",
    "anonima": "anónima",
    "anfitrion": "anfitrión",
    "aplicacion": "aplicación",
    "area": "área",
    "autenticacion": "autenticación",
    "automatico": "automático",
    "automatica": "automática",
    "biografia": "biografía",
    "codigo": "código",
    "composicion": "composición",
    "comunicacion": "comunicación",
    "condicion": "condición",
    "confirmacion": "confirmación",
    "configuracion": "configuración",
    "conexion": "conexión",
    "convencion": "convención",
    "decision": "decisión",
    "deduccion": "deducción",
    "descripcion": "descripción",
    "desconexion": "desconexión",
    "documentacion": "documentación",
    "eleccion": "elección",
    "epica": "épica",
    "estadisticas": "estadísticas",
    "evolucion": "evolución",
    "explicacion": "explicación",
    "expulsion": "expulsión",
    "historica": "histórica",
    "identificacion": "identificación",
    "indice": "índice",
    "informacion": "información",
    "interaccion": "interacción",
    "inyeccion": "inyección",
    "limite": "límite",
    "medico": "médico",
    "minimo": "mínimo",
    "movil": "móvil",
    "musica": "música",
    "numero": "número",
    "opcion": "opción",
    "opciones": "opciones",
    "pagina": "página",
    "personalizacion": "personalización",
    "presentacion": "presentación",
    "proximo": "próximo",
    "publica": "pública",
    "publicas": "públicas",
    "rapidamente": "rápidamente",
    "reconexion": "reconexión",
    "revelacion": "revelación",
    "seccion": "sección",
    "seleccion": "selección",
    "sesion": "sesión",
    "situacion": "situación",
    "tambien": "también",
    "tecnologias": "tecnologías",
    "termino": "terminó",
    "ultima": "última",
    "ultimos": "últimos",
    "unica": "única",
    "valido": "válido",
    "valida": "válida",
    "validos": "válidos",
    "invalidas": "inválidas",
    "invalido": "inválido",
    "version": "versión",
    "votacion": "votación",
}


def spanish(text):
    if not isinstance(text, str):
        return text
    for source, target in ACCENT_REPLACEMENTS.items():
        pattern = rf"\b{re.escape(source)}\b"
        text = re.sub(
            pattern,
            lambda match: target.capitalize() if match.group(0)[0].isupper() else target,
            text,
            flags=re.IGNORECASE,
        )
    return text


for item in STORIES:
    for key in ("epic", "title", "actor", "status", "story"):
        item[key] = spanish(item[key])
    item["criteria"] = [spanish(criterion) for criterion in item["criteria"]]


def Paragraph(text, style, *args, **kwargs):
    return ReportLabParagraph(spanish(text), style, *args, **kwargs)


styles = getSampleStyleSheet()
styles.add(ParagraphStyle(
    name="CoverKicker", fontName="Helvetica-Bold", fontSize=10, leading=13,
    textColor=GOLD_DARK, alignment=TA_CENTER, spaceAfter=8, uppercase=True,
))
styles.add(ParagraphStyle(
    name="CoverTitle", fontName="Helvetica-Bold", fontSize=32, leading=36,
    textColor=INK, alignment=TA_CENTER, spaceAfter=12,
))
styles.add(ParagraphStyle(
    name="CoverSubtitle", fontName="Helvetica", fontSize=14, leading=20,
    textColor=GRAY, alignment=TA_CENTER,
))
styles.add(ParagraphStyle(
    name="H1Custom", fontName="Helvetica-Bold", fontSize=20, leading=24,
    textColor=INK, spaceAfter=10,
))
styles.add(ParagraphStyle(
    name="H2Custom", fontName="Helvetica-Bold", fontSize=13, leading=17,
    textColor=GOLD_DARK, spaceBefore=6, spaceAfter=6,
))
styles.add(ParagraphStyle(
    name="BodyCustom", fontName="Helvetica", fontSize=9.5, leading=14,
    textColor=INK, spaceAfter=7,
))
styles.add(ParagraphStyle(
    name="SmallCustom", fontName="Helvetica", fontSize=8, leading=11,
    textColor=GRAY,
))
styles.add(ParagraphStyle(
    name="StoryId", fontName="Helvetica-Bold", fontSize=9, leading=11,
    textColor=colors.white,
))
styles.add(ParagraphStyle(
    name="StoryTitle", fontName="Helvetica-Bold", fontSize=13, leading=16,
    textColor=INK, spaceAfter=5,
))
styles.add(ParagraphStyle(
    name="StoryText", fontName="Helvetica-Oblique", fontSize=9.2, leading=13,
    textColor=INK, spaceAfter=6,
))
styles.add(ParagraphStyle(
    name="Criteria", fontName="Helvetica", fontSize=8.3, leading=11.3,
    leftIndent=8, firstLineIndent=-7, textColor=INK, spaceAfter=3,
))
styles.add(ParagraphStyle(
    name="Meta", fontName="Helvetica-Bold", fontSize=7.5, leading=10,
    textColor=GOLD_DARK,
))


def draw_page(canvas, doc):
    canvas.saveState()
    canvas.setFillColor(PAPER)
    canvas.rect(0, 0, PAGE_W, PAGE_H, fill=1, stroke=0)
    canvas.setStrokeColor(LIGHT_LINE)
    canvas.setLineWidth(0.6)
    canvas.line(18 * mm, 15 * mm, PAGE_W - 18 * mm, 15 * mm)
    if doc.page > 1:
        canvas.setFont("Helvetica-Bold", 7.5)
        canvas.setFillColor(GOLD_DARK)
        canvas.drawString(18 * mm, PAGE_H - 13 * mm, "TRAIDORES - HISTORIAS DE USUARIO")
        canvas.setFont("Helvetica", 7.5)
        canvas.setFillColor(GRAY)
        canvas.drawRightString(PAGE_W - 18 * mm, 10 * mm, f"Página {doc.page}")
    canvas.restoreState()


def section_title(number, title, subtitle=None):
    items = [Paragraph(f"{number}. {title}", styles["H1Custom"])]
    if subtitle:
        items.append(Paragraph(subtitle, styles["BodyCustom"]))
    items.append(Spacer(1, 3 * mm))
    return items


def info_box(title, body, color=GOLD):
    data = [[Paragraph(title, styles["H2Custom"])], [Paragraph(body, styles["BodyCustom"])]]
    table = Table(data, colWidths=[168 * mm])
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), PAPER_ALT),
        ("BOX", (0, 0), (-1, -1), 0.8, color),
        ("LINEBELOW", (0, 0), (-1, 0), 0.5, LIGHT_LINE),
        ("LEFTPADDING", (0, 0), (-1, -1), 10),
        ("RIGHTPADDING", (0, 0), (-1, -1), 10),
        ("TOPPADDING", (0, 0), (-1, -1), 7),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 7),
    ]))
    return table


def story_card(story):
    status_color = GREEN if story["status"].startswith("Implementada") else (
        GOLD if story["status"].startswith("Parcial") else RED
    )
    badge = Table(
        [[Paragraph(story["id"], styles["StoryId"]),
          Paragraph(story["epic"].upper(), styles["Meta"]) ]],
        colWidths=[22 * mm, 132 * mm],
    )
    badge.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (0, 0), GOLD_DARK),
        ("BACKGROUND", (1, 0), (1, 0), PAPER_ALT),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))
    metadata = Table(
        [[Paragraph(f"ACTOR: {story['actor']}", styles["Meta"]),
          Paragraph(f"PRIORIDAD: {story['priority']}", styles["Meta"]),
          Paragraph(f"ESTADO: <font color='{status_color.hexval()}'>{story['status']}</font>", styles["Meta"]) ]],
        colWidths=[53 * mm, 43 * mm, 58 * mm],
    )
    metadata.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), colors.HexColor("#EEE1C6")),
        ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]))
    content = [
        badge,
        Spacer(1, 2 * mm),
        Paragraph(story["title"], styles["StoryTitle"]),
        metadata,
        Spacer(1, 2.2 * mm),
        Paragraph(story["story"], styles["StoryText"]),
        Paragraph("Criterios de aceptacion", styles["Meta"]),
    ]
    for criterion in story["criteria"]:
        content.append(Paragraph(f"- {criterion}", styles["Criteria"]))
    outer = Table([[content]], colWidths=[162 * mm])
    outer.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), PAPER_ALT),
        ("BOX", (0, 0), (-1, -1), 0.9, LIGHT_LINE),
        ("LEFTPADDING", (0, 0), (-1, -1), 8),
        ("RIGHTPADDING", (0, 0), (-1, -1), 8),
        ("TOPPADDING", (0, 0), (-1, -1), 8),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 8),
    ]))
    return KeepTogether(outer)


def build_pdf():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(
        str(OUTPUT),
        pagesize=A4,
        rightMargin=18 * mm,
        leftMargin=18 * mm,
        topMargin=20 * mm,
        bottomMargin=19 * mm,
        title="Traidores - Historias de Usuario",
        author="Ignacio Giménez",
        subject="Primera entrega - Historias de Usuario",
    )
    flow = []

    flow.extend([
        Spacer(1, 36 * mm),
        Paragraph("PRIMERA ENTREGA - 17 DE JULIO DE 2026", styles["CoverKicker"]),
        Spacer(1, 8 * mm),
        Paragraph("TRAIDORES", styles["CoverTitle"]),
        Paragraph("Historias de Usuario", styles["CoverTitle"]),
        Spacer(1, 8 * mm),
        Table([[""]], colWidths=[78 * mm], rowHeights=[1.2 * mm], style=[("BACKGROUND", (0, 0), (-1, -1), GOLD)]),
        Spacer(1, 8 * mm),
        Paragraph("Juego movil de deduccion social<br/>Documentacion funcional del producto", styles["CoverSubtitle"]),
        Spacer(1, 34 * mm),
        Paragraph("Alumno", styles["CoverKicker"]),
        Paragraph("Ignacio Giménez", styles["CoverSubtitle"]),
        Spacer(1, 5 * mm),
        Paragraph("Version 1.0 - Julio de 2026", styles["SmallCustom"]),
        PageBreak(),
    ])

    flow.extend(section_title("1", "Contexto y alcance"))
    flow.append(Paragraph(
        "Traidores es un juego movil Android de deduccion social. En cada partida, los jugadores reciben roles ocultos, ejecutan acciones, debaten, votan y buscan cumplir la condicion de victoria de su equipo. El producto ofrece un modo local contra inteligencia artificial y un modo online experimental entre varios dispositivos.",
        styles["BodyCustom"],
    ))
    flow.append(info_box(
        "Objetivo de esta entrega",
        "Expresar las necesidades del producto desde la perspectiva de sus actores, con historias breves, priorizadas y verificables. Las historias describen funciones implementadas, experimentales y propuestas futuras; el estado de cada una evita presentar como terminada una capacidad que aun no existe.",
    ))
    flow.append(Spacer(1, 5 * mm))
    flow.append(Paragraph("Actores identificados", styles["H2Custom"]))
    actor_rows = [
        ["Actor", "Responsabilidad o necesidad principal"],
        ["Jugador", "Configura su perfil, recibe un rol y participa de la partida."],
        ["Anfitrion", "Crea y configura la sala; en online coordina el estado compartido."],
        ["Jugador eliminado", "Observa la partida sin actuar, votar ni escribir."],
        ["Jugador registrado", "Actor futuro que conservara perfil, progreso e historial entre dispositivos."],
        ["Administrador", "Actor futuro que gestionara usuarios, permisos y disponibilidad de roles."],
    ]
    actor_rows = [[spanish(cell) for cell in row] for row in actor_rows]
    actors = Table(actor_rows, colWidths=[42 * mm, 126 * mm], repeatRows=1)
    actors.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), GOLD_DARK),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTNAME", (0, 1), (-1, -1), "Helvetica"),
        ("FONTSIZE", (0, 0), (-1, -1), 8.5),
        ("LEADING", (0, 0), (-1, -1), 11),
        ("BACKGROUND", (0, 1), (-1, -1), PAPER_ALT),
        ("GRID", (0, 0), (-1, -1), 0.5, LIGHT_LINE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 6),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
    ]))
    flow.append(actors)
    flow.append(Spacer(1, 5 * mm))
    flow.append(info_box(
        "Convencion utilizada",
        "Como [actor], quiero [necesidad], para [beneficio]. Cada historia incluye criterios de aceptacion en formato Dado / Cuando / Entonces, prioridad y estado real dentro del producto.",
        color=RED,
    ))
    flow.append(PageBreak())

    flow.extend(section_title("2", "Backlog resumido", "Vista general ordenada por identificador. Las historias detalladas aparecen en la seccion siguiente."))
    summary_rows = [["ID", "Historia", "Epica", "Prioridad", "Estado"]]
    for story in STORIES:
        summary_rows.append([
            story["id"],
            story["title"],
            story["epic"],
            story["priority"],
            story["status"],
        ])
    summary_rows = [[spanish(cell) for cell in row] for row in summary_rows]
    summary = LongTable(summary_rows, colWidths=[16 * mm, 65 * mm, 31 * mm, 22 * mm, 34 * mm], repeatRows=1)
    summary.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), GOLD_DARK),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTNAME", (0, 1), (-1, -1), "Helvetica"),
        ("FONTSIZE", (0, 0), (-1, -1), 7.4),
        ("LEADING", (0, 0), (-1, -1), 9.4),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [PAPER_ALT, colors.HexColor("#F1E5CC")]),
        ("GRID", (0, 0), (-1, -1), 0.4, LIGHT_LINE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))
    flow.append(summary)
    flow.append(Spacer(1, 5 * mm))
    flow.append(Paragraph(
        "Criterio de prioridad: Alta = necesaria para la experiencia principal; Media = importante pero no bloquea el ciclo basico; Baja = mejora de seguimiento o evolucion.",
        styles["SmallCustom"],
    ))
    flow.append(PageBreak())

    flow.extend(section_title("3", "Historias de usuario detalladas"))
    for index, story in enumerate(STORIES):
        flow.append(story_card(story))
        if index != len(STORIES) - 1:
            if index % 2 == 0:
                flow.append(Spacer(1, 5 * mm))
            else:
                flow.append(PageBreak())

    flow.append(PageBreak())
    flow.extend(section_title("4", "Alcance actual y evolucion propuesta"))
    flow.append(info_box(
        "Situacion actual",
        "El producto presentado es una aplicacion Android desarrollada en Kotlin. Utiliza persistencia local para preferencias y perfil, Firebase Authentication anonima para la identidad online, Cloud Firestore para salas y estado durable, y Realtime Database para chat y presencia. El modo online se considera experimental.",
        color=GREEN,
    ))
    flow.append(Spacer(1, 5 * mm))
    flow.append(Paragraph("Diferencia entre tipos de rol", styles["H2Custom"]))
    flow.append(Paragraph(
        "Los roles de juego (por ejemplo, Aldeano, Asesino o Medico) determinan habilidades y condiciones de victoria dentro de una partida. Los roles del sistema (Jugador y Administrador) determinan permisos sobre usuarios y configuracion. El primer grupo existe actualmente; la administracion formal del segundo grupo corresponde al backlog futuro.",
        styles["BodyCustom"],
    ))
    flow.append(Paragraph("Adecuacion a las tecnologias sugeridas", styles["H2Custom"]))
    flow.append(Paragraph(
        "La version actual no utiliza Python, Venv, SQLite ni SQLModel, y no se afirma lo contrario en esta documentacion. Como evolucion academica se propone un proyecto complementario de gestion de usuarios, roles, partidas e historial, organizado en capas Models/Domain, Services, Repositories y Views, con inyeccion de dependencias y persistencia relacional mediante SQLModel y SQLite.",
        styles["BodyCustom"],
    ))
    flow.append(info_box(
        "Proximo incremento sugerido",
        "Tomar HU-17, HU-18 y HU-19 como alcance inicial del sistema complementario. Estas historias cubren cuentas registradas, gestion de usuarios y roles, e historial persistente sin alterar el juego Android que ya funciona.",
        color=RED,
    ))
    flow.append(Spacer(1, 12 * mm))
    flow.append(Paragraph(
        "Documento preparado a partir del alcance y del estado real del proyecto Traidores al 15 de julio de 2026.",
        styles["SmallCustom"],
    ))

    doc.build(flow, onFirstPage=draw_page, onLaterPages=draw_page)
    print(OUTPUT)


if __name__ == "__main__":
    build_pdf()
