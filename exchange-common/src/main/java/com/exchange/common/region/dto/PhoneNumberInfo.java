package com.exchange.common.region.dto;

import java.io.Serializable;

/**
 * 手机号信息
 */
public class PhoneNumberInfo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 原始手机号
     */
    private String phoneNumber;
    
    /**
     * 国家代码
     */
    private String countryCode;
    
    /**
     * 区号
     */
    private String areaCode;
    
    /**
     * 国内号码部分
     */
    private String nationalNumber;
    
    /**
     * 地区代码
     */
    private String regionCode;
    
    /**
     * 手机号类型
     */
    private String phoneNumberType;
    
    /**
     * 是否有效
     */
    private boolean valid;
    
    /**
     * 国家信息
     */
    private CountryInfo country;
    
    /**
     * 地区信息
     */
    private RegionInfo region;
    
    /**
     * 格式化后的手机号
     */
    private String formattedNumber;
    
    public PhoneNumberInfo() {}
    
    public PhoneNumberInfo(String phoneNumber, String countryCode, String areaCode, 
                          String nationalNumber, String regionCode, String phoneNumberType, 
                          boolean valid) {
        this.phoneNumber = phoneNumber;
        this.countryCode = countryCode;
        this.areaCode = areaCode;
        this.nationalNumber = nationalNumber;
        this.regionCode = regionCode;
        this.phoneNumberType = phoneNumberType;
        this.valid = valid;
    }
    
    // Getters and Setters
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
    
    public String getCountryCode() {
        return countryCode;
    }
    
    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }
    
    public String getAreaCode() {
        return areaCode;
    }
    
    public void setAreaCode(String areaCode) {
        this.areaCode = areaCode;
    }
    
    public String getNationalNumber() {
        return nationalNumber;
    }
    
    public void setNationalNumber(String nationalNumber) {
        this.nationalNumber = nationalNumber;
    }
    
    public String getRegionCode() {
        return regionCode;
    }
    
    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }
    
    public String getPhoneNumberType() {
        return phoneNumberType;
    }
    
    public void setPhoneNumberType(String phoneNumberType) {
        this.phoneNumberType = phoneNumberType;
    }
    
    public boolean isValid() {
        return valid;
    }
    
    public void setValid(boolean valid) {
        this.valid = valid;
    }
    
    public CountryInfo getCountry() {
        return country;
    }
    
    public void setCountry(CountryInfo country) {
        this.country = country;
    }
    
    public RegionInfo getRegion() {
        return region;
    }
    
    public void setRegion(RegionInfo region) {
        this.region = region;
    }
    
    public String getFormattedNumber() {
        return formattedNumber;
    }
    
    public void setFormattedNumber(String formattedNumber) {
        this.formattedNumber = formattedNumber;
    }
    
    @Override
    public String toString() {
        return "PhoneNumberInfo{" +
                "phoneNumber='" + phoneNumber + '\'' +
                ", countryCode='" + countryCode + '\'' +
                ", areaCode='" + areaCode + '\'' +
                ", nationalNumber='" + nationalNumber + '\'' +
                ", regionCode='" + regionCode + '\'' +
                ", phoneNumberType='" + phoneNumberType + '\'' +
                ", valid=" + valid +
                ", country=" + country +
                ", region=" + region +
                ", formattedNumber='" + formattedNumber + '\'' +
                '}';
    }
} 