package com.exchange.common.region.impl;

import com.exchange.common.region.dto.CountryInfo;
import com.exchange.common.region.dto.RegionInfo;
import com.exchange.common.region.dto.PhoneNumberInfo;
import com.exchange.common.region.dto.IpLocationInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 地区和区号处理服务实现
 * 使用Google的libphonenumber库进行手机号解析
 * 支持IP地址和域名解析
 */
public class RegionPhoneServiceImpl {
    
    private static final Logger log = LoggerFactory.getLogger(RegionPhoneServiceImpl.class);
    
    // IP地址验证正则表达式
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
    private static final Pattern IPV6_PATTERN = Pattern.compile(
        "^([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$|^::1$|^::$|^([0-9a-fA-F]{1,4}:){1,7}:$|^:([0-9a-fA-F]{1,4}:){1,7}$|^([0-9a-fA-F]{1,4}:){0,6}::([0-9a-fA-F]{1,4}:){0,6}[0-9a-fA-F]{1,4}$");
    
    // 缓存国家信息
    private final Map<String, CountryInfo> countryCache = new ConcurrentHashMap<>();
    
    // 缓存地区信息
    private final Map<String, RegionInfo> regionCache = new ConcurrentHashMap<>();
    
    // 缓存IP地理位置信息
    private final Map<String, IpLocationInfo> ipLocationCache = new ConcurrentHashMap<>();
    
    public RegionPhoneServiceImpl() {
        initializeCountryCache();
        initializeRegionCache();
    }
    
    // ========== 手机号解析功能 ==========
    
