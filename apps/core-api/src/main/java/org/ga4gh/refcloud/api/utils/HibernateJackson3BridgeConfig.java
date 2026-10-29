package org.ga4gh.refcloud.api.utils;

import org.hibernate.cfg.AvailableSettings;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.format.FormatMapper;
import org.hibernate.type.descriptor.WrapperOptions;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Make sure your ObjectMapper import matches your active Jackson 3 package
// Typically: import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectMapper; 

@Configuration
public class HibernateJackson3BridgeConfig {

    @Bean
    public HibernatePropertiesCustomizer jsonFormatMapperCustomizer(ObjectMapper objectMapper) {
        return hibernateProperties -> 
            hibernateProperties.put(AvailableSettings.JSON_FORMAT_MAPPER, new FormatMapper() {
                
                @Override
                public <T> T fromString(CharSequence charSequence, JavaType<T> javaType, WrapperOptions wrapperOptions) {
                    // Bridge Hibernate's JavaType to Jackson 3's TypeFactory layout
                    var jacksonType = objectMapper.getTypeFactory().constructType(javaType.getJavaType());
                    return objectMapper.readValue(charSequence.toString(), jacksonType);
                }

                @Override
                public <T> String toString(T value, JavaType<T> javaType, WrapperOptions wrapperOptions) {
                    // Serialize the object safely to string using Jackson 3
                    return objectMapper.writeValueAsString(value);
                }
            });
    }
}