package se.arkalix.security.identity;

import java.io.IOException;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Holds certificates associated with <i>trusted</i> Arrowhead systems,
 * operators, clouds, companies and other authorities.
 * <p>
 * Instances of this class are guaranteed to only hold x.509 certificates
 * complying to the Arrowhead certificate {@link ArCertificate naming
 * conventions}.
 *
 * @see <a href="https://tools.ietf.org/html/rfc5280">RFC 5280</a>
 */
public class ArTrustStore {
    private final List<ArCertificate> arCertificates;

    private X509Certificate[] x509Certificates = null;

    /**
     * Creates new x.509 trust store from given array of certificates.
     *
     * @param certificates Trusted certificates.
     * @see <a href="https://tools.ietf.org/html/rfc5280">RFC 5280</a>
     */
    public ArTrustStore(final ArCertificate... certificates) {
        this.arCertificates = List.of(certificates);
    }

    /**
     * Creates new x.509 trust store by collecting all certificates from given
     * initialized {@link KeyStore}.
     *
     * @param keyStore Key store containing trusted certificates.
     * @return New x.509 trust store.
     * @throws KeyStoreException If {@code keyStore} has not been initialized.
     * @see <a href="https://tools.ietf.org/html/rfc5280">RFC 5280</a>
     */
    public static ArTrustStore from(final KeyStore keyStore) throws KeyStoreException {
        final var certificates = new ArrayList<ArCertificate>();
        for (final var alias : Collections.list(keyStore.aliases())) {
            final var certificateChain = keyStore.getCertificateChain(alias);

            final var x509chain = new X509Certificate[certificateChain.length];
            var i = 0;
            for (final var certificate : certificateChain) {
                if (!(certificate instanceof X509Certificate)) {
                    throw new KeyStoreException("Only x.509 certificates " +
                        "are permitted in ArTrustStore instances; the " +
                        "following certificate is of some other type: " +
                        certificate);
                }
                x509chain[i++] = (X509Certificate) certificate;
            }

            certificates.add(ArCertificate.from(x509chain));
        }
        return new ArTrustStore(certificates.toArray(new ArCertificate[0]));
    }

    private ArTrustStore(final List<ArCertificate> certificates) {
        this.arCertificates = Collections.unmodifiableList(certificates);
    }

    /**
     * Repackages the contents of this trust store into an array of Java 1.2
     * x.509 certificates.
     *
     * @return Array of x.509 certificates, representing the contents of this
     * trust store.
     */
    public X509Certificate[] toX509CertificateArray() {
        if (x509Certificates == null) {
            x509Certificates = arCertificates.stream()
                .flatMap(certificate -> Stream.of(certificate.toX509CertificateChain()))
                .distinct()
                .toArray(X509Certificate[]::new);
        }
        return x509Certificates.clone();
    }

    /**
     * Reads JVM-compatible key store from specified path and collects all
     * contained certificates into a created {@link ArTrustStore}.
     * <p>
     * As of Java 11, only the PKCS#12 key store format is mandatory to
     * support for Java implementations.
     *
     * @param path     Filesystem path to key store to load.
     * @param password Key store password, or {@code null} if not required.
     * @return New x.509 trust store.
     * @throws GeneralSecurityException If the key store contains data or
     *                                  details that cannot be interpreted
     *                                  or supported properly.
     * @throws IOException              If the key store at the specified
     *                                  {@code path} could not be read.
     * @see <a href="https://tools.ietf.org/html/rfc5280">RFC 5280</a>
     * @see <a href="https://tools.ietf.org/html/rfc7292">RFC 7292</a>
     */
    public static ArTrustStore read(final Path path, final char[] password)
        throws GeneralSecurityException, IOException
    {
        final var file = path.toFile();
        final var keyStore = password != null
            ? KeyStore.getInstance(file, password)
            : KeyStore.getInstance(file, (KeyStore.LoadStoreParameter) null);
        return from(keyStore);
    }

    /**
     * @return Unmodifiable list of trusted Arrowhead certificates.
     * @see <a href="https://tools.ietf.org/html/rfc5280">RFC 5280</a>
     */
    public List<ArCertificate> certificates() {
        return arCertificates;
    }
}