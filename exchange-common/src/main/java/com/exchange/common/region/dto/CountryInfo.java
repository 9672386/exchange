package com.exchange.common.region.dto;

import java.io.Serializable;

/**
 * 国家信息
 */
public class CountryInfo implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 国家代码 (ISO 3166-1 alpha-2)
     */
    private String code;
    
    /**
     * 国家名称 (中文)
     */
    private String name;
    
    /**
     * 国家名称 (英文)
     */
    private String nameEn;
    
    /**
     * 区号
     */
    private String areaCode;
    
    /**
     * 货币代码
     */
    private String currency;
    
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
    
    public CountryInfo() {}
    
    public CountryInfo(String code, String name, String nameEn, String areaCode, 
                      String currency, String timezone) {
        this.code = code;
        this.name = name;
        this.nameEn = nameEn;
        this.areaCode = areaCode;
        this.currency = currency;
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
    
    public String getAreaCode() {
        return areaCode;
    }
    
    public void setAreaCode(String areaCode) {
        this.areaCode = areaCode;
    }
    
    public String getCurrency() {
        return currency;
    }
    
    public void setCurrency(String currency) {
        this.currency = currency;
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
        return "CountryInfo{" +
                "code='" + code + '\'' +
                ", name='" + name + '\'' +
                ", nameEn='" + nameEn + '\'' +
                ", areaCode='" + areaCode + '\'' +
                ", currency='" + currency + '\'' +
                ", timezone='" + timezone + '\'' +
                ", smsSupported=" + smsSupported +
                ", voiceSupported=" + voiceSupported +
                '}';
    }
} 