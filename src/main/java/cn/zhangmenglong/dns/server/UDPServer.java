package cn.zhangmenglong.dns.server;

import cn.zhangmenglong.dns.thread.UDPThread;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollChannelOption;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

public class UDPServer {

    private EventLoopGroup  eventLoopGroup;

    public void init(int port) {

        eventLoopGroup = Epoll.isAvailable() ? new EpollEventLoopGroup() : new NioEventLoopGroup();

        try {
            Bootstrap bootstrap = new Bootstrap();

            bootstrap.group(eventLoopGroup)
                    .channel(Epoll.isAvailable() ? EpollDatagramChannel.class : NioDatagramChannel.class)
                    .option(ChannelOption.SO_BROADCAST, true)
                    .handler(new UDPThread());

            if (Epoll.isAvailable()) {
                bootstrap.option(EpollChannelOption.SO_REUSEPORT, true);
            }

            if (Epoll.isAvailable()) {
                int cpuNum = Runtime.getRuntime().availableProcessors();
                for (int index = 0; index < cpuNum; index++) {
                    ChannelFuture channelFuture = bootstrap.bind(port).sync();
                }
            } else {
                ChannelFuture channelFuture = bootstrap.bind(port).sync();
            }


        } catch (Exception ignored) {}










    }

}
