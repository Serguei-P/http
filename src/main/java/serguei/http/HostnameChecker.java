package serguei.http;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

import javax.naming.InvalidNameException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapName;
import javax.naming.ldap.Rdn;
import javax.net.ssl.SSLException;
import javax.security.auth.x500.X500Principal;

import serguei.http.utils.Utils;

public class HostnameChecker {

    private static int DNS_ALT_NAME = 2;
    private static int IP_ALT_NAME = 7;

    public boolean check(String host, X509Certificate cert) throws SSLException {
        List<AltSubjectName> subjectAlternativeNameList = getSubjectAlternativeNames(cert);
        if (!subjectAlternativeNameList.isEmpty()) {
            HostNameFormat hostnameType = determineHostFormat(host);
            switch (hostnameType) {
                case IPv4 :
                    return verifyIPAddress(host, subjectAlternativeNameList);
                case IPv6 :
                    return verifyIPv6Address(host, subjectAlternativeNameList);
                default:
                    return verifyHostname(host, subjectAlternativeNameList);
            }
        } else {
            X500Principal subjectPrincipal = cert.getSubjectX500Principal();
            String cn = extractCN(subjectPrincipal.getName(X500Principal.RFC2253));
            if (cn == null) {
                throw new SSLException("Certificate subject for " + host
                        + " doesn't contain a common name and does not have alternative names");
            }
            return verifyAgainstSubject(host, cn);
        }
    }

    private boolean verifyIPAddress(String host, List<AltSubjectName> altNamesList) throws SSLException {
        for (int i = 0; i < altNamesList.size(); i++) {
            AltSubjectName subjectAlt = altNamesList.get(i);
            if (subjectAlt.getType() == IP_ALT_NAME) {
                if (host.equals(subjectAlt.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean verifyIPv6Address(String host, List<AltSubjectName> altNameList) throws SSLException {
        String normalisedHost = normaliseIpv6Address(host);
        for (int i = 0; i < altNameList.size(); i++) {
            AltSubjectName subjectAlt = altNameList.get(i);
            if (subjectAlt.getType() == IP_ALT_NAME) {
                String normalizedSubjectAlt = normaliseIpv6Address(subjectAlt.getValue());
                if (normalisedHost.equals(normalizedSubjectAlt)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean verifyHostname(String host, List<AltSubjectName> altNameList) throws SSLException {
        String normalizedHost = host.toLowerCase();
        for (int i = 0; i < altNameList.size(); i++) {
            AltSubjectName subjectAlt = altNameList.get(i);
            if (subjectAlt.getType() == DNS_ALT_NAME) {
                String normalizedSubjectAlt = subjectAlt.getValue().toLowerCase();
                if (matchHost(normalizedHost, normalizedSubjectAlt)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean verifyAgainstSubject(String host, String cn) {
        String normalizedHost = host.toLowerCase();
        String normalizedCn = cn.toLowerCase();
        return matchHost(normalizedHost, normalizedCn);
    }

    private boolean matchHost(String host, String name) {
        int asteriskPos = name.indexOf('*');
        if (asteriskPos != -1) {
            String prefix = name.substring(0, asteriskPos);
            String suffix = name.substring(asteriskPos + 1);
            if (!prefix.isEmpty() && !host.startsWith(prefix)) {
                return false;
            }
            if (!suffix.isEmpty() && !host.endsWith(suffix)) {
                return false;
            }
            String remainder = host.substring(prefix.length(), host.length() - suffix.length());
            if (remainder.contains(".")) {
                return false;
            }
            return true;
        }
        return host.equalsIgnoreCase(name);
    }

    private String extractCN(String subjectPrincipal) throws SSLException {
        if (subjectPrincipal == null) {
            return null;
        }
        try {
            LdapName subjectDN = new LdapName(subjectPrincipal);
            List<Rdn> rdnList = subjectDN.getRdns();
            for (int i = rdnList.size() - 1; i >= 0; i--) {
                Rdn rds = rdnList.get(i);
                Attributes attributes = rds.toAttributes();
                Attribute cn = attributes.get("cn");
                if (cn != null) {
                    try {
                        Object value = cn.get();
                        if (value != null) {
                            return value.toString();
                        }
                    } catch (NoSuchElementException ignore) {
                        // ignore
                    } catch (NamingException ignore) {
                        // ignore
                    }
                }
            }
            return null;
        } catch (InvalidNameException e) {
            throw new SSLException(subjectPrincipal + " is not a valid X500 distinguished name");
        }
    }

    private HostNameFormat determineHostFormat(String host) {
        if (Utils.isIPv4Address(host)) {
            return HostNameFormat.IPv4;
        }
        if (Utils.isIPv6Address(host)) {
            return HostNameFormat.IPv6;
        }
        return HostNameFormat.UNRESOLVED;
    }

    private List<AltSubjectName> getSubjectAlternativeNames(X509Certificate cert) {
        try {
            Collection<List<?>> altNamesFromCertificate = cert.getSubjectAlternativeNames();
            if (altNamesFromCertificate == null) {
                return Collections.emptyList();
            }
            List<AltSubjectName> result = new ArrayList<>();
            for (List<?> entry : altNamesFromCertificate) {
                Integer type = entry.size() >= 2 ? (Integer)entry.get(0) : null;
                if (type != null) {
                    if (type == DNS_ALT_NAME || type == IP_ALT_NAME) {
                        Object entryObject = entry.get(1);
                        if (entryObject instanceof String) {
                            result.add(new AltSubjectName((String)entryObject, type));
                        } else if (entryObject instanceof byte[]) {
                            // ASN.1 DER not supported
                        }
                    }
                }
            }
            return result;
        } catch (CertificateParsingException ignore) {
            return Collections.emptyList();
        }
    }

    private String normaliseIpv6Address(String hostname) {
        try {
            InetAddress inetAddress = InetAddress.getByName(hostname);
            return inetAddress.getHostAddress();
        } catch (UnknownHostException unexpected) {
            // Should not happen unless the check for IPv6 was wrong
            return hostname;
        }
    }

    private static class AltSubjectName {

        private final String value;
        private final int type;

        public AltSubjectName(String value, int type) {
            this.value = value;
            this.type = type;
        }

        public int getType() {
            return type;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value + " " + type;
        }

    }

    private enum HostNameFormat {
        UNRESOLVED, IPv4, IPv6;
    }

}
