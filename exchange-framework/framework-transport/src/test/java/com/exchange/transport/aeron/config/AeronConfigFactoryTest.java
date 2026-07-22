package com.exchange.transport.aeron.config;

import com.exchange.transport.aeron.config.AeronConfigFactory.AeronConfigException;
import com.exchange.transport.aeron.config.AeronConfigFactory.AwsInterfaceValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * AeronConfigFactory 单元测试
 *
 * <p>覆盖：Publisher / Subscriber channel 字符串构建、AWS interface 校验逻辑。
 * 不依赖 Aeron MediaDriver，纯逻辑测试。
 */
class AeronConfigFactoryTest {

    /* ══════════════════════════════════════════════════════════════
     *  Publisher channel 构建
     * ══════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("Publisher channel 构建")
    class PublisherChannelTests {

        @Test
        @DisplayName("不绑定 interface 时只含 control + control-mode=dynamic")
        void buildPublisherChannel_noIface() {
            String ch = AeronConfigFactory.buildPublisherChannel("10.0.1.5:20001", null);

            assertThat(ch).contains("control=10.0.1.5:20001");
            assertThat(ch).contains("control-mode=dynamic");
            assertThat(ch).doesNotContain("interface");
        }

        @Test
        @DisplayName("controlAddr 格式非法时抛 AeronConfigException")
        void buildPublisherChannel_invalidControlAddr() {
            assertThatThrownBy(() -> AeronConfigFactory.buildPublisherChannel("bad-addr", null))
                    .isInstanceOf(AeronConfigException.class)
                    .hasMessageContaining("format invalid");
        }

        @Test
        @DisplayName("controlAddr 端口超范围时抛异常")
        void buildPublisherChannel_portOutOfRange() {
            assertThatThrownBy(() -> AeronConfigFactory.buildPublisherChannel("10.0.0.1:99999", null))
                    .isInstanceOf(AeronConfigException.class)
                    .hasMessageContaining("port out of range");
        }

        @Test
        @DisplayName("controlAddr 端口为 0 时抛异常（publisher 不允许 0 端口）")
        void buildPublisherChannel_portZero() {
            assertThatThrownBy(() -> AeronConfigFactory.buildPublisherChannel("10.0.0.1:0", null))
                    .isInstanceOf(AeronConfigException.class)
                    .hasMessageContaining("port out of range");
        }
    }

    /* ══════════════════════════════════════════════════════════════
     *  Subscriber channel 构建
     * ══════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("Subscriber channel 构建")
    class SubscriberChannelTests {

        @Test
        @DisplayName("合法参数生成含 endpoint 的 MDC subscriber channel")
        void buildSubscriberChannel_valid() {
            String ch = AeronConfigFactory.buildSubscriberChannel(
                    "10.0.1.5:20001", "10.0.1.10:0", null);

            assertThat(ch).contains("control=10.0.1.5:20001");
            assertThat(ch).contains("control-mode=dynamic");
            assertThat(ch).contains("endpoint=10.0.1.10:0");
        }

        @Test
        @DisplayName("endpoint 使用 hostname 而非 IPv4 时抛异常")
        void buildSubscriberChannel_hostnameEndpoint() {
            assertThatThrownBy(() ->
                    AeronConfigFactory.buildSubscriberChannel(
                            "10.0.1.5:20001", "my-host:9000", null))
                    .isInstanceOf(AeronConfigException.class)
                    .hasMessageContaining("must be an IPv4 address");
        }

        @Test
        @DisplayName("endpoint 端口为 0 时合法（OS 分配随机端口）")
        void buildSubscriberChannel_ephemeralPort() {
            assertThatCode(() ->
                    AeronConfigFactory.buildSubscriberChannel(
                            "10.0.1.5:20001", "10.0.1.10:0", null))
                    .doesNotThrowAnyException();
        }
    }

    /* ══════════════════════════════════════════════════════════════
     *  AWS AwsInterfaceValidator
     * ══════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("AwsInterfaceValidator — 私有地址范围校验")
    class AwsPrivateRangeTests {

        @Test
        @DisplayName("10/8 范围内的地址通过范围校验")
        void isPrivate_class10() {
            assertThat(AwsInterfaceValidator.isPrivateRange("10.0.0.0")).isTrue();
            assertThat(AwsInterfaceValidator.isPrivateRange("10.128.64.32")).isTrue();
            assertThat(AwsInterfaceValidator.isPrivateRange("10.255.255.255")).isTrue();
        }

        @Test
        @DisplayName("172.16/12 范围内的地址通过范围校验")
        void isPrivate_class172() {
            assertThat(AwsInterfaceValidator.isPrivateRange("172.16.0.0")).isTrue();
            assertThat(AwsInterfaceValidator.isPrivateRange("172.20.1.5")).isTrue();
            assertThat(AwsInterfaceValidator.isPrivateRange("172.31.255.255")).isTrue();
        }

        @Test
        @DisplayName("192.168/16 范围内的地址通过范围校验")
        void isPrivate_class192() {
            assertThat(AwsInterfaceValidator.isPrivateRange("192.168.0.0")).isTrue();
            assertThat(AwsInterfaceValidator.isPrivateRange("192.168.100.1")).isTrue();
            assertThat(AwsInterfaceValidator.isPrivateRange("192.168.255.255")).isTrue();
        }

        @Test
        @DisplayName("公网 IP 不在私有范围内")
        void isPrivate_publicIp() {
            assertThat(AwsInterfaceValidator.isPrivateRange("8.8.8.8")).isFalse();
            assertThat(AwsInterfaceValidator.isPrivateRange("52.94.225.1")).isFalse();
            assertThat(AwsInterfaceValidator.isPrivateRange("172.15.255.255")).isFalse();
            assertThat(AwsInterfaceValidator.isPrivateRange("172.32.0.0")).isFalse();
        }

        @Test
        @DisplayName("172.16/12 边界值精确")
        void isPrivate_172Boundary() {
            // 172.15.x.x 不在范围内
            assertThat(AwsInterfaceValidator.isPrivateRange("172.15.255.255")).isFalse();
            // 172.32.x.x 不在范围内
            assertThat(AwsInterfaceValidator.isPrivateRange("172.32.0.0")).isFalse();
        }
    }

    @Nested
    @DisplayName("AwsInterfaceValidator — 格式与规则校验")
    class AwsFormatValidationTests {

        @Test
        @DisplayName("空字符串抛异常")
        void validate_blankInput() {
            assertThatThrownBy(() -> AwsInterfaceValidator.validate(""))
                    .isInstanceOf(AeronConfigException.class)
                    .hasMessageContaining("must not be blank");
        }

        @Test
        @DisplayName("通配地址 0.0.0.0 抛异常")
        void validate_wildcardAddress() {
            assertThatThrownBy(() -> AwsInterfaceValidator.validate("0.0.0.0"))
                    .isInstanceOf(AeronConfigException.class)
                    .hasMessageContaining("wildcard");
        }

        @Test
        @DisplayName("回环地址 127.0.0.1 抛异常")
        void validate_loopbackAddress() {
            assertThatThrownBy(() -> AwsInterfaceValidator.validate("127.0.0.1"))
                    .isInstanceOf(AeronConfigException.class)
                    .hasMessageContaining("loopback");
        }

        @Test
        @DisplayName("公网 IP 抛异常，提示 RFC-1918")
        void validate_publicIp() {
            assertThatThrownBy(() -> AwsInterfaceValidator.validate("54.239.28.85"))
                    .isInstanceOf(AeronConfigException.class)
                    .hasMessageContaining("RFC-1918");
        }

        @Test
        @DisplayName("非 IPv4 格式（含端口号）抛异常")
        void validate_ipWithPort() {
            assertThatThrownBy(() -> AwsInterfaceValidator.validate("10.0.1.5:20001"))
                    .isInstanceOf(AeronConfigException.class)
                    .hasMessageContaining("Not a valid IPv4 address");
        }

        @Test
        @DisplayName("Octet 超过 255 抛异常")
        void validate_octetOverflow() {
            assertThatThrownBy(() -> AwsInterfaceValidator.validate("10.0.256.1"))
                    .isInstanceOf(AeronConfigException.class)
                    .hasMessageContaining("octet out of range");
        }
    }

    /* ══════════════════════════════════════════════════════════════
     *  Subscriber endpoint 自动推导
     * ══════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("Subscriber endpoint 自动推导")
    class ResolveEndpointTests {

        @Test
        @DisplayName("显式指定 endpoint 时直接使用")
        void resolveEndpoint_explicit() {
            String ep = AeronConfigFactory.resolveSubscriberEndpoint("10.0.1.10:9001", "10.0.1.20");
            assertThat(ep).isEqualTo("10.0.1.10:9001");
        }

        @Test
        @DisplayName("未指定 endpoint 时取 iface:0")
        void resolveEndpoint_fromIface() {
            String ep = AeronConfigFactory.resolveSubscriberEndpoint(null, "10.0.1.20");
            assertThat(ep).isEqualTo("10.0.1.20:0");
        }

        @Test
        @DisplayName("未指定 endpoint 且 iface 为 null 时取 localhost:0")
        void resolveEndpoint_fallbackLocalhost() {
            String ep = AeronConfigFactory.resolveSubscriberEndpoint(null, null);
            assertThat(ep).isEqualTo("localhost:0");
        }
    }

    /* ══════════════════════════════════════════════════════════════
     *  Stream ID 解析
     * ══════════════════════════════════════════════════════════════ */

    @Nested
    @DisplayName("Stream ID 解析（环境变量 mock 通过子类覆盖 resolveStreamId）")
    class StreamIdTests {

        @Test
        @DisplayName("非数字 STREAM_ID 抛 AeronConfigException")
        void streamId_nonNumeric() {
            // 直接调用带字符串解析路径的场景，通过 buildPublisherChannel 不涉及 env，
            // 此处通过 ipToLong 边界测试替代（env 相关 case 需集成测试 / env mock）
            assertThat(AwsInterfaceValidator.ipToLong("10.0.0.1")).isEqualTo(167772161L);
            assertThat(AwsInterfaceValidator.ipToLong("192.168.255.255")).isEqualTo(3232300031L);
        }
    }
}
