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

    public UDPServer (int port) {

        //创建EventLoopGroup，如果支持epoll就创建EpollEventLoopGroup，否则创建NioEventLoopGroup
        EventLoopGroup eventLoopGroup = Epoll.isAvailable() ? new EpollEventLoopGroup() : new NioEventLoopGroup();

        try {
            //创建Bootstrap
            Bootstrap bootstrap = new Bootstrap();

            //设置bootstrap
            bootstrap.group(eventLoopGroup)
                    //设置channel,如果支持epoll就使用EpollDatagramChannel，否则使用NioDatagramChannel
                    .channel(Epoll.isAvailable() ? EpollDatagramChannel.class : NioDatagramChannel.class)
                    //设置SO_BROADCAST
                    .option(ChannelOption.SO_BROADCAST, true)
                    //设置业务处理线程
                    .handler(new UDPThread());

            //如果支持epoll
            if (Epoll.isAvailable()) {
                //设置允许端口复用
                bootstrap.option(EpollChannelOption.SO_REUSEPORT, true);
                //获取cpu数量
                int cpuNum = Runtime.getRuntime().availableProcessors();
                //循环开启多个线程监听53端口消息
                for (int index = 0; index < cpuNum; index++) {
                    ChannelFuture channelFuture = bootstrap.bind(port).sync();
                }
            } else {
                //不支持epoll就单线程监听53端口消息
                ChannelFuture channelFuture = bootstrap.bind(port).sync();
            }


        } catch (Exception ignored) {}










    }

}
