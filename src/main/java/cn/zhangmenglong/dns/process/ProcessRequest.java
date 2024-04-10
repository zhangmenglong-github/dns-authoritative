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
import java.util.Set;

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
                    //获取该域名的委托NS RRset
                    RRset nsRRset = querySetResponse.getNS();
                    //如果支持dnssec
                    if (isDnssec) {
                        //获取ns记录的rrsig记录
                        List<RRSIGRecord> nsSigs = nsRRset.sigs();
                        for (RRSIGRecord sig : nsSigs) {
                            //循环将ns记录的rrsig记录添加到权威区域
                            message.addRecord(sig, Section.AUTHORITY);
                        }
                    }
                    //获取ns记录的rrs
                    List<Record> nsRRS = nsRRset.rrs();
                    //循环将该域名的委托NS记录写入message并且判断该域名的委托NS记录是否为该域名本身的子域
                    for (Record nsRecord : nsRRS) {
                        //添加NS记录到权威段落
                        message.addRecord(nsRecord, Section.AUTHORITY);
                        //如果NS记录中的委托域名是该域名的子域就查询该区域中被委托区域域名的A/AAAA记录
                        if (nsRecord.getAdditionalName().subdomain(nsRecord.getName())) {
                            //查询被委托子域的A记录
                            RRset additionalNameARRset = queryZone.findExactMatch(geoCode, nsRecord.getAdditionalName(), Type.A);
                            //查询被委托子域的AAAA记录
                            RRset additionalNameAAAARRset = queryZone.findExactMatch(geoCode, nsRecord.getAdditionalName(), Type.AAAA);
                            //如果被委托子域的A记录不为空就将对应A记录添加到附加段落
                            if (additionalNameARRset != null) {
                                //如果支持dnssec就将委托子域的A记录的rrsig记录添加到附加区域
                                if (isDnssec) {
                                    List<RRSIGRecord> additionalNameASigs = additionalNameARRset.sigs();
                                    for (RRSIGRecord sig : additionalNameASigs) {
                                        message.addRecord(sig, Section.ADDITIONAL);
                                    }
                                }
                                //将查询到的A记录添加到附加区域
                                List<Record> additionalNameARRsetList = additionalNameARRset.rrs();
                                for (Record aRecord : additionalNameARRsetList) {
                                    message.addRecord(aRecord, Section.ADDITIONAL);
                                }
                            } else if (!geoCode.contentEquals("*")) {
                                //如果对应geo的委托子域没有查找到，就查找默认区域
                                additionalNameARRset = queryZone.findExactMatch("*", nsRecord.getAdditionalName(), Type.A);
                                if (additionalNameARRset != null) {
                                    if (isDnssec) {
                                        List<RRSIGRecord> additionalNameASigs = additionalNameARRset.sigs();
                                        for (RRSIGRecord sig : additionalNameASigs) {
                                            message.addRecord(sig, Section.ADDITIONAL);
                                        }
                                    }
                                    List<Record> additionalNameARRsetList = additionalNameARRset.rrs();
                                    for (Record aRecord : additionalNameARRsetList) {
                                        message.addRecord(aRecord, Section.ADDITIONAL);
                                    }
                                }
                            }
                            //如果被委托子域的AAAA记录不为空就将对应A记录添加到附加段落
                            if (additionalNameAAAARRset != null) {
                                if (isDnssec) {
                                    List<RRSIGRecord> additionalNameAAAASigs = additionalNameAAAARRset.sigs();
                                    for (RRSIGRecord sig : additionalNameAAAASigs) {
                                        message.addRecord(sig, Section.ADDITIONAL);
                                    }
                                }
                                List<Record> additionalNameAAAARRsetList = additionalNameAAAARRset.rrs();
                                for (Record aaaaRecord : additionalNameAAAARRsetList) {
                                    message.addRecord(aaaaRecord, Section.ADDITIONAL);
                                }
                            } else if (!geoCode.contentEquals("*")) {
                                additionalNameAAAARRset = queryZone.findExactMatch("*", nsRecord.getAdditionalName(), Type.AAAA);
                                if (additionalNameAAAARRset != null) {
                                    if (isDnssec) {
                                        List<RRSIGRecord> additionalNameAAAASigs = additionalNameAAAARRset.sigs();
                                        for (RRSIGRecord sig : additionalNameAAAASigs) {
                                            message.addRecord(sig, Section.ADDITIONAL);
                                        }
                                    }
                                    List<Record> additionalNameAAAARRsetList = additionalNameAAAARRset.rrs();
                                    for (Record aaaaRecord : additionalNameAAAARRsetList) {
                                        message.addRecord(aaaaRecord, Section.ADDITIONAL);
                                    }
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
                }  else if (querySetResponse.isCNAME()) {
                    RRset cnameRRset = queryZone.findExactMatch(geoCode, querySetResponse.getCNAME().getName(), Type.CNAME);
                    if (isDnssec) {
                        List<RRSIGRecord> cnameSigs = cnameRRset.sigs();
                        for (Record sig : cnameSigs) {
                            message.addRecord(sig, Section.ANSWER);
                        }
                    }
                    List<Record> cnameRRS = cnameRRset.rrs();
                    for (Record cnameRecord : cnameRRS) {
                        message.addRecord(cnameRecord, Section.ANSWER);
                    }
                    message.getHeader().setFlag(Flags.QR);
                    message.getHeader().setFlag(Flags.RD);
                    message.getHeader().setFlag(Flags.AA);
                    if (isUdp && (message.toWire().length > 512)) {
                        message.getHeader().setFlag(Flags.TC);
                    }
                } else if (querySetResponse.isDNAME()) {
                    RRset dnameRRset = queryZone.findExactMatch(geoCode, querySetResponse.getDNAME().getName(), Type.DNAME);
                    if (isDnssec) {
                        List<RRSIGRecord> dnameSigs = dnameRRset.sigs();
                        for (Record sig : dnameSigs) {
                            message.addRecord(sig, Section.ADDITIONAL);
                        }
                    }
                    List<Record> dnameRRS = dnameRRset.rrs();
                    for (Record dnameRecord : dnameRRS) {
                        message.addRecord(dnameRecord, Section.ANSWER);
                    }
                    message.getHeader().setFlag(Flags.QR);
                    message.getHeader().setFlag(Flags.RD);
                    message.getHeader().setFlag(Flags.AA);
                    if (isUdp && (message.toWire().length > 512)) {
                        message.getHeader().setFlag(Flags.TC);
                    }
                } else if (querySetResponse.isSuccessful()) {
                    List<RRset> querySetResponseRRsetList = querySetResponse.answers();
                    for (RRset querySetResponseRRset : querySetResponseRRsetList) {
                        if (isDnssec) {
                            List<RRSIGRecord> querySetResponseSigs = querySetResponseRRset.sigs();
                            for (RRSIGRecord querySetResponseSig : querySetResponseSigs) {
                                message.addRecord(querySetResponseSig, Section.ANSWER);
                            }
                        }
                        List<Record> answerRecordList = querySetResponseRRset.rrs();
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
                        //在该区域中查询该域名对应记录
                        querySetResponse = queryZone.findRecords("*", queryName, message.getQuestion().getType());

                        //如果查询到该域名是委托域名
                        if (querySetResponse.isDelegation()) {
                            //获取该域名的委托NS RRset
                            RRset nsRRset = querySetResponse.getNS();
                            //如果支持dnssec
                            if (isDnssec) {
                                //获取ns记录的rrsig记录
                                List<RRSIGRecord> nsSigs = nsRRset.sigs();
                                for (RRSIGRecord sig : nsSigs) {
                                    //循环将ns记录的rrsig记录添加到权威区域
                                    message.addRecord(sig, Section.AUTHORITY);
                                }
                            }
                            //获取ns记录的rrs
                            List<Record> nsRRS = nsRRset.rrs();
                            //循环将该域名的委托NS记录写入message并且判断该域名的委托NS记录是否为该域名本身的子域
                            for (Record nsRecord : nsRRS) {
                                //添加NS记录到权威段落
                                message.addRecord(nsRecord, Section.AUTHORITY);
                                //如果NS记录中的委托域名是该域名的子域就查询该区域中被委托区域域名的A/AAAA记录
                                if (nsRecord.getAdditionalName().subdomain(nsRecord.getName())) {
                                    //查询被委托子域的A记录
                                    RRset additionalNameARRset = queryZone.findExactMatch("*", nsRecord.getAdditionalName(), Type.A);
                                    //查询被委托子域的AAAA记录
                                    RRset additionalNameAAAARRset = queryZone.findExactMatch("*", nsRecord.getAdditionalName(), Type.AAAA);
                                    //如果被委托子域的A记录不为空就将对应A记录添加到附加段落
                                    if (additionalNameARRset != null) {
                                        //如果支持dnssec就将委托子域的A记录的rrsig记录添加到附加区域
                                        if (isDnssec) {
                                            List<RRSIGRecord> additionalNameASigs = additionalNameARRset.sigs();
                                            for (RRSIGRecord sig : additionalNameASigs) {
                                                message.addRecord(sig, Section.ADDITIONAL);
                                            }
                                        }
                                        //将查询到的A记录添加到附加区域
                                        List<Record> additionalNameARRsetList = additionalNameARRset.rrs();
                                        for (Record aRecord : additionalNameARRsetList) {
                                            message.addRecord(aRecord, Section.ADDITIONAL);
                                        }
                                    }
                                    //如果被委托子域的AAAA记录不为空就将对应A记录添加到附加段落
                                    if (additionalNameAAAARRset != null) {
                                        if (isDnssec) {
                                            List<RRSIGRecord> additionalNameAAAASigs = additionalNameAAAARRset.sigs();
                                            for (RRSIGRecord sig : additionalNameAAAASigs) {
                                                message.addRecord(sig, Section.ADDITIONAL);
                                            }
                                        }
                                        List<Record> additionalNameAAAARRsetList = additionalNameAAAARRset.rrs();
                                        for (Record aaaaRecord : additionalNameAAAARRsetList) {
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
                        }  else if (querySetResponse.isCNAME()) {
                            RRset cnameRRset = queryZone.findExactMatch("*", querySetResponse.getCNAME().getName(), Type.CNAME);
                            if (isDnssec) {
                                List<RRSIGRecord> cnameSigs = cnameRRset.sigs();
                                for (Record sig : cnameSigs) {
                                    message.addRecord(sig, Section.ANSWER);
                                }
                            }
                            List<Record> cnameRRS = cnameRRset.rrs();
                            for (Record cnameRecord : cnameRRS) {
                                message.addRecord(cnameRecord, Section.ANSWER);
                            }
                            message.getHeader().setFlag(Flags.QR);
                            message.getHeader().setFlag(Flags.RD);
                            message.getHeader().setFlag(Flags.AA);
                            if (isUdp && (message.toWire().length > 512)) {
                                message.getHeader().setFlag(Flags.TC);
                            }
                        } else if (querySetResponse.isDNAME()) {
                            RRset dnameRRset = queryZone.findExactMatch("*", querySetResponse.getDNAME().getName(), Type.DNAME);
                            if (isDnssec) {
                                List<RRSIGRecord> dnameSigs = dnameRRset.sigs();
                                for (Record sig : dnameSigs) {
                                    message.addRecord(sig, Section.ADDITIONAL);
                                }
                            }
                            List<Record> dnameRRS = dnameRRset.rrs();
                            for (Record dnameRecord : dnameRRS) {
                                message.addRecord(dnameRecord, Section.ANSWER);
                            }
                            message.getHeader().setFlag(Flags.QR);
                            message.getHeader().setFlag(Flags.RD);
                            message.getHeader().setFlag(Flags.AA);
                            if (isUdp && (message.toWire().length > 512)) {
                                message.getHeader().setFlag(Flags.TC);
                            }
                        } else if (querySetResponse.isSuccessful()) {
                            List<RRset> querySetResponseRRsetList = querySetResponse.answers();
                            for (RRset querySetResponseRRset : querySetResponseRRsetList) {
                                if (isDnssec) {
                                    List<RRSIGRecord> querySetResponseSigs = querySetResponseRRset.sigs();
                                    for (RRSIGRecord querySetResponseSig : querySetResponseSigs) {
                                        message.addRecord(querySetResponseSig, Section.ANSWER);
                                    }
                                }
                                List<Record> answerRecordList = querySetResponseRRset.rrs();
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
                            if (isDnssec) {
                                RRset soaRRset = queryZone.findExactMatch("*", queryName, Type.SOA);
                                RRset nsecRRset = queryZone.findExactMatch("*", queryName, Type.NSEC);
                                List<RRSIGRecord> soaSigs = soaRRset.sigs();
                                for (RRSIGRecord soaSig : soaSigs) {
                                    message.addRecord(soaSig, Section.AUTHORITY);
                                }
                                List<Record> nsecRecordList = nsecRRset.rrs();
                                for (Record nsecRecord : nsecRecordList) {
                                    message.addRecord(nsecRecord, Section.AUTHORITY);
                                }
                                List<RRSIGRecord> nsecSigs = nsecRRset.sigs();
                                for (RRSIGRecord nsecSig : nsecSigs) {
                                    message.addRecord(nsecSig, Section.AUTHORITY);
                                }
                            }
                            message.addRecord(queryZone.getSOA(), Section.AUTHORITY);
                            message.getHeader().setFlag(Flags.QR);
                            message.getHeader().setFlag(Flags.RD);
                            message.getHeader().setFlag(Flags.AA);
                        }
                    } else {
                        if (isDnssec) {
                            RRset soaRRset = queryZone.findExactMatch("*", queryName, Type.SOA);
                            RRset nsecRRset = queryZone.findExactMatch("*", queryName, Type.NSEC);
                            List<RRSIGRecord> soaSigs = soaRRset.sigs();
                            for (RRSIGRecord soaSig : soaSigs) {
                                message.addRecord(soaSig, Section.AUTHORITY);
                            }
                            List<Record> nsecRecordList = nsecRRset.rrs();
                            for (Record nsecRecord : nsecRecordList) {
                                message.addRecord(nsecRecord, Section.AUTHORITY);
                            }
                            List<RRSIGRecord> nsecSigs = nsecRRset.sigs();
                            for (RRSIGRecord nsecSig : nsecSigs) {
                                message.addRecord(nsecSig, Section.AUTHORITY);
                            }
                        }
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
