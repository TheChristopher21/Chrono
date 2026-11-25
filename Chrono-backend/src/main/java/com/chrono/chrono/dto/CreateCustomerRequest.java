package com.chrono.chrono.dto;

public class CreateCustomerRequest extends CreateRequest {
    private String name;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
