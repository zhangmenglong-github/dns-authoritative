package cn.zhangmenglong.dns.thread;

import cn.zhangmenglong.dns.process.ProcessRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.xbill.DNS.Message;

import java.io.IOException;

@ChannelHandler.Sharable
public class UDPThread extends SimpleChannelInboundHandler<DatagramPacket> {

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, DatagramPacket datagramPacket) {
        try {
            //获取UDP报文content
            ByteBuf receiveByteBuf = datagramPacket.content();
            //创建消息bytes储存DNS报文
            byte[] receiveBytes = new byte[receiveByteBuf.readableBytes()];
            //读取DNS报文
            receiveByteBuf.readBytes(receiveBytes);
            //创建DNS报文对象
            Message message = new Message(receiveBytes);

            //查询DNS记录
            ProcessRequest.find(message, true, datagramPacket.sender().getHostString());

            //返回查询结果
            channelHandlerContext.writeAndFlush(new DatagramPacket(Unpooled.copiedBuffer(message.toWire()), datagramPacket.sender()));

        } catch (IOException ignored) {}
    }
}
