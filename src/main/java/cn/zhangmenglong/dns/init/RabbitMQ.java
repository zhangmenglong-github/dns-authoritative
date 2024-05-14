package cn.zhangmenglong.dns.init;

import cn.zhangmenglong.dns.config.Config;
import cn.zhangmenglong.dns.zone.DNSZone;
import cn.zhangmenglong.dns.zone.ZoneMap;
import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.DefaultExceptionHandler;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

public class RabbitMQ {
    private static Channel channel;

    private static String statisticsQueue;

    public static void init() {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost((String) Config.params.get("mq-server-ip"));
        connectionFactory.setPort((Integer) Config.params.get("mq-server-port"));
        connectionFactory.setUsername((String) Config.params.get("mq-server-username"));
        connectionFactory.setPassword((String) Config.params.get("mq-server-password"));
        String adminQueue = (String) Config.params.get("mq-server-admin-init-queue");
        String authoritativeQueue = (String) Config.params.get("mq-server-authoritative-init-queue");
        String updateExchange = (String) Config.params.get("mq-server-authoritative-update-exchange");
        statisticsQueue = (String) Config.params.get("mq-server-authoritative-statistics-queue");
        final ExceptionHandler exceptionHandler = new DefaultExceptionHandler() {
            @Override
            public void handleConsumerException(Channel channel, Throwable exception, Consumer consumer, String consumerTag, String methodName) {}
        };
        connectionFactory.setExceptionHandler(exceptionHandler);
        try {
            Connection connection = connectionFactory.newConnection();
            channel = connection.createChannel();
            channel.queueDeclare(authoritativeQueue, true, false, true, null);
            channel.exchangeDeclare(updateExchange, BuiltinExchangeType.FANOUT, true, false, null);
            channel.queueBind(authoritativeQueue, updateExchange, "");
            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                    try {
                        Map<String, Object> zone = (Map<String, Object>) objectInputStream.readObject();
                        String domain = (String) zone.get("domain");
                        String type = (String) zone.get("type");

                        if ("update".contentEquals(type)) {
                            Map<String, List<Record>> geoZone = (Map<String, List<Record>>) zone.get("geoZone");
                            List<Record> recordList = geoZone.get("*");

                            DNSZone dnsZone = new DNSZone("*", new Name(domain), recordList.toArray(new Record[]{}));

                            dnsZone.setDnssec((Boolean) zone.get("dnssec"));

                            for (String geo : geoZone.keySet()) {
                                if (!geo.contentEquals("*")) {
                                    recordList = geoZone.get(geo);
                                    for (Record record : recordList) {
                                        dnsZone.addRecord(geo, record);
                                    }
                                }
                            }
                            ZoneMap.collect.put(domain, dnsZone);
                        } else if ("delete".contentEquals(type)) {
                            ZoneMap.collect.remove(domain);
                        }

                    } catch (ClassNotFoundException ignored) {
                    }
                }
            };
            channel.basicConsume(authoritativeQueue, true, consumer);
            channel.basicPublish("", adminQueue, null, authoritativeQueue.getBytes());
        } catch (IOException | TimeoutException ignored) {}
    }

    public static void send(byte[] body) {
        try {
            channel.basicPublish("", statisticsQueue, null, body);
        } catch (IOException ignored) {}
    }
}
