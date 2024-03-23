package cn.zhangmenglong.dns.zone;

import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.IOException;
import java.io.Serializable;
import java.util.*;

public class DNSZone implements Serializable {
    private static final long serialVersionUID = -9220510891189510942L;
    private Map<String, Map<Name, Object>> data;
    private Name origin;
    private Object originNode;
    private RRset NS;
    private SOARecord SOA;
    private boolean hasWild;

    private void validate(String geo) throws IOException {
        originNode = exactName(geo, origin);
        if (originNode == null) {
            throw new IOException(origin + ": no data specified");
        }
        RRset rrset = oneRRset(originNode, Type.SOA);
        if (rrset == null || rrset.size() != 1) {
            throw new IOException(origin + ": exactly 1 SOA must be specified");
        }
        SOA = (SOARecord) rrset.rrs().get(0);

        NS = oneRRset(originNode, Type.NS);
        if (NS == null) {
            throw new IOException(origin + ": no NS set specified");
        }
    }

    private void maybeAddRecord(String geo, Record record) throws IOException {
        int rtype = record.getType();
        Name name = record.getName();
        if (rtype == Type.SOA && !name.equals(origin)) {
            throw new IOException("SOA owner " + name + " does not match zone origin " + origin);
        }
        if (name.subdomain(origin)) {
            addRecord(geo, record);
        }
    }

    /**
     * Creates a Zone from the records in the specified master file.
     *
     * @param zone The name of the zone.
     * @param file The master file to read from.
     * @see Master
     */
    public DNSZone(String geo, Name zone, String file) throws IOException {
        data = new HashMap<>();
        if (zone == null) {
            throw new IllegalArgumentException("no zone name specified");
        }
        try (Master m = new Master(file, zone)) {
            Record record;

            origin = zone;
            while ((record = m.nextRecord()) != null) {
                maybeAddRecord(geo, record);
            }
        }
        validate(geo);
    }

    /**
     * Creates a Zone from an array of records.
     *
     * @param zone The name of the zone.
     * @param records The records to add to the zone.
     * @see Master
     */
    public DNSZone(String geo, Name zone, Record[] records) throws IOException {
        data = new HashMap<>();
        if (zone == null) {
            throw new IllegalArgumentException("no zone name specified");
        }
        origin = zone;
        for (Record record : records) {
            maybeAddRecord(geo, record);
        }
        validate(geo);
    }


    /** Returns the Zone's origin */
    public Name getOrigin() {
        return origin;
    }

    /** Returns the Zone origin's NS records */
    public RRset getNS() {
        return NS;
    }

    /** Returns the Zone's SOA record */
    public SOARecord getSOA() {
        return SOA;
    }

    /** Returns the Zone's class */
    public int getDClass() {
        return DClass.IN;
    }

    private Object exactName(String geo, Name name) {
        try {
            return data.get(geo).get(name);
        } catch (Exception exception) {
            return null;
        }

    }

    private RRset oneRRset(Object types, int type) {
        if (type == Type.ANY) {
            throw new IllegalArgumentException("oneRRset(ANY)");
        }
        if (types instanceof List) {
            @SuppressWarnings("unchecked")
            List<RRset> list = (List<RRset>) types;
            for (RRset set : list) {
                if (set.getType() == type) {
                    return set;
                }
            }
        } else {
            RRset set = (RRset) types;
            if (set.getType() == type) {
                return set;
            }
        }
        return null;
    }

    private RRset findRRset(String geo, Name name, int type) {
        Object types = exactName(geo, name);
        if (types == null) {
            return null;
        }
        return oneRRset(types, type);
    }

    private void addRRset(String geo, Name name, RRset rrset) {
        if (!hasWild && name.isWild()) {
            hasWild = true;
        }
        Map<Name, Object> geoZone = data.get(geo);
        data.put(geo, (geoZone == null) ? new HashMap<>() : geoZone);
        Object types = data.get(geo).get(name);
        if (types == null) {
            geoZone = data.get(geo);
            geoZone.put(name, rrset);
            data.put(geo, geoZone);
            return;
        }
        int rtype = rrset.getType();
        if (types instanceof List) {
            @SuppressWarnings("unchecked")
            List<RRset> list = (List<RRset>) types;
            for (int i = 0; i < list.size(); i++) {
                RRset set = list.get(i);
                if (set.getType() == rtype) {
                    list.set(i, rrset);
                    return;
                }
            }
            list.add(rrset);
        } else {
            RRset set = (RRset) types;
            if (set.getType() == rtype) {
                geoZone = data.get(geo);
                geoZone.put(name, rrset);
                data.put(geo, geoZone);
            } else {
                LinkedList<RRset> list = new LinkedList<>();
                list.add(set);
                list.add(rrset);
                geoZone = data.get(geo);
                geoZone.put(name, list);
                data.put(geo, geoZone);
            }
        }
    }

