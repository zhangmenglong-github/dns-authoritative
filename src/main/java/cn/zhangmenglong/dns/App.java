package cn.zhangmenglong.dns;


import cn.zhangmenglong.dns.config.Config;
import cn.zhangmenglong.dns.init.RabbitMQ;
import cn.zhangmenglong.dns.server.TCPServer;
import cn.zhangmenglong.dns.server.UDPServer;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        try {
            //构造配置文件
            new Config();

            new RabbitMQ();

            //启动UDP服务
            new UDPServer((Integer) Config.params.get("dns-port"));
            //启动TCP服务
            new TCPServer((Integer) Config.params.get("dns-port"));
        } catch (Exception ignored) {}

    }
}
