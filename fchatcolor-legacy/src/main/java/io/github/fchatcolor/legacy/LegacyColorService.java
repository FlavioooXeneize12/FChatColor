package io.github.fchatcolor.legacy;

import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.settings.ChatDefaults;
import io.github.fchatcolor.api.util.HexColor;
import org.bukkit.ChatColor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio de color compatible con todos los servidores soportados (1.8 en adelante).
 *
 * <ul>
 *   <li>{@link ColorMode#LEGACY}: {@code &a} → {@code §a} vía
 *       {@link ChatColor#translateAlternateColorCodes(char, String)}.</li>
 *   <li>{@link ColorMode#HEX}: {@code &#RRGGBB} → {@code §x§R§R§G§G§B§B} si el
 *       servidor lo soporta (1.16+), o al color legacy más cercano en 1.8.</li>
 *   <li>{@link ColorMode#MINIMESSAGE}: no hay MiniMessage en 1.8, así que se
 *       traducen las etiquetas conocidas a códigos legacy (mejor esfuerzo) y se
 *       eliminan las que no se puedan representar (gradientes, etc.).</li>
 * </ul>
 *
 * <p>Únicamente usa clases presentes desde Spigot 1.8.8.</p>
 */
public final class LegacyColorService implements ColorService {

    private final PluginContext context;
    private final boolean hexCapable;
    private final Pattern hexPattern;
    private final Pattern tagPattern;
    private final Map<String, String> miniTagToLegacy;

    public LegacyColorService(PluginContext context) {
        this.context = context;
        this.hexCapable = context.getServerInfo().hasLegacyHex();

        char character = ChatDefaults.COLOR_CHARACTER;
        String quoted = Pattern.quote(String.valueOf(character));
        this.hexPattern = Pattern.compile("(?i)" + quoted + "#([0-9a-f]{6})");
        this.tagPattern = Pattern.compile("</?([a-zA-Z0-9_#][a-zA-Z0-9_:#-]*)>");
        this.miniTagToLegacy = buildMiniTagMap();
    }

    @Override
    public String translate(String texto, ColorMode modo) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }

        String resultado = texto;
        switch (modo) {
            case HEX:
                resultado = translateHex(resultado);
                break;
            case MINIMESSAGE:
                resultado = translateMiniTags(resultado);
                resultado = translateHex(resultado);
                break;
            case LEGACY:
            default:
                break;
        }

        return ChatColor.translateAlternateColorCodes(ChatDefaults.COLOR_CHARACTER, resultado);
    }

    @Override
    public boolean supports(ColorMode modo) {
        // LEGACY siempre; MINIMESSAGE con traducción parcial; HEX real solo en 1.16+
        return true;
    }

    @Override
    public String name() {
        return "legacy" + (hexCapable ? "+hex" : "");
    }

    /** ¿El servidor puede representar colores hex reales? */
    public boolean isHexCapable() {
        return hexCapable;
    }

    /**
     * Sustituye {@code &#RRGGBB} por su version hex si el servidor la soporta,
     * o por el color legacy más cercano si no (1.8/1.12/1.15...).
     */
    private String translateHex(String input) {
        Matcher matcher = hexPattern.matcher(input);
        if (!matcher.find()) {
            return input;
        }

        // StringBuffer: obligatorio con --release 8 (StringBuilder llegó en Java 9)
        StringBuffer buffer = new StringBuffer(input.length());
        matcher.reset();
        while (matcher.find()) {
            Integer rgb = HexColor.parseRgb(matcher.group(1));
            String replacement;
            if (rgb == null) {
                replacement = "";
            } else if (hexCapable) {
                replacement = HexColor.toLegacySectionHex(rgb);
            } else {
                // Servidor sin soporte hex: aproximación al color legacy más cercano
                replacement = HexColor.toNearestLegacySection(rgb);
            }
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    /**
     * Traduce etiquetas MiniMessage habituales a códigos legacy y elimina las
     * que no tengan equivalente (gradientes, transparencias, etc.).
     */
    private String translateMiniTags(String input) {
        if (input.indexOf('<') < 0) {
            return input;
        }

        Matcher matcher = tagPattern.matcher(input);
        if (!matcher.find()) {
            return input;
        }

        StringBuffer buffer = new StringBuffer(input.length());
        matcher.reset();
        while (matcher.find()) {
            String tag = matcher.group(1).toLowerCase(Locale.ROOT);
            String replacement = resolveMiniTag(tag);
            if (replacement != null) {
                matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
            } else {
                // Etiqueta desconocida/no representable: se elimina pero se conserva el texto
                matcher.appendReplacement(buffer, "");
            }
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String resolveMiniTag(String tag) {
        if (tag.startsWith("#") && tag.length() == 7) {
            Integer rgb = HexColor.parseRgb(tag);
            if (rgb == null) {
                return "";
            }
            return hexCapable ? HexColor.toLegacySectionHex(rgb) : HexColor.toNearestLegacySection(rgb);
        }
        // Etiquetas de cierre (</red>, </bold>) no aportan nada en legacy
        return miniTagToLegacy.get(tag);
    }

    private static Map<String, String> buildMiniTagMap() {
        Map<String, String> map = new HashMap<String, String>();

        // Colores con nombre
        map.put("black", "\u00A70");
        map.put("dark_blue", "\u00A71");
        map.put("dark_green", "\u00A72");
        map.put("dark_aqua", "\u00A73");
        map.put("dark_red", "\u00A74");
        map.put("dark_purple", "\u00A75");
        map.put("gold", "\u00A76");
        map.put("gray", "\u00A77");
        map.put("dark_gray", "\u00A78");
        map.put("blue", "\u00A79");
        map.put("green", "\u00A7a");
        map.put("aqua", "\u00A7b");
        map.put("red", "\u00A7c");
        map.put("light_purple", "\u00A7d");
        map.put("yellow", "\u00A7e");
        map.put("white", "\u00A7f");

        // Alias habituales
        map.put("grey", "\u00A77");
        map.put("dark_grey", "\u00A78");
        map.put("pink", "\u00A7d");
        map.put("orange", "\u00A76");

        // Formatos
        map.put("bold", "\u00A7l");
        map.put("italic", "\u00A7o");
        map.put("obfuscated", "\u00A7k");
        map.put("strikethrough", "\u00A7m");
        map.put("underlined", "\u00A7n");
        map.put("underline", "\u00A7n");
        map.put("reset", "\u00A7r");

        return map;
    }
}
