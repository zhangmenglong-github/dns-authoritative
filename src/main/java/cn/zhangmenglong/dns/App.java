package cn.zhangmenglong.dns;


import cn.zhangmenglong.dns.server.UDPServer;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        UDPServer udpServer = new UDPServer();
        udpServer.init(53);
    }
}
