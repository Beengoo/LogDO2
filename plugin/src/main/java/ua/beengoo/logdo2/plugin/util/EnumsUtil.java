package ua.beengoo.logdo2.plugin.util;

import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;


@Slf4j
public class EnumsUtil {
    private EnumsUtil() {
        // utility class
    }

    public static <E extends Enum<E>> Set<E> parseEnums(
            Class<E> enumClass,
            Collection<String> names
    ) {
        EnumSet<E> result = EnumSet.noneOf(enumClass);

        for (String name : names) {
            if (name == null) {
                continue;
            }
            try {
                E constant = Enum.valueOf(enumClass, name.toUpperCase(Locale.ROOT));
                result.add(constant);
            } catch (IllegalArgumentException ex) {
                log.warn("Unknown enum entry '{}' for {}", name, enumClass.getName());
            }
        }

        return result;
    }
}
