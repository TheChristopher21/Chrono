package com.chrono.chrono.dto;

import com.chrono.chrono.entities.Role;
import com.chrono.chrono.entities.User;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.ArrayList;

public class UserDTO {
    // ----- Fields -----
    private Long id;
    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private String address;
    private LocalDate birthDate;
    private LocalDate entryDate;
    private String country;
    private String taxClass;
    private String tarifCode;
    private String canton;
    private String civilStatus;
    private Integer children;
    private String religion;
    private String federalState;
    private Boolean churchTax;
    private String healthInsurance;
    private Double gkvAdditionalRate;
    private String personnelNumber;
    private String email;
    private String mobilePhone;
    private String landlinePhone;
    private Boolean emailNotifications;
    private List<String> roles;
    private Integer expectedWorkDays;
    private Double dailyWorkHours;
    private Integer breakDuration;
    private String color;
    private Integer scheduleCycle;
    private List<Map<String, Double>> weeklySchedule;
    private LocalDate scheduleEffectiveDate;
    private Boolean isHourly;
    private Integer annualVacationDays;
    private Integer trackingBalanceInMinutes;
    private Boolean isPercentage;
    private Integer workPercentage;
    private Double hourlyRate;
    private Double monthlySalary;
    private String bankAccount;
    private String socialSecurityNumber;
    private Boolean deleted;
    private Boolean optOut;
    private Long companyId;
    private String companyCantonAbbreviation;
    private Boolean customerTrackingEnabled; // Kept
    private Long lastCustomerId;
    private String lastCustomerName;

    public UserDTO() {
        this.roles = new ArrayList<>();
        this.weeklySchedule = new ArrayList<>();
    }

    // Constructor from User entity
    public UserDTO(User user) {
        this.id = user.getId();
        this.username = user.getUsername();
        this.firstName = user.getFirstName();
        this.lastName = user.getLastName();
        this.address = user.getAddress();
        this.birthDate = user.getBirthDate();
        this.entryDate = user.getEntryDate();
        this.country = user.getCountry();
        this.taxClass = user.getTaxClass();
        this.tarifCode = user.getTarifCode();
        this.canton = user.getCanton();
        this.civilStatus = user.getCivilStatus();
        this.children = user.getChildren();
        this.religion = user.getReligion();
        this.federalState = user.getFederalState();
        this.churchTax = user.getChurchTax();
        this.healthInsurance = user.getHealthInsurance();
        this.gkvAdditionalRate = user.getGkvAdditionalRate();
        this.personnelNumber = user.getPersonnelNumber();
        this.email = user.getEmail();
        this.mobilePhone = user.getMobilePhone();
        this.landlinePhone = user.getLandlinePhone();
        this.emailNotifications = user.isEmailNotifications();
        this.roles = user.getRoles().stream()
                .map(Role::getRoleName)
                .collect(Collectors.toList());
        this.expectedWorkDays = user.getExpectedWorkDays();
        this.dailyWorkHours = user.getDailyWorkHours();
        this.breakDuration = user.getBreakDuration();
        this.color = user.getColor();
        this.scheduleCycle = user.getScheduleCycle();
        this.weeklySchedule = user.getWeeklySchedule();
        this.scheduleEffectiveDate = user.getScheduleEffectiveDate();
        this.isHourly = user.getIsHourly();
        this.annualVacationDays = user.getAnnualVacationDays();
        this.trackingBalanceInMinutes = user.getTrackingBalanceInMinutes();
        this.isPercentage = user.getIsPercentage();
        this.workPercentage = user.getWorkPercentage();
        this.hourlyRate = user.getHourlyRate();
        this.monthlySalary = user.getMonthlySalary();
        this.bankAccount = user.getBankAccount();
        this.socialSecurityNumber = user.getSocialSecurityNumber();
        this.deleted = user.isDeleted();
        this.optOut = user.isOptOut();
        this.companyId = (user.getCompany() != null) ? user.getCompany().getId() : null;
        this.companyCantonAbbreviation = (user.getCompany() != null) ? user.getCompany().getCantonAbbreviation() : null;
        this.lastCustomerId = user.getLastCustomer() != null ? user.getLastCustomer().getId() : null;
        this.lastCustomerName = user.getLastCustomer() != null ? user.getLastCustomer().getName() : null;
        this.customerTrackingEnabled = (user.getCompany() != null) ? user.getCompany().getCustomerTrackingEnabled() : null; // Kept
    }

