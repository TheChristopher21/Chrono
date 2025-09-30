package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.AddressType;
import com.chrono.chrono.entities.crm.CustomerAddress;

public class CustomerAddressDTO {
    private final Long id;
    private final AddressType type;
    private final String street;
    private final String postalCode;
    private final String city;
    private final String country;

    public CustomerAddressDTO(Long id, AddressType type, String street, String postalCode, String city, String country) {
        this.id = id;
        this.type = type;
        this.street = street;
        this.postalCode = postalCode;
        this.city = city;
        this.country = country;
    }

    public static CustomerAddressDTO from(CustomerAddress address) {
        return new CustomerAddressDTO(
                address.getId(),
                address.getType(),
                address.getStreet(),
                address.getPostalCode(),
                address.getCity(),
                address.getCountry());
    }

    public Long getId() {
        return id;
    }

    public AddressType getType() {
        return type;
    }

    public String getStreet() {
        return street;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public String getCity() {
        return city;
    }

    public String getCountry() {
        return country;
    }
}
