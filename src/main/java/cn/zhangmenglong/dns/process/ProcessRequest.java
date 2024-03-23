package cn.zhangmenglong.dns.process;

import cn.zhangmenglong.dns.zone.DNSZone;
import cn.zhangmenglong.dns.zone.SetResponse;
import cn.zhangmenglong.dns.zone.ZoneMap;
import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.List;

public class ProcessRequest {

    private static final int DNSSEC_SUPPORT_FLAG = 32768;


    public static DatabaseReader reader = null;
    static {
        try {
            InputStream inputStream = ProcessRequest.class.getResourceAsStream("/geo.mmdb");
            reader = new DatabaseReader.Builder(inputStream).withCache(new CHMCache()).build();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String getClientIp(OPTRecord optRecord) {
        try {
            return ((ClientSubnetOption)(optRecord.getOptions(EDNSOption.Code.CLIENT_SUBNET).get(0))).getAddress().getHostAddress();
        } catch (Exception exception) {
            return null;
        }
    }

    private static boolean getClientDnssec(OPTRecord optRecord) {
        try {
            return optRecord.getFlags() == DNSSEC_SUPPORT_FLAG;
        } catch (Exception exception) {
            return false;
        }
    }

    public static void find(Message message, boolean isUdp, String clientIp) {
        //拒绝ANY类型查询
        if (message.getQuestion().getType() != Type.ANY) {

            //获取DNS查询域名
            Name queryName = message.getQuestion().getName();


            //储存DNS查询域名组成部分
            StringBuilder queryNameStringBuilder = new StringBuilder();

            //获取查询域名段落长度
            int queryNameLabelsLength = queryName.labels() - 2;

            //初始化查询区域
            DNSZone queryZone = null;

            //循环拼接查询段落
            for (int index = queryNameLabelsLength; index >= 0; index--) {
                //拼接查询域名段落
                queryNameStringBuilder.insert(0, queryName.getLabelString(index) + ".");
                //查询该域名区域是否存在
                queryZone = ZoneMap.collect.containsKey(queryNameStringBuilder.toString()) ? ZoneMap.collect.get(queryNameStringBuilder.toString()) : queryZone;
            }

            //如果该查询域名的区域存在
            if (queryZone != null) {

                OPTRecord eDNSOptRecord = message.getOPT();
                boolean isDnssec = false;
                if (eDNSOptRecord != null) {
                    isDnssec = getClientDnssec(message.getOPT());
                    String ednsIp = getClientIp(eDNSOptRecord);
                    clientIp = (ednsIp == null) ? clientIp : ednsIp;
                }

                String geoCode;

                try {
                    CityResponse response = reader.city(InetAddress.getByName(clientIp));
                    geoCode = response.getCountry().getIsoCode();
                    geoCode = (geoCode == null) ? "*" : geoCode;
                } catch (IOException | GeoIp2Exception ignored) {
                    geoCode = "*";
                }


                //在该区域中查询该域名对应记录
                SetResponse querySetResponse = queryZone.findRecords(geoCode, queryName, message.getQuestion().getType());

                //如果查询到该域名是委托域名
                if (querySetResponse.isDelegation()) {

                    //获取该域名的委托NS记录
                    List<Record> nsRRS = querySetResponse.getNS().rrs();

                    //循环将该域名的委托NS记录写入message并且判断该域名的委托NS记录是否为该域名本身的子域
                    for (Record nsRecord : nsRRS) {
                        //添加NS记录到权威段落
                        message.addRecord(nsRecord, Section.AUTHORITY);
                        //如果NS记录中的委托域名是该域名的子域就查询该区域中被委托区域域名的A/AAAA记录
                        if (nsRecord.getAdditionalName().subdomain(nsRecord.getName())) {
                            //查询被委托子域的A记录
                            RRset additionalNameARRSet = queryZone.findExactMatch("*", nsRecord.getAdditionalName(), Type.A);
                            //查询被委托子域的AAAA记录
                            RRset additionalNameAAAARRSet = queryZone.findExactMatch("*", nsRecord.getAdditionalName(), Type.AAAA);
                            //如果被委托子域的A记录不为空就将对应A记录添加到附加段落
                            if (additionalNameARRSet != null) {
                                List<Record> additionalNameARRSetList = additionalNameARRSet.rrs();
                                for (Record aRecord : additionalNameARRSetList) {
                                    message.addRecord(aRecord, Section.ADDITIONAL);
                                }
                            }
                            //如果被委托子域的AAAA记录不为空就将对应A记录添加到附加段落
                            if (additionalNameAAAARRSet != null) {
                                List<Record> additionalNameAAAARRSetList = additionalNameAAAARRSet.rrs();
                                for (Record aaaaRecord : additionalNameAAAARRSetList) {
                                    message.addRecord(aaaaRecord, Section.ADDITIONAL);
                                }
                            }
                        }
                    }
                    //设置DNS报文头部为请求响应
                    message.getHeader().setFlag(Flags.QR);
                    message.getHeader().setFlag(Flags.RD);
                    //如果DNS报文长度大于512字节就清空报文区域段落并且设置TCP重传
                    if (isUdp && (message.toWire().length > 512)) {
                        message.getHeader().setFlag(Flags.TC);
                    }
                } else if (querySetResponse.isDNAME()) {
                    message.addRecord(querySetResponse.getDNAME(), Section.ANSWER);
                    message.getHeader().setFlag(Flags.QR);
                    message.getHeader().setFlag(Flags.RD);
                    message.getHeader().setFlag(Flags.AA);
                    if (isUdp && (message.toWire().length > 512)) {
                        message.getHeader().setFlag(Flags.TC);
                    }
                } else if (querySetResponse.isCNAME()) {
                    message.addRecord(querySetResponse.getCNAME(), Section.ANSWER);
                    message.getHeader().setFlag(Flags.QR);
                    message.getHeader().setFlag(Flags.RD);
                    message.getHeader().setFlag(Flags.AA);
                    if (isUdp && (message.toWire().length > 512)) {
                        message.getHeader().setFlag(Flags.TC);
                    }
                } else if (querySetResponse.isSuccessful()) {
                    List<RRset> querySetResponseRRSetList = querySetResponse.answers();
                    for (RRset querySetResponseRRSet : querySetResponseRRSetList) {
                        List<Record> answerRecordList = querySetResponseRRSet.rrs();
                        for (Record answerRecord : answerRecordList) {
                            message.addRecord(answerRecord, Section.ANSWER);
                        }
                    }
                    message.getHeader().setFlag(Flags.QR);
                    message.getHeader().setFlag(Flags.RD);
                    message.getHeader().setFlag(Flags.AA);
                    if (isUdp && (message.toWire().length > 512)) {
                        message.getHeader().setFlag(Flags.TC);
                    }
                } else {

                    if (!geoCode.contentEquals("*")) {
                        geoCode = "*";
                        //在该区域中查询该域名对应记录
                        querySetResponse = queryZone.findRecords(geoCode, queryName, message.getQuestion().getType());

                        //如果查询到该域名是委托域名
                        if (querySetResponse.isDelegation()) {

                            //获取该域名的委托NS记录
                            List<Record> nsRRS = querySetResponse.getNS().rrs();

                            //循环将该域名的委托NS记录写入message并且判断该域名的委托NS记录是否为该域名本身的子域
                            for (Record nsRecord : nsRRS) {
                                //添加NS记录到权威段落
                                message.addRecord(nsRecord, Section.AUTHORITY);
                                //如果NS记录中的委托域名是该域名的子域就查询该区域中被委托区域域名的A/AAAA记录
                                if (nsRecord.getAdditionalName().subdomain(nsRecord.getName())) {
                                    //查询被委托子域的A记录
                                    RRset additionalNameARRSet = queryZone.findExactMatch("*", nsRecord.getAdditionalName(), Type.A);
                                    //查询被委托子域的AAAA记录
                                    RRset additionalNameAAAARRSet = queryZone.findExactMatch("*", nsRecord.getAdditionalName(), Type.AAAA);
                                    //如果被委托子域的A记录不为空就将对应A记录添加到附加段落
                                    if (additionalNameARRSet != null) {
                                        List<Record> additionalNameARRSetList = additionalNameARRSet.rrs();
                                        for (Record aRecord : additionalNameARRSetList) {
                                            message.addRecord(aRecord, Section.ADDITIONAL);
                                        }
                                    }
                                    //如果被委托子域的AAAA记录不为空就将对应A记录添加到附加段落
                                    if (additionalNameAAAARRSet != null) {
                                        List<Record> additionalNameAAAARRSetList = additionalNameAAAARRSet.rrs();
                                        for (Record aaaaRecord : additionalNameAAAARRSetList) {
                                            message.addRecord(aaaaRecord, Section.ADDITIONAL);
                                        }
                                    }
                                }
                            }
                            //设置DNS报文头部为请求响应
                            message.getHeader().setFlag(Flags.QR);
                            message.getHeader().setFlag(Flags.RD);
                            //如果DNS报文长度大于512字节就清空报文区域段落并且设置TCP重传
                            if (isUdp && (message.toWire().length > 512)) {
                                message.getHeader().setFlag(Flags.TC);
                            }
                        } else if (querySetResponse.isDNAME()) {
                            message.addRecord(querySetResponse.getDNAME(), Section.ANSWER);
                            message.getHeader().setFlag(Flags.QR);
                            message.getHeader().setFlag(Flags.RD);
                            message.getHeader().setFlag(Flags.AA);
                            if (isUdp && (message.toWire().length > 512)) {
                                message.getHeader().setFlag(Flags.TC);
                            }
                        } else if (querySetResponse.isCNAME()) {
                            message.addRecord(querySetResponse.getCNAME(), Section.ANSWER);
                            message.getHeader().setFlag(Flags.QR);
                            message.getHeader().setFlag(Flags.RD);
                            message.getHeader().setFlag(Flags.AA);
                            if (isUdp && (message.toWire().length > 512)) {
                                message.getHeader().setFlag(Flags.TC);
                            }
                        } else if (querySetResponse.isSuccessful()) {
                            List<RRset> querySetResponseRRSetList = querySetResponse.answers();
                            for (RRset querySetResponseRRSet : querySetResponseRRSetList) {
                                List<Record> answerRecordList = querySetResponseRRSet.rrs();
                                for (Record answerRecord : answerRecordList) {
                                    message.addRecord(answerRecord, Section.ANSWER);
                                }
                            }
                            message.getHeader().setFlag(Flags.QR);
                            message.getHeader().setFlag(Flags.RD);
                            message.getHeader().setFlag(Flags.AA);
                            if (isUdp && (message.toWire().length > 512)) {
                                message.getHeader().setFlag(Flags.TC);
                            }
                        } else {
                            message.addRecord(queryZone.getSOA(), Section.AUTHORITY);
                            message.getHeader().setFlag(Flags.QR);
                            message.getHeader().setFlag(Flags.RD);
                            message.getHeader().setFlag(Flags.AA);
                        }
                    } else {
                        message.addRecord(queryZone.getSOA(), Section.AUTHORITY);
                        message.getHeader().setFlag(Flags.QR);
                        message.getHeader().setFlag(Flags.RD);
                        message.getHeader().setFlag(Flags.AA);
                    }
                }
            }
        }




    }

}
