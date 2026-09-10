package com.example.ecommerce.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum CustomerStatus {
    ACTIVE, INACTIVE;

    @JsonCreator
    public static CustomerStatus fromString(String value){
        if(value == null ) return null;
        try {
            return CustomerStatus.valueOf(value.toUpperCase());
        }catch(IllegalArgumentException e){
            return null;
        }
    }
}
