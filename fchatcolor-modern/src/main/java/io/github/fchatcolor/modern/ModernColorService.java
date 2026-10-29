package io.github.fchatcolor.modern;

import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.settings.ChatDefaults;
import io.github.fchatcolor.api.util.HexColor;
import io.github.fchatcolor.api.util.RainbowColor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio de color moderno basado en Adventure (Paper/Folia/Canvas 1.20.5+,
 * 1.21.x y versiones posteriores incluida la numeración por años 26.x).
 *
 * <ul>
 *   <li>{@link ColorMode#MINIMESSAGE}: etiquetas completas
 *       ({@code <red>}, {@code <#FF55FF>}, {@code <gradient:...>}) además de
 *       códigos {@code &a} convertidos previamente.</li>
 *   <li>{@link ColorMode#HEX}: {@code &#RRGGBB} resuelto como color hex real.</li>
 *   <li>{@link ColorMode#LEGACY}: {@code &a} / {@code §a} clásicos.</li>
 * </ul>
 *
 * <p>Esta clase solo se carga when {@code ServerDetector} confirma que
 * Adventure existe en el runtime: nunca se carga en un servidor 1.8.</p>
 */
public final class ModernColorService implements ColorService {

    private static final LegacyComponentSerializer AMPERSAND = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    /**
     * Serializer de salida. Sin {@code useUnusualXRepeatedCharacterHexFormat()}
     * Adventure emite {@code §#RRGGBB}, formato que el cliente Minecraft NO
     * entiende; con el flag emite {@code §x§R§R§G&G&B&B}, el mismo que usa
     * {@code ChatColor.of()} de Bukkit. El parser acepta ambos formatos, así que
     * el flag no rompe la lectura de mensajes ya existentes.
     */
    private static final LegacyComponentSerializer SECTION = LegacyComponentSerializer.builder()
            .character('\u00A7')
            .hexColors()
            .useUnusualXRepeatedCharacterHexFormat()
            .build();

    /** Conversion {@code &c} → etiqueta MiniMessage, para entrada mixta. */
    private static final Map<Character, String> LEGACY_TO_MINI = new HashMap<Character, String>();

    static {
        LEGACY_TO_MINI.put('0', "black");
        LEGACY_TO_MINI.put('1', "dark_blue");
        LEGACY_TO_MINI.put('2', "dark_green");
        LEGACY_TO_MINI.put('3', "dark_aqua");
        LEGACY_TO_MINI.put('4', "dark_red");
        LEGACY_TO_MINI.put('5', "dark_purple");
        LEGACY_TO_MINI.put('6', "gold");
        LEGACY_TO_MINI.put('7', "gray");
        LEGACY_TO_MINI.put('8', "dark_gray");
        LEGACY_TO_MINI.put('9', "blue");
        LEGACY_TO_MINI.put('a', "green");
        LEGACY_TO_MINI.put('b', "aqua");
        LEGACY_TO_MINI.put('c', "red");
        LEGACY_TO_MINI.put('d', "light_purple");
        LEGACY_TO_MINI.put('e', "yellow");
        LEGACY_TO_MINI.put('f', "white");
        LEGACY_TO_MINI.put('k', "obfuscated");
        LEGACY_TO_MINI.put('l', "bold");
        LEGACY_TO_MINI.put('m', "strikethrough");
        LEGACY_TO_MINI.put('n', "underlined");
        LEGACY_TO_MINI.put('o', "italic");
        LEGACY_TO_MINI.put('r', "reset");
    }

    /** De códigos legacy de un carácter a colores Adventure (para prefijos). */
    private static final Map<Character, NamedTextColor> LEGACY_TO_NAMED = new HashMap<Character, NamedTextColor>();

    static {
        LEGACY_TO_NAMED.put('0', NamedTextColor.BLACK);
        LEGACY_TO_NAMED.put('1', NamedTextColor.DARK_BLUE);
        LEGACY_TO_NAMED.put('2', NamedTextColor.DARK_GREEN);
        LEGACY_TO_NAMED.put('3', NamedTextColor.DARK_AQUA);
        LEGACY_TO_NAMED.put('4', NamedTextColor.DARK_RED);
        LEGACY_TO_NAMED.put('5', NamedTextColor.DARK_PURPLE);
        LEGACY_TO_NAMED.put('6', NamedTextColor.GOLD);
        LEGACY_TO_NAMED.put('7', NamedTextColor.GRAY);
        LEGACY_TO_NAMED.put('8', NamedTextColor.DARK_GRAY);
        LEGACY_TO_NAMED.put('9', NamedTextColor.BLUE);
        LEGACY_TO_NAMED.put('a', NamedTextColor.GREEN);
        LEGACY_TO_NAMED.put('b', NamedTextColor.AQUA);
        LEGACY_TO_NAMED.put('c', NamedTextColor.RED);
        LEGACY_TO_NAMED.put('d', NamedTextColor.LIGHT_PURPLE);
        LEGACY_TO_NAMED.put('e', NamedTextColor.YELLOW);
        LEGACY_TO_NAMED.put('f', NamedTextColor.WHITE);
    }

    private static final Pattern HEX_TAG_PATTERN = Pattern.compile("(?i)<#([0-9a-f]{6})>");
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("(?i)&#([0-9a-f]{6})");
    private static final Pattern BARE_HEX_PATTERN = Pattern.compile("(?i)(?<!&)#([0-9a-f]{6})\\b");

    private final PluginContext context;

    public ModernColorService(PluginContext context) {
        this.context = context;
    }

    /** Carácter fijo de códigos de color (no configurable). */
    private char colorCharacter() {
        return ChatDefaults.COLOR_CHARACTER;
    }

    /**
     * Traduce a un {@link Component} de Adventure, la representación nativa
     * del chat en servidores modernos.
     */
    public Component translateComponent(String texto, ColorMode modo) {
        if (texto == null || texto.isEmpty()) {
            return Component.empty();
        }

        switch (modo) {
            case MINIMESSAGE:
                return parseMiniMessage(prepareMiniMessage(texto));
            case HEX:
                return AMPERSAND.deserialize(normalizeHex(texto));
            case LEGACY:
            default:
                return AMPERSAND.deserialize(normalizeSection(texto));
        }
    }

    /**
     * Devuelve el color Adventure correspondiente a un color guardado en la BD
     * ({@code #RRGGBB} o código legacy de un carácter).
     *
     * @param storedColor valor de la BD
     * @return {@code null} si el valor no es reconocible
     */
    public TextColor resolveTextColor(String storedColor) {
        return resolveTextColor(storedColor, true);
    }

    /**
     * Como {@link #resolveTextColor(String)} pero controlando si se permite el
     * color hex real. Si no se permite, un {@code #RRGGBB} se degrada al color
     * legacy más cercano (consistente con el permiso {@code fchatcolor.chat.hex}).
     *
     * @param storedColor valor de la BD
     * @param allowHex    {@code false} para forzar aproximación legacy
     */
    public TextColor resolveTextColor(String storedColor, boolean allowHex) {
        if (storedColor == null || storedColor.isEmpty()) {
            return null;
        }
        // Rainbow: se resuelve como el color del ciclo actual; respeta
        // allowHex igual que cualquier otro hex (si no, legacy más cercano)
        if (RainbowColor.isRainbow(storedColor)) {
            return resolveTextColor(RainbowColor.currentHex(), allowHex);
        }
        if (storedColor.startsWith("#")) {
            Integer rgb = HexColor.parseRgb(storedColor);
            if (rgb == null) {
                return null;
            }
            if (allowHex) {
                return TextColor.color(rgb.intValue());
            }
            return LEGACY_TO_NAMED.get(Character.valueOf(HexColor.nearestLegacyCode(rgb.intValue())));
        }
        if (HexColor.isLegacyCode(storedColor)) {
            return LEGACY_TO_NAMED.get(Character.toLowerCase(storedColor.charAt(0)));
        }
        return null;
    }

    /**
     * Carácter centinela: se añade temporalmente para que Adventure emita los
     * códigos de color de un componente sin texto y después se elimina.
     */
    private static final char CENTINELA = '\u0001';

    @Override
    public String translate(String texto, ColorMode modo) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        Component component = translateComponent(texto, modo);
        String serializado = SECTION.serialize(component);
        if (!serializado.isEmpty()) {
            return serializado;
        }
        // Adventure solo emite códigos when hay texto que colorear: una
        // entrada que es SOLO código de color (&#RRGGBB o &a sin nada detrás,
        // como el preview de /fchatcolor set) serializaba a cadena vacía y el
        // color se perdía. Serializamos con un centinela y lo quitamos para
        // devolver los códigos, igual que ya hace el servicio legacy.
        String conCentinela = SECTION.serialize(component.append(Component.text(CENTINELA)));
        int pos = conCentinela.lastIndexOf(CENTINELA);
        if (pos < 0) {
            return conCentinela;
        }
        return conCentinela.substring(0, pos) + conCentinela.substring(pos + 1);
    }

    @Override
    public boolean supports(ColorMode modo) {
        // En este módulo siempre hay Adventure completo
        return modo != null;
    }

    @Override
    public String name() {
        return "modern+minimessage";
    }

    // ------------------------------------------------------------------
    // Preprocesado
    // ------------------------------------------------------------------

    /** Normaliza {@code §...} a {@code &...} para reutilizar el serializer ampersand. */
    private String normalizeSection(String input) {
        if (input.indexOf('\u00A7') < 0) {
            return input;
        }
        return input.replace('\u00A7', '&');
    }

    /** Convierte {@code &#RRGGBB} y {@code #RRGGBB} al formato {@code &x&R&R&G&G&B&B} que entiende el serializer. */
    private String normalizeHex(String input) {
        String result = normalizeSection(input);
        if (result.indexOf('#') < 0) {
            return result;
        }
        result = BARE_HEX_PATTERN.matcher(result).replaceAll("&#$1");
        Matcher matcher = LEGACY_HEX_PATTERN.matcher(result);
        if (!matcher.find()) {
            return result;
        }
        // StringBuffer: obligatorio con --release 8 (StringBuilder llegó en Java 9)
        StringBuffer buffer = new StringBuffer(result.length() + 8);
        matcher.reset();
        while (matcher.find()) {
            matcher.appendReplacement(buffer,
                    Matcher.quoteReplacement(HexColor.toLegacyAmpersandHex(HexColor.parseRgb(matcher.group(1)))));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * Convierte códigos legacy ({@code &a}, {@code &#RRGGBB}) en etiquetas
     * MiniMessage para poder procesar entrada mixta con un solo parser.
     */
    private String prepareMiniMessage(String input) {
        String result = normalizeSection(input);
        result = BARE_HEX_PATTERN.matcher(result).replaceAll("&#$1");

        // &#RRGGBB -> <#RRGGBB>
        Matcher hexMatcher = LEGACY_HEX_PATTERN.matcher(result);
        if (hexMatcher.find()) {
            StringBuffer buffer = new StringBuffer(result.length() + 8);
            hexMatcher.reset();
            while (hexMatcher.find()) {
                hexMatcher.appendReplacement(buffer,
                        Matcher.quoteReplacement("<#" + hexMatcher.group(1).toLowerCase(Locale.ROOT) + ">"));
            }
            hexMatcher.appendTail(buffer);
            result = buffer.toString();
        }

        // &c -> <red>  /  & -> c
        StringBuilder builder = new StringBuilder(result.length());
        char prefix = colorCharacter();
        for (int i = 0; i < result.length(); i++) {
            char current = result.charAt(i);
            if (current == prefix && i + 1 < result.length()) {
                char code = Character.toLowerCase(result.charAt(i + 1));
                String tag = LEGACY_TO_MINI.get(Character.valueOf(code));
                if (tag != null) {
                    builder.append('<').append(tag).append('>');
                    i++;
                    continue;
                }
            }
            builder.append(current);
        }
        return builder.toString();
    }

    /** Parsea con MiniMessage con red de seguridad ante etiquetas malformadas. */
    private Component parseMiniMessage(String input) {
        try {
            return MiniMessage.miniMessage().deserialize(input);
        } catch (Throwable t) {
            try {
                return AMPERSAND.deserialize(input);
            } catch (Throwable ignored) {
                return Component.text(input);
            }
        }
    }
}
