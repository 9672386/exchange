package com.exchange.common.region;

import com.exchange.common.region.dto.CountryInfo;
import com.exchange.common.region.dto.PhoneNumberInfo;
import com.exchange.common.region.dto.RegionInfo;
import com.exchange.common.region.dto.IpLocationInfo;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 地区解析服务测试
 */
public class RegionPhoneServiceTest {
    
    @Test
    public void testParsePhoneNumber() {
        // 测试中国手机号
        Optional<PhoneNumberInfo> result = RegionPhoneService.parsePhoneNumber("+8613800138000");
        assertTrue(result.isPresent());
        PhoneNumberInfo info = result.get();
        assertEquals("+8613800138000", info.getPhoneNumber());
        assertEquals("86", info.getCountryCode());
        assertTrue(info.isValid());
        assertNotNull(info.getCountry());
        assertEquals("CN", info.getCountry().getCode());
        assertEquals("中国", info.getCountry().getName());
    }
    
    @Test
    public void testIsValidPhoneNumber() {
        // 测试有效手机号
        assertTrue(RegionPhoneService.isValidPhoneNumber("+8613800138000"));
        assertTrue(RegionPhoneService.isValidPhoneNumber("+12025550123"));
        
        // 测试无效手机号
        assertFalse(RegionPhoneService.isValidPhoneNumber("123"));
        assertFalse(RegionPhoneService.isValidPhoneNumber("invalid"));
    }
    
    @Test
    public void testExtractAreaCode() {
        Optional<String> areaCode = RegionPhoneService.extractAreaCode("+8613800138000");
        assertTrue(areaCode.isPresent());
        assertEquals("86", areaCode.get());
    }
    
    @Test
    public void testExtractCountryCode() {
        Optional<String> countryCode = RegionPhoneService.extractCountryCode("+8613800138000");
        assertTrue(countryCode.isPresent());
        assertEquals("CN", countryCode.get());
    }
    
    @Test
    public void testGetCountryByCode() {
        Optional<CountryInfo> country = RegionPhoneService.getCountryByCode("CN");
        assertTrue(country.isPresent());
        assertEquals("中国", country.get().getName());
        assertEquals("China", country.get().getNameEn());
        assertEquals("CNY", country.get().getCurrency());
    }
    
    @Test
    public void testGetCountryByAreaCode() {
        Optional<CountryInfo> country = RegionPhoneService.getCountryByAreaCode("86");
        assertTrue(country.isPresent());
        assertEquals("CN", country.get().getCode());
        assertEquals("中国", country.get().getName());
    }
    
    @Test
    public void testGetRegionByCode() {
        Optional<RegionInfo> region = RegionPhoneService.getRegionByCode("CN");
        assertTrue(region.isPresent());
        assertEquals("中国大陆", region.get().getName());
        assertEquals("Mainland China", region.get().getNameEn());
    }
    
    @Test
    public void testGetAllCountries() {
        List<CountryInfo> countries = RegionPhoneService.getAllCountries();
        assertFalse(countries.isEmpty());
        
        // 验证包含主要国家
        boolean hasChina = countries.stream().anyMatch(c -> "CN".equals(c.getCode()));
        boolean hasUS = countries.stream().anyMatch(c -> "US".equals(c.getCode()));
        assertTrue(hasChina);
        assertTrue(hasUS);
    }
    
    @Test
    public void testGetAllRegions() {
        List<RegionInfo> regions = RegionPhoneService.getAllRegions();
        assertFalse(regions.isEmpty());
        
        // 验证包含主要地区
        boolean hasChina = regions.stream().anyMatch(r -> "CN".equals(r.getCode()));
        boolean hasUS = regions.stream().anyMatch(r -> "US".equals(r.getCode()));
        assertTrue(hasChina);
        assertTrue(hasUS);
    }
    
    @Test
    public void testFormatPhoneNumber() {
        Optional<String> formatted = RegionPhoneService.formatPhoneNumber("+8613800138000");
        assertTrue(formatted.isPresent());
        assertTrue(formatted.get().startsWith("+86"));
    }
    
    @Test
    public void testGetPhoneNumberType() {
        Optional<String> type = RegionPhoneService.getPhoneNumberType("+8613800138000");
        assertTrue(type.isPresent());
        // 手机号类型应该是MOBILE
        assertEquals("MOBILE", type.get());
    }
    
    // ========== IP地理位置解析测试 ==========
    
    @Test
    public void testParseIpLocation() {
        // 测试Google DNS IP
        Optional<IpLocationInfo> result = RegionPhoneService.parseIpLocation("8.8.8.8");
        assertTrue(result.isPresent());
        IpLocationInfo info = result.get();
        assertEquals("8.8.8.8", info.getIpAddress());
        assertEquals("US", info.getCountryCode());
        assertEquals("美国", info.getCountryName());
        assertTrue(info.isValid());
        assertNotNull(info.getCountry());
        assertEquals("US", info.getCountry().getCode());
    }
    
    @Test
    public void testIsValidIpAddress() {
        // 测试有效IP地址
        assertTrue(RegionPhoneService.isValidIpAddress("8.8.8.8"));
        assertTrue(RegionPhoneService.isValidIpAddress("192.168.1.1"));
        assertTrue(RegionPhoneService.isValidIpAddress("2001:db8::1"));
        
        // 测试无效IP地址
        assertFalse(RegionPhoneService.isValidIpAddress("256.256.256.256"));
        assertFalse(RegionPhoneService.isValidIpAddress("invalid"));
        assertFalse(RegionPhoneService.isValidIpAddress(""));
    }
    
    @Test
    public void testGetIpAddressType() {
        // 测试IPv4
        Optional<String> type = RegionPhoneService.getIpAddressType("8.8.8.8");
        assertTrue(type.isPresent());
        assertEquals("IPv4", type.get());
        
        // 测试私有IP
        Optional<String> privateType = RegionPhoneService.getIpAddressType("192.168.1.1");
        assertTrue(privateType.isPresent());
        assertEquals("PRIVATE_IPv4", privateType.get());
    }
    
    @Test
    public void testResolveDomainToIps() {
        // 测试域名解析
        List<String> ips = RegionPhoneService.resolveDomainToIps("google.com");
        assertFalse(ips.isEmpty());
        assertTrue(ips.stream().allMatch(ip -> RegionPhoneService.isValidIpAddress(ip)));
    }
    
    @Test
    public void testParseDomainLocation() {
        // 测试域名地理位置解析
        Optional<IpLocationInfo> result = RegionPhoneService.parseDomainLocation("google.com");
        // 由于是模拟数据，可能返回空，但不会抛出异常
        assertNotNull(result);
    }
    
    @Test
    public void testGetCountryByIp() {
        // 测试根据IP获取国家信息
        Optional<CountryInfo> country = RegionPhoneService.getCountryByIp("8.8.8.8");
        assertTrue(country.isPresent());
        assertEquals("US", country.get().getCode());
        assertEquals("美国", country.get().getName());
    }
    
    @Test
    public void testBatchParseIpLocations() {
        // 测试批量解析IP地理位置
        List<String> ipAddresses = Arrays.asList("8.8.8.8", "114.114.114.114", "192.168.1.1");
        List<IpLocationInfo> results = RegionPhoneService.batchParseIpLocations(ipAddresses);
        assertFalse(results.isEmpty());
        assertTrue(results.size() <= ipAddresses.size());
    }
} 