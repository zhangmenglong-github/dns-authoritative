package cn.zhangmenglong.dns.zone;

import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ZoneMap {
    public static final Map<String, DNSZone> collect = new HashMap<>();


    static {

        List<Record> recordList = new LinkedList<>();

        try {
            //Name name, int dclass, long ttl, Name host, Name admin, long serial, long refresh, long retry, long expire, long minimum
            recordList.add(new SOARecord(new Name("zhangmenglong.cn."), DClass.IN, 600, new Name("ns1.zhangmenglong.cn."), new Name("domain@zhangmenglong.cn."), 600, 600, 600, 600, 600));

            //Name name, int dclass, long ttl, Name target
            recordList.add(new NSRecord(new Name("zhangmenglong.cn."), DClass.IN, 600, new Name("ns1.zhangmenglong.cn.")));
            recordList.add(new NSRecord(new Name("zhangmenglong.cn."), DClass.IN, 600, new Name("ns2.zhangmenglong.cn.")));

            //Name name, int dclass, long ttl, InetAddress address
            recordList.add(new ARecord(new Name("*.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("120.78.160.70")));
            recordList.add(new ARecord(new Name("ss.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("120.78.160.71")));

            //Name name, int dclass, long ttl, Name target
            recordList.add(new NSRecord(new Name("dns.zhangmenglong.cn."), DClass.IN, 600, new Name("ns1.dns.zhangmenglong.cn.")));
            recordList.add(new NSRecord(new Name("dns.zhangmenglong.cn."), DClass.IN, 600, new Name("ns2.dns.zhangmenglong.cn.")));

            //Name name, int dclass, long ttl, InetAddress address
            recordList.add(new ARecord(new Name("ns1.dns.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("1.1.1.1")));
            recordList.add(new ARecord(new Name("ns2.dns.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("2.2.2.2")));

            //Name name, int dclass, long ttl, InetAddress address
            recordList.add(new AAAARecord(new Name("ns1.dns.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("::1")));
            recordList.add(new AAAARecord(new Name("ns2.dns.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("::2")));

            //Name name, int dclass, long ttl, Name alias
            recordList.add(new CNAMERecord(new Name("cname.zhangmenglong.cn."), DClass.IN, 600, new Name("1.cname.cn.")));
            recordList.add(new CNAMERecord(new Name("cname.zhangmenglong.cn."), DClass.IN, 600, new Name("2.cname.cn.")));





//
//            recordList.add(new CNAMERecord(new Name("dns.zhangmenglong.cn."), DClass.IN, 600, new Name("ns3.dns.cn.")));
//            recordList.add(new CNAMERecord(new Name("dns.zhangmenglong.cn."), DClass.IN, 600, new Name("ns4.dns.cn.")));




            DNSZone zone = new DNSZone("*", new Name("zhangmenglong.cn."), recordList.toArray(new Record[]{}));

            zone.addRecord("CN", new ARecord(new Name("ss.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("120.78.160.72")));

            Zone z = new Zone(new Name("zhangmenglong.cn."), recordList.toArray(new Record[]{}));

            collect.put("zhangmenglong.cn.",zone);
        } catch (TextParseException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


    }

}
