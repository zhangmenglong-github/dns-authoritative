package cn.zhangmenglong.dns;


import cn.zhangmenglong.dns.config.Config;
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
        //构造配置文件
        new Config();
        //启动UDP服务
        new UDPServer((Integer) Config.params.get("port"));
        //启动TCP服务
        new TCPServer((Integer) Config.params.get("port"));
    }
}
