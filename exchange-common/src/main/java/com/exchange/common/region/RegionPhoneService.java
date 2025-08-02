package com.exchange.common.region;

import com.exchange.common.region.dto.CountryInfo;
import com.exchange.common.region.dto.RegionInfo;
import com.exchange.common.region.dto.PhoneNumberInfo;
import com.exchange.common.region.dto.IpLocationInfo;
import com.exchange.common.region.impl.RegionPhoneServiceImpl;

import java.util.List;
import java.util.Optional;

/**
 * 地区和区号处理服务工具类
 * 提供手机号解析、国家信息查询、地区信息查询、IP地理位置解析等功能
 * 
 * 使用静态方法，无需Spring容器，可直接调用
 */
public class RegionPhoneService {
    
    // 单例实例
    private static final RegionPhoneServiceImpl INSTANCE = new RegionPhoneServiceImpl();
    
    /**
     * 从手机号中提取区号
     * @param phoneNumber 手机号
     * @return 区号
     */
    public static Optional<String> extractAreaCode(String phoneNumber) {
        return INSTANCE.extractAreaCode(phoneNumber);
    }
    
    /**
     * 从手机号中提取国家代码
     * @param phoneNumber 手机号
     * @return 国家代码
     */
    public static Optional<String> extractCountryCode(String phoneNumber) {
        return INSTANCE.extractCountryCode(phoneNumber);
    }
    
    /**
     * 从手机号中提取地区代码
     * @param phoneNumber 手机号
     * @return 地区代码
     */
    public static Optional<String> extractRegionCode(String phoneNumber) {
        return INSTANCE.extractRegionCode(phoneNumber);
    }
    
    /**
     * 验证手机号格式
     * @param phoneNumber 手机号
     * @return 是否有效
     */
    public static boolean isValidPhoneNumber(String phoneNumber) {
        return INSTANCE.isValidPhoneNumber(phoneNumber);
    }
    
    /**
     * 格式化手机号
     * @param phoneNumber 手机号
     * @return 格式化后的手机号
     */
    public static Optional<String> formatPhoneNumber(String phoneNumber) {
        return INSTANCE.formatPhoneNumber(phoneNumber);
    }
    
    /**
     * 获取手机号类型
     * @param phoneNumber 手机号
     * @return 手机号类型 (MOBILE, FIXED_LINE, etc.)
     */
    public static Optional<String> getPhoneNumberType(String phoneNumber) {
        return INSTANCE.getPhoneNumberType(phoneNumber);
    }
    
    /**
     * 根据区号获取国家信息
     * @param areaCode 区号
     * @return 国家信息
     */
    public static Optional<CountryInfo> getCountryByAreaCode(String areaCode) {
        return INSTANCE.getCountryByAreaCode(areaCode);
    }
    
    /**
     * 根据国家代码获取国家信息
     * @param countryCode 国家代码
     * @return 国家信息
     */
    public static Optional<CountryInfo> getCountryByCode(String countryCode) {
        return INSTANCE.getCountryByCode(countryCode);
    }
    
    /**
     * 根据地区代码获取地区信息
     * @param regionCode 地区代码
     * @return 地区信息
     */
    public static Optional<RegionInfo> getRegionByCode(String regionCode) {
        return INSTANCE.getRegionByCode(regionCode);
    }
    
    /**
     * 获取所有支持的国家列表
     * @return 国家信息列表
     */
    public static List<CountryInfo> getAllCountries() {
        return INSTANCE.getAllCountries();
    }
    
    /**
     * 获取所有支持的地区列表
     * @return 地区信息列表
     */
    public static List<RegionInfo> getAllRegions() {
        return INSTANCE.getAllRegions();
    }
    
    /**
     * 解析完整的手机号信息
     * @param phoneNumber 手机号
     * @return 手机号信息
     */
    public static Optional<PhoneNumberInfo> parsePhoneNumber(String phoneNumber) {
        return INSTANCE.parsePhoneNumber(phoneNumber);
    }
    
    // ========== IP地理位置解析功能 ==========
    
    /**
     * 根据IP地址解析地理位置信息
     * @param ipAddress IP地址
     * @return IP地理位置信息
     */
    public static Optional<IpLocationInfo> parseIpLocation(String ipAddress) {
        return INSTANCE.parseIpLocation(ipAddress);
    }
    
    /**
     * 根据IP地址获取国家信息
     * @param ipAddress IP地址
     * @return 国家信息
     */
    public static Optional<CountryInfo> getCountryByIp(String ipAddress) {
        return INSTANCE.getCountryByIp(ipAddress);
    }
    
    /**
     * 根据IP地址获取地区信息
     * @param ipAddress IP地址
     * @return 地区信息
     */
    public static Optional<RegionInfo> getRegionByIp(String ipAddress) {
        return INSTANCE.getRegionByIp(ipAddress);
    }
    
    /**
     * 验证IP地址格式
     * @param ipAddress IP地址
     * @return 是否有效
     */
    public static boolean isValidIpAddress(String ipAddress) {
        return INSTANCE.isValidIpAddress(ipAddress);
    }
    
    /**
     * 获取IP地址类型
     * @param ipAddress IP地址
     * @return IP地址类型 (IPv4, IPv6, PRIVATE, etc.)
     */
    public static Optional<String> getIpAddressType(String ipAddress) {
        return INSTANCE.getIpAddressType(ipAddress);
    }
    
    /**
     * 根据域名解析IP地址
     * @param domain 域名
     * @return IP地址列表
     */
    public static List<String> resolveDomainToIps(String domain) {
        return INSTANCE.resolveDomainToIps(domain);
    }
    
    /**
     * 根据域名解析地理位置信息
     * @param domain 域名
     * @return IP地理位置信息
     */
    public static Optional<IpLocationInfo> parseDomainLocation(String domain) {
        return INSTANCE.parseDomainLocation(domain);
    }
    
    /**
     * 批量解析IP地址地理位置
     * @param ipAddresses IP地址列表
     * @return IP地理位置信息列表
     */
    public static List<IpLocationInfo> batchParseIpLocations(List<String> ipAddresses) {
        return INSTANCE.batchParseIpLocations(ipAddresses);
    }
} 