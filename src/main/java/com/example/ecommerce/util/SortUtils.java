package com.example.ecommerce.util;

import org.springframework.data.domain.Sort;

public final class SortUtils {
    private SortUtils() {}

    public static Sort buildSort(String sort, String defaultField) {
        if (sort == null || sort.isBlank()) {
            return Sort.unsorted();
        }

        String[] sortParams = sort.split(",");
        String field = sortParams[0];
        Sort.Direction direction = Sort.Direction.ASC;

        if (field.equalsIgnoreCase("asc") || field.equalsIgnoreCase("desc")) {
            direction = field.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
            field = defaultField;
        } else if (sortParams.length > 1 && sortParams[1].trim().equalsIgnoreCase("desc")) {
            direction = Sort.Direction.DESC;
        }

        return Sort.by(direction, field);
    }
}
