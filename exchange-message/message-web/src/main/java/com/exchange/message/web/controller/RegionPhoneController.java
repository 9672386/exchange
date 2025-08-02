package com.exchange.message.web.controller;

import com.exchange.common.region.RegionPhoneService;
import com.exchange.common.region.dto.CountryInfo;
import com.exchange.common.region.dto.RegionInfo;
import com.exchange.common.region.dto.PhoneNumberInfo;
import com.exchange.common.region.dto.IpLocationInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 地区和区号处理控制器
 */
@RestController
@RequestMapping("/api/region-phone")
@Tag(name = "地区和区号处理", description = "地区和区号解析相关接口")
public class RegionPhoneController {
    
    // 使用静态方法，无需注入
    
    @GetMapping("/parse")
    @Operation(summary = "解析手机号", description = "解析手机号获取详细信息")
    public Optional<PhoneNumberInfo> parsePhoneNumber(@RequestParam String phoneNumber) {
        return RegionPhoneService.parsePhoneNumber(phoneNumber);
    }
    
    @GetMapping("/validate")
    @Operation(summary = "验证手机号", description = "验证手机号格式是否有效")
    public boolean isValidPhoneNumber(@RequestParam String phoneNumber) {
        return RegionPhoneService.isValidPhoneNumber(phoneNumber);
    }
    
    @GetMapping("/format")
    @Operation(summary = "格式化手机号", description = "格式化手机号为标准格式")
    public Optional<String> formatPhoneNumber(@RequestParam String phoneNumber) {
        return RegionPhoneService.formatPhoneNumber(phoneNumber);
    }
    
    @GetMapping("/extract/area-code")
    @Operation(summary = "提取区号", description = "从手机号中提取区号")
    public Optional<String> extractAreaCode(@RequestParam String phoneNumber) {
        return RegionPhoneService.extractAreaCode(phoneNumber);
    }
    
    @GetMapping("/extract/country-code")
    @Operation(summary = "提取国家代码", description = "从手机号中提取国家代码")
    public Optional<String> extractCountryCode(@RequestParam String phoneNumber) {
        return RegionPhoneService.extractCountryCode(phoneNumber);
    }
    
    @GetMapping("/extract/region-code")
    @Operation(summary = "提取地区代码", description = "从手机号中提取地区代码")
    public Optional<String> extractRegionCode(@RequestParam String phoneNumber) {
        return RegionPhoneService.extractRegionCode(phoneNumber);
    }
    
    @GetMapping("/phone-type")
    @Operation(summary = "获取手机号类型", description = "获取手机号类型(MOBILE, FIXED_LINE等)")
    public Optional<String> getPhoneNumberType(@RequestParam String phoneNumber) {
        return RegionPhoneService.getPhoneNumberType(phoneNumber);
    }
    
    @GetMapping("/country/area-code/{areaCode}")
    @Operation(summary = "根据区号获取国家", description = "根据区号获取国家信息")
    public Optional<CountryInfo> getCountryByAreaCode(@PathVariable String areaCode) {
        return RegionPhoneService.getCountryByAreaCode(areaCode);
    }
    
    @GetMapping("/country/code/{countryCode}")
    @Operation(summary = "根据国家代码获取国家", description = "根据国家代码获取国家信息")
    public Optional<CountryInfo> getCountryByCode(@PathVariable String countryCode) {
        return RegionPhoneService.getCountryByCode(countryCode);
    }
    
    @GetMapping("/region/code/{regionCode}")
    @Operation(summary = "根据地区代码获取地区", description = "根据地区代码获取地区信息")
    public Optional<RegionInfo> getRegionByCode(@PathVariable String regionCode) {
        return RegionPhoneService.getRegionByCode(regionCode);
    }
    
    @GetMapping("/countries")
    @Operation(summary = "获取所有国家", description = "获取所有支持的国家列表")
    public List<CountryInfo> getAllCountries() {
        return RegionPhoneService.getAllCountries();
    }
    
    @GetMapping("/regions")
    @Operation(summary = "获取所有地区", description = "获取所有支持的地区列表")
    public List<RegionInfo> getAllRegions() {
        return RegionPhoneService.getAllRegions();
    }
    
    // ========== IP地理位置解析API ==========
    
    @GetMapping("/ip/parse")
    @Operation(summary = "解析IP地理位置", description = "解析IP地址获取地理位置信息")
    public Optional<IpLocationInfo> parseIpLocation(@RequestParam String ipAddress) {
        return RegionPhoneService.parseIpLocation(ipAddress);
    }
    
    @GetMapping("/ip/validate")
    @Operation(summary = "验证IP地址", description = "验证IP地址格式是否有效")
    public boolean isValidIpAddress(@RequestParam String ipAddress) {
        return RegionPhoneService.isValidIpAddress(ipAddress);
    }
    
    @GetMapping("/ip/type")
    @Operation(summary = "获取IP地址类型", description = "获取IP地址类型(IPv4, IPv6, PRIVATE等)")
    public Optional<String> getIpAddressType(@RequestParam String ipAddress) {
        return RegionPhoneService.getIpAddressType(ipAddress);
    }
    
    @GetMapping("/ip/country")
    @Operation(summary = "根据IP获取国家", description = "根据IP地址获取国家信息")
    public Optional<CountryInfo> getCountryByIp(@RequestParam String ipAddress) {
        return RegionPhoneService.getCountryByIp(ipAddress);
    }
    
    @GetMapping("/ip/region")
    @Operation(summary = "根据IP获取地区", description = "根据IP地址获取地区信息")
    public Optional<RegionInfo> getRegionByIp(@RequestParam String ipAddress) {
        return RegionPhoneService.getRegionByIp(ipAddress);
    }
    
    @GetMapping("/domain/resolve")
    @Operation(summary = "解析域名IP", description = "解析域名获取IP地址列表")
    public List<String> resolveDomainToIps(@RequestParam String domain) {
        return RegionPhoneService.resolveDomainToIps(domain);
    }
    
    @GetMapping("/domain/location")
    @Operation(summary = "解析域名地理位置", description = "解析域名获取地理位置信息")
    public Optional<IpLocationInfo> parseDomainLocation(@RequestParam String domain) {
        return RegionPhoneService.parseDomainLocation(domain);
    }
    
    @PostMapping("/ip/batch")
    @Operation(summary = "批量解析IP地理位置", description = "批量解析IP地址获取地理位置信息")
    public List<IpLocationInfo> batchParseIpLocations(@RequestBody List<String> ipAddresses) {
        return RegionPhoneService.batchParseIpLocations(ipAddresses);
    }
} 