package cn.zhangmenglong.dns.thread;

import cn.zhangmenglong.dns.process.ProcessRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.xbill.DNS.Message;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

@ChannelHandler.Sharable
public class TCPThread extends ChannelInboundHandlerAdapter {

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg){

        try {

            //获取UDP报文content
            ByteBuf receiveByteBuf = (ByteBuf) msg;
            receiveByteBuf.readByte();
            receiveByteBuf.readByte();
            //创建消息bytes储存DNS报文
            byte[] receiveBytes = new byte[receiveByteBuf.readableBytes()];
            //读取DNS报文
            receiveByteBuf.readBytes(receiveBytes);
            //创建DNS报文对象
            Message message = new Message(receiveBytes);

            //查询DNS记录
            ProcessRequest.find(message, false);

            //获取DNS报文字节
            byte[] responseBytes = message.toWire();

            //创建一个ByteBuf包装对象
            ByteBuf responseByteBuf = Unpooled.buffer();

            //写入TCP DNS报文长度
            responseByteBuf.writeShort(responseBytes.length);

            //写入TCP DNS报文内容
            responseByteBuf.writeBytes(responseBytes);

            //返回查询结果
            ctx.writeAndFlush(responseByteBuf);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
