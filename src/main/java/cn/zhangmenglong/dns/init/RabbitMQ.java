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
    private Channel channel;

    public RabbitMQ() {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost((String) Config.params.get("mq-server-ip"));
        connectionFactory.setPort((Integer) Config.params.get("mq-server-port"));
        connectionFactory.setUsername((String) Config.params.get("mq-server-username"));
        connectionFactory.setPassword((String) Config.params.get("mq-server-password"));
        String authoritativeQueue = (String) Config.params.get("mq-server-authoritative-init-queue");
        String updateExchange = (String) Config.params.get("mq-server-authoritative-update-exchange");
        String initExchange = (String) Config.params.get("mq-server-authoritative-init-exchange");
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
            channel.exchangeDeclare(initExchange, BuiltinExchangeType.FANOUT, true, false, null);
            channel.queueBind(authoritativeQueue, updateExchange, "");
            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                    try {
                        Map<String, Object> zone = (Map<String, Object>) objectInputStream.readObject();
                        String domain = (String) zone.get("domain");
                        Map<String, List<Record>> geoZone = (Map<String, List<Record>>) zone.get("geoZone");
                        List<Record> recordList = geoZone.get("*");
                        System.out.println(recordList);

                        DNSZone dnsZone = new DNSZone("*", new Name(domain), recordList.toArray(new Record[]{}));

                        for (String geo : geoZone.keySet()) {
                            if (!geo.contentEquals("*")) {
                                recordList = geoZone.get(geo);
                                for (Record record : recordList) {
                                    dnsZone.addRecord(geo, record);
                                    System.out.println(record);
                                }
                            }
                        }
                        ZoneMap.collect.put(domain, dnsZone);
                    } catch (ClassNotFoundException ignored) {
                    }
                }
            };
            channel.basicConsume(authoritativeQueue, true, consumer);
            channel.basicPublish(initExchange, "", null, authoritativeQueue.getBytes());
        } catch (IOException | TimeoutException ignored) {
        }
    }
}
