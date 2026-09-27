package com.agrolink.app.config;

import com.agrolink.app.model.EscrowStatus;
import com.agrolink.app.model.ListingStatus;
import com.agrolink.app.model.OfferStatus;
import com.agrolink.app.model.OrderStatus;
import com.agrolink.app.model.PaymentMethod;
import com.agrolink.app.model.Role;
import org.bson.types.Decimal128;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.GenericConverter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Configuration
public class MongoDecimalConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoDecimalConfig.class);

    @Bean
    public MongoCustomConversions decimal128Conversions() {
        return new MongoCustomConversions(List.of(
                new Decimal128ToBigDecimalConverter(),
                new BigDecimalToDecimal128Converter(),
                new LenientStatusEnumConverter()));
    }

    @ReadingConverter
    static class Decimal128ToBigDecimalConverter implements Converter<Decimal128, BigDecimal> {
        @Override
        public BigDecimal convert(Decimal128 source) {
            try {
                return source.bigDecimalValue();
            } catch (ArithmeticException overflow) {
                log.error("Decimal128 {} exceeds 34-digit precision; falling back to double", source);
                return BigDecimal.valueOf(source.doubleValue());
            }
        }
    }

    @WritingConverter
    static class BigDecimalToDecimal128Converter implements Converter<BigDecimal, Decimal128> {
        @Override
        public Decimal128 convert(BigDecimal source) {
            return source == null ? null : new Decimal128(source);
        }
    }

    @ReadingConverter
    static class LenientStatusEnumConverter implements GenericConverter {

        private static final Set<Class<?>> TARGETS = Set.of(
                OrderStatus.class, EscrowStatus.class, OfferStatus.class,
                ListingStatus.class, PaymentMethod.class, Role.class);

        @Override
        public Set<ConvertiblePair> getConvertibleTypes() {
            return TARGETS.stream()
                    .map(target -> new ConvertiblePair(String.class, target))
                    .collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public Object convert(Object source, TypeDescriptor sourceType, TypeDescriptor targetType) {
            if (source == null) {
                return null;
            }
            String needle = String.valueOf(source).trim();
            if (needle.isEmpty()) {
                return null;
            }
            Class<?> target = targetType.getType();
            for (Object constant : target.getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(needle)) {
                    return constant;
                }
            }
            log.warn("Unrecognised {} value '{}' in the database; reading it as null instead of failing the query",
                    target.getSimpleName(), needle);
            return null;
        }
    }
}