    // All-Args-Constructor
    public UserDTO(Long id, String username, String password, String firstName, String lastName,
                   String address, LocalDate birthDate, LocalDate entryDate, String country,
                   String taxClass, String tarifCode, String canton, String civilStatus,
                   Integer children, String religion, String federalState, Boolean churchTax,
                   String healthInsurance, Double gkvAdditionalRate, String personnelNumber,
                   String email, String mobilePhone, String landlinePhone,
                   Boolean emailNotifications, List<String> roles,
                   Integer expectedWorkDays, Double dailyWorkHours, Integer breakDuration, String color,
                   Integer scheduleCycle, List<Map<String, Double>> weeklySchedule, LocalDate scheduleEffectiveDate,
                   Boolean isHourly, Integer annualVacationDays, Integer trackingBalanceInMinutes,
                   Boolean isPercentage, Integer workPercentage, Double hourlyRate, Double monthlySalary, String bankAccount,
                   String socialSecurityNumber, Long companyId,
                   Long lastCustomerId, String lastCustomerName, Boolean customerTrackingEnabled,
                   Boolean deleted, Boolean optOut) { // Kept
        this.id = id;
        this.username = username;
        this.password = password;
        this.firstName = firstName;
        this.lastName = lastName;
        this.address = address;
        this.birthDate = birthDate;
        this.entryDate = entryDate;
        this.country = country;
        this.taxClass = taxClass;
        this.tarifCode = tarifCode;
        this.canton = canton;
        this.civilStatus = civilStatus;
        this.children = children;
        this.religion = religion;
        this.federalState = federalState;
        this.churchTax = churchTax;
        this.healthInsurance = healthInsurance;
        this.gkvAdditionalRate = gkvAdditionalRate;
        this.personnelNumber = personnelNumber;
        this.email = email;
        this.mobilePhone = mobilePhone;
        this.landlinePhone = landlinePhone;
        this.emailNotifications = emailNotifications != null ? emailNotifications : true;
        this.roles = roles != null ? roles : new ArrayList<>();
        this.expectedWorkDays = expectedWorkDays;
        this.dailyWorkHours = dailyWorkHours;
        this.breakDuration = breakDuration;
        this.color = color;
        this.scheduleCycle = scheduleCycle;
        this.weeklySchedule = weeklySchedule != null ? weeklySchedule : new ArrayList<>();
        this.scheduleEffectiveDate = scheduleEffectiveDate;
        this.isHourly = isHourly != null ? isHourly : false;
        this.annualVacationDays = annualVacationDays;
        this.trackingBalanceInMinutes = trackingBalanceInMinutes != null ? trackingBalanceInMinutes : 0;
        this.isPercentage = isPercentage != null ? isPercentage : false;
        this.workPercentage = workPercentage != null ? workPercentage : 100;
        this.hourlyRate = hourlyRate;
        this.monthlySalary = monthlySalary;
        this.bankAccount = bankAccount;
        this.socialSecurityNumber = socialSecurityNumber;
        this.companyId = companyId;
        this.lastCustomerId = lastCustomerId;
        this.lastCustomerName = lastCustomerName;
        this.customerTrackingEnabled = customerTrackingEnabled; // Kept
        this.deleted = deleted;
        this.optOut = optOut;
    }

    // ----- Getters -----
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getAddress() { return address; }
    public LocalDate getBirthDate() { return birthDate; }
    public LocalDate getEntryDate() { return entryDate; }
    public String getCountry() { return country; }
    public String getTaxClass() { return taxClass; }
    public String getTarifCode() { return tarifCode; }
    public String getCanton() { return canton; }
    public String getCivilStatus() { return civilStatus; }
    public Integer getChildren() { return children; }
    public String getReligion() { return religion; }
    public String getFederalState() { return federalState; }
    public Boolean getChurchTax() { return churchTax; }
    public String getHealthInsurance() { return healthInsurance; }
    public Double getGkvAdditionalRate() { return gkvAdditionalRate; }
    public String getPersonnelNumber() { return personnelNumber; }
    public String getEmail() { return email; }
    public String getMobilePhone() { return mobilePhone; }
    public String getLandlinePhone() { return landlinePhone; }
    public Boolean getEmailNotifications() { return emailNotifications; }
    public List<String> getRoles() { return roles; }
    public Integer getExpectedWorkDays() { return expectedWorkDays; }
    public Double getDailyWorkHours() { return dailyWorkHours; }
    public Integer getBreakDuration() { return breakDuration; }
    public String getColor() { return color; }
    public Integer getScheduleCycle() { return scheduleCycle; }
    public List<Map<String, Double>> getWeeklySchedule() { return weeklySchedule; }
    public LocalDate getScheduleEffectiveDate() { return scheduleEffectiveDate; }
    public Boolean getIsHourly() { return isHourly; }
    public Integer getAnnualVacationDays() { return annualVacationDays; }
    public Integer getTrackingBalanceInMinutes() { return trackingBalanceInMinutes; }
    public Boolean getIsPercentage() { return isPercentage; }
    public Integer getWorkPercentage() { return workPercentage; }
    public Double getHourlyRate() { return hourlyRate; }
    public Double getMonthlySalary() { return monthlySalary; }
    public String getBankAccount() { return bankAccount; }
    public String getSocialSecurityNumber() { return socialSecurityNumber; }
    public Boolean getDeleted() { return deleted; }
    public Boolean getOptOut() { return optOut; }
    public Long getCompanyId() { return companyId; }
    public String getCompanyCantonAbbreviation() { return companyCantonAbbreviation; }
    public Long getLastCustomerId() { return lastCustomerId; }
    public String getLastCustomerName() { return lastCustomerName; }
    public Boolean getCustomerTrackingEnabled() { return customerTrackingEnabled; } // Kept

