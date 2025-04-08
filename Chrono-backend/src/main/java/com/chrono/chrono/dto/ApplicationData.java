package com.chrono.chrono.dto;

public class ApplicationData {
    private String companyName;
    private String contactName;
    private String email;
    private String phone;
    private String additionalInfo;

    // Neu: chosenPackage
    private String chosenPackage;

    public String getCompanyName() {
        return companyName;
    }
    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getContactName() {
        return contactName;
    }
    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }
    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAdditionalInfo() {
        return additionalInfo;
    }
    public void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    // Getter/Setter fürs gewählte Paket
    public String getChosenPackage() {
        return chosenPackage;
    }
    public void setChosenPackage(String chosenPackage) {
        this.chosenPackage = chosenPackage;
    }
}
