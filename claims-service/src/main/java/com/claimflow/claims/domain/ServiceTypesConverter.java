package com.claimflow.claims.domain;

import com.claimflow.events.ServiceType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/** Stores a set of service types as "LAB,PHARMACY" in one column. */
@Converter
public class ServiceTypesConverter implements AttributeConverter<Set<ServiceType>, String> {
    @Override
    public String convertToDatabaseColumn(Set<ServiceType> types) {
        return types.stream().map(Enum::name).sorted().collect(Collectors.joining(","));
    }

    @Override
    public Set<ServiceType> convertToEntityAttribute(String column) {
        EnumSet<ServiceType> set = EnumSet.noneOf(ServiceType.class);
        if (column != null && !column.isBlank()) {
            Arrays.stream(column.split(",")).map(String::trim).map(ServiceType::valueOf).forEach(set::add);
        }
        return set;
    }
}