    private SetResponse lookup(String geo, Name name, int type) {
        if (!name.subdomain(origin)) {
            return SetResponse.ofType(SetResponse.NXDOMAIN);
        }

        int labels = name.labels();
        int olabels = origin.labels();

        for (int tlabels = olabels; tlabels <= labels; tlabels++) {
            boolean isOrigin = tlabels == olabels;
            boolean isExact = tlabels == labels;

            Name tname;
            if (isOrigin) {
                tname = origin;
            } else if (isExact) {
                tname = name;
            } else {
                tname = new Name(name, labels - tlabels);
            }

            Object types = exactName(geo, tname);
            if (types == null) {
                continue;
            }

            /* If this is a delegation, return that. */
            if (!isOrigin) {
                RRset ns = oneRRset(types, Type.NS);
                if (ns != null) {
                    return new SetResponse(SetResponse.DELEGATION, ns);
                }
            }

            /*
             * If this is the name, look for the actual type or a CNAME.
             * Otherwise, look for a DNAME.
             */
            if (isExact) {
                RRset rrset = oneRRset(types, type);
                if (rrset != null) {
                    return new SetResponse(SetResponse.SUCCESSFUL, rrset);
                }
                rrset = oneRRset(types, Type.CNAME);
                if (rrset != null) {
                    return new SetResponse(SetResponse.CNAME, rrset);
                }
            } else {
                RRset rrset = oneRRset(types, Type.DNAME);
                if (rrset != null) {
                    return new SetResponse(SetResponse.DNAME, rrset);
                }
            }

            /* We found the name, but not the type. */
            if (isExact) {
                return SetResponse.ofType(SetResponse.NXRRSET);
            }
        }

        if (hasWild) {
            for (int i = 0; i < labels - olabels; i++) {
                Name tname = name.wild(i + 1);
                Object types = exactName(geo, tname);
                if (types == null) {
                    continue;
                }

                RRset rrset = oneRRset(types, type);
                if (rrset != null) {
                    return new SetResponse(SetResponse.SUCCESSFUL, expandSet(rrset, name));
                }
            }
        }

        return SetResponse.ofType(SetResponse.NXDOMAIN);
    }

    private RRset expandSet(RRset set, Name tname) {
        RRset expandedSet = new RRset();
        for (Record r : set.rrs()) {
            expandedSet.addRR(r.withName(tname));
        }
        for (RRSIGRecord r : set.sigs()) {
            expandedSet.addRR(r.withName(tname));
        }
        return expandedSet;
    }

    /**
     * Looks up Records in the Zone. The answer can be a {@code CNAME} instead of the actual requested
     * type and wildcards are expanded.
     *
     * @param name The name to look up
     * @param type The type to look up
     * @return A SetResponse object
     * @see SetResponse
     */
    public SetResponse findRecords(String geo, Name name, int type) {
        return lookup(geo, name, type);
    }

    /**
     * Looks up Records in the zone, finding exact matches only.
     *
     * @param name The name to look up
     * @param type The type to look up
     * @return The matching RRset
     * @see RRset
     */
    public RRset findExactMatch(String geo, Name name, int type) {
        Object types = exactName(geo, name);
        if (types == null) {
            return null;
        }
        return oneRRset(types, type);
    }

    /**
     * Adds an RRset to the Zone
     *
     * @param rrset The RRset to be added
     * @see RRset
     */
    public void addRRset(String geo, RRset rrset) {
        Name name = rrset.getName();
        addRRset(geo, name, rrset);
    }

    /**
     * Adds a Record to the Zone
     *
     * @param r The record to be added
     * @see Record
     */
    public <T extends Record> void addRecord(String geo, T r) {
        Name name = r.getName();
        int rtype = r.getRRsetType();
        RRset rrset = findRRset(geo, name, rtype);
        if (rrset == null) {
            rrset = new RRset(r);
            addRRset(geo, name, rrset);
        } else {
            rrset.addRR(r);
        }
    }
}
