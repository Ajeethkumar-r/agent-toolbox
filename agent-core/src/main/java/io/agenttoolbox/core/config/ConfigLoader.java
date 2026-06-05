package io.agenttoolbox.core.config;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.introspector.BeanAccess;
import org.yaml.snakeyaml.introspector.Property;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.io.InputStream;

public final class ConfigLoader {

    private ConfigLoader() {}

    public static AgentConfig load(InputStream yamlStream) {
        LoaderOptions options = new LoaderOptions();
        Constructor constructor = new Constructor(AgentConfig.class, options);
        constructor.setPropertyUtils(new KebabCasePropertyUtils());
        Yaml yaml = new Yaml(constructor);
        return yaml.load(yamlStream);
    }

    public static AgentConfig loadFromClasspath() {
        InputStream is = ConfigLoader.class.getClassLoader().getResourceAsStream("application.yaml");
        if (is == null) return new AgentConfig();
        return load(is);
    }

    /**
     * Custom PropertyUtils that converts kebab-case YAML keys (e.g. "max-messages")
     * to camelCase Java property names (e.g. "maxMessages").
     */
    private static class KebabCasePropertyUtils extends PropertyUtils {

        @Override
        public Property getProperty(Class<?> type, String name) {
            return super.getProperty(type, kebabToCamel(name));
        }

        @Override
        public Property getProperty(Class<?> type, String name, BeanAccess bAccess) {
            return super.getProperty(type, kebabToCamel(name), bAccess);
        }

        private static String kebabToCamel(String name) {
            if (name == null || !name.contains("-")) {
                return name;
            }
            StringBuilder sb = new StringBuilder();
            boolean upperNext = false;
            for (int i = 0; i < name.length(); i++) {
                char c = name.charAt(i);
                if (c == '-') {
                    upperNext = true;
                } else if (upperNext) {
                    sb.append(Character.toUpperCase(c));
                    upperNext = false;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }
    }
}
