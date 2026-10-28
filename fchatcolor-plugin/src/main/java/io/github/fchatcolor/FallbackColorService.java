/*
 * Decompiled with CFR 0.152.
 */
package io.github.fchatcolor;

import io.github.fchatcolor.api.color.ColorMode;
import io.github.fchatcolor.api.color.ColorService;
import io.github.fchatcolor.api.context.PluginContext;
import io.github.fchatcolor.api.util.HexColor;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FallbackColorService
implements ColorService {
    private static final Map<Character, Character> LEGACY_CODES = new HashMap<Character, Character>();
    private final PluginContext context;

    public FallbackColorService(PluginContext context) {
        this.context = context;
    }

    @Override
    public String translate(String texto, ColorMode modo) {
        if (texto == null || texto.isEmpty()) {
            return "";
        }
        String resultado = texto;
        if (modo == ColorMode.HEX) {
            resultado = this.translateHex(resultado);
        } else if (modo == ColorMode.MINIMESSAGE) {
            resultado = resultado.replaceAll("</?[a-zA-Z0-9_#][a-zA-Z0-9_:#-]*>", "");
            resultado = this.translateHex(resultado);
        }
        char prefix = '&';
        StringBuilder builder = new StringBuilder(resultado.length());
        for (int i = 0; i < resultado.length(); ++i) {
            Character code;
            char current = resultado.charAt(i);
            if (current == prefix && i + 1 < resultado.length() && (code = LEGACY_CODES.get(Character.valueOf(Character.toLowerCase(resultado.charAt(i + 1))))) != null) {
                builder.append('\u00a7').append(code.charValue());
                ++i;
                continue;
            }
            builder.append(current);
        }
        return builder.toString();
    }

    private String translateHex(String texto) {
        Matcher matcher = Pattern.compile("(?i)&#([0-9a-f]{6})").matcher(texto);
        if (!matcher.find()) {
            return texto;
        }
        StringBuffer buffer = new StringBuffer(texto.length());
        matcher.reset();
        while (matcher.find()) {
            Integer rgb = HexColor.parseRgb(matcher.group(1));
            String replacement = rgb == null ? "" : HexColor.toNearestLegacySection(rgb);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    @Override
    public boolean supports(ColorMode modo) {
        return modo == ColorMode.LEGACY || modo == ColorMode.HEX;
    }

    @Override
    public String name() {
        return "fallback";
    }

    static {
        String codes = "0123456789abcdefklmno";
        for (int i = 0; i < codes.length(); ++i) {
            char code = codes.charAt(i);
            LEGACY_CODES.put(Character.valueOf(code), Character.valueOf(code));
        }
    }
}

