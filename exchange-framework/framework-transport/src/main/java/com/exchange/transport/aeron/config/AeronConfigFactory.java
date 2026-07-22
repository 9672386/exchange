package com.exchange.transport.aeron.config;

import io.aeron.ChannelUriStringBuilder;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.net.*;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Aeron MDC Channel 配置工厂
 *
 * <p>通过环境变量动态生成 Publisher / Subscriber 的 Aeron MDC channel 字符串，
 * 并对 AWS 环境下的 {@code interface} 绑定进行安全校验。
 *
 * <h3>支持的环境变量</h3>
 * <pre>
 *   MATCH_CONTROL_ADDR  MDC 控制地址，格式 ip:port，如 10.0.1.5:20001（必填，无默认会 warn）
 *   STREAM_ID           Aeron Stream ID，正整数，默认 1001
 *   AERON_INTERFACE     (可选) 本机 ENI 私有 IP，AWS 环境下用于绑定特定网卡
 * </pre>
 *
 * <h3>MDC channel 格式</h3>
 * <pre>
 *   Publisher  : aeron:udp?control=<addr>|control-mode=dynamic[|interface=<ip>]
 *   Subscriber : aeron:udp?control=<addr>|control-mode=dynamic|endpoint=<local-ip>:0[|interface=<ip>]
 * </pre>
 *
 * <h3>AWS interface 校验规则</h3>
 * <ol>
 *   <li>合法 IPv4 格式</li>
 *   <li>RFC-1918 私有地址（10/8、172.16/12、192.168/16）</li>
 *   <li>非回环地址（127.x.x.x）</li>
 *   <li>非通配地址（0.0.0.0）</li>
 *   <li>本机必须存在该 IP 对应的 UP 状态网卡</li>
 * </ol>
 */
@Slf4j
public final class AeronConfigFactory {

    /* ─── 环境变量 Key ──────────────────────────────────────────── */

    public static final String ENV_MATCH_CONTROL_ADDR = "MATCH_CONTROL_ADDR";
    public static final String ENV_STREAM_ID          = "STREAM_ID";
    public static final String ENV_AERON_INTERFACE    = "AERON_INTERFACE";

    /* ─── 默认值 ─────────────────────────────────────────────────── */

    private static final String DEFAULT_CONTROL_ADDR  = "localhost:20001";
    private static final int    DEFAULT_STREAM_ID     = 1001;

    /* ─── 校验正则 ───────────────────────────────────────────────── */

    /** host:port 或 ip:port，host 允许 DNS 名（仅用于 controlAddr） */
    private static final Pattern ADDR_PORT_RE =
            Pattern.compile("^([\\w.\\-]+):(\\d{1,5})$");

    /** 严格 IPv4 格式（不接受 DNS 名，用于 endpoint / interface） */
    private static final Pattern IPV4_RE =
            Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");

    private AeronConfigFactory() {}

    /* ══════════════════════════════════════════════════════════════
     *  Public API — 从环境变量构建配置
     * ══════════════════════════════════════════════════════════════ */

    /**
     * 从环境变量构建 <b>Publisher</b>（MDC dynamic）配置。
     *
     * <p>通常由撮合引擎（match-core）调用，用于向 Risk、Quote 等模块广播撮合结果。
     *
     * @return Publisher channel 配置，包含 channel 字符串与 stream ID
     * @throws AeronConfigException 若环境变量格式非法或 AWS interface 校验失败
     */
    public static PublisherChannelConfig buildPublisherFromEnv() {
        String controlAddr = resolveControlAddr();
        int    streamId    = resolveStreamId();
        String iface       = resolveInterface();

        String channel = buildPublisherChannel(controlAddr, iface);
        log.info("[Aeron|MDC] Publisher ready — channel={}, streamId={}", channel, streamId);
        return new PublisherChannelConfig(channel, streamId, controlAddr, iface);
    }

    /**
     * 从环境变量构建 <b>Subscriber</b>（MDC dynamic）配置。
     *
     * <p>通常由风控（risk-core）、行情（quote-core）等消费方调用。
     *
     * @param localEndpoint 本机监听地址，格式 {@code ip:port}，port 传 {@code 0} 表示随机端口。
     *                      传 {@code null} 时自动取 {@code AERON_INTERFACE:0}，
     *                      若 {@code AERON_INTERFACE} 也未设则使用 {@code localhost:0}。
     * @return Subscriber channel 配置
     * @throws AeronConfigException 若校验失败
     */
    public static SubscriberChannelConfig buildSubscriberFromEnv(String localEndpoint) {
        String controlAddr      = resolveControlAddr();
        int    streamId         = resolveStreamId();
        String iface            = resolveInterface();
        String resolvedEndpoint = resolveSubscriberEndpoint(localEndpoint, iface);

        String channel = buildSubscriberChannel(controlAddr, resolvedEndpoint, iface);
        log.info("[Aeron|MDC] Subscriber ready — channel={}, streamId={}", channel, streamId);
        return new SubscriberChannelConfig(channel, streamId, controlAddr, resolvedEndpoint, iface);
    }

