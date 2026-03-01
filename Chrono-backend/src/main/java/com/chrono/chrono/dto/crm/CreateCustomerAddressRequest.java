package com.chrono.chrono.dto.crm;

import com.chrono.chrono.entities.crm.AddressType;
import com.chrono.chrono.entities.crm.CustomerAddress;

public class CreateCustomerAddressRequest {
    private AddressType type = AddressType.OFFICE;
    private String street;
    private String postalCode;
    private String city;
    private String country;

    public AddressType getType() {
        return type;
    }

    public void setType(AddressType type) {
        this.type = type;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public CustomerAddress toEntity() {
        CustomerAddress address = new CustomerAddress();
        address.setType(type);
        address.setStreet(street);
        address.setPostalCode(postalCode);
        address.setCity(city);
        address.setCountry(country);
        return address;
    }
}
