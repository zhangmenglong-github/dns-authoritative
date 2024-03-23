package cn.zhangmenglong.dns.server;

import cn.zhangmenglong.dns.thread.TCPThread;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;

public class TCPServer {

    public TCPServer(int port) {
        /*
         * 创建BossGroup 和 WorkerGroup
         * 说明:
         *  1. 创建连个线程组 bossGroup 和 workerGroup
         *  2. bossGroup只处理连接请求(accept), 真正的和客户端业务处理, 会交给workerGroup完成
         *  3. 两个都是无线循环
         *  4. bossGroup 和 workerGroup 含有的子线程(NioEventLoop)的个数
         * 默认: 实际CPU核数 * 2
         */
        EventLoopGroup bossGroup = Epoll.isAvailable() ? new EpollEventLoopGroup() : new NioEventLoopGroup();
        EventLoopGroup workerGroup = Epoll.isAvailable() ? new EpollEventLoopGroup() : new NioEventLoopGroup();


        try {
            // 创建服务器端的启动对象, 配置参数
            ServerBootstrap serverBootstrap = new ServerBootstrap();

            serverBootstrap.group(bossGroup, workerGroup) //设置两个线程组
                    .channel(Epoll.isAvailable() ? EpollServerSocketChannel.class : NioServerSocketChannel.class) // 使用NioServerSocketChannel作为服务器的通道实现
                    .option(ChannelOption.SO_BACKLOG, 128) // 设置线程队列等待连接个数
                    .childHandler(new TCPThread());

            // 绑定一个接口 并且同步 生成一个 ChannelFuture 对象
            // 启动服务器(并绑定端口)
            ChannelFuture channelFuture = serverBootstrap.bind(port).sync();
        } catch (InterruptedException ignored) {}

    }

}