    /* ══════════════════════════════════════════════════════════════
     *  Public API — 直接构建 channel 字符串（不依赖环境变量）
     * ══════════════════════════════════════════════════════════════ */

    /**
     * 构建 MDC Publisher channel 字符串。
     *
     * @param controlAddr MDC 控制地址，格式 {@code host:port}
     * @param iface       本机网卡 IP（AWS ENI），{@code null} 则不绑定
     * @return Aeron channel URI
     */
    public static String buildPublisherChannel(String controlAddr, String iface) {
        validateControlAddr(controlAddr);
        if (iface != null && !iface.isBlank()) {
            AwsInterfaceValidator.validate(iface);
        }

        ChannelUriStringBuilder builder = new ChannelUriStringBuilder()
                .media("udp")
                .controlEndpoint(controlAddr)
                .controlMode("dynamic");

        if (iface != null && !iface.isBlank()) {
            builder.networkInterface(iface);
        }
        return builder.build();
    }

    /**
     * 构建 MDC Subscriber channel 字符串。
     *
     * @param controlAddr   MDC 控制地址，格式 {@code host:port}
     * @param localEndpoint 本机监听地址，格式 {@code ip:port}（port 可为 0）
     * @param iface         本机网卡 IP（AWS ENI），{@code null} 则不绑定
     * @return Aeron channel URI
     */
    public static String buildSubscriberChannel(String controlAddr, String localEndpoint, String iface) {
        validateControlAddr(controlAddr);
        validateIpPort(localEndpoint, "localEndpoint");
        if (iface != null && !iface.isBlank()) {
            AwsInterfaceValidator.validate(iface);
        }

        ChannelUriStringBuilder builder = new ChannelUriStringBuilder()
                .media("udp")
                .controlEndpoint(controlAddr)
                .controlMode("dynamic")
                .endpoint(localEndpoint);

        if (iface != null && !iface.isBlank()) {
            builder.networkInterface(iface);
        }
        return builder.build();
    }

    /* ══════════════════════════════════════════════════════════════
     *  Environment resolution（包级可见，便于单元测试 override）
     * ══════════════════════════════════════════════════════════════ */

    static String resolveControlAddr() {
        String val = System.getenv(ENV_MATCH_CONTROL_ADDR);
        if (val == null || val.isBlank()) {
            log.warn("[Aeron] {} not set, falling back to default: {}",
                    ENV_MATCH_CONTROL_ADDR, DEFAULT_CONTROL_ADDR);
            return DEFAULT_CONTROL_ADDR;
        }
        return val.trim();
    }

    static int resolveStreamId() {
        String val = System.getenv(ENV_STREAM_ID);
        if (val == null || val.isBlank()) {
            log.warn("[Aeron] {} not set, falling back to default: {}", ENV_STREAM_ID, DEFAULT_STREAM_ID);
            return DEFAULT_STREAM_ID;
        }
        try {
            int id = Integer.parseInt(val.trim());
            if (id <= 0) {
                throw new AeronConfigException(
                        ENV_STREAM_ID + " must be a positive integer, got: " + val);
            }
            return id;
        } catch (NumberFormatException e) {
            throw new AeronConfigException("Invalid " + ENV_STREAM_ID + " value: " + val, e);
        }
    }

    static String resolveInterface() {
        String val = System.getenv(ENV_AERON_INTERFACE);
        return (val == null || val.isBlank()) ? null : val.trim();
    }

    static String resolveSubscriberEndpoint(String explicit, String iface) {
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        // 未显式指定：使用 iface:0 或 localhost:0（让 OS 分配端口）
        String host = (iface != null && !iface.isBlank()) ? iface : "localhost";
        String resolved = host + ":0";
        log.debug("[Aeron] localEndpoint not specified, resolved to {}", resolved);
        return resolved;
    }

    /* ══════════════════════════════════════════════════════════════
     *  Validation
     * ══════════════════════════════════════════════════════════════ */

    private static void validateControlAddr(String addr) {
        if (addr == null || addr.isBlank()) {
            throw new AeronConfigException("controlAddr must not be blank");
        }
        Matcher m = ADDR_PORT_RE.matcher(addr);
        if (!m.matches()) {
            throw new AeronConfigException(
                    "controlAddr format invalid (expected host:port): " + addr);
        }
        int port = Integer.parseInt(m.group(2));
        if (port < 1 || port > 65535) {
            throw new AeronConfigException(
                    "controlAddr port out of range [1,65535]: " + port);
        }
    }

    /**
     * 校验 ip:port 格式（endpoint / interface 等严格要求 IPv4）。
     * port 允许为 0（ephemeral）。
     */
    private static void validateIpPort(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new AeronConfigException(fieldName + " must not be blank");
        }
        Matcher m = ADDR_PORT_RE.matcher(value);
        if (!m.matches()) {
            throw new AeronConfigException(
                    fieldName + " format invalid (expected ip:port): " + value);
        }
        // endpoint host 部分必须是合法 IPv4
        String host = m.group(1);
        if (!IPV4_RE.matcher(host).matches()) {
            throw new AeronConfigException(
                    fieldName + " host must be an IPv4 address, got: " + host);
        }
        int port = Integer.parseInt(m.group(2));
        if (port < 0 || port > 65535) {
            throw new AeronConfigException(
                    fieldName + " port out of range [0,65535]: " + port);
        }
    }

    /* ══════════════════════════════════════════════════════════════
     *  AWS Interface Validator
     * ══════════════════════════════════════════════════════════════ */

    /**
     * AWS 环境下 {@code AERON_INTERFACE} 安全校验器。
     *
     * <p>AWS EC2 实例的 Aeron interface 绑定必须指向 ENI 的私有 IP，
     * 否则会导致 UDP 包从错误网卡收发，造成静默丢包或路由异常。
     */
    public static final class AwsInterfaceValidator {

        /**
         * RFC-1918 私有地址范围（long 区间，含端点）。
         * <pre>
         *   10.0.0.0     – 10.255.255.255   (10/8)
         *   172.16.0.0   – 172.31.255.255   (172.16/12)
         *   192.168.0.0  – 192.168.255.255  (192.168/16)
         * </pre>
         */
        private static final List<long[]> PRIVATE_RANGES = List.of(
                new long[]{ ipToLong("10.0.0.0"),    ipToLong("10.255.255.255")   },
                new long[]{ ipToLong("172.16.0.0"),  ipToLong("172.31.255.255")   },
                new long[]{ ipToLong("192.168.0.0"), ipToLong("192.168.255.255")  }
        );

        private AwsInterfaceValidator() {}

        /**
         * 校验 AWS interface IP，失败抛 {@link AeronConfigException}。
         *
         * @param ifaceIp ENI 私有 IP，如 {@code 10.0.1.5}
         */
        public static void validate(String ifaceIp) {
            if (ifaceIp == null || ifaceIp.isBlank()) {
                throw new AeronConfigException("AWS interface IP must not be blank");
            }

            // Rule 1: 必须是合法 IPv4
            InetAddress addr = parseIpv4Strict(ifaceIp);

            // Rule 2: 不允许通配地址
            if ("0.0.0.0".equals(ifaceIp)) {
                throw new AeronConfigException(
                        "[AWS] interface must not be wildcard 0.0.0.0; " +
                        "specify the ENI private IP explicitly (e.g. 10.0.1.5)");
            }

            // Rule 3: 不允许回环地址
            if (addr.isLoopbackAddress()) {
                throw new AeronConfigException(
                        "[AWS] interface must not be loopback " + ifaceIp +
                        "; use the ENI private IP assigned to this instance");
            }

            // Rule 4: 必须在 RFC-1918 私有地址范围
            if (!isPrivateRange(ifaceIp)) {
                throw new AeronConfigException(
                        "[AWS] interface IP " + ifaceIp + " is not in RFC-1918 private range " +
                        "(10/8, 172.16/12, 192.168/16). " +
                        "Public or link-local IPs cannot be used as Aeron interface binding in AWS VPC.");
            }

            // Rule 5: 本机必须存在该 IP 对应的、处于 UP 状态的网卡
            validateExistsOnLocalNic(ifaceIp);

            log.debug("[Aeron|AWS] Interface {} passed all validation checks", ifaceIp);
        }

        // ── 内部 helpers ─────────────────────────────────────────

        private static InetAddress parseIpv4Strict(String ip) {
            Matcher m = IPV4_RE.matcher(ip);
            if (!m.matches()) {
                throw new AeronConfigException(
                        "[AWS] Not a valid IPv4 address: " + ip +
                        " (Aeron interface binding requires an explicit IPv4, not a hostname)");
            }
            for (int g = 1; g <= 4; g++) {
                int octet = Integer.parseInt(m.group(g));
                if (octet > 255) {
                    throw new AeronConfigException("[AWS] IPv4 octet out of range [0,255] in: " + ip);
                }
            }
            try {
                return InetAddress.getByName(ip);
            } catch (UnknownHostException e) {
                // getByName 对合法 IPv4 字符串不会抛此异常，防御性保留
                throw new AeronConfigException("[AWS] Cannot resolve interface IP: " + ip, e);
            }
        }

        static boolean isPrivateRange(String ip) {
            long ipLong = ipToLong(ip);
            for (long[] range : PRIVATE_RANGES) {
                if (ipLong >= range[0] && ipLong <= range[1]) return true;
            }
            return false;
        }

        private static void validateExistsOnLocalNic(String ifaceIp) {
            try {
                Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
                if (nics == null) {
                    throw new AeronConfigException(
                            "[AWS] Cannot enumerate network interfaces on this host");
                }

                while (nics.hasMoreElements()) {
                    NetworkInterface nic = nics.nextElement();

                    // 跳过 DOWN 和回环口
                    if (!nic.isUp() || nic.isLoopback()) continue;

                    Enumeration<InetAddress> addrs = nic.getInetAddresses();
                    while (addrs.hasMoreElements()) {
                        if (addrs.nextElement().getHostAddress().equals(ifaceIp)) {
                            log.debug("[Aeron|AWS] Interface {} matched NIC {} ({})",
                                    ifaceIp, nic.getName(), nic.getDisplayName());
                            return; // 校验通过
                        }
                    }
                }

                throw new AeronConfigException(
                        "[AWS] Interface IP " + ifaceIp + " does not exist on any local UP network interface. " +
                        "Possible causes: (1) ENI not attached to this instance, " +
                        "(2) IP mismatch between AERON_INTERFACE and the actual ENI private IP, " +
                        "(3) interface is DOWN. Run `ip addr show` to confirm.");

            } catch (SocketException e) {
                throw new AeronConfigException(
                        "[AWS] Failed to enumerate network interfaces: " + e.getMessage(), e);
            }
        }

        /**
         * 将点分十进制 IPv4 转换为 long，用于区间比较。
         * 调用方保证 ip 格式合法。
         */
        static long ipToLong(String ip) {
            String[] parts = ip.split("\\.");
            long result = 0;
            for (String part : parts) {
                result = result * 256 + Integer.parseInt(part);
            }
            return result;
        }
    }

    /* ══════════════════════════════════════════════════════════════
     *  Value Objects
     * ══════════════════════════════════════════════════════════════ */

    /**
     * MDC Publisher 通道配置。
     * 持有 channel 字符串和 stream ID，可直接传入 {@link io.aeron.Aeron#addPublication}。
     */
    @Getter
    @ToString
    public static final class PublisherChannelConfig {
        /** Aeron channel URI */
        private final String channel;
        /** Aeron stream ID */
        private final int    streamId;
        /** MDC 控制地址（调试用） */
        private final String controlAddr;
        /** 绑定的本机网卡 IP，未绑定则为 null */
        private final String boundInterface;

        private PublisherChannelConfig(String channel, int streamId,
                                       String controlAddr, String boundInterface) {
            this.channel       = channel;
            this.streamId      = streamId;
            this.controlAddr   = controlAddr;
            this.boundInterface = boundInterface;
        }
    }

    /**
     * MDC Subscriber 通道配置。
     * 持有 channel 字符串和 stream ID，可直接传入 {@link io.aeron.Aeron#addSubscription}。
     */
    @Getter
    @ToString
    public static final class SubscriberChannelConfig {
        /** Aeron channel URI */
        private final String channel;
        /** Aeron stream ID */
        private final int    streamId;
        /** MDC 控制地址（调试用） */
        private final String controlAddr;
        /** 本机监听地址（ip:port） */
        private final String localEndpoint;
        /** 绑定的本机网卡 IP，未绑定则为 null */
        private final String boundInterface;

        private SubscriberChannelConfig(String channel, int streamId,
                                        String controlAddr, String localEndpoint,
                                        String boundInterface) {
            this.channel        = channel;
            this.streamId       = streamId;
            this.controlAddr    = controlAddr;
            this.localEndpoint  = localEndpoint;
            this.boundInterface = boundInterface;
        }
    }

    /* ══════════════════════════════════════════════════════════════
     *  Exception
     * ══════════════════════════════════════════════════════════════ */

    /**
     * Aeron 配置校验失败异常。属于 unchecked，启动时触发，让进程快速失败。
     */
    public static final class AeronConfigException extends RuntimeException {
        public AeronConfigException(String message) {
            super(message);
        }
        public AeronConfigException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
