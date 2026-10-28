package io.github.fchatcolor.api.color;

/**
 * Translates color codes in text into chat output.
 *
 * <p>Hay dos implementaciones: la legacy (módulo {@code fchatcolor-legacy},
 * válida desde 1.8) y la moderna (módulo {@code fchatcolor-modern}, basada en
 * Adventure/MiniMessage). La concreta se elige en runtime mediante reflection.</p>
 */
public interface ColorService {

    /**
     * Traduce el texto indicado usando el modo pedido.
     *
     * @param texto entrada del usuario (puede contener códigos de color)
     * @param modo  modo de traducción solicitado
     * @return cadena ya traducida, lista para mostrarse en el chat
     */
    String translate(String texto, ColorMode modo);

    /** Whether this service can actually apply the given mode on this server. */
    boolean supports(ColorMode modo);

    /** Nombre corto para logs ("legacy" / "modern"). */
    String name();
}