    public Optional<String> extractAreaCode(String phoneNumber) {
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil phoneUtil = 
                com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber number = 
                phoneUtil.parse(phoneNumber, null);
            
            if (phoneUtil.isValidNumber(number)) {
                return Optional.of(String.valueOf(number.getCountryCode()));
            }
        } catch (Exception e) {
            log.warn("提取区号失败: {}", phoneNumber, e);
        }
        return Optional.empty();
    }
    
    public Optional<String> extractCountryCode(String phoneNumber) {
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil phoneUtil = 
                com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber number = 
                phoneUtil.parse(phoneNumber, null);
            
            if (phoneUtil.isValidNumber(number)) {
                String countryCode = phoneUtil.getRegionCodeForNumber(number);
                return Optional.ofNullable(countryCode);
            }
        } catch (Exception e) {
            log.warn("提取国家代码失败: {}", phoneNumber, e);
        }
        return Optional.empty();
    }
    
    public Optional<String> extractRegionCode(String phoneNumber) {
        // 对于大多数情况，地区代码就是国家代码
        return extractCountryCode(phoneNumber);
    }
    
    public boolean isValidPhoneNumber(String phoneNumber) {
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil phoneUtil = 
                com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber number = 
                phoneUtil.parse(phoneNumber, null);
            
            return phoneUtil.isValidNumber(number);
        } catch (Exception e) {
            log.warn("验证手机号失败: {}", phoneNumber, e);
            return false;
        }
    }
    
    public Optional<String> formatPhoneNumber(String phoneNumber) {
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil phoneUtil = 
                com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber number = 
                phoneUtil.parse(phoneNumber, null);
            
            if (phoneUtil.isValidNumber(number)) {
                String formatted = phoneUtil.format(number, 
                    com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.E164);
                return Optional.of(formatted);
            }
        } catch (Exception e) {
            log.warn("格式化手机号失败: {}", phoneNumber, e);
        }
        return Optional.empty();
    }
    
    public Optional<String> getPhoneNumberType(String phoneNumber) {
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil phoneUtil = 
                com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber number = 
                phoneUtil.parse(phoneNumber, null);
            
            if (phoneUtil.isValidNumber(number)) {
                com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType type = 
                    phoneUtil.getNumberType(number);
                return Optional.of(type.name());
            }
        } catch (Exception e) {
            log.warn("获取手机号类型失败: {}", phoneNumber, e);
        }
        return Optional.empty();
    }
    
    public Optional<CountryInfo> getCountryByAreaCode(String areaCode) {
        return Optional.ofNullable(countryCache.get(areaCode));
    }
    
    public Optional<CountryInfo> getCountryByCode(String countryCode) {
        return countryCache.values().stream()
                .filter(country -> countryCode.equals(country.getCode()))
                .findFirst();
    }
    
    public Optional<RegionInfo> getRegionByCode(String regionCode) {
        return Optional.ofNullable(regionCache.get(regionCode));
    }
    
    public List<CountryInfo> getAllCountries() {
        return new ArrayList<>(countryCache.values());
    }
    
    public List<RegionInfo> getAllRegions() {
        return new ArrayList<>(regionCache.values());
    }
    
    public Optional<PhoneNumberInfo> parsePhoneNumber(String phoneNumber) {
        try {
            com.google.i18n.phonenumbers.PhoneNumberUtil phoneUtil = 
                com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance();
            
            com.google.i18n.phonenumbers.Phonenumber.PhoneNumber number = 
                phoneUtil.parse(phoneNumber, null);
            
            if (!phoneUtil.isValidNumber(number)) {
                return Optional.empty();
            }
            
            PhoneNumberInfo info = new PhoneNumberInfo();
            info.setPhoneNumber(phoneNumber);
            info.setValid(true);
            info.setCountryCode(String.valueOf(number.getCountryCode()));
            info.setNationalNumber(String.valueOf(number.getNationalNumber()));
            
            // 获取国家信息
            String countryCode = phoneUtil.getRegionCodeForNumber(number);
            if (countryCode != null) {
                info.setRegionCode(countryCode);
                getCountryByCode(countryCode).ifPresent(info::setCountry);
                getRegionByCode(countryCode).ifPresent(info::setRegion);
            }
            
            // 获取手机号类型
            getPhoneNumberType(phoneNumber).ifPresent(info::setPhoneNumberType);
            
            // 格式化手机号
            formatPhoneNumber(phoneNumber).ifPresent(info::setFormattedNumber);
            
            return Optional.of(info);
            
        } catch (Exception e) {
            log.warn("解析手机号失败: {}", phoneNumber, e);
            return Optional.empty();
        }
    }
    
    // ========== IP地理位置解析功能 ==========
    
    public Optional<IpLocationInfo> parseIpLocation(String ipAddress) {
        if (!isValidIpAddress(ipAddress)) {
            return Optional.empty();
        }
        
        // 检查缓存
        IpLocationInfo cached = ipLocationCache.get(ipAddress);
        if (cached != null) {
            return Optional.of(cached);
        }
        
        try {
            // 这里可以集成第三方IP地理位置服务
            // 目前使用模拟数据
            IpLocationInfo locationInfo = createMockIpLocation(ipAddress);
            if (locationInfo != null) {
                ipLocationCache.put(ipAddress, locationInfo);
                return Optional.of(locationInfo);
            }
        } catch (Exception e) {
            log.warn("解析IP地理位置失败: {}", ipAddress, e);
        }
        
        return Optional.empty();
    }
    
    public Optional<CountryInfo> getCountryByIp(String ipAddress) {
        return parseIpLocation(ipAddress)
                .map(IpLocationInfo::getCountry);
    }
    
    public Optional<RegionInfo> getRegionByIp(String ipAddress) {
        return parseIpLocation(ipAddress)
                .map(IpLocationInfo::getRegion);
    }
    
    public boolean isValidIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = ipAddress.trim();
        
        try {
            InetAddress.getByName(trimmed);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    public Optional<String> getIpAddressType(String ipAddress) {
        if (!isValidIpAddress(ipAddress)) {
            return Optional.empty();
        }
        
        String trimmed = ipAddress.trim();
        
        if (IPV4_PATTERN.matcher(trimmed).matches()) {
            if (isPrivateIp(trimmed)) {
                return Optional.of("PRIVATE_IPv4");
            }
            return Optional.of("IPv4");
        }
        
        if (IPV6_PATTERN.matcher(trimmed).matches()) {
            if (isPrivateIpv6(trimmed)) {
                return Optional.of("PRIVATE_IPv6");
            }
            return Optional.of("IPv6");
        }
        
        return Optional.empty();
    }
    
    public List<String> resolveDomainToIps(String domain) {
        List<String> ips = new ArrayList<>();
        
        try {
            InetAddress[] addresses = InetAddress.getAllByName(domain);
            for (InetAddress address : addresses) {
                ips.add(address.getHostAddress());
            }
        } catch (UnknownHostException e) {
            log.warn("解析域名失败: {}", domain, e);
        }
        
        return ips;
    }
    
    public Optional<IpLocationInfo> parseDomainLocation(String domain) {
        List<String> ips = resolveDomainToIps(domain);
        if (!ips.isEmpty()) {
            return parseIpLocation(ips.get(0));
        }
        return Optional.empty();
    }
    
    public List<IpLocationInfo> batchParseIpLocations(List<String> ipAddresses) {
        List<IpLocationInfo> results = new ArrayList<>();
        
        for (String ipAddress : ipAddresses) {
            parseIpLocation(ipAddress).ifPresent(results::add);
        }
        
        return results;
    }
    
    // ========== 私有辅助方法 ==========
    
    private boolean isPrivateIp(String ipAddress) {
        try {
            String[] parts = ipAddress.split("\\.");
            int firstOctet = Integer.parseInt(parts[0]);
            int secondOctet = Integer.parseInt(parts[1]);
            
            return (firstOctet == 10) ||
                   (firstOctet == 172 && secondOctet >= 16 && secondOctet <= 31) ||
                   (firstOctet == 192 && secondOctet == 168);
        } catch (Exception e) {
            return false;
        }
    }
    
    private boolean isPrivateIpv6(String ipAddress) {
        return ipAddress.startsWith("fe80:") || 
               ipAddress.startsWith("fc00:") || 
               ipAddress.startsWith("fd00:");
    }
    
    private IpLocationInfo createMockIpLocation(String ipAddress) {
        // 模拟IP地理位置数据
        // 在实际应用中，这里应该调用第三方API服务
        
        if (ipAddress.startsWith("8.8.8.") || ipAddress.equals("8.8.8.8")) {
            // Google DNS - 美国
            IpLocationInfo info = new IpLocationInfo(ipAddress, "US", "美国", "US", "美国", "Mountain View");
            info.setLatitude(37.4056);
            info.setLongitude(-122.0775);
            info.setTimezone("America/Los_Angeles");
            info.setIsp("Google");
            info.setOrganization("Google LLC");
            info.setIpType("PUBLIC");
            getCountryByCode("US").ifPresent(info::setCountry);
            getRegionByCode("US").ifPresent(info::setRegion);
            return info;
        } else if (ipAddress.startsWith("114.114.114.") || ipAddress.equals("114.114.114.114")) {
            // 114 DNS - 中国
            IpLocationInfo info = new IpLocationInfo(ipAddress, "CN", "中国", "CN", "中国", "南京");
            info.setLatitude(32.0584);
            info.setLongitude(118.7965);
            info.setTimezone("Asia/Shanghai");
            info.setIsp("China Mobile");
            info.setOrganization("China Mobile Communications Corporation");
            info.setIpType("PUBLIC");
            getCountryByCode("CN").ifPresent(info::setCountry);
            getRegionByCode("CN").ifPresent(info::setRegion);
            return info;
        } else if (ipAddress.startsWith("192.168.") || ipAddress.startsWith("10.") || ipAddress.startsWith("172.")) {
            // 私有IP
            IpLocationInfo info = new IpLocationInfo(ipAddress, "PRIVATE", "私有网络", "PRIVATE", "私有网络", "未知");
            info.setIpType("PRIVATE");
            return info;
        }
        
        return null;
    }
    
    /**
     * 初始化国家缓存
     */
    private void initializeCountryCache() {
        // 中国
        CountryInfo china = new CountryInfo("CN", "中国", "China", "86", "CNY", "Asia/Shanghai");
        china.setSmsSupported(true);
        china.setVoiceSupported(true);
        countryCache.put("86", china);
        countryCache.put("CN", china);
        
        // 美国
        CountryInfo usa = new CountryInfo("US", "美国", "United States", "1", "USD", "America/New_York");
        usa.setSmsSupported(true);
        usa.setVoiceSupported(true);
        countryCache.put("1", usa);
        countryCache.put("US", usa);
        
        // 日本
        CountryInfo japan = new CountryInfo("JP", "日本", "Japan", "81", "JPY", "Asia/Tokyo");
        japan.setSmsSupported(true);
        japan.setVoiceSupported(true);
        countryCache.put("81", japan);
        countryCache.put("JP", japan);
        
        // 韩国
        CountryInfo korea = new CountryInfo("KR", "韩国", "South Korea", "82", "KRW", "Asia/Seoul");
        korea.setSmsSupported(true);
        korea.setVoiceSupported(true);
        countryCache.put("82", korea);
        countryCache.put("KR", korea);
        
        // 香港
        CountryInfo hongKong = new CountryInfo("HK", "香港", "Hong Kong", "852", "HKD", "Asia/Hong_Kong");
        hongKong.setSmsSupported(true);
        hongKong.setVoiceSupported(true);
        countryCache.put("852", hongKong);
        countryCache.put("HK", hongKong);
        
        // 台湾
        CountryInfo taiwan = new CountryInfo("TW", "台湾", "Taiwan", "886", "TWD", "Asia/Taipei");
        taiwan.setSmsSupported(true);
        taiwan.setVoiceSupported(true);
        countryCache.put("886", taiwan);
        countryCache.put("TW", taiwan);
        
        // 新加坡
        CountryInfo singapore = new CountryInfo("SG", "新加坡", "Singapore", "65", "SGD", "Asia/Singapore");
        singapore.setSmsSupported(true);
        singapore.setVoiceSupported(true);
        countryCache.put("65", singapore);
        countryCache.put("SG", singapore);
        
        // 英国
        CountryInfo uk = new CountryInfo("GB", "英国", "United Kingdom", "44", "GBP", "Europe/London");
        uk.setSmsSupported(true);
        uk.setVoiceSupported(true);
        countryCache.put("44", uk);
        countryCache.put("GB", uk);
        
        // 德国
        CountryInfo germany = new CountryInfo("DE", "德国", "Germany", "49", "EUR", "Europe/Berlin");
        germany.setSmsSupported(true);
        germany.setVoiceSupported(true);
        countryCache.put("49", germany);
        countryCache.put("DE", germany);
        
        // 法国
        CountryInfo france = new CountryInfo("FR", "法国", "France", "33", "EUR", "Europe/Paris");
        france.setSmsSupported(true);
        france.setVoiceSupported(true);
        countryCache.put("33", france);
        countryCache.put("FR", france);
        
        log.info("初始化国家缓存完成，共加载{}个国家", countryCache.size() / 2);
    }
    
    /**
     * 初始化地区缓存
     */
    private void initializeRegionCache() {
        // 中国地区
        RegionInfo mainland = new RegionInfo("CN", "中国大陆", "Mainland China", "CN", "Asia/Shanghai");
        mainland.setSmsSupported(true);
        mainland.setVoiceSupported(true);
        regionCache.put("CN", mainland);
        
        // 香港地区
        RegionInfo hk = new RegionInfo("HK", "香港", "Hong Kong", "HK", "Asia/Hong_Kong");
        hk.setSmsSupported(true);
        hk.setVoiceSupported(true);
        regionCache.put("HK", hk);
        
        // 台湾地区
        RegionInfo tw = new RegionInfo("TW", "台湾", "Taiwan", "TW", "Asia/Taipei");
        tw.setSmsSupported(true);
        tw.setVoiceSupported(true);
        regionCache.put("TW", tw);
        
        // 美国地区
        RegionInfo us = new RegionInfo("US", "美国", "United States", "US", "America/New_York");
        us.setSmsSupported(true);
        us.setVoiceSupported(true);
        regionCache.put("US", us);
        
        log.info("初始化地区缓存完成，共加载{}个地区", regionCache.size());
    }
} 