    // ----- Setters -----
    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    public void setAddress(String address) { this.address = address; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public void setEntryDate(LocalDate entryDate) { this.entryDate = entryDate; }
    public void setCountry(String country) { this.country = country; }
    public void setTaxClass(String taxClass) { this.taxClass = taxClass; }
    public void setTarifCode(String tarifCode) { this.tarifCode = tarifCode; }
    public void setCanton(String canton) { this.canton = canton; }
    public void setCivilStatus(String civilStatus) { this.civilStatus = civilStatus; }
    public void setChildren(Integer children) { this.children = children; }
    public void setReligion(String religion) { this.religion = religion; }
    public void setFederalState(String federalState) { this.federalState = federalState; }
    public void setChurchTax(Boolean churchTax) { this.churchTax = churchTax; }
    public void setHealthInsurance(String healthInsurance) { this.healthInsurance = healthInsurance; }
    public void setGkvAdditionalRate(Double gkvAdditionalRate) { this.gkvAdditionalRate = gkvAdditionalRate; }
    public void setPersonnelNumber(String personnelNumber) { this.personnelNumber = personnelNumber; }
    public void setEmail(String email) { this.email = email; }
    public void setMobilePhone(String mobilePhone) { this.mobilePhone = mobilePhone; }
    public void setLandlinePhone(String landlinePhone) { this.landlinePhone = landlinePhone; }
    public void setEmailNotifications(Boolean emailNotifications) { this.emailNotifications = emailNotifications; }
    public void setRoles(List<String> roles) { this.roles = roles; }
    public void setExpectedWorkDays(Integer expectedWorkDays) { this.expectedWorkDays = expectedWorkDays; }
    public void setDailyWorkHours(Double dailyWorkHours) { this.dailyWorkHours = dailyWorkHours; }
    public void setBreakDuration(Integer breakDuration) { this.breakDuration = breakDuration; }
    public void setColor(String color) { this.color = color; }
    public void setScheduleCycle(Integer scheduleCycle) { this.scheduleCycle = scheduleCycle; }
    public void setWeeklySchedule(List<Map<String, Double>> weeklySchedule) { this.weeklySchedule = weeklySchedule; }
    public void setScheduleEffectiveDate(LocalDate scheduleEffectiveDate) { this.scheduleEffectiveDate = scheduleEffectiveDate; }
    public void setIsHourly(Boolean isHourly) { this.isHourly = isHourly; }
    public void setAnnualVacationDays(Integer annualVacationDays) { this.annualVacationDays = annualVacationDays; }
    public void setTrackingBalanceInMinutes(Integer trackingBalanceInMinutes) { this.trackingBalanceInMinutes = trackingBalanceInMinutes; }
    public void setIsPercentage(Boolean isPercentage) { this.isPercentage = isPercentage; }
    public void setWorkPercentage(Integer workPercentage) { this.workPercentage = workPercentage; }
    public void setHourlyRate(Double hourlyRate) { this.hourlyRate = hourlyRate; }
    public void setMonthlySalary(Double monthlySalary) { this.monthlySalary = monthlySalary; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    public void setSocialSecurityNumber(String socialSecurityNumber) { this.socialSecurityNumber = socialSecurityNumber; }
    public void setDeleted(Boolean deleted) { this.deleted = deleted; }
    public void setOptOut(Boolean optOut) { this.optOut = optOut; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }
    public void setCompanyCantonAbbreviation(String companyCantonAbbreviation) { this.companyCantonAbbreviation = companyCantonAbbreviation; }
    public void setLastCustomerId(Long lastCustomerId) { this.lastCustomerId = lastCustomerId; }
    public void setLastCustomerName(String lastCustomerName) { this.lastCustomerName = lastCustomerName; }
    public void setCustomerTrackingEnabled(Boolean customerTrackingEnabled) { this.customerTrackingEnabled = customerTrackingEnabled; } // Kept
}