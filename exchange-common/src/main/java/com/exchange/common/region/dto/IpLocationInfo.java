package com.exchange.common.region.dto;

import java.io.Serializable;

/**
 * IP地理位置信息
 */
public class IpLocationInfo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * IP地址
     */
    private String ipAddress;
    
    /**
     * 国家代码
     */
    private String countryCode;
    
    /**
     * 国家名称
     */
    private String countryName;
    
    /**
     * 地区代码
     */
    private String regionCode;
    
    /**
     * 地区名称
     */
    private String regionName;
    
    /**
     * 城市名称
     */
    private String cityName;
    
    /**
     * 邮政编码
     */
    private String postalCode;
    
    /**
     * 纬度
     */
    private Double latitude;
    
    /**
     * 经度
     */
    private Double longitude;
    
    /**
     * 时区
     */
    private String timezone;
    
    /**
     * ISP提供商
     */
    private String isp;
    
    /**
     * 组织名称
     */
    private String organization;
    
    /**
     * IP地址类型
     */
    private String ipType;
    
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
    
    public IpLocationInfo() {}
    
    public IpLocationInfo(String ipAddress, String countryCode, String countryName, 
                         String regionCode, String regionName, String cityName) {
        this.ipAddress = ipAddress;
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.regionCode = regionCode;
        this.regionName = regionName;
        this.cityName = cityName;
        this.valid = true;
    }
    
    // Getters and Setters
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    public String getCountryCode() {
        return countryCode;
    }
    
    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }
    
    public String getCountryName() {
        return countryName;
    }
    
    public void setCountryName(String countryName) {
        this.countryName = countryName;
    }
    
    public String getRegionCode() {
        return regionCode;
    }
    
    public void setRegionCode(String regionCode) {
        this.regionCode = regionCode;
    }
    
    public String getRegionName() {
        return regionName;
    }
    
    public void setRegionName(String regionName) {
        this.regionName = regionName;
    }
    
    public String getCityName() {
        return cityName;
    }
    
    public void setCityName(String cityName) {
        this.cityName = cityName;
    }
    
    public String getPostalCode() {
        return postalCode;
    }
    
    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }
    
    public Double getLatitude() {
        return latitude;
    }
    
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }
    
    public Double getLongitude() {
        return longitude;
    }
    
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
    
    public String getTimezone() {
        return timezone;
    }
    
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
    
    public String getIsp() {
        return isp;
    }
    
    public void setIsp(String isp) {
        this.isp = isp;
    }
    
    public String getOrganization() {
        return organization;
    }
    
    public void setOrganization(String organization) {
        this.organization = organization;
    }
    
    public String getIpType() {
        return ipType;
    }
    
    public void setIpType(String ipType) {
        this.ipType = ipType;
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
    
    @Override
    public String toString() {
        return "IpLocationInfo{" +
                "ipAddress='" + ipAddress + '\'' +
                ", countryCode='" + countryCode + '\'' +
                ", countryName='" + countryName + '\'' +
                ", regionCode='" + regionCode + '\'' +
                ", regionName='" + regionName + '\'' +
                ", cityName='" + cityName + '\'' +
                ", postalCode='" + postalCode + '\'' +
                ", latitude=" + latitude +
                ", longitude=" + longitude +
                ", timezone='" + timezone + '\'' +
                ", isp='" + isp + '\'' +
                ", organization='" + organization + '\'' +
                ", ipType='" + ipType + '\'' +
                ", valid=" + valid +
                ", country=" + country +
                ", region=" + region +
                '}';
    }
} 