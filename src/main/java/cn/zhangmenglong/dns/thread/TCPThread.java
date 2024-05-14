package cn.zhangmenglong.dns.thread;

import cn.zhangmenglong.dns.init.RabbitMQ;
import cn.zhangmenglong.dns.process.ProcessRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.xbill.DNS.Message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

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
            Map<String, Object> queryStatistics = ProcessRequest.find(message, false, ((InetSocketAddress)ctx.channel().remoteAddress()).getHostString());

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

            if (queryStatistics != null) {
                queryStatistics.put("isUdp", false);
                queryStatistics.put("queryTime", System.currentTimeMillis());
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                objectOutputStream.writeObject(queryStatistics);
                objectOutputStream.flush();
                RabbitMQ.send(byteArrayOutputStream.toByteArray());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
