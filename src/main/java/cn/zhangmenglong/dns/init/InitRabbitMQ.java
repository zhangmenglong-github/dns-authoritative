package cn.zhangmenglong.dns.init;

import cn.zhangmenglong.dns.config.Config;
import com.rabbitmq.client.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

public class InitRabbitMQ {

    private static ConnectionFactory connectionFactory;

    static {
        connectionFactory = new ConnectionFactory();
        connectionFactory.setHost((String) Config.params.get("mq-server-ip"));
        connectionFactory.setPort((Integer) Config.params.get("mq-server-port"));
        connectionFactory.setUsername((String) Config.params.get("mq-server-username"));
        connectionFactory.setPassword((String) Config.params.get("mq-server-password"));
    }

    private static Connection getConnection() {
        try {
            return connectionFactory.newConnection();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public static void init() {
        String uuid = UUID.randomUUID().toString();

        Connection initConnection = getConnection();

        try {
            Channel channel = initConnection.createChannel();

            channel.basicPublish((String) Config.params.get("mq-server-zone-init-exchange"), "", null, uuid.getBytes());

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Connection updateConnection = getConnection();


        try {
            Channel channel = updateConnection.createChannel();

            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    String s = new String(body);
                    System.out.println(body);
                }
            };

//            String queue, boolean durable, boolean exclusive, boolean autoDelete,
//            Map<String, Object> arguments
            channel.queueDeclare(uuid, false, false, false, null);

            channel.basicConsume(uuid, true, consumer);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

}
