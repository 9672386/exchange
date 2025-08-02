package com.exchange.common.region.dto;

import java.io.Serializable;

/**
 * 地区信息
 */
public class RegionInfo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 地区代码
     */
    private String code;
    
    /**
     * 地区名称 (中文)
     */
    private String name;
    
    /**
     * 地区名称 (英文)
     */
    private String nameEn;
    
    /**
     * 所属国家代码
     */
    private String countryCode;
    
    /**
     * 时区
     */
    private String timezone;
    
    /**
     * 是否支持短信
     */
    private boolean smsSupported;
    
    /**
     * 是否支持语音
     */
    private boolean voiceSupported;
    
    public RegionInfo() {}
    
    public RegionInfo(String code, String name, String nameEn, String countryCode, String timezone) {
        this.code = code;
        this.name = name;
        this.nameEn = nameEn;
        this.countryCode = countryCode;
        this.timezone = timezone;
    }
    
    // Getters and Setters
    public String getCode() {
        return code;
    }
    
    public void setCode(String code) {
        this.code = code;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getNameEn() {
        return nameEn;
    }
    
    public void setNameEn(String nameEn) {
        this.nameEn = nameEn;
    }
    
    public String getCountryCode() {
        return countryCode;
    }
    
    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }
    
    public String getTimezone() {
        return timezone;
    }
    
    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }
    
    public boolean isSmsSupported() {
        return smsSupported;
    }
    
    public void setSmsSupported(boolean smsSupported) {
        this.smsSupported = smsSupported;
    }
    
    public boolean isVoiceSupported() {
        return voiceSupported;
    }
    
    public void setVoiceSupported(boolean voiceSupported) {
        this.voiceSupported = voiceSupported;
    }
    
    @Override
    public String toString() {
        return "RegionInfo{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", nameEn='" + nameEn + '\'' +
                ", countryCode='" + countryCode + '\'' +
                ", timezone='" + timezone + '\'' +
                ", smsSupported=" + smsSupported +
                ", voiceSupported=" + voiceSupported +
                '}';
    }
} 