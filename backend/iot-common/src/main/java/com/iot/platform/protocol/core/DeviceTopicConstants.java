package com.iot.platform.protocol.core;

/**
 * 设备主题常量(MQTT 主题规范,参考阿里云 IoT)
 *
 * 上行(设备 → 平台):
 *   /sys/{productKey}/{deviceKey}/thing/event/property/post
 *   /sys/{productKey}/{deviceKey}/thing/event/{eventIdentifier}/post
 *   /sys/{productKey}/{deviceKey}/thing/service/{serviceIdentifier}/reply
 *   /sys/{productKey}/{deviceKey}/heartbeat
 *
 * 下行(平台 → 设备):
 *   /sys/{productKey}/{deviceKey}/thing/service/{serviceIdentifier}/invoke
 *   /sys/{productKey}/{deviceKey}/thing/property/set
 *
 * @author IoT Platform Team
 */
public final class DeviceTopicConstants {

    private DeviceTopicConstants() {}

    public static final String SYS_PREFIX = "/sys";
    public static final String TOPIC_THING_PROPERTY_POST = "/thing/event/property/post";
    public static final String TOPIC_THING_EVENT_POST    = "/thing/event/%s/post";
    public static final String TOPIC_THING_SERVICE_INVOKE = "/thing/service/%s/invoke";
    public static final String TOPIC_THING_PROPERTY_SET   = "/thing/property/set";
    public static final String TOPIC_THING_SERVICE_REPLY  = "/thing/service/%s/reply";
    public static final String TOPIC_HEARTBEAT            = "/heartbeat";

    public static String propertyPostTopic(String productKey, String deviceKey) {
        return SYS_PREFIX + "/" + productKey + "/" + deviceKey + TOPIC_THING_PROPERTY_POST;
    }

    public static String eventPostTopic(String productKey, String deviceKey, String eventIdentifier) {
        return SYS_PREFIX + "/" + productKey + "/" + deviceKey + String.format(TOPIC_THING_EVENT_POST, eventIdentifier);
    }

    public static String serviceInvokeTopic(String productKey, String deviceKey, String serviceIdentifier) {
        return SYS_PREFIX + "/" + productKey + "/" + deviceKey + String.format(TOPIC_THING_SERVICE_INVOKE, serviceIdentifier);
    }

    public static String propertySetTopic(String productKey, String deviceKey) {
        return SYS_PREFIX + "/" + productKey + "/" + deviceKey + TOPIC_THING_PROPERTY_SET;
    }

    public static String serviceReplyTopic(String productKey, String deviceKey, String serviceIdentifier) {
        return SYS_PREFIX + "/" + productKey + "/" + deviceKey + String.format(TOPIC_THING_SERVICE_REPLY, serviceIdentifier);
    }

    public static String heartbeatTopic(String productKey, String deviceKey) {
        return SYS_PREFIX + "/" + productKey + "/" + deviceKey + TOPIC_HEARTBEAT;
    }
